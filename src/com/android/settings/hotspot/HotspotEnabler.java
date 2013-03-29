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

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

import com.android.settings.R;
import com.android.settings.WirelessSettings;

public class HotspotEnabler implements CompoundButton.OnCheckedChangeListener {
    private final Context mContext;
    private Switch mSwitch;

    private final WifiManager mWifiManager;
    private boolean mStateMachineEvent;
    private final IntentFilter mIntentFilter;
    private int mWifiApState = WifiManager.WIFI_AP_STATE_FAILED;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiManager.WIFI_AP_STATE_CHANGED_ACTION.equals(action)) {
                mWifiApState = intent.getIntExtra(
                        WifiManager.EXTRA_WIFI_AP_STATE, WifiManager.WIFI_AP_STATE_FAILED);
                handleWifiApStateChanged(mWifiApState);
            } else if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
                handleWifiStateChanged(intent.getIntExtra(
                        WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN));
            } else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                setSwitchEnabledState();
            }
        }
    };

    public HotspotEnabler(Context context, Switch switch_) {
        mContext = context;
        mSwitch = switch_;

        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        mIntentFilter = new IntentFilter(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);

        mWifiApState = mWifiManager.getWifiApState();
    }

    public void resume() {
        mContext.registerReceiver(mReceiver, mIntentFilter);
        mSwitch.setOnCheckedChangeListener(this);
        setSwitchEnabledState();
    }

    public void pause() {
        mContext.unregisterReceiver(mReceiver);
        mSwitch.setOnCheckedChangeListener(null);
    }

    public void setSwitch(Switch switch_) {
        if (mSwitch == switch_) return;
        mSwitch.setOnCheckedChangeListener(null);
        mSwitch = switch_;
        mSwitch.setOnCheckedChangeListener(this);

        final int wifiApState = mWifiManager.getWifiApState();
        boolean isEnabled = wifiApState == WifiManager.WIFI_AP_STATE_ENABLED;
        mSwitch.setChecked(isEnabled);
        setSwitchEnabledState();
    }

    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        // Do nothing if called as a result of a state machine event
        if (mStateMachineEvent) {
            return;
        }

        final ContentResolver cr = mContext.getContentResolver();

        // Disable wifi if enabling tethering
        int wifiState = mWifiManager.getWifiState();
        if (isChecked && ((wifiState == WifiManager.WIFI_STATE_ENABLING) ||
                (wifiState == WifiManager.WIFI_STATE_ENABLED))) {
            mWifiManager.setWifiEnabled(false);
            Settings.Secure.putInt(cr, Settings.Global.WIFI_SAVED_STATE, 1);
        }

        if (mWifiManager.setWifiApEnabled(null, isChecked)) {
            // Intent has been taken into account, disable until new state is active
            mSwitch.setEnabled(false);
        } else {
            // Error
            Toast.makeText(mContext, R.string.wifi_error, Toast.LENGTH_SHORT).show();
        }

        // If needed, restore Wifi on tether disable
        if (!isChecked) {
            int wifiSavedState = 0;
            try {
                wifiSavedState = Settings.Secure.getInt(cr, Settings.Global.WIFI_SAVED_STATE);
            } catch (Settings.SettingNotFoundException e) {
                ;
            }
            if (wifiSavedState == 1) {
                mWifiManager.setWifiEnabled(true);
                Settings.Secure.putInt(cr, Settings.Global.WIFI_SAVED_STATE, 0);
            }
        }
    }

    private void handleWifiApStateChanged(int state) {
        switch (state) {
            case WifiManager.WIFI_AP_STATE_ENABLING:
                setSwitchEnabledState();
                break;
            case WifiManager.WIFI_AP_STATE_ENABLED:
                setSwitchChecked(true);
                setSwitchEnabledState();
                break;
            case WifiManager.WIFI_AP_STATE_DISABLING:
                setSwitchEnabledState();
                break;
            case WifiManager.WIFI_AP_STATE_DISABLED:
                setSwitchChecked(false);
                setSwitchEnabledState();
                break;
            default:
                setSwitchChecked(false);
                setSwitchEnabledState();
                break;
        }
    }

    private void handleWifiStateChanged(int state) {
        switch (state) {
            case WifiManager.WIFI_STATE_ENABLED:
            case WifiManager.WIFI_STATE_DISABLED:
            case WifiManager.WIFI_STATE_ENABLING:
            case WifiManager.WIFI_STATE_DISABLING:
                setSwitchEnabledState();
                break;
            default: break;
        }
    }

    private void setSwitchChecked(boolean checked) {
        if (checked != mSwitch.isChecked()) {
            mStateMachineEvent = true;
            mSwitch.setChecked(checked);
            mStateMachineEvent = false;
        }
    }

    private void setSwitchEnabledState() {
        boolean isWifiAllowed = WirelessSettings.isRadioAllowed(mContext, Settings.System.RADIO_WIFI);
        boolean isCellDataAllowed = WirelessSettings.isRadioAllowed(mContext, Settings.System.RADIO_CELL);
        int wifiState = mWifiManager.getWifiState();
        // Enabling Hotspot makes sense only if we have a cellular data connection to share
        if (isWifiAllowed && isCellDataAllowed &&
            (wifiState != WifiManager.WIFI_STATE_DISABLING) &&
            (wifiState != WifiManager.WIFI_STATE_ENABLING) &&
            (mWifiApState != WifiManager.WIFI_AP_STATE_DISABLING) &&
            (mWifiApState != WifiManager.WIFI_AP_STATE_ENABLING)) {
            mSwitch.setEnabled(true);
        } else {
            mSwitch.setEnabled(false);
        }
    }
}
