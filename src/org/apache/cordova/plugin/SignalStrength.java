package org.apache.cordova.plugin;

import android.content.Context;
import java.util.*;

import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import android.telephony.CellSignalStrength;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthCdma;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthWcdma;

import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoWcdma;
import android.telephony.CellInfoGsm;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.LOG;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class SignalStrength extends CordovaPlugin {
    private static final String TAG = "SignalStrengthPlugin";

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

        if (action.equals("dbm")) {
            TelephonyManager tm = (TelephonyManager) cordova.getActivity().getSystemService(Context.TELEPHONY_SERVICE);
            List<CellInfo> cellInfos = tm.getAllCellInfo();   //This will give info of all sims present inside your mobile
            if(cellInfos != null) {
                for (int i = 0 ; i < cellInfos.size() ; i++) {
                    if (cellInfos.get(i).isRegistered()) {
                        if (cellInfos.get(i) instanceof CellInfoWcdma) {
                            CellInfoWcdma cellInfoWcdma = (CellInfoWcdma) cellInfos.get(i);
                            CellSignalStrengthWcdma cellSignalStrengthWcdma = cellInfoWcdma.getCellSignalStrength();
                            dbm = cellSignalStrengthWcdma.getDbm();
                            level = cellSignalStrengthWcdma.getLevel();
                            LOG.d(TAG, "WCDMA dbm(" + dbm + ") level(" + level + ")");
                        } else if (cellInfos.get(i) instanceof CellInfoGsm) {
                            CellInfoGsm cellInfogsm = (CellInfoGsm) cellInfos.get(i);
                            CellSignalStrengthGsm cellSignalStrengthGsm = cellInfogsm.getCellSignalStrength();
                            dbm = cellSignalStrengthGsm.getDbm();
                            level = cellSignalStrengthGsm.getLevel();
                            LOG.d(TAG, "GSM dbm(" + dbm + ") level(" + level + ")");
                        } else if (cellInfos.get(i) instanceof CellInfoLte) {
                            CellInfoLte cellInfoLte = (CellInfoLte) cellInfos.get(i);
                            CellSignalStrengthLte cellSignalStrengthLte = cellInfoLte.getCellSignalStrength();
                            dbm = cellSignalStrengthLte.getDbm();
                            level = cellSignalStrengthLte.getLevel();
                            LOG.d(TAG, "LTE dbm(" + dbm + ") level(" + level + ")");
                        } else if (cellInfos.get(i) instanceof CellInfoCdma) {
                            CellInfoCdma cellInfoCdma = (CellInfoCdma) cellInfos.get(i);
                            CellSignalStrengthCdma cellSignalStrengthCdma = cellInfoCdma.getCellSignalStrength();
                            dbm = cellSignalStrengthCdma.getDbm();
                            level = cellSignalStrengthCdma.getLevel();
                            LOG.d(TAG, "CDMA dbm(" + dbm + ") level(" + level + ")");
                        }
                    }
                }
            }
            String strength = "dbm(" + dbm + ") level(" + level + ")";
            callbackContext.success(strength);
            return true;
        }

        return false;
    }

    int dbm = -1;
    int level = 0;
}

