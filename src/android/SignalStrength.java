package com.hrs.signalstrength;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoTdscdma;
import android.telephony.CellSignalStrength;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyCallback;
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
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

public class SignalStrength extends CordovaPlugin {
    // aliases to more easily parse version checking logic
    private static final int ANDROID_12 = Build.VERSION_CODES.S;
    private static final boolean IS_ANDROID_12_OR_GREATER = Build.VERSION.SDK_INT >= ANDROID_12;

    /**
     * included for backward compatibility; will be removed in a future plugin version
     * @deprecated use ACTION_GET_CELL_STATE instead
     */
    private static final String ACTION_DBM = "dbm";
    private static final String ACTION_GET_CELL_STATE = "getCellState";
    private static final String ACTION_GET_WIFI_STATE = "getWifiState";
    private static final String ACTION_SET_SHARED_EVENT_DELEGATE = "setSharedEventDelegate";

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
    private static final String KEY_TYPE = "type";
    private static final String KEY_DATA = "data";
    private static final String KEY_INFO = "info";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_CONNECTED = "connected";
    private static final String KEY_REASON = "reason";

    private static final String EVENT_TYPE_CELL_STATE_UPDATED = "cellStateUpdated";
    private static final String EVENT_TYPE_WIFI_STATE_UPDATED = "wifiStateUpdated";
    private static final String REASON_LOST_CONNECTION = "lostConnection";
    private static final String REASON_UNAVAILABLE = "unavailable";
    private static final String REASON_DISABLED = "disabled";
    private static final String CELL_TYPE_UNKNOWN = "UNKNOWN";

    private TelephonyManager telephonyManager = null;
    private WifiManager wifiManager = null;
    private ConnectivityManager connectivityManager = null;
    private final ArrayList<CallbackContext> networkInfoCallbacks = new ArrayList<>();
    private CallbackContext sharedJsEventCallback = null;
    private TelephonyCallback cellChangeCallback = null;
    private Handler legacyWifiInfoPollHandler = null;
    private Runnable legacyWifiInfoPollRunnable = null;
    private IntentFilter wifiStateChangedFilter = null;
    private boolean eventListenerCallbacksEnabled = false;

    @RequiresApi(api = Build.VERSION_CODES.S)
    private class PluginTelephonyCallback extends TelephonyCallback
        implements TelephonyCallback.CellInfoListener, TelephonyCallback.SignalStrengthsListener {

        @Override
        public void onCellInfoChanged(@NonNull List<CellInfo> list) {
            notifyCellInfoChanged(list);
        }

        @Override
        public void onSignalStrengthsChanged(@NonNull android.telephony.SignalStrength signalStrength) {
            notifyCellInfoRefresh();
        }
    }

    private final PhoneStateListener legacyCellChangeCallback = new PhoneStateListener() {
        @Override
        public void onCellInfoChanged(List<CellInfo> cellInfo) {
            notifyCellInfoChanged(cellInfo);
        }

        @Override
        public void onSignalStrengthsChanged(android.telephony.SignalStrength signalStrength) {
            notifyCellInfoRefresh();
        }
    };

    private final NetworkRequest networkRequest = new NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .build();

    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {

        @Override
        public void onAvailable(@NonNull Network network) {
            notifyWifiNetworkAvailable(network);
        }

        @Override
        public void onUnavailable() {
            notifyWifiNetworkUnavailable();
        }

        @Override
        public void onLost(@NonNull Network network) {
            notifyWifiNetworkLostConnection();
        }

        @RequiresApi(api = Build.VERSION_CODES.Q)
        @Override
        public void onCapabilitiesChanged(
            @NonNull Network network,
            @NonNull NetworkCapabilities networkCapabilities
        ) {
            notifyWifiNetworkCapabilitiesChanged(network, networkCapabilities);
        }
    };

    private final BroadcastReceiver wifiStateChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(intent.getAction())) {
                Timber.v("SignalStrength wifiStateReceiver NETWORK_STATE_CHANGED_ACTION");
                if (!wifiManager.isWifiEnabled()) {
                    notifyWifiDisabled();
                }
            }
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
    public void onDestroy() {
        super.onDestroy();
        unregisterEventCallbackListeners();
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        Timber.v("execute action %s", action);
        switch (action) {
            // included for backward compatibility; will be removed in a future plugin version
            case ACTION_DBM:
                getDbm(callbackContext);
                break;
            case ACTION_GET_CELL_STATE:
                getCellState(callbackContext);
                break;
            case ACTION_GET_WIFI_STATE:
                getWifiState(callbackContext);
                break;
            case ACTION_SET_SHARED_EVENT_DELEGATE:
                boolean remove = args.optBoolean(0, false);
                setSharedEventDelegate(callbackContext, remove);
                break;
            default:
                return false;
        }
        return true;
    }

    /**
     * included for backward compatibility; will be removed in a future plugin version
     * @deprecated use getCellState() instead
     */
    private void getDbm(CallbackContext callbackContext) {
        getCellState(callbackContext);
    }

    private void getCellState(CallbackContext callbackContext) {
        cordova.getThreadPool().execute(() -> {
            try {
                getCellStateSync(callbackContext);
            } catch (JSONException e) {
                String errorMessage = "getCellState ERROR: " + e.getMessage();
                callbackContext.error(errorMessage);
                Timber.e(e, errorMessage);
            }
        });
    }

    private void getWifiState(CallbackContext callbackContext) {
        cordova.getThreadPool().execute(() -> {
            try {
                getWifiStateSync(callbackContext);
            } catch (JSONException e) {
                String errorMessage = "getWifiState ERROR: " + e.getMessage();
                callbackContext.error(errorMessage);
                Timber.e(e, errorMessage);
            }
        });
    }

    private void setSharedEventDelegate(CallbackContext callbackContext, boolean remove) {
        cordova.getThreadPool().execute(() -> {
            setSharedEventDelegateSync(callbackContext, remove);
        });
    }

    private void setSharedEventDelegateSync(CallbackContext callbackContext, boolean remove) {
        if (remove) {
            unregisterEventCallbackListeners();
            sharedJsEventCallback = null;
        } else {
            sharedJsEventCallback = callbackContext;
            registerEventCallbackListeners();
        }
    }

    private void registerEventCallbackListeners() {
        if (!eventListenerCallbacksEnabled) {
            registerTelephonyListener();
            registerWifiListener();
            eventListenerCallbacksEnabled = true;
        }
    }

    private void unregisterEventCallbackListeners() {
        if (eventListenerCallbacksEnabled) {
            unregisterTelephonyListener();
            unregisterWifiListener();
            eventListenerCallbacksEnabled = false;
        }
    }

    private void registerTelephonyListener() {
        if (IS_ANDROID_12_OR_GREATER) {
            if (cellChangeCallback == null) {
                cellChangeCallback = new PluginTelephonyCallback();
            }
            telephonyManager.registerTelephonyCallback(cordova.getContext().getMainExecutor(), cellChangeCallback);
        } else {
            int flags = PhoneStateListener.LISTEN_CELL_INFO
                | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS;
            telephonyManager.listen(legacyCellChangeCallback, flags);
        }
    }

    private void unregisterTelephonyListener() {
        if (IS_ANDROID_12_OR_GREATER) {
            telephonyManager.unregisterTelephonyCallback(cellChangeCallback);
        } else {
            telephonyManager.listen(legacyCellChangeCallback, PhoneStateListener.LISTEN_NONE);
        }
    }

    private void registerWifiListener() {
        if (wifiStateChangedFilter == null) {
            wifiStateChangedFilter = new IntentFilter();
            wifiStateChangedFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        }

        cordova.getActivity().registerReceiver(wifiStateChangedReceiver, wifiStateChangedFilter);

        if (IS_ANDROID_12_OR_GREATER) {
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
            connectivityManager.requestNetwork(networkRequest, networkCallback);

            // registered network callback might not give us any initial info if
            // the radio is off on startup, so manually check here for initialization purposes
            if (!wifiManager.isWifiEnabled()) {
                notifyWifiDisabled();
            }
            return;
        }

        if (legacyWifiInfoPollHandler == null) {
            legacyWifiInfoPollHandler = new Handler(cordova.getActivity().getMainLooper());
        }

        if (legacyWifiInfoPollRunnable == null) {
            legacyWifiInfoPollRunnable = () -> {
                final long nextPollDelayMs = 10000;
                pollLegacyWifiStateSync();
                legacyWifiInfoPollHandler.postDelayed(legacyWifiInfoPollRunnable, nextPollDelayMs);
            };
        }

        // Start polling legacy wifi info format.
        // The first poll should be relatively quick so the shared callback gets fresh data
        legacyWifiInfoPollHandler.postDelayed(legacyWifiInfoPollRunnable, 50);
    }

    private void unregisterWifiListener() {
        cordova.getActivity().unregisterReceiver(wifiStateChangedReceiver);

        if (IS_ANDROID_12_OR_GREATER) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
            return;
        }

        // Stop polling legacy wifi info format.
        if (legacyWifiInfoPollHandler != null) {
            legacyWifiInfoPollHandler.removeCallbacks(legacyWifiInfoPollRunnable);
        }
    }

    private void notifyCellInfoRefresh() {
        if (ActivityCompat.checkSelfPermission(
            cordova.getContext(),
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            notifyCellInfoChanged(telephonyManager.getAllCellInfo());
        } else {
            Timber.w("ACCESS_FINE_LOCATION permission not granted (from notifyCellInfoRefresh)");
        }
    }

    private void notifyCellInfoChanged(List<CellInfo> list) {
        try {
            JSONObject data = getCellStatePayloadJson(list);
            emitSharedJsEvent(EVENT_TYPE_CELL_STATE_UPDATED, data);
        } catch (JSONException e) {
            Timber.e(e, "failed to notify webview of cell info updates");
        }
    }

    private void notifyWifiNetworkAvailable(@NonNull Network network) {
        Timber.v("notifyWifiNetworkAvailable handle=%s", network.getNetworkHandle());
    }

    private void notifyWifiNetworkLostConnection() {
        Timber.v("notifyWifiNetworkLostConnection()");
        notifyWifiNetworkDisconnected(REASON_LOST_CONNECTION);
    }

    private void notifyWifiNetworkUnavailable() {
        Timber.v("notifyWifiNetworkUnavailable()");
        notifyWifiNetworkDisconnected(REASON_UNAVAILABLE);
    }

    private void notifyWifiDisabled() {
        Timber.v("notifyWifiDisabled()");
        notifyWifiNetworkDisconnected(REASON_DISABLED);
    }

    private void notifyWifiNetworkDisconnected(String reason) {
        Timber.v("notifyWifiNetworkDisconnected reason=%s", reason);
        try {
            JSONObject data = getWifiStatePayloadJson(null).put(KEY_REASON, reason);
            notifyNetworkInfoSuccess(data);
            emitSharedJsEvent(EVENT_TYPE_WIFI_STATE_UPDATED, data);
        } catch (Exception e) {
            String errorMessage = "failed to obtain wifi info: " + e.getMessage();
            Timber.e(e, errorMessage);
            notifyNetworkInfoError(errorMessage);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void notifyWifiNetworkCapabilitiesChanged(
        @NonNull Network network,
        @NonNull NetworkCapabilities networkCapabilities
    ) {
        Timber.v("notifyWifiNetworkCapabilitiesChanged handle=%s", network.getNetworkHandle());
        try {
            WifiInfo info = (WifiInfo) networkCapabilities.getTransportInfo();
            JSONObject data = getWifiStatePayloadJson(info);
            notifyNetworkInfoSuccess(data);
            emitSharedJsEvent(EVENT_TYPE_WIFI_STATE_UPDATED, data);
        } catch (Exception e) {
            String errorMessage = "failed to obtain wifi info: " + e.getMessage();
            Timber.e(e, errorMessage);
            notifyNetworkInfoError(errorMessage);
        }
    }

    private void getCellStateSync(CallbackContext callbackContext) throws JSONException {
        Timber.v("getCellStateSync()");

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

        if (infoList != null) {
            JSONObject data = getCellStatePayloadJson(infoList);
            callbackContext.success(data);
            emitSharedJsEvent(EVENT_TYPE_CELL_STATE_UPDATED, data);
        } else {
            String errorMessage = "failed to get cell info list";
            Timber.w(errorMessage);
            callbackContext.error(errorMessage);
        }
    }

    private void notifyNetworkInfoSuccess(JSONObject payload) {
        if (networkInfoCallbacks.isEmpty()) {
            Timber.w("notifyNetworkInfoSuccess() callback list is empty");
            return;
        }

        for (CallbackContext cb : networkInfoCallbacks) {
            if (cb != null) {
                cb.success(payload);
            }
        }

        networkInfoCallbacks.clear();
    }

    private void notifyNetworkInfoError(String errorMessage) {
        if (networkInfoCallbacks.isEmpty()) {
            Timber.w("notifyNetworkInfoError() callback list is empty");
            return;
        }

        for (CallbackContext cb : networkInfoCallbacks) {
            if (cb != null) {
                cb.error(errorMessage);
            }
        }

        networkInfoCallbacks.clear();
    }

    private void getWifiStateSync(CallbackContext callbackContext) throws JSONException {
        Timber.v("getWifiStateSync()");
        if (IS_ANDROID_12_OR_GREATER) {
            requestStandardWifiStateSync(callbackContext);
        } else {
            requestLegacyWifiStateSync(callbackContext);
        }
    }

    private void requestStandardWifiStateSync(CallbackContext callbackContext) {
        Timber.v("requestStandardWifiStateSync()");
        networkInfoCallbacks.add(callbackContext);
        connectivityManager.requestNetwork(networkRequest, networkCallback);
    }

    private void requestLegacyWifiStateSync(CallbackContext callbackContext) throws JSONException {
        Timber.v("requestLegacyWifiStateSync()");
        WifiInfo info = wifiManager.getConnectionInfo();
        JSONObject result = getWifiStatePayloadJson(info);
        callbackContext.success(result);
        emitSharedJsEvent(EVENT_TYPE_WIFI_STATE_UPDATED, result);
    }

    private void pollLegacyWifiStateSync() {
        Timber.v("pollLegacyWifiStateSync()");
        try {
            WifiInfo info = wifiManager.getConnectionInfo();
            JSONObject result = getWifiStatePayloadJson(info);
            emitSharedJsEvent(EVENT_TYPE_WIFI_STATE_UPDATED, result);
        } catch (Exception e) {
            Timber.e(e, "pollLegacyWifiStateSync() failed!");
        }
    }

    private void emitSharedJsEvent(String type, JSONObject data) throws JSONException {
        if (sharedJsEventCallback != null && type != null) {
            Timber.v("emitSharedJsEvent type = %s", type);
            if (data == null) {
                data = new JSONObject();
            }
            JSONObject payload = new JSONObject()
                .put(KEY_TYPE, type)
                .put(KEY_DATA, data);
            PluginResult result = new PluginResult(PluginResult.Status.OK, payload);
            result.setKeepCallback(true);
            sharedJsEventCallback.sendPluginResult(result);
        }
    }

    private JSONObject getWifiStatePayloadJson(WifiInfo info) throws JSONException {
        JSONObject result = new JSONObject();
        boolean wifiEnabled = wifiManager.isWifiEnabled();
        boolean hasWifiInfo = info != null;

        result.put(KEY_ENABLED, wifiEnabled);
        result.put(KEY_CONNECTED, hasWifiInfo);

        if (hasWifiInfo) {
            result.put(KEY_INFO, getWifiInfoJson(info));
        }

        Timber.v("getWifiStatePayloadJson() enabled = %s, has info = %s", wifiEnabled, hasWifiInfo);

        return result;
    }

    private JSONObject getWifiInfoJson(WifiInfo info) throws JSONException {
        JSONObject result = new JSONObject();
        String ssid = info.getSSID();
        int rssi = info.getRssi();
        boolean includeLevel = false;

        result.put(KEY_SSID, info.getSSID());
        result.put(KEY_BSSID, info.getBSSID());
        result.put(KEY_NETWORK_ID, info.getNetworkId());
        result.put(KEY_RSSI, rssi);
        result.put(KEY_LINK_SPEED_MBPS, info.getLinkSpeed());

        if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            includeLevel = true;
            result.put(KEY_LEVEL, wifiManager.calculateSignalLevel(rssi));
            result.put(KEY_MAX_LEVEL, wifiManager.getMaxSignalLevel());
            result.put(KEY_TX_LINK_SPEED_MBPS, info.getTxLinkSpeedMbps());
            result.put(KEY_MAX_TX_LINK_SPEED_MBPS, info.getMaxSupportedTxLinkSpeedMbps());
            result.put(KEY_RX_LINK_SPEED_MBPS, info.getRxLinkSpeedMbps());
            result.put(KEY_MAX_RX_LINK_SPEED_MBPS, info.getMaxSupportedRxLinkSpeedMbps());
        }

        Timber.v("getWifiInfoJson() ssid = %s, rssi = %s = includeLevel = %s", ssid, rssi, includeLevel);

        return result;
    }

    private JSONObject getCellStatePayloadJson(List<CellInfo> infoList) throws JSONException {
        JSONObject primary = null;
        ArrayList<JSONObject> alternates = new ArrayList<>();

        if (infoList != null) {
            Timber.v("getCellStatePayloadJson() checking %s instance(s)", infoList.size());
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
        } else {
            // shouldn't ever happen... but just in case
            Timber.w("getCellStatePayloadJson() received null list");
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

        result.put(KEY_ALTERNATES, new JSONArray(alternates));

        return result;
    }

    private JSONObject getCellInfoJson(CellInfo info) throws JSONException {
        JSONObject result = new JSONObject();
        String cellType = CELL_TYPE_UNKNOWN;
        boolean primary = false;
        boolean loaded = false;
        int connectionStatus = 0;
        int dbm = -1;
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
