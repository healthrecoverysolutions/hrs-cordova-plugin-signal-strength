package com.hrs.signalstrength;

import android.Manifest;
import android.content.Context;

import android.content.pm.PackageManager;
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
     * @deprecated use ACTION_GET_CELL_INFO instead
     */
    private static final String ACTION_DBM = "dbm";
    private static final String ACTION_GET_CELL_INFO = "getCellInfo";

    private static final String KEY_DBM = "dbm";
    private static final String KEY_LEVEL = "level";
    private static final String KEY_CELL_TYPE = "cellType";
    private static final String KEY_CELL_DATA_LOADED = "cellDataLoaded";
    private static final String KEY_PRIMARY = "primary";
    private static final String KEY_ALTERNATES = "alternates";
    private static final String KEY_CONNECTION_STATUS = "connectionStatus";

    private static final String CELL_TYPE_UNKNOWN = "UNKNOWN";

    private TelephonyManager telephonyManager;

    @Override
    protected void pluginInitialize() {
        super.pluginInitialize();
        telephonyManager = (TelephonyManager) cordova.getActivity().getSystemService(Context.TELEPHONY_SERVICE);
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        Timber.v("execute action %s", action);
        switch (action) {
            // included for backward compatibility, but will be removed in a future plugin version
            case ACTION_DBM:
                getDbm(callbackContext);
                break;
            case ACTION_GET_CELL_INFO:
                getCellInfo(callbackContext);
                break;
            default:
                return false;
        }
        return true;
    }

    /**
     * @deprecated use getCellInfo() instead
     */
    private void getDbm(CallbackContext callbackContext) {
        getCellInfo(callbackContext);
    }

    private void getCellInfo(CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    getCellInfoSync(callbackContext);
                } catch (JSONException e) {
                    Timber.e(e);
                }
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

        Timber.v("getCellInfo() checking %s instances", infoList.size());

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

        JSONObject result;

        if (primary != null) {
            result = primary;
        } else if (!alternates.isEmpty()) {
            result = alternates.get(0);
            result.put(KEY_PRIMARY, false);
        } else {
            result = new JSONObject();
            result.put(KEY_PRIMARY, false);
        }

        result.put(KEY_ALTERNATES, alternates);
        callbackContext.success(result);
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

        Timber.d("getCellInfoJson() result = %s dbm(%s) level(%s) primary = %s", cellType, dbm, level, primary);

        result.put(KEY_PRIMARY, primary);
        result.put(KEY_CELL_TYPE, cellType);
        result.put(KEY_DBM, dbm);
        result.put(KEY_LEVEL, level);
        result.put(KEY_CONNECTION_STATUS, connectionStatus);
        result.put(KEY_CELL_DATA_LOADED, loaded);

        return result;
    }
}
