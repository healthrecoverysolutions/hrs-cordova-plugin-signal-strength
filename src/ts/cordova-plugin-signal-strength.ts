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
     * True if at least one registered cell provider was found.
     */
    hasRegisteredCellProvider: boolean;
    /**
     * Any CellInfo instances past the first found "primary"
     * that also report themselves as primary (see CellConnectionStatus)
     */
    alternatePrimary: CellInfo[];
    /**
     * Any registered non-primary CellInfo instances.
     * NOTE: although this is named "secondary", these instances
     * may not have a "secondary serving" status.
     */
    secondary: CellInfo[];
}

export class SignalStrengthCordovaInterface {

    constructor() {
    }

    /**
     * @deprecated use getCellInfo() instead
     */
    public dbm(): Promise<CellInfoWithAlternates> {
        return invoke('dbm');
    }

    public getCellInfo(): Promise<CellInfoWithAlternates> {
        return invoke('getCellInfo');
    }
}

/**
 * Singleton reference to interact with this cordova plugin
 */
export const SignalStrength = new SignalStrengthCordovaInterface();