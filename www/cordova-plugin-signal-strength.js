"use strict";
////////////////////////////////////////////////////////////////
// Generic Cordova Utilities
////////////////////////////////////////////////////////////////
Object.defineProperty(exports, "__esModule", { value: true });
exports.dbm = exports.SignalStrength = exports.SignalStrengthCordovaInterface = exports.CellConnectionStatus = exports.CellInfoType = void 0;
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
function invoke(method) {
    var args = [];
    for (var _i = 1; _i < arguments.length; _i++) {
        args[_i - 1] = arguments[_i];
    }
    return cordovaExecPromise(PLUGIN_NAME, method, args);
}
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
var SignalStrengthCordovaInterface = /** @class */ (function () {
    function SignalStrengthCordovaInterface() {
    }
    SignalStrengthCordovaInterface.prototype.getCellInfo = function () {
        return invoke('getCellInfo');
    };
    return SignalStrengthCordovaInterface;
}());
exports.SignalStrengthCordovaInterface = SignalStrengthCordovaInterface;
/**
 * Singleton reference to interact with this cordova plugin
 */
exports.SignalStrength = new SignalStrengthCordovaInterface();
/**
 * Provided for backwards compatibility - this will be removed in a future release.
 * @deprecated use `SignalStrength.getCellInfo()` instead
 */
function dbm(successCallback, errorCallback) {
    cordovaExec(PLUGIN_NAME, 'dbm', successCallback, errorCallback, []);
}
exports.dbm = dbm;
