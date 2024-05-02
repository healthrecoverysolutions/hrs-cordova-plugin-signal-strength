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
function invoke(method, ...args) {
    return cordovaExecPromise(PLUGIN_NAME, method, args);
}
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
export class SignalStrengthCordovaInterface {
    constructor() {
    }
    /**
     * @deprecated use getCellInfo() instead
     */
    dbm() {
        return invoke('dbm');
    }
    getCellInfo() {
        return invoke('getCellInfo');
    }
}
/**
 * Singleton reference to interact with this cordova plugin
 */
export const SignalStrength = new SignalStrengthCordovaInterface();
