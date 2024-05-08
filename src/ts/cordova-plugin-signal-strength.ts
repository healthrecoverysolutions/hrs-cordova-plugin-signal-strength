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

function invoke<T>(method: string, ...args: any[]): Promise<T> {
    return cordovaExecPromise<T>(PLUGIN_NAME, method, args);
}

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

export interface CellInfoWithAlternates extends CellInfo {
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

export interface WifiInfoWithState {
    connected: boolean;
    info: WifiInfo;
}

const DEFAULT_BEST_RSSI = -40;
const DEFAULT_WORST_RSSI = -95;
const DEFAULT_MAX_LEVEL = 4;
const DEFAULT_MIN_LEVEL = 0;

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

/**
 * Provided for backwards compatibility - this will be removed in a future release.
 * @deprecated use `SignalStrength.getCellInfo()` instead
 */
export function dbm(successCallback: SuccessCallback<CellInfoWithAlternates>, errorCallback: ErrorCallback): void {
    cordovaExec<CellInfoWithAlternates>(PLUGIN_NAME, 'dbm', successCallback, errorCallback, []);
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

function normalizeWifiInfo(info: WifiInfo): WifiInfo {
    // If level / max level were not provided due to older android version (< API 30),
    // attempt to calculate these values manually and shim them onto the response object.
    if (info && (typeof info.level !== 'number' || typeof info.maxLevel !== 'number')) {
        info.level = calculateSignalLevel(info.rssi);
        info.maxLevel = DEFAULT_MAX_LEVEL;
    }
    return info;
}

export enum SignalStrengthEventType {
    CELL_INFO_UPDATED = 'cellInfoUpdated',
    WIFI_INTO_UPDATED = 'wifiInfoUpdated'
}

export interface SignalStrengthEvent {
    type: SignalStrengthEventType;
    data: CellInfoWithAlternates | WifiInfoWithState | any;
}

export class SignalStrengthCordovaInterface {

    constructor() {
    }

    public getCellInfo(): Promise<CellInfoWithAlternates> {
        return invoke<CellInfoWithAlternates>('getCellInfo');
    }

    public getWifiInfo(): Promise<WifiInfo> {
        return invoke<WifiInfo>('getWifiInfo').then(normalizeWifiInfo);
    }

    public setSharedEventDelegate(success: SuccessCallback<SignalStrengthEvent>, error?: ErrorCallback): void {
        const successWrapper: SuccessCallback<SignalStrengthEvent> = (ev) => {
            if (ev.type === SignalStrengthEventType.WIFI_INTO_UPDATED && ev.data?.info) {
                ev.data.info = normalizeWifiInfo(ev.data.info);
            }
            success(ev);
        };
        cordovaExec<SignalStrengthEvent>(PLUGIN_NAME, 'setSharedEventDelegate', successWrapper, error, []);
    }

    public removeSharedEventDelegate(): void {
        cordovaExec<SignalStrengthEvent>(PLUGIN_NAME, 'setSharedEventDelegate', undefined, undefined, [true]);
    }
}

/**
 * Singleton reference to interact with this cordova plugin
 */
export const SignalStrength = new SignalStrengthCordovaInterface();
