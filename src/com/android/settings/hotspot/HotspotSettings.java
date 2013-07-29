/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.settings.hotspot;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiApConnectedDevice;
import android.net.wifi.WifiApConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;

import com.android.settings.hotspot.HotspotEnabler;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.wifi.WifiApDialog;
import com.android.settings.R;

import java.util.WeakHashMap;
import java.util.List;

/**
 * HotspotSettings is the Settings screen for Wifi AP configuration and
 * connection management.
 */
public final class HotspotSettings extends SettingsPreferenceFragment
        implements DialogInterface.OnClickListener {

    private static final String TAG = "HotspotSettings";

    private static final int DIALOG_AP_SETTINGS = 1;

    private PreferenceGroup mConnectedDevicesCategory;
    final WeakHashMap<String, Preference> mDevicePreferenceMap =
            new WeakHashMap<String, Preference>();

    private WifiApDialog mDialog;
    private WifiManager mWifiManager;

    private HotspotEnabler mHotspotEnabler;

    private TextView mEmptyView;

    private final IntentFilter mIntentFilter;

    // accessed from inner class (not private to avoid thunks)
    Preference mMyDevicePreference;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiManager.WIFI_AP_STATE_CHANGED_ACTION.equals(action)) {
                int wifiApState = intent.getIntExtra(
                        WifiManager.EXTRA_WIFI_AP_STATE, WifiManager.WIFI_AP_STATE_FAILED);
                updateContent(wifiApState);
            } else if (WifiManager.WIFI_AP_STA_TETHER_CONNECT_ACTION.equals(action)) {
                String macAddress = intent.getStringExtra(WifiManager.EXTRA_WIFI_AP_DEVICE_ADDRESS);
                String hostName = intent.getStringExtra(WifiManager.EXTRA_WIFI_AP_HOST_NAME);

                onDeviceAdded(hostName, macAddress);
            } else if (WifiManager.WIFI_AP_STA_NOTIFICATION_ACTION.equals(action)) {
                int apStaEvent = intent.getIntExtra(WifiManager.EXTRA_WIFI_AP_STA_EVENT,
                                                    WifiManager.WIFI_AP_STA_UNKNOWN);
                String macAddress = intent.getStringExtra(WifiManager.EXTRA_WIFI_AP_DEVICE_ADDRESS);

                switch (apStaEvent) {
                    case WifiManager.WIFI_AP_STA_DISCONNECT:
                        onDeviceDeleted(macAddress);
                        break;
                }
            }
        }
    };

    public HotspotSettings() {
        mIntentFilter = new IntentFilter(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiManager.WIFI_AP_STA_NOTIFICATION_ACTION);
        mIntentFilter.addAction(WifiManager.WIFI_AP_STA_TETHER_CONNECT_ACTION);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mWifiManager = (WifiManager) getActivity().getSystemService(Context.WIFI_SERVICE);

        mEmptyView = (TextView) getView().findViewById(android.R.id.empty);
        getListView().setEmptyView(mEmptyView);
    }

    void addPreferencesForActivity() {
        addPreferencesFromResource(R.xml.hotspot_settings);

        Activity activity = getActivity();

        Switch actionBarSwitch = new Switch(activity);

        if (activity instanceof PreferenceActivity) {
            PreferenceActivity preferenceActivity = (PreferenceActivity) activity;
            if (preferenceActivity.onIsHidingHeaders() || !preferenceActivity.onIsMultiPane()) {
                final int padding = activity.getResources().getDimensionPixelSize(
                        R.dimen.action_bar_switch_padding);
                actionBarSwitch.setPadding(0, 0, padding, 0);
                activity.getActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
                        ActionBar.DISPLAY_SHOW_CUSTOM);
                activity.getActionBar().setCustomView(actionBarSwitch, new ActionBar.LayoutParams(
                        ActionBar.LayoutParams.WRAP_CONTENT,
                        ActionBar.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER_VERTICAL | Gravity.RIGHT));
            }
        }

        mHotspotEnabler = new HotspotEnabler(activity, actionBarSwitch);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesForActivity();
    }

    @Override
    public void onResume() {
        if (mConnectedDevicesCategory == null) {
            mConnectedDevicesCategory = new PreferenceCategory(getActivity());
        }
        if (mHotspotEnabler != null) {
            mHotspotEnabler.resume();
        }
        super.onResume();

        getActivity().registerReceiver(mReceiver, mIntentFilter);
        updateContent(mWifiManager.getWifiApState());
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mHotspotEnabler != null) {
            mHotspotEnabler.pause();
        }
        getActivity().unregisterReceiver(mReceiver);
    }

    private void onDeviceAdded(String name, String address) {
        Preference preference;
        if (mDevicePreferenceMap.get(address) == null) {
            preference = new Preference(getActivity());
            preference.setTitle(name);
            preference.setSummary(address);

            mConnectedDevicesCategory.addPreference(preference);
            mDevicePreferenceMap.put(address, preference);
        }
    }

    private void onDeviceDeleted(String address) {
        Preference preference = mDevicePreferenceMap.remove(address);
        if (preference != null) {
            mConnectedDevicesCategory.removePreference(preference);
        }
    }

    private void updateContent(int hotspotState) {
        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        int messageId = R.string.hotspot_off;

        switch (hotspotState) {
            case WifiManager.WIFI_AP_STATE_ENABLED:
                preferenceScreen.removeAll();
                preferenceScreen.setOrderingAsAdded(true);
                mDevicePreferenceMap.clear();

                if (mMyDevicePreference == null) {
                    mMyDevicePreference = new Preference(getActivity());
                }
                mMyDevicePreference.setTitle(R.string.wifi_tether_configure_ap_text);

                WifiApConfiguration apConf = mWifiManager.getWifiApConfiguration();
                if (apConf != null)
                    mMyDevicePreference.setSummary(apConf.SSID);

                mMyDevicePreference.setPersistent(false);
                mMyDevicePreference.setEnabled(true);
                preferenceScreen.addPreference(mMyDevicePreference);

                mConnectedDevicesCategory.removeAll();
                mConnectedDevicesCategory.setTitle(R.string.hotspot_connected_devices);
                getPreferenceScreen().addPreference(mConnectedDevicesCategory);
                mConnectedDevicesCategory.setEnabled(true);

                List<WifiApConnectedDevice> devices = mWifiManager.getWifiApConnectedList();
                if ((devices != null) && (devices.size() > 0)) {
                    for (final WifiApConnectedDevice device : devices) {
                        onDeviceAdded(device.getDeviceName(), device.getMacAddr());
                    }
                }

                return; // not break

            case WifiManager.WIFI_AP_STATE_DISABLING:
                messageId = R.string.hotspot_stopping;
                break;

            case WifiManager.WIFI_AP_STATE_FAILED:
            case WifiManager.WIFI_AP_STATE_DISABLED:
                messageId = R.string.hotspot_off;
                break;

            case WifiManager.WIFI_AP_STATE_ENABLING:
                messageId = R.string.hotspot_starting;
                break;
        }

        preferenceScreen.removeAll();
        mEmptyView.setText(messageId);
        getActivity().invalidateOptionsMenu();
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen screen, Preference preference) {
        if (preference == mMyDevicePreference) {
            showDialog(DIALOG_AP_SETTINGS);
        }
        return super.onPreferenceTreeClick(screen, preference);
    }

    @Override
    public Dialog onCreateDialog(int id) {
        if (id == DIALOG_AP_SETTINGS) {
            final Activity activity = getActivity();
            mDialog = new WifiApDialog(activity, this, mWifiManager.getWifiApConfiguration());
            return mDialog;
        }

        return null;
    }

    public void onClick(DialogInterface dialogInterface, int button) {
        if (button == DialogInterface.BUTTON_POSITIVE) {
            WifiApConfiguration mWifiConfig = mDialog.getConfig();
            if (mWifiConfig != null) {
                /**
                 * if soft AP is stopped, bring up
                 * else restart with new config
                 * TODO: update config on a running access point when framework support is added
                 */
                if (mWifiManager.getWifiApState() == WifiManager.WIFI_AP_STATE_ENABLED) {
                    mWifiManager.setWifiApEnabled(null, false);
                    mWifiManager.setWifiApEnabled(mWifiConfig, true);
                } else {
                    mWifiManager.setWifiApConfiguration(mWifiConfig);
                }
                mMyDevicePreference.setSummary(mWifiConfig.SSID);
            }
            Settings.System.putInt(getContentResolver(), mDialog.KEY_HOTSPOT_SOUND_NOTIFY,
                    mDialog.isSoundNotifyEnabled() ? 1: 0);
        }
    }

}
