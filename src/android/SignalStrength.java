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
    private static final String KEY_HAS_REGISTERED_CELL_PROVIDER = "hasRegisteredCellProvider";
    private static final String KEY_ALTERNATE_PRIMARY = "alternatePrimary";
    private static final String KEY_SECONDARY = "secondary";
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
    private void getDbm(CallbackContext callbackContext) throws JSONException {
        getCellInfo(callbackContext);
    }

    private void getCellInfo(CallbackContext callbackContext) throws JSONException {
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

        JSONObject result = null;
        ArrayList<JSONObject> altPrimaryInfoList = new ArrayList<>();
        ArrayList<JSONObject> secondaryInfoList = new ArrayList<>();

        for (CellInfo info : infoList) {
            if (info == null || !info.isRegistered()) {
                continue;
            }

            if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                int status = info.getCellConnectionStatus();
                switch (status) {
                    case CellInfo.CONNECTION_PRIMARY_SERVING:
                        if (result == null) {
                            // keep the first "primary" we find as the root object
                            result = getPrimaryCellInfoJson(info);
                        } else {
                            altPrimaryInfoList.add(getCellInfoJson(info));
                        }
                        break;
                    case CellInfo.CONNECTION_SECONDARY_SERVING:
                    case CellInfo.CONNECTION_NONE:
                    case CellInfo.CONNECTION_UNKNOWN:
                    default:
                        secondaryInfoList.add(getCellInfoJson(info));
                        break;
                }
            } else if (result == null) {
                // keep the first "primary" we find as the root object
                result = getPrimaryCellInfoJson(info);
            } else {
                secondaryInfoList.add(getCellInfoJson(info));
            }
        }

        if (result == null) {
            result = new JSONObject();
            result.put(KEY_HAS_REGISTERED_CELL_PROVIDER, false);
        } else {
            // if result is not null, we found at least one registered provider,
            // so add any alternate providers as sub-arrays.
            result.put(KEY_ALTERNATE_PRIMARY, altPrimaryInfoList);
            result.put(KEY_SECONDARY, secondaryInfoList);
        }

        callbackContext.success(result);
    }

    private JSONObject getPrimaryCellInfoJson(CellInfo info) throws JSONException {
        JSONObject result = getCellInfoJson(info);
        result.put(KEY_HAS_REGISTERED_CELL_PROVIDER, true);
        return result;
    }

    private JSONObject getCellInfoJson(CellInfo info) throws JSONException {
        JSONObject result = new JSONObject();
        String cellType = CELL_TYPE_UNKNOWN;
        boolean loaded = false;
        int dbm = 0;
        int level = 0;

        if (info != null) {
            cellType = info.getClass().getSimpleName();
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

        Timber.d("getCellInfoJson() result = %s dbm(%s) level(%s)", cellType, dbm, level);
        result.put(KEY_CELL_TYPE, cellType);
        result.put(KEY_DBM, dbm);
        result.put(KEY_LEVEL, level);
        result.put(KEY_CELL_DATA_LOADED, loaded);

        if (info != null && Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            result.put(KEY_CONNECTION_STATUS, info.getCellConnectionStatus());
        }

        return result;
    }
}
