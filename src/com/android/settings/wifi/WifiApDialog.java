/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.wifi.WifiChannel;
import android.net.wifi.WifiApConfiguration;
import android.net.wifi.WifiConfiguration.AuthAlgorithm;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import com.android.settings.R;

/**
 * Dialog to configure the SSID and security settings
 * for Access Point operation
 */
public class WifiApDialog extends AlertDialog implements View.OnClickListener,
        TextWatcher, AdapterView.OnItemSelectedListener {

    static final int SSID_MAX_LENGTH = 32;
    static final int BUTTON_SUBMIT = DialogInterface.BUTTON_POSITIVE;

    private final DialogInterface.OnClickListener mListener;

    public static final int OPEN_INDEX = 0;
    public static final int WPA2_INDEX = 1;

    public static final int BG_INDEX = 0;
    public static final int BGN_INDEX = 1;
    public static final int A_INDEX = 2;
    public static final int AN_INDEX = 3;
    public static final int AC_INDEX = 4;

    public static final String KEY_HOTSPOT_SOUND_NOTIFY = "hotspot_sound_notify";

    private View mView;
    private TextView mSsid;
    private int mSecurityTypeIndex = OPEN_INDEX;
    private int mBandIndex = BGN_INDEX;
    private int mChannelIndex = 0;
    private EditText mPassword;
    private CheckBox mCheckboxShowPassword;
    private CheckBox mCheckboxEnableSoundNotify;
    private CheckBox mCheckboxShowAdvanced;
    private LinearLayout mAdvancedFields;
    private Spinner mSecuritySpinner;
    private Spinner mBandSpinner;
    private Spinner mChannelSpinner;
    private TextView mLocalIp;
    private TextView mSubnetMask;
    private boolean mShowPassword = false;
    private boolean mEnableSoundNotify = true;
    private boolean mShowAdvanced = false;
    WifiApConfiguration mWifiConfig;

    public WifiApDialog(Context context, DialogInterface.OnClickListener listener,
            WifiApConfiguration wifiConfig) {
        super(context);
        mListener = listener;
        mWifiConfig = wifiConfig;
        if (wifiConfig != null) {
            mSecurityTypeIndex = getSecurityTypeIndex(wifiConfig);
            mBandIndex = getBandIndex(wifiConfig);
        }
    }

    public static int getSecurityTypeIndex(WifiApConfiguration wifiConfig) {
        if (wifiConfig.allowedKeyManagement.get(KeyMgmt.WPA2_PSK)) {
            return WPA2_INDEX;
        }
        return OPEN_INDEX;
    }

    public static int getBandIndex(WifiApConfiguration apConfig) {
        if (apConfig.hwMode.equals(WifiApConfiguration.HW_MODE_BG))
            return apConfig.is80211n ? BGN_INDEX : BG_INDEX;
        else if (apConfig.hwMode.equals(WifiApConfiguration.HW_MODE_A))
            return apConfig.is80211n ? AN_INDEX : A_INDEX;
        else if (apConfig.hwMode.equals(WifiApConfiguration.HW_MODE_AC))
            return AC_INDEX;
        return BGN_INDEX;
    }

    public boolean isSoundNotifyEnabled() {
        return mEnableSoundNotify;
    }

    public WifiApConfiguration getConfig() {

        WifiApConfiguration config = new WifiApConfiguration();

        /**
         * TODO: SSID in WifiApConfiguration for soft ap
         * is being stored as a raw string without quotes.
         * This is not the case on the client side. We need to
         * make things consistent and clean it up
         */
        config.SSID = mSsid.getText().toString();

        switch (mSecurityTypeIndex) {
            case OPEN_INDEX:
                config.allowedKeyManagement.set(KeyMgmt.NONE);
                break;

            case WPA2_INDEX:
                config.allowedKeyManagement.set(KeyMgmt.WPA2_PSK);
                config.allowedAuthAlgorithms.set(AuthAlgorithm.OPEN);
                if (mPassword.length() != 0) {
                    String password = mPassword.getText().toString();
                    config.preSharedKey = password;
                }
                break;

            default:
                return null;
        }

        switch (mBandIndex) {
            case BG_INDEX:
                config.hwMode = WifiApConfiguration.HW_MODE_BG;
                config.is80211n = false;
                break;
            case BGN_INDEX:
                config.hwMode = WifiApConfiguration.HW_MODE_BG;
                config.is80211n = true;
                break;
            case A_INDEX:
                config.hwMode = WifiApConfiguration.HW_MODE_A;
                config.is80211n = false;
                break;
            case AN_INDEX:
                config.hwMode = WifiApConfiguration.HW_MODE_A;
                config.is80211n = true;
                break;
            case AC_INDEX:
                config.hwMode = WifiApConfiguration.HW_MODE_AC;
                config.is80211n = true;
                break;
            default:
                return null;
        }
        if (mChannelIndex == 0) {
            if (mBandIndex >= A_INDEX)
                config.channel = new WifiChannel(WifiChannel.DEFAULT_5_CHANNEL);
            else
                config.channel = new WifiChannel(WifiChannel.DEFAULT_2_4_CHANNEL);
        }
        else
            config.channel = new WifiChannel(
                    (String) mChannelSpinner.getItemAtPosition(mChannelIndex));

        return config;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        mView = getLayoutInflater().inflate(R.layout.wifi_ap_dialog, null);
        mSecuritySpinner = ((Spinner) mView.findViewById(R.id.security));

        setView(mView);
        setInverseBackgroundForced(true);

        Context context = getContext();

        setTitle(R.string.wifi_tether_configure_ap_text);
        mView.findViewById(R.id.type).setVisibility(View.VISIBLE);
        mSsid = (TextView) mView.findViewById(R.id.ssid);
        mPassword = (EditText) mView.findViewById(R.id.password);
        mAdvancedFields = (LinearLayout) mView.findViewById(R.id.hotspot_advanced_settings);
        if (mAdvancedFields != null) {
            mAdvancedFields.setVisibility(mShowAdvanced ? View.VISIBLE : View.GONE);
        }
        mChannelSpinner = (Spinner) mView.findViewById(R.id.hotspot_channel_spinner);
        mBandSpinner = (Spinner) mView.findViewById(R.id.hotspot_band_mode_spinner);

        setButton(BUTTON_SUBMIT, context.getString(R.string.wifi_save), mListener);
        setButton(DialogInterface.BUTTON_NEGATIVE,
        context.getString(R.string.wifi_cancel), mListener);

        if (mWifiConfig != null) {
            mSsid.setText(mWifiConfig.SSID);
            mSecuritySpinner.setSelection(mSecurityTypeIndex);
            if (mSecurityTypeIndex == WPA2_INDEX) {
                  mPassword.setText(mWifiConfig.preSharedKey);
            }
            mBandSpinner.setSelection(mBandIndex);
        }

        if (savedInstanceState != null) {//Restore show password after rotation
            Boolean show_pass = (Boolean)savedInstanceState.get("show_password");
            if (show_pass != null) {
                mShowPassword = show_pass;
            }
        }
        mSsid.addTextChangedListener(this);
        mPassword.setInputType(
                InputType.TYPE_CLASS_TEXT | (mShowPassword ?
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD :
                InputType.TYPE_TEXT_VARIATION_PASSWORD));
        mPassword.addTextChangedListener(this);

        mEnableSoundNotify = (Settings.System.getInt(context.getContentResolver(),
                KEY_HOTSPOT_SOUND_NOTIFY, 1) != 0);

        mCheckboxShowPassword = (CheckBox) mView.findViewById(R.id.show_password);
        if (mCheckboxShowPassword != null) {
            mCheckboxShowPassword.setOnClickListener(this);
            mCheckboxShowPassword.setChecked(mShowPassword);
        }

        mCheckboxEnableSoundNotify = (CheckBox) mView.findViewById(R.id.enable_sound_notify);
        if (mCheckboxEnableSoundNotify != null) {
            mCheckboxEnableSoundNotify.setOnClickListener(this);
            mCheckboxEnableSoundNotify.setChecked(mEnableSoundNotify);
        }

        mCheckboxShowAdvanced = (CheckBox) mView.findViewById(R.id.hotspot_advanced_togglebox);
        if (mCheckboxShowAdvanced != null) {
            mCheckboxShowAdvanced.setOnClickListener(this);
            mCheckboxShowAdvanced.setChecked(mShowAdvanced);
        }

        populateBand();
        populateChannels();
        mSecuritySpinner.setOnItemSelectedListener(this);
        mBandSpinner.setOnItemSelectedListener(this);
        mChannelSpinner.setOnItemSelectedListener(this);

        super.onCreate(savedInstanceState);

        showSecurityFields();
        validate();
    }

    private void validate() {
        final byte[] utf8Ssid = mSsid.getText().toString().getBytes();

        if ((mSsid != null && (mSsid.length() == 0 || utf8Ssid.length > SSID_MAX_LENGTH )) ||
                   (mSecurityTypeIndex == WPA2_INDEX && mPassword.length() < 8)) {
            getButton(BUTTON_SUBMIT).setEnabled(false);
        } else {
            getButton(BUTTON_SUBMIT).setEnabled(true);
        }
    }

    private List<WifiChannel> getWifiAuthorizedChannels() {
        WifiManager wManager = (WifiManager) getContext().getSystemService(Context.WIFI_SERVICE);
        List<WifiChannel> channels = wManager.getWifiAuthorizedChannels();
        if (channels == null || channels.size() == 0) {
            channels = new ArrayList<WifiChannel>();
            channels.add(new WifiChannel(WifiChannel.DEFAULT_2_4_CHANNEL));
        }
        return channels;
    }

    private void populateBand() {
        String[] allBands = getContext().getResources().getStringArray(R.array.wifi_ap_band_mode);
        List<String> allowedBands = new ArrayList<String>();
        List<WifiChannel> channels = getWifiAuthorizedChannels();
        int maxIndex = channels.get(channels.size() - 1).getBand() == WifiChannel.Band.BAND_5GHZ ?
                AC_INDEX : BGN_INDEX;

        for (int i = 0; i <= maxIndex; i++)
            allowedBands.add(allBands[i]);
        ArrayAdapter spinnerArrayAdapter = new ArrayAdapter(getContext(),
                android.R.layout.simple_spinner_item, allowedBands);
        spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        if (mBandSpinner != null) {
            mBandSpinner.setAdapter(spinnerArrayAdapter);
            if (mBandIndex > maxIndex)
                mBandIndex = BGN_INDEX;
            mBandSpinner.setSelection(mBandIndex);
        }
    }

    private void populateChannels() {
        WifiChannel.Band band = WifiChannel.Band.BAND_2_4GHZ;
        if (mBandIndex >= A_INDEX)
            band = WifiChannel.Band.BAND_5GHZ;
        mChannelIndex = 0;
        WifiChannel selectedChannel = null;
        if (mWifiConfig != null)
            selectedChannel = mWifiConfig.channel;
        List<String> userList = new ArrayList<String>();
        List<WifiChannel> channels = getWifiAuthorizedChannels();
        userList.add(getContext().getString(R.string.hotspot_channel_auto));
        for (WifiChannel channel : channels) {
            if (channel.getBand() == band) {
                if (channel.equals(selectedChannel))
                    mChannelIndex = userList.size();
                userList.add(channel.toString());
            }
        }
        ArrayAdapter spinnerArrayAdapter = new ArrayAdapter(getContext(),
                android.R.layout.simple_spinner_item, userList);
        spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        if (mChannelSpinner != null) {
            mChannelSpinner.setAdapter(spinnerArrayAdapter);
            mChannelSpinner.setSelection(mChannelIndex);
        }
    }

    public void onClick(View view) {
        if (view == mCheckboxShowPassword) {
            int position = mPassword.getSelectionStart();
            mShowPassword = mCheckboxShowPassword.isChecked();
            mPassword.setInputType(
                    InputType.TYPE_CLASS_TEXT | (mShowPassword ?
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD :
                    InputType.TYPE_TEXT_VARIATION_PASSWORD));
            if (mPassword.isFocused()) {
                ((EditText)mPassword).setSelection(position);
            }
        } else if (view == mCheckboxEnableSoundNotify) {
            mEnableSoundNotify = mCheckboxEnableSoundNotify.isChecked();
        } else if (view == mCheckboxShowAdvanced) {
            mShowAdvanced = mCheckboxShowAdvanced.isChecked();
            if (mAdvancedFields != null)
                mAdvancedFields.setVisibility(mShowAdvanced ? View.VISIBLE : View.GONE);
        }
    }

    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    public void afterTextChanged(Editable editable) {
        validate();
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (parent == mSecuritySpinner) {
            mSecurityTypeIndex = position;
            showSecurityFields();
            validate();
        }
        else if (parent == mBandSpinner) {
            mBandIndex = position;
            populateChannels();
        }
        else if (parent == mChannelSpinner) {
            mChannelIndex = position;
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    private void showSecurityFields() {
        if (mSecurityTypeIndex == OPEN_INDEX) {
            mView.findViewById(R.id.fields).setVisibility(View.GONE);
            return;
        }
        mView.findViewById(R.id.fields).setVisibility(View.VISIBLE);
    }

    @Override
    public Bundle onSaveInstanceState() {
        Bundle b = super.onSaveInstanceState();
        CheckBox show_pass = (CheckBox) mView.findViewById(R.id.show_password);
        if (show_pass != null)
            b.putBoolean("show_password", show_pass.isChecked());
        return b;
    }
}
