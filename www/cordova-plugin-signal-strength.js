"use strict";
////////////////////////////////////////////////////////////////
// Generic Cordova Utilities
////////////////////////////////////////////////////////////////
Object.defineProperty(exports, "__esModule", { value: true });
exports.SignalStrength = exports.SignalStrengthCordovaInterface = exports.calculateSignalLevel = exports.calculateSignalPercentage = exports.dbm = exports.CellConnectionStatus = exports.SignalStrengthEventType = exports.CellInfoType = void 0;
function noop() {
    return;
}
function cordovaExec(plugin, method, successCallback, errorCallback, args) {
    if (successCallback === void 0) { successCallback = noop; }
    if (errorCallback === void 0) { errorCallback = noop; }
    if (args === void 0) { args = []; }
    if (window.cordova) {
        window.cordova.exec(successCallback, errorCallback, plugin, method, args);
    }
    else {
        console.warn("".concat(plugin, ".").concat(method, "(...) :: cordova not available"));
        errorCallback && errorCallback("cordova_not_available");
    }
}
function cordovaExecPromise(plugin, method, args) {
    return new Promise(function (resolve, reject) {
        cordovaExec(plugin, method, resolve, reject, args);
    });
}
////////////////////////////////////////////////////////////////
// Plugin Interface
////////////////////////////////////////////////////////////////
var PLUGIN_NAME = 'SignalStrength';
var DEFAULT_BEST_RSSI = -40;
var DEFAULT_WORST_RSSI = -95;
var DEFAULT_MAX_LEVEL = 4;
var DEFAULT_MIN_LEVEL = 0;
/**
 * Translates to known subclasses of CellInfo:
 * https://developer.android.com/reference/android/telephony/CellInfo
 */
var CellInfoType;
(function (CellInfoType) {
    CellInfoType["CDMA"] = "CellInfoCdma";
    CellInfoType["GSM"] = "CellInfoGsm";
    CellInfoType["LTE"] = "CellInfoLte";
    CellInfoType["NR"] = "CellInfoNr";
    CellInfoType["TDSCDMA"] = "CellInfoTdscdma";
    CellInfoType["WCDMA"] = "CellInfoWcdma";
})(CellInfoType || (exports.CellInfoType = CellInfoType = {}));
var SignalStrengthEventType;
(function (SignalStrengthEventType) {
    SignalStrengthEventType["CELL_STATE_UPDATED"] = "cellStateUpdated";
    SignalStrengthEventType["WIFI_STATE_UPDATED"] = "wifiStateUpdated";
})(SignalStrengthEventType || (exports.SignalStrengthEventType = SignalStrengthEventType = {}));
/**
 * Constants reported from CellInfo:
 * https://developer.android.com/reference/android/telephony/CellInfo#constants_1
 */
var CellConnectionStatus;
(function (CellConnectionStatus) {
    CellConnectionStatus[CellConnectionStatus["CONNECTION_NONE"] = 0] = "CONNECTION_NONE";
    CellConnectionStatus[CellConnectionStatus["CONNECTION_PRIMARY_SERVING"] = 1] = "CONNECTION_PRIMARY_SERVING";
    CellConnectionStatus[CellConnectionStatus["CONNECTION_SECONDARY_SERVING"] = 2] = "CONNECTION_SECONDARY_SERVING";
    CellConnectionStatus[CellConnectionStatus["CONNECTION_UNKNOWN"] = 2147483647] = "CONNECTION_UNKNOWN";
})(CellConnectionStatus || (exports.CellConnectionStatus = CellConnectionStatus = {}));
function invoke(method) {
    var args = [];
    for (var _i = 1; _i < arguments.length; _i++) {
        args[_i - 1] = arguments[_i];
    }
    return cordovaExecPromise(PLUGIN_NAME, method, args);
}
function clamp(value, min, max) {
    return Math.min(Math.max(value, min), max);
}
function lerpUnclamped(a, b, t) {
    return a + (b - a) * t;
}
function lerp(a, b, t) {
    var low = Math.min(a, b);
    var high = Math.max(a, b);
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
 * @deprecated use `SignalStrength.getCellInfo()` instead
 */
function dbm(successCallback, errorCallback) {
    cordovaExec(PLUGIN_NAME, 'dbm', successCallback, errorCallback, []);
}
exports.dbm = dbm;
/**
 * Given an rssi and a valid best -> worst rssi range,
 * this will return a floating point value in range [0, 1].
 *
 * NOTE: lower rssi values will drop off logarithmically
 * in an attempt to more closely match the behavior of dBm units.
 */
function calculateSignalPercentage(rssi, bestRssi, worstRssi) {
    if (bestRssi === void 0) { bestRssi = DEFAULT_BEST_RSSI; }
    if (worstRssi === void 0) { worstRssi = DEFAULT_WORST_RSSI; }
    if (rssi >= bestRssi)
        return 1;
    if (bestRssi <= worstRssi || rssi <= worstRssi)
        return 0;
    // variation of the formula the linux kernel uses:
    // https://github.com/torvalds/linux/blob/9ff9b0d392ea08090cd1780fb196f36dbb586529/drivers/net/wireless/intel/ipw2x00/ipw2200.c#L4321
    var range = (bestRssi - worstRssi);
    var deviation = (bestRssi - rssi);
    var scalar = range * range * 100;
    var qualityLoss = deviation * (15 * range + 95 * deviation);
    var percentage = (scalar - qualityLoss) / scalar;
    return clamp(percentage, 0, 1);
}
exports.calculateSignalPercentage = calculateSignalPercentage;
/**
 * Converts the given RSSI to a percentage, and then
 * interpolates between the given min level and max level
 * with that percentage.
 */
function calculateSignalLevel(rssi, maxLevel, minLevel, bestRssi, worstRssi) {
    if (maxLevel === void 0) { maxLevel = DEFAULT_MAX_LEVEL; }
    if (minLevel === void 0) { minLevel = DEFAULT_MIN_LEVEL; }
    if (bestRssi === void 0) { bestRssi = DEFAULT_BEST_RSSI; }
    if (worstRssi === void 0) { worstRssi = DEFAULT_WORST_RSSI; }
    var percentage = calculateSignalPercentage(rssi, bestRssi, worstRssi);
    return Math.floor(lerp(minLevel, maxLevel, percentage));
}
exports.calculateSignalLevel = calculateSignalLevel;
var SignalStrengthCordovaInterface = /** @class */ (function () {
    function SignalStrengthCordovaInterface() {
    }
    SignalStrengthCordovaInterface.prototype.getCellState = function () {
        return invoke('getCellState');
    };
    SignalStrengthCordovaInterface.prototype.getWifiState = function () {
        return invoke('getWifiState').then(normalizeWifiState);
    };
    SignalStrengthCordovaInterface.prototype.setSharedEventDelegate = function (success, error) {
        var successWrapper = function (ev) {
            if (ev.type === SignalStrengthEventType.WIFI_STATE_UPDATED && ev.data) {
                ev.data = normalizeWifiState(ev.data);
            }
            success(ev);
        };
        cordovaExec(PLUGIN_NAME, 'setSharedEventDelegate', successWrapper, error, [false]);
    };
    SignalStrengthCordovaInterface.prototype.removeSharedEventDelegate = function () {
        cordovaExec(PLUGIN_NAME, 'setSharedEventDelegate', undefined, undefined, [true]);
    };
    return SignalStrengthCordovaInterface;
}());
exports.SignalStrengthCordovaInterface = SignalStrengthCordovaInterface;
/**
 * Singleton reference to interact with this cordova plugin
 */
exports.SignalStrength = new SignalStrengthCordovaInterface();
