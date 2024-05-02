type SuccessCallback<TValue> = (value: TValue) => void;
type ErrorCallback = (error: any) => void;
/**
 * Translates to known subclasses of CellInfo:
 * https://developer.android.com/reference/android/telephony/CellInfo
 */
export declare enum CellInfoType {
    CDMA = "CellInfoCdma",
    GSM = "CellInfoGsm",
    LTE = "CellInfoLte",
    NR = "CellInfoNr",
    TDSCDMA = "CellInfoTdscdma",
    WCDMA = "CellInfoWcdma"
}
/**
 * Constants reported from CellInfo:
 * https://developer.android.com/reference/android/telephony/CellInfo#constants_1
 */
export declare enum CellConnectionStatus {
    CONNECTION_NONE = 0,
    CONNECTION_PRIMARY_SERVING = 1,
    CONNECTION_SECONDARY_SERVING = 2,
    CONNECTION_UNKNOWN = 2147483647
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
export declare class SignalStrengthCordovaInterface {
    constructor();
    getCellInfo(): Promise<CellInfoWithAlternates>;
}
/**
 * Singleton reference to interact with this cordova plugin
 */
export declare const SignalStrength: SignalStrengthCordovaInterface;
/**
 * Provided for backwards compatibility - this will be removed in a future release.
 * @deprecated use `SignalStrength.getCellInfo()` instead
 */
export declare function dbm(successCallback: SuccessCallback<CellInfoWithAlternates>, errorCallback: ErrorCallback): void;
export {};