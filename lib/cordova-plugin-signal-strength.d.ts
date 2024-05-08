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
export declare enum SignalStrengthEventType {
    CELL_STATE_UPDATED = "cellStateUpdated",
    WIFI_STATE_UPDATED = "wifiStateUpdated"
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
/**
 * Provided for backwards compatibility - this will be removed in a future release.
 * @deprecated use `SignalStrength.getCellInfo()` instead
 */
export declare function dbm(successCallback: SuccessCallback<CellState>, errorCallback: ErrorCallback): void;
/**
 * Given an rssi and a valid best -> worst rssi range,
 * this will return a floating point value in range [0, 1].
 *
 * NOTE: lower rssi values will drop off logarithmically
 * in an attempt to more closely match the behavior of dBm units.
 */
export declare function calculateSignalPercentage(rssi: number, bestRssi?: number, worstRssi?: number): number;
/**
 * Converts the given RSSI to a percentage, and then
 * interpolates between the given min level and max level
 * with that percentage.
 */
export declare function calculateSignalLevel(rssi: number, maxLevel?: number, minLevel?: number, bestRssi?: number, worstRssi?: number): number;
export declare class SignalStrengthCordovaInterface {
    constructor();
    getCellState(): Promise<CellState>;
    getWifiState(): Promise<WifiState>;
    setSharedEventDelegate(success: SuccessCallback<SignalStrengthEvent>, error?: ErrorCallback): void;
    removeSharedEventDelegate(): void;
}
/**
 * Singleton reference to interact with this cordova plugin
 */
export declare const SignalStrength: SignalStrengthCordovaInterface;
export {};
