/*
 * Copyright 2014 Intel Corporation All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.wifi;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.util.Log;

import com.intel.cam.api.CamConnectionRequest;
import com.intel.cam.api.CamManager;
import com.intel.cam.api.CamScanResult;

import java.util.List;

/**
 * CamOverlay provides the CAM-specific changes to the Settings app.
 *
 * <pre>
 * The main functionality of CamOverlay are:
 * 1. Check whether Cam Service is available.
 * 2. Check whether Cam UI is available.
 * 3. Connect to the HS20 network.
 * 4. Start the CAM UI.
 * 5. Start the Credentials UI.
 * 6. Update the Wi-Fi Settings UI with HS20 networks.
 * 7. Send the Wi-Fi disconnect event to CAM service
 * </pre>
 *
 * @author WINS-BA-CAM
 */
class CamOverlay {
    private static final String TAG = "CAM:Overlay";

    private PreferenceFragment mFragment;
    private Activity mActivity;
    private IntentFilter mFilter;
    private BroadcastReceiver mReceiver;

    private List<CamScanResult> mCamScanList;
    private CamManager mCamManager;
    private boolean isBoundtoService;

    private static final String mPackageName = "com.intel.cam.ui";
    private static final String mMainUI = "com.intel.cam.ui.CAMSettings";

    private static final String mHotspot20PackageName = "com.intel.cam.ui.hotspot20";
    private static final String mCredentialUI = "com.intel.cam.ui.hotspot20.CamAddCredentials";

    private static final String mServicePackageName = "com.intel.cam";
    private static final String mServiceName = "com.intel.cam.service.CamService";

    /**
     * Constructor for {@link CamOverlay}.
     */
    CamOverlay(PreferenceFragment fragment, Activity activity) {
        mFragment = fragment;
        mActivity = activity;

        init();
    }

    /**
     * This method initializes the Cam Manager and register the broadcast
     * receivers.
     */
    private void init() {
        // Check for the service only if UI is installed.
        if (isCamUIInstalled()) {
            mCamManager = CamManager.getInstance(mActivity);
            if (mCamManager != null) {
                startCamService();
            }
        } else {
            Log.e(TAG, "The UI component was not installed");
        }
        setBroadcastReceiver();
        mActivity.registerReceiver(mReceiver, mFilter);
    }

    /**
     * Set the broadcast receiver to listen for below intents:
     *
     * <pre>
     * 1. CAM_SCAN_RESULTS_AVAILABLE_ACTION
     * 2. ACTION_SERVICE_BOUND
     * 3. ACTION_SERVICE_UNBOUND
     * </pre>
     */
    private void setBroadcastReceiver() {
        mFilter = new IntentFilter();
        mFilter.addAction(CamManager.CAM_SCAN_RESULTS_AVAILABLE_ACTION);
        mFilter.addAction(CamManager.ACTION_SERVICE_BOUND);
        mFilter.addAction(CamManager.ACTION_SERVICE_UNBOUND);

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                handleEvent(context, intent);
            }
        };
    }

    /**
     * Receives the registered intents and updates the UI accordingly
     */
    private void handleEvent(Context context, Intent intent) {
        String action = intent.getAction();
        if (CamManager.CAM_SCAN_RESULTS_AVAILABLE_ACTION.equals(action)) {
            if (isBoundtoService) {
                updateCAMScanList();
            }
        } else if (CamManager.ACTION_SERVICE_BOUND.equals(action)) {
            Log.e(TAG, "ACTION_SERVICE_BOUND");
            isBoundtoService = true;
            updateCAMScanList();
        } else if (CamManager.ACTION_SERVICE_UNBOUND.equals(action)) {
            Log.e(TAG, "ACTION_SERVICE_UNBOUND");
            isBoundtoService = false;
        }
    }

    /**
     * Perform release operations in following sequence. <br>
     *
     * <pre>
     * 1. Release the Cam Manager instance
     * 2. Unregister the broadcast receiver
     * </pre>
     */
    void release() {
        if (mCamManager != null) {
            mCamManager.release(mActivity);
        }
        mActivity.unregisterReceiver(mReceiver);
        mFragment = null;
        isBoundtoService = false;
    }

    /**
     * Checks whether Cam Service is installed.
     *
     * @return {@code true} if the operation succeeded
     */
    boolean isCamServiceAvailable() {
        if (mCamManager != null) {
            return true;
        }
        return false;
    }

    /**
     * Checks whether Cam UI is installed.
     *
     * @return {@code true} if the operation succeeded
     */
    boolean isCamUIInstalled() {
        PackageManager pm = mActivity.getPackageManager();
        try {
            pm.getPackageInfo(mPackageName, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Starts the Cam UI.
     *
     * @return {@code true} if the operation succeeded
     */
    boolean startCamUI() {
        try {
            Intent camIntent = new Intent();
            camIntent.setComponent(new ComponentName(mPackageName, mMainUI));
            ((PreferenceActivity) mActivity).startActivity(camIntent);
            return true;
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "CAM Settings activity not found");
            return false;
        }
    }

    /**
     * Starts the CAM Service.
     */
    private void startCamService() {
        Intent serviceIntent = new Intent();
        serviceIntent.setClassName(mServicePackageName,
                mServiceName);
        mActivity.startService(serviceIntent);
    }

    /**
     * This method broadcasts the disconnect intent to Cam Service.
     */
    void broadcastWifiDisconnect() {
        if (mCamManager != null && mCamManager.isBoundToService()) {
            if (mCamManager.isSmartSelectionEnabled()) {
                Log.e(TAG, "Broadcasting the disconnect intent");
                final Intent intent = new Intent(CamManager.CAM_WIFI_DISCONNECT_ACTION);
                mActivity.sendBroadcast(intent);
            }
        }
    }

    /**
     * Method for starting the Cam credentials UI to enable the user to enter
     * the HS20 credentials for a HS20 access point(AP).
     *
     * @param ssid the SSID of the AP
     * @param bssid the BSSID of the AP
     * @param provId the Provider ID of the AP
     * @param netType the network type
     * @return {@code true} if the operation succeeded
     */
    boolean startCamCredentialsUI(String ssid, String bssid, int provId,
            int netType) {
        try {
            Intent camIntent = new Intent();
            Bundle camBundle = new Bundle();

            camBundle.putString("ssid", ssid);
            camBundle.putString("bssid", bssid);
            camBundle.putInt("providerId", provId);
            camBundle.putInt("networkType", netType);
            camIntent.putExtras(camBundle);

            camIntent.setComponent(new ComponentName(mHotspot20PackageName,
                    mCredentialUI));
            ((PreferenceActivity) mActivity).startActivity(camIntent);
            return true;
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "CAM Credentials activity not found");
            return false;
        }
    }

    /**
     * Initiate connection to a HS20 network with the given informations.
     *
     * @param ssid the SSID of the AP
     * @param bssid the BSSID of the AP
     * @param credId the Credential ID of the AP
     * @param netType the network type
     * @param data the eap data to connect to an AP
     * @return {@code true} if the operation succeeded
     */
    boolean connectNetwork(String ssid, String bssid, int netType, int credId,
            String data) {
        if (mCamManager == null) {
            Log.e(TAG, "updateCAMList() Cam Manager is NULL");
            return false;
        }

        if (!mCamManager.isBoundToService()) {
            Log.e(TAG, "updateCAMList() Cam Service not bound");
            return false;
        }

        CamConnectionRequest connectionRequest = new CamConnectionRequest(
                ssid, bssid, netType, credId, data);
        boolean connResult = mCamManager
                .connectCamNetwork(connectionRequest);
        Log.e(TAG, "Connection result " + connResult);
        return connResult;
    }

    /**
     * This method checks whether a given access point is HS20 enabled. If the
     * AP is HS20 enabled, the HS20 related variables are updated for the given
     * AccessPoint object.
     *
     * @param accessPoint the AccessPoint object
     */
    void updateCAMAccessPoint(AccessPoint accessPoint) {
        // Don't do anything if either CamManager or CamScanList is null
        if (mCamManager == null || mCamScanList == null) {
            return;
        }

        if (!mCamManager.isBoundToService()) {
            Log.e(TAG, "updateCAMAccessPoint() Cam Service not bound!");
            return;
        }

        // if CAM is disabled or not ready, no need get the list.
        if (!mCamManager.isSmartSelectionEnabled()
                || mCamManager.getCamState() != CamManager.CAM_STATE_ENABLED) {
            Log.e(TAG, "updateCAMAccessPoint() CAM is not ready");
            return;
        }

        String accessPointBssid = accessPoint.bssid;
        String accessPointSsid = accessPoint.ssid;
        Log.i(TAG, "AccessPoint bssid " + accessPointBssid + " ssid " + accessPointSsid);

        // Iterate through the cam list to find if the given AP is available.
        // If present, update the AP with HS20-related informations.
        for (CamScanResult camRecord : mCamScanList) {
            if (accessPointBssid == null || camRecord.camBSSID == null) {
                String ssid = AccessPoint.removeDoubleQuotes(camRecord.camSSID);
                if (accessPointSsid.equals(ssid)) {
                    Log.e(TAG, "SSID match found " + ssid);
                    accessPoint.update(camRecord);
                    return;
                }
                continue;
            }
            if (accessPointBssid.equals(camRecord.camBSSID)) {
                Log.e(TAG, "BSSID match found " + accessPointSsid);
                accessPoint.update(camRecord);
                return;
            }
        }
    }

    /**
     * This method updates the AccessPoint list with the CAM-related information
     * The operation includes:
     *
     * <pre>
     * 1. Get the CAM Scan List.
     * 2. Retrieve the list of AccessPoint objects.
     * 3. Compare the AccessPoint objects and CAM Scan List and
     *    update the Wi-Fi List UI with the HS20 information.
     * </pre>
     */
    void updateCAMScanList() {
        // Set the local CAM list to null before requesting the new ones
        mCamScanList = null;

        if (mCamManager == null) {
            return;
        }

        if (!mCamManager.isBoundToService()) {
            Log.e(TAG, "updateCAMScanList() Cam Service not bound");
            return;
        }

        // if CAM is disabled or not ready, no need get the list.
        if (!mCamManager.isSmartSelectionEnabled()
                || mCamManager.getCamState() != CamManager.CAM_STATE_ENABLED) {
            Log.e(TAG, "updateCAMScanList() CAM is not ready");
            return;
        }

        mCamScanList = mCamManager.getCamScanResults();
        if (mCamScanList == null) {
            Log.e(TAG, "updateCAMScanList() Scan list is null");
        }

        if (mCamScanList != null && mFragment != null) {
            Log.e(TAG, "CAM List " + mCamScanList.toString());
            // Update the CAM Acesss points with the icon
            int totalAP = mFragment.getPreferenceScreen().getPreferenceCount();
            int counter = 0;
            while (totalAP > counter) {
                Preference preference = mFragment.getPreferenceScreen().getPreference(counter);
                if (preference instanceof AccessPoint) {
                    final AccessPoint accessPoint = (AccessPoint) preference;
                    updateCAMAccessPoint(accessPoint);
                }
                counter++;
            }
        }
    }
}
