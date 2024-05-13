////////////////////////////////////////////////////////////////
// Generic Cordova Utilities
////////////////////////////////////////////////////////////////
function noop() {
    return;
}
function cordovaExec(plugin, method, successCallback = noop, errorCallback = noop, args = []) {
    if (window.cordova) {
        window.cordova.exec(successCallback, errorCallback, plugin, method, args);
    }
    else {
        console.warn(`${plugin}.${method}(...) :: cordova not available`);
        errorCallback && errorCallback(`cordova_not_available`);
    }
}
function cordovaExecPromise(plugin, method, args) {
    return new Promise((resolve, reject) => {
        cordovaExec(plugin, method, resolve, reject, args);
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
export var CellInfoType;
(function (CellInfoType) {
    CellInfoType["CDMA"] = "CellInfoCdma";
    CellInfoType["GSM"] = "CellInfoGsm";
    CellInfoType["LTE"] = "CellInfoLte";
    CellInfoType["NR"] = "CellInfoNr";
    CellInfoType["TDSCDMA"] = "CellInfoTdscdma";
    CellInfoType["WCDMA"] = "CellInfoWcdma";
})(CellInfoType || (CellInfoType = {}));
export var SignalStrengthEventType;
(function (SignalStrengthEventType) {
    SignalStrengthEventType["CELL_STATE_UPDATED"] = "cellStateUpdated";
    SignalStrengthEventType["WIFI_STATE_UPDATED"] = "wifiStateUpdated";
})(SignalStrengthEventType || (SignalStrengthEventType = {}));
/**
 * Constants reported from CellInfo:
 * https://developer.android.com/reference/android/telephony/CellInfo#constants_1
 */
export var CellConnectionStatus;
(function (CellConnectionStatus) {
    CellConnectionStatus[CellConnectionStatus["CONNECTION_NONE"] = 0] = "CONNECTION_NONE";
    CellConnectionStatus[CellConnectionStatus["CONNECTION_PRIMARY_SERVING"] = 1] = "CONNECTION_PRIMARY_SERVING";
    CellConnectionStatus[CellConnectionStatus["CONNECTION_SECONDARY_SERVING"] = 2] = "CONNECTION_SECONDARY_SERVING";
    CellConnectionStatus[CellConnectionStatus["CONNECTION_UNKNOWN"] = 2147483647] = "CONNECTION_UNKNOWN";
})(CellConnectionStatus || (CellConnectionStatus = {}));
function invoke(method, ...args) {
    return cordovaExecPromise(PLUGIN_NAME, method, args);
}
function clamp(value, min, max) {
    return Math.min(Math.max(value, min), max);
}
function lerpUnclamped(a, b, t) {
    return a + (b - a) * t;
}
function lerp(a, b, t) {
    const low = Math.min(a, b);
    const high = Math.max(a, b);
    return clamp(lerpUnclamped(a, b, t), low, high);
}
function normalizeWifiState(state) {
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
export function dbm(successCallback, errorCallback) {
    if (typeof errorCallback !== 'function') {
        // backward compatibility shim
        errorCallback = () => {
            successCallback({ dbm: -1, level: 0 });
        };
    }
    cordovaExec(PLUGIN_NAME, 'dbm', successCallback, errorCallback, []);
}
/**
 * Given an rssi and a valid best -> worst rssi range,
 * this will return a floating point value in range [0, 1].
 *
 * NOTE: lower rssi values will drop off logarithmically
 * in an attempt to more closely match the behavior of dBm units.
 */
export function calculateSignalPercentage(rssi, bestRssi = DEFAULT_BEST_RSSI, worstRssi = DEFAULT_WORST_RSSI) {
    if (rssi >= bestRssi)
        return 1;
    if (bestRssi <= worstRssi || rssi <= worstRssi)
        return 0;
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
export function calculateSignalLevel(rssi, maxLevel = DEFAULT_MAX_LEVEL, minLevel = DEFAULT_MIN_LEVEL, bestRssi = DEFAULT_BEST_RSSI, worstRssi = DEFAULT_WORST_RSSI) {
    const percentage = calculateSignalPercentage(rssi, bestRssi, worstRssi);
    return Math.floor(lerp(minLevel, maxLevel, percentage));
}
export class SignalStrengthCordovaInterface {
    constructor() {
    }
    getCellState() {
        return invoke('getCellState');
    }
    getWifiState() {
        return invoke('getWifiState').then(normalizeWifiState);
    }
    setSharedEventDelegate(success, error) {
        const successWrapper = (ev) => {
            if (ev.type === SignalStrengthEventType.WIFI_STATE_UPDATED && ev.data) {
                ev.data = normalizeWifiState(ev.data);
            }
            success(ev);
        };
        cordovaExec(PLUGIN_NAME, 'setSharedEventDelegate', successWrapper, error, [false]);
    }
    removeSharedEventDelegate() {
        cordovaExec(PLUGIN_NAME, 'setSharedEventDelegate', undefined, undefined, [true]);
    }
}
/**
 * Singleton reference to interact with this cordova plugin
 */
export const SignalStrength = new SignalStrengthCordovaInterface();
