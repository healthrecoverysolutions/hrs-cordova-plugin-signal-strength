package com.hrs.signalstrength;

import android.Manifest;
import android.app.Activity;
import android.content.Context;

import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoTdscdma;
import android.telephony.CellSignalStrength;
import android.telephony.TelephonyManager;

import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthCdma;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthWcdma;

import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoWcdma;
import android.telephony.CellInfoGsm;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

public class SignalStrength extends CordovaPlugin {
    /**
     * included for backward compatibility; will be removed in a future plugin version
     * @deprecated use ACTION_GET_CELL_INFO instead
     */
    private static final String ACTION_DBM = "dbm";
    private static final String ACTION_GET_CELL_INFO = "getCellInfo";
    private static final String ACTION_GET_WIFI_INFO = "getWifiInfo";

    private static final String KEY_DBM = "dbm";
    private static final String KEY_RSSI = "rssi";
    private static final String KEY_LEVEL = "level";
    private static final String KEY_MAX_LEVEL = "maxLevel";
    private static final String KEY_CELL_TYPE = "cellType";
    private static final String KEY_CELL_DATA_LOADED = "cellDataLoaded";
    private static final String KEY_PRIMARY = "primary";
    private static final String KEY_ALTERNATES = "alternates";
    private static final String KEY_CONNECTION_STATUS = "connectionStatus";
    private static final String KEY_SSID = "ssid";
    private static final String KEY_BSSID = "bssid";
    private static final String KEY_NETWORK_ID = "networkId";
    private static final String KEY_LINK_SPEED_MBPS = "linkSpeedMbps";
    private static final String KEY_TX_LINK_SPEED_MBPS = "txLinkSpeedMbps";
    private static final String KEY_MAX_TX_LINK_SPEED_MBPS = "maxTxLinkSpeedMbps";
    private static final String KEY_RX_LINK_SPEED_MBPS = "rxLinkSpeedMbps";
    private static final String KEY_MAX_RX_LINK_SPEED_MBPS = "maxRxLinkSpeedMbps";

    private static final String CELL_TYPE_UNKNOWN = "UNKNOWN";

    private TelephonyManager telephonyManager = null;
    private WifiManager wifiManager = null;
    private ConnectivityManager connectivityManager = null;
    private CallbackContext networkInfoCallback = null;

    private final NetworkRequest networkRequest = new NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .build();

    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {

        @Override
        public void onAvailable(@NonNull Network network) {}

        @RequiresApi(api = Build.VERSION_CODES.Q)
        @Override
        public void onCapabilitiesChanged(
            @NonNull Network network,
            @NonNull NetworkCapabilities networkCapabilities
        ) {
            if (networkInfoCallback == null) {
                return;
            }
            WifiInfo info = (WifiInfo) networkCapabilities.getTransportInfo();
            if (info == null) {
                Timber.w("received null wifi info from connectivity manager");
                return;
            }
            try {
                JSONObject result = getWifiInfoJson(info);
                networkInfoCallback.success(result);
            } catch (Exception e) {
                String errorMessage = "failed to obtain wifi info: " + e.getMessage();
                Timber.e(e, errorMessage);
                networkInfoCallback.error(errorMessage);
            }
            networkInfoCallback = null;
        }
    };

    @Override
    protected void pluginInitialize() {
        super.pluginInitialize();
        Activity activity = cordova.getActivity();
        telephonyManager = (TelephonyManager) activity.getSystemService(Context.TELEPHONY_SERVICE);
        wifiManager = (WifiManager) activity.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        connectivityManager = activity.getSystemService(ConnectivityManager.class);
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        Timber.v("execute action %s", action);
        switch (action) {
            // included for backward compatibility; will be removed in a future plugin version
            case ACTION_DBM:
                getDbm(callbackContext);
                break;
            case ACTION_GET_CELL_INFO:
                getCellInfo(callbackContext);
                break;
            case ACTION_GET_WIFI_INFO:
                getWifiInfo(callbackContext);
                break;
            default:
                return false;
        }
        return true;
    }

    /**
     * included for backward compatibility; will be removed in a future plugin version
     * @deprecated use getCellInfo() instead
     */
    private void getDbm(CallbackContext callbackContext) {
        getCellInfo(callbackContext);
    }

    private void getCellInfo(CallbackContext callbackContext) {
        cordova.getThreadPool().execute(() -> {
            try {
                getCellInfoSync(callbackContext);
            } catch (JSONException e) {
                String errorMessage = "getCellInfo ERROR: " + e.getMessage();
                callbackContext.error(errorMessage);
                Timber.e(e, errorMessage);
            }
        });
    }

    private void getWifiInfo(CallbackContext callbackContext) {
        cordova.getThreadPool().execute(() -> {
            try {
                getWifiInfoSync(callbackContext);
            } catch (JSONException e) {
                String errorMessage = "getWifiInfo ERROR: " + e.getMessage();
                callbackContext.error(errorMessage);
                Timber.e(e, errorMessage);
            }
        });
    }

    private void getCellInfoSync(CallbackContext callbackContext) throws JSONException {
        Timber.v("getCellInfo()");

        if (ActivityCompat.checkSelfPermission(
            cordova.getContext(),
            Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            String errorMessage = "ACCESS_FINE_LOCATION permission not granted";
            Timber.w(errorMessage);
            callbackContext.error(errorMessage);
            return;
        }

        // This will give info of all sims present inside your mobile
        List<CellInfo> infoList = telephonyManager.getAllCellInfo();

        if (infoList == null) {
            String errorMessage = "failed to get cell info list";
            Timber.w(errorMessage);
            callbackContext.error(errorMessage);
            return;
        }

        Timber.v("getCellInfo() checking %s instance(s)", infoList.size());

        JSONObject primary = null;
        ArrayList<JSONObject> alternates = new ArrayList<>();

        for (CellInfo info : infoList) {
            if (info == null || !info.isRegistered()) {
                continue;
            }

            JSONObject serializedInfo = getCellInfoJson(info);

            if (primary == null && serializedInfo.optBoolean(KEY_PRIMARY)) {
                primary = serializedInfo;
            } else {
                serializedInfo.remove(KEY_PRIMARY);
                alternates.add(serializedInfo);
            }
        }

        // If we failed to find a proper primary, try to
        // fall back to an alternate instance that has loaded data
        if (primary == null && !alternates.isEmpty()) {
            for (JSONObject alternate : alternates) {
                if (alternate != null && alternate.optBoolean(KEY_CELL_DATA_LOADED)) {
                    primary = alternate;
                    break;
                }
            }
            // Couldn't find an alternate with loaded data, fall back to
            // first instance as a last-ditch effort
            if (primary == null) {
                primary = alternates.get(0);
            }
            // If we found a new primary, remove it from the alternates list to
            // avoid creating a cyclic JSON object
            if (primary != null) {
                primary.put(KEY_PRIMARY, false);
                alternates.remove(primary);
            }
        }

        JSONObject result;

        if (primary != null) {
            result = primary;
        } else {
            result = new JSONObject();
            result.put(KEY_PRIMARY, false);
        }

        result.put(KEY_ALTERNATES, alternates);
        callbackContext.success(result);
    }

    private void getWifiInfoSync(CallbackContext callbackContext) throws JSONException {
        if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            Timber.v("using connectivity manager to obtain wifi info");
            if (networkInfoCallback != null) {
                networkInfoCallback.error("request overwritten");
            }
            networkInfoCallback = callbackContext;
            connectivityManager.requestNetwork(networkRequest, networkCallback);
        } else {
            Timber.v("using wifi manager to obtain wifi info");
            WifiInfo info = wifiManager.getConnectionInfo();
            JSONObject result = getWifiInfoJson(info);
            callbackContext.success(result);
        }
    }

    private JSONObject getWifiInfoJson(WifiInfo info) throws JSONException {
        JSONObject result = new JSONObject();
        int rssi = info.getRssi();

        result.put(KEY_SSID, info.getSSID());
        result.put(KEY_BSSID, info.getBSSID());
        result.put(KEY_NETWORK_ID, info.getNetworkId());
        result.put(KEY_RSSI, rssi);
        result.put(KEY_LINK_SPEED_MBPS, info.getLinkSpeed());

        if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            result.put(KEY_LEVEL, wifiManager.calculateSignalLevel(rssi));
            result.put(KEY_MAX_LEVEL, wifiManager.getMaxSignalLevel());
            result.put(KEY_TX_LINK_SPEED_MBPS, info.getTxLinkSpeedMbps());
            result.put(KEY_MAX_TX_LINK_SPEED_MBPS, info.getMaxSupportedTxLinkSpeedMbps());
            result.put(KEY_RX_LINK_SPEED_MBPS, info.getRxLinkSpeedMbps());
            result.put(KEY_MAX_RX_LINK_SPEED_MBPS, info.getMaxSupportedRxLinkSpeedMbps());
        }

        return result;
    }

    private JSONObject getCellInfoJson(CellInfo info) throws JSONException {
        JSONObject result = new JSONObject();
        String cellType = CELL_TYPE_UNKNOWN;
        boolean primary = false;
        boolean loaded = false;
        int connectionStatus = 0;
        int dbm = 0;
        int level = 0;

        if (info != null) {
            cellType = info.getClass().getSimpleName();
            boolean registered = info.isRegistered();
            if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                connectionStatus = info.getCellConnectionStatus();
                primary = registered && (
                    connectionStatus == CellInfo.CONNECTION_PRIMARY_SERVING
                        || connectionStatus == CellInfo.CONNECTION_SECONDARY_SERVING
                );
            } else {
                primary = registered;
            }
        }

        // Unfortunately, we need to switch over these manually instead of
        // using getCellSignalStrength() so we can support android versions lower than 30.
        if (info instanceof CellInfoCdma) {
            CellInfoCdma cellInfoCdma = (CellInfoCdma) info;
            CellSignalStrengthCdma cellSignalStrengthCdma = cellInfoCdma.getCellSignalStrength();
            dbm = cellSignalStrengthCdma.getDbm();
            level = cellSignalStrengthCdma.getLevel();
            loaded = true;
        } else if (info instanceof CellInfoGsm) {
            CellInfoGsm cellInfogsm = (CellInfoGsm) info;
            CellSignalStrengthGsm cellSignalStrengthGsm = cellInfogsm.getCellSignalStrength();
            dbm = cellSignalStrengthGsm.getDbm();
            level = cellSignalStrengthGsm.getLevel();
            loaded = true;
        } else if (info instanceof CellInfoLte) {
            CellInfoLte cellInfoLte = (CellInfoLte) info;
            CellSignalStrengthLte cellSignalStrengthLte = cellInfoLte.getCellSignalStrength();
            dbm = cellSignalStrengthLte.getDbm();
            level = cellSignalStrengthLte.getLevel();
            loaded = true;
        } else if (info instanceof CellInfoWcdma) {
            CellInfoWcdma cellInfoWcdma = (CellInfoWcdma) info;
            CellSignalStrengthWcdma cellSignalStrengthWcdma = cellInfoWcdma.getCellSignalStrength();
            dbm = cellSignalStrengthWcdma.getDbm();
            level = cellSignalStrengthWcdma.getLevel();
            loaded = true;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (info instanceof CellInfoNr) {
                CellInfoNr cellInfoNr = (CellInfoNr) info;
                CellSignalStrength cellSignalStrengthNr = cellInfoNr.getCellSignalStrength();
                dbm = cellSignalStrengthNr.getDbm();
                level = cellSignalStrengthNr.getLevel();
                loaded = true;
            } else if (info instanceof CellInfoTdscdma) {
                CellInfoTdscdma cellInfoTdscdma = (CellInfoTdscdma) info;
                CellSignalStrength cellSignalStrengthTdscdma = cellInfoTdscdma.getCellSignalStrength();
                dbm = cellSignalStrengthTdscdma.getDbm();
                level = cellSignalStrengthTdscdma.getLevel();
                loaded = true;
            }
        }

        Timber.v("getCellInfoJson() result = %s dbm(%s) level(%s) primary = %s", cellType, dbm, level, primary);

        result.put(KEY_PRIMARY, primary);
        result.put(KEY_CELL_TYPE, cellType);
        result.put(KEY_DBM, dbm);
        result.put(KEY_LEVEL, level);
        result.put(KEY_CONNECTION_STATUS, connectionStatus);
        result.put(KEY_CELL_DATA_LOADED, loaded);

        return result;
    }
}
