////////////////////////////////////////////////////////////////
// Generic Cordova Utilities
////////////////////////////////////////////////////////////////

type SuccessCallback<TValue> = (value: TValue) => void;
type ErrorCallback = (error: any) => void;

function noop() {
    return;
}

function cordovaExec<T>(
    plugin: string,
	method: string,
	successCallback: SuccessCallback<T> = noop,
	errorCallback: ErrorCallback = noop,
	args: any[] = [],
): void {
    if (window.cordova) {
        window.cordova.exec(successCallback, errorCallback, plugin, method, args);

    } else {
        console.warn(`${plugin}.${method}(...) :: cordova not available`);
        errorCallback && errorCallback(`cordova_not_available`);
    }
}

function cordovaExecPromise<T>(plugin: string, method: string, args?: any[]): Promise<T> {
    return new Promise<T>((resolve, reject) => {
        cordovaExec<T>(plugin, method, resolve, reject, args);
    });
}

////////////////////////////////////////////////////////////////
// Plugin Interface
////////////////////////////////////////////////////////////////

const PLUGIN_NAME = 'SignalStrength';
const DEFAULT_BEST_RSSI = -40;
const DEFAULT_WORST_RSSI = -95;
const DEFAULT_MAX_LEVEL = 4;
const DEFAULT_MIN_LEVEL = 0;

/**
 * Translates to known subclasses of CellInfo:
 * https://developer.android.com/reference/android/telephony/CellInfo
 */
export enum CellInfoType {
    CDMA = 'CellInfoCdma',
    GSM = 'CellInfoGsm',
    LTE = 'CellInfoLte',
    NR = 'CellInfoNr',
    TDSCDMA = 'CellInfoTdscdma',
    WCDMA = 'CellInfoWcdma'
}

export enum SignalStrengthEventType {
    CELL_STATE_UPDATED = 'cellStateUpdated',
    WIFI_STATE_UPDATED = 'wifiStateUpdated'
}

/**
 * Constants reported from CellInfo:
 * https://developer.android.com/reference/android/telephony/CellInfo#constants_1
 */
export enum CellConnectionStatus {
    CONNECTION_NONE = 0,
    CONNECTION_PRIMARY_SERVING = 1,
    CONNECTION_SECONDARY_SERVING = 2,
    CONNECTION_UNKNOWN = 0x7fffffff
}

export interface CellInfo {
    cellType: CellInfoType;
    connectionStatus: CellConnectionStatus;
    /**
     * True if dbm and level were populated successfully.
     */
    cellDataLoaded: boolean;
    dbm: number;
    level: number;
}

export interface CellState extends CellInfo {
    /**
     * Indicates whether a target "primary" instance was found.
     * See `CellConnectionStatus.CONNECTION_PRIMARY_SERVING` for more info.
     * DBM and level should ideally only be used from the primary (i.e. this object).
     */
    primary: boolean;
    /**
     * Any registered non-primary CellInfo instances.
     */
    alternates: CellInfo[];
}

export interface WifiInfo {
    ssid: string;
    bssid: string;
    networkId: number;
    rssi: number;
    linkSpeedMbps: number;
    level: number;
    maxLevel: number;
    // Below options only supported on Android 11 (API 30) and above
    txLinkSpeedMbps?: number;
    maxTxLinkSpeedMbps?: number;
    rxLinkSpeedMbps?: number;
    maxRxLinkSpeedMbps?: number;
}

export interface WifiState {
    enabled: boolean;
    connected: boolean;
    info: WifiInfo;
}

export interface SignalStrengthEvent {
    type: SignalStrengthEventType;
    data: CellState | WifiState | any;
}

function invoke<T>(method: string, ...args: any[]): Promise<T> {
    return cordovaExecPromise<T>(PLUGIN_NAME, method, args);
}

function clamp(value: number, min: number, max: number): number {
    return Math.min(Math.max(value, min), max);
}

function lerpUnclamped(a: number, b: number, t: number): number {
    return a + (b - a) * t;
}

function lerp(a: number, b: number, t: number): number {
    const low = Math.min(a, b);
    const high = Math.max(a, b);
    return clamp(lerpUnclamped(a, b, t), low, high);
}

function normalizeWifiState(state: WifiState): WifiState {
    if (state && state.info) {
        // If level / max level were not provided due to older android version (< API 30),
        // attempt to calculate these values manually and shim them onto the response object.
        if (typeof state.info.level !== 'number' 
            || typeof state.info.maxLevel !== 'number') {
                state.info.level = calculateSignalLevel(state.info.rssi);
                state.info.maxLevel = DEFAULT_MAX_LEVEL;
        }
    }
    return state;
}

/**
 * Provided for backwards compatibility - this will be removed in a future release.
 * @deprecated use `SignalStrength.getCellState()` instead
 */
export function dbm(successCallback: SuccessCallback<CellState>, errorCallback?: ErrorCallback): void {
    if (typeof errorCallback !== 'function') {
        // backward compatibility shim
        errorCallback = () => {
            successCallback({dbm: -1, level: 0} as any);
        };
    }
    cordovaExec<CellState>(PLUGIN_NAME, 'dbm', successCallback, errorCallback, []);
}

/**
 * Given an rssi and a valid best -> worst rssi range,
 * this will return a floating point value in range [0, 1].
 * 
 * NOTE: lower rssi values will drop off logarithmically
 * in an attempt to more closely match the behavior of dBm units.
 */
export function calculateSignalPercentage(
    rssi: number, 
    bestRssi: number = DEFAULT_BEST_RSSI, 
    worstRssi: number = DEFAULT_WORST_RSSI
): number {
	if (rssi >= bestRssi) return 1;
    if (bestRssi <= worstRssi || rssi <= worstRssi) return 0;

    // variation of the formula the linux kernel uses:
    // https://github.com/torvalds/linux/blob/9ff9b0d392ea08090cd1780fb196f36dbb586529/drivers/net/wireless/intel/ipw2x00/ipw2200.c#L4321
    const range = (bestRssi - worstRssi);
    const deviation = (bestRssi - rssi);
    const scalar = range * range * 100;
    const qualityLoss = deviation * (15 * range + 95 * deviation);
    const percentage = (scalar - qualityLoss) / scalar;

    return clamp(percentage, 0, 1);
}

/**
 * Converts the given RSSI to a percentage, and then
 * interpolates between the given min level and max level
 * with that percentage.
 */
export function calculateSignalLevel(
    rssi: number,
    maxLevel: number = DEFAULT_MAX_LEVEL,
    minLevel: number = DEFAULT_MIN_LEVEL, 
    bestRssi: number = DEFAULT_BEST_RSSI, 
    worstRssi: number = DEFAULT_WORST_RSSI
): number {
    const percentage = calculateSignalPercentage(rssi, bestRssi, worstRssi);
    return Math.floor(lerp(minLevel, maxLevel, percentage));
}

export class SignalStrengthCordovaInterface {

    constructor() {
    }

    public getCellState(): Promise<CellState> {
        return invoke<CellState>('getCellState');
    }

    public getWifiState(): Promise<WifiState> {
        return invoke<WifiState>('getWifiState').then(normalizeWifiState);
    }

    public setSharedEventDelegate(success: SuccessCallback<SignalStrengthEvent>, error?: ErrorCallback): void {
        const successWrapper: SuccessCallback<SignalStrengthEvent> = (ev) => {
            if (ev.type === SignalStrengthEventType.WIFI_STATE_UPDATED && ev.data) {
                ev.data = normalizeWifiState(ev.data);
            }
            success(ev);
        };
        cordovaExec<SignalStrengthEvent>(PLUGIN_NAME, 'setSharedEventDelegate', successWrapper, error, [false]);
    }

    public removeSharedEventDelegate(): void {
        cordovaExec<SignalStrengthEvent>(PLUGIN_NAME, 'setSharedEventDelegate', undefined, undefined, [true]);
    }
}

/**
 * Singleton reference to interact with this cordova plugin
 */
export const SignalStrength = new SignalStrengthCordovaInterface();
