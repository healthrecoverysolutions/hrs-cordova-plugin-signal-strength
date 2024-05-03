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

export class SignalStrengthCordovaInterface {

    constructor() {
    }

    public getCellInfo(): Promise<CellInfoWithAlternates> {
        return invoke('getCellInfo');
    }
}

/**
 * Singleton reference to interact with this cordova plugin
 */
export const SignalStrength = new SignalStrengthCordovaInterface();

/**
 * Provided for backwards compatibility - this will be removed in a future release.
 * @deprecated use `SignalStrength.getCellInfo()` instead
 */
export function dbm(successCallback: SuccessCallback<CellInfoWithAlternates>, errorCallback: ErrorCallback): void {
    cordovaExec<CellInfoWithAlternates>(PLUGIN_NAME, 'dbm', successCallback, errorCallback, []);
}
