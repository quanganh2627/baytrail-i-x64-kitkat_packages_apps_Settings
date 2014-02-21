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
import android.net.LinkAddress;
import android.net.NetworkUtils;
import android.net.wifi.WifiApConfiguration;
import android.net.wifi.WifiChannel;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.AuthAlgorithm;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import com.android.settings.R;
import com.android.settings.net.NetworkPolicyEditor;
import com.intel.cws.cwsservicemanager.ICwsServiceMgr;
import java.lang.CharSequence;
/**
 * Dialog to configure the SSID and security settings
 * for Access Point operation
 */
public class WifiApDialog extends AlertDialog implements View.OnClickListener,
        TextWatcher, AdapterView.OnItemSelectedListener {

    static final int SSID_MAX_LENGTH = 32;
    static final int BUTTON_SUBMIT = DialogInterface.BUTTON_POSITIVE;

    static final String TAG = "WifiAPDialog";
    private final DialogInterface.OnClickListener mListener;

    public static final int OPEN_INDEX = 0;
    public static final int WPA2_INDEX = 1;

    public static final int BG_INDEX = 0;
    public static final int BGN_INDEX = 1;
    public static final int A_INDEX = 2;
    public static final int AN_INDEX = 3;
    public static final int AC_INDEX = 4;

    static final int WIFI_DEFAULT_MIN_CHAN = 1;
    static final int WIFI_DEFAULT_MAX_CHAN = 11;

    private View mView;
    private TextView mSsid;
    private int mSecurityTypeIndex = OPEN_INDEX;
    private int mBandIndex = BGN_INDEX;
    private int mChannelIndex = 0;
    private int mNetMaskIndex = 0;
    private CheckBox mCheckboxShowPassword;
    private CheckBox mCheckboxShowAdvanced;
    private LinearLayout mAdvancedFields;
    private Spinner mSecuritySpinner;
    private Spinner mBandSpinner;
    private Spinner mChannelSpinner;
    private EditText mPassword;
    private EditText mIpAddress;
    private Spinner mNetMaskSpinner;
    private boolean mShowPassword = false;
    private boolean mShowAdvanced = false;
    WifiConfiguration mWifiConfig;
    private ICwsServiceMgr mCwsServiceManager;

    public WifiApDialog(Context context, DialogInterface.OnClickListener listener,
            WifiConfiguration wifiConfig) {
        super(context);
        mListener = listener;
        mWifiConfig = wifiConfig;
        if (wifiConfig != null) {
            mSecurityTypeIndex = getSecurityTypeIndex(wifiConfig);
            mBandIndex = getBandIndex(wifiConfig);
        } else {
            Log.e(TAG, "WifiApDialog - wifiConfig is null");
        }
        mCwsServiceManager = ICwsServiceMgr.Stub.
                asInterface(ServiceManager.getService(Context.CSM_SERVICE));
        if (mCwsServiceManager == null) {
            Log.e(TAG, "Failed to get a reference on mCwsServiceManager");
        }
    }

    public static int getSecurityTypeIndex(WifiConfiguration wifiConfig) {
        if (wifiConfig.allowedKeyManagement.get(KeyMgmt.WPA2_PSK)) {
            return WPA2_INDEX;
        }
        return OPEN_INDEX;
    }

    private static int getBandIndex(WifiConfiguration apConfig) {

        if (apConfig != null) {
            WifiApConfiguration cfg = apConfig.getWifiApConfigurationAdv();
            if (cfg != null) {
                if (cfg.mHwMode.equals(WifiApConfiguration.HW_MODE_BG))
                    return cfg.mIs80211n ? BGN_INDEX : BG_INDEX;
                else if (cfg.mHwMode.equals(WifiApConfiguration.HW_MODE_A))
                    return cfg.mIs80211n ? AN_INDEX : A_INDEX;
                else if (cfg.mHwMode.equals(WifiApConfiguration.HW_MODE_AC))
                    return AC_INDEX;
            }
        }

        return BGN_INDEX;
    }

    public WifiConfiguration getConfig() {

        WifiConfiguration config = new WifiConfiguration();

        if (config != null) {

            WifiApConfiguration cfg = config.getWifiApConfigurationAdv();
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

            if (cfg != null) {
                cfg.mIpAddress = mIpAddress.getText().toString();
                switch (mBandIndex) {
                    case BG_INDEX:
                        cfg.mHwMode = WifiApConfiguration.HW_MODE_BG;
                        cfg.mIs80211n = false;
                        break;
                    case BGN_INDEX:
                        cfg.mHwMode = WifiApConfiguration.HW_MODE_BG;
                        cfg.mIs80211n = true;
                        break;
                    case A_INDEX:
                        cfg.mHwMode = WifiApConfiguration.HW_MODE_A;
                        cfg.mIs80211n = false;
                        break;
                    case AN_INDEX:
                        cfg.mHwMode = WifiApConfiguration.HW_MODE_A;
                        cfg.mIs80211n = true;
                        break;
                    case AC_INDEX:
                        cfg.mHwMode = WifiApConfiguration.HW_MODE_AC;
                        cfg.mIs80211n = true;
                        break;
                    default:
                        return null;
                }
                if (mChannelIndex == 0) {
                    if (mBandIndex >= A_INDEX)
                        cfg.mChannel = new WifiChannel(WifiChannel.DEFAULT_5_CHANNEL);
                    else
                        cfg.mChannel = new WifiChannel(WifiChannel.DEFAULT_2_4_CHANNEL);
                }
                else
                    cfg.mChannel = new WifiChannel(
                            (String) mChannelSpinner.getItemAtPosition(mChannelIndex));
                cfg.mNetMask =(String)(mNetMaskSpinner.getItemAtPosition(mNetMaskIndex));
            }
        }
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
        mIpAddress = (EditText) mView.findViewById(R.id.ipaddress);
        mNetMaskSpinner = (Spinner) mView.findViewById(R.id.subnet_mask_settings);
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
            if (mBandSpinner != null) {
                mBandSpinner.setSelection(mBandIndex);
            } else {
                Log.e(TAG, "WifiApDialog - spinner view is null");
            }
            if (mIpAddress != null)
                mIpAddress.setText(mWifiConfig.getWifiApConfigurationAdv().mIpAddress);
            selectNetMaskIndex(mWifiConfig);
        }

        if (savedInstanceState != null) { // Restore show password after rotation
            Boolean show_pass = (Boolean)savedInstanceState.get("show_password");
            if (show_pass != null) {
                mShowPassword = show_pass;
            }
        }
        mSsid.addTextChangedListener(this);
        if (mIpAddress != null)
            mIpAddress.addTextChangedListener (new TextWatcher() {

                public void beforeTextChanged(CharSequence s, int start,
                        int count, int after) {
                }
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                public void afterTextChanged(Editable s) {
                    Button b = getButton(BUTTON_SUBMIT);
                    if (!isValidIpAddress(s.toString())) {
                        Toast.makeText(getContext(),
                                R.string.invalid_wifi_ip_address, Toast.LENGTH_SHORT).show();
                        if (b != null)
                            b.setEnabled(false);
                    } else {
                        if (b != null)
                            b.setEnabled(true);
                    }
                }
            }
                );
        mPassword.setInputType(
                InputType.TYPE_CLASS_TEXT | (mShowPassword ?
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD :
                InputType.TYPE_TEXT_VARIATION_PASSWORD));
        mPassword.addTextChangedListener(this);

        mCheckboxShowPassword = (CheckBox) mView.findViewById(R.id.show_password);
        if (mCheckboxShowPassword != null) {
            mCheckboxShowPassword.setOnClickListener(this);
            mCheckboxShowPassword.setChecked(mShowPassword);
        }

        mCheckboxShowAdvanced = (CheckBox) mView.findViewById(R.id.hotspot_advanced_togglebox);
        if (mCheckboxShowAdvanced != null) {
            mCheckboxShowAdvanced.setOnClickListener(this);
            mCheckboxShowAdvanced.setChecked(mShowAdvanced);
        }

        populateBand();
        populateChannels();
        if (mSecuritySpinner != null) {
            mSecuritySpinner.setOnItemSelectedListener(this);
        }
        if (mBandSpinner != null) {
            mBandSpinner.setOnItemSelectedListener(this);
        }
        if (mChannelSpinner != null) {
            mChannelSpinner.setOnItemSelectedListener(this);
        }
        if (mNetMaskSpinner != null) {
            mNetMaskSpinner.setOnItemSelectedListener(this);
        }

        super.onCreate(savedInstanceState);

        showSecurityFields();
        validate();
    }

    private void selectNetMaskIndex(WifiConfiguration config) {
        if (mNetMaskSpinner != null) {
            for (int i = 0; i < mNetMaskSpinner.getCount(); i++) {
                if (config.getWifiApConfigurationAdv().
                        mNetMask != null) {
                    if (config.getWifiApConfigurationAdv().
                            mNetMask.equals((String)(mNetMaskSpinner.getItemAtPosition(i)))) {
                        mNetMaskIndex = i;
                        mNetMaskSpinner.setSelection(i);
                    }
                }
            }
        }
    }

    private boolean isValidIpAddress(String ipAddress) {
        try {
            NetworkUtils.numericToInetAddress(ipAddress);
            return true;
        }
        catch(IllegalArgumentException e) {
            return false;
        }
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

    private void populateBand() {
        String[] allBands = getContext().getResources().getStringArray(R.array.wifi_ap_band_mode);
        List<String> allowedBands = new ArrayList<String>();

        WifiManager wManager = (WifiManager) getContext().getSystemService(Context.WIFI_SERVICE);
        List<WifiChannel> channels = wManager.getWifiAuthorizedChannels();

        int maxIndex = BGN_INDEX;

        if (channels != null) {
            maxIndex = channels.get(channels.size() - 1).getBand() == WifiChannel.Band.BAND_5GHZ
                    ? AC_INDEX : BGN_INDEX;
        } else {
            Log.i(TAG, "getWifiAuthorizedChannels returned NULL, BAND will be forced to 2GHZ");
        }

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
        WifiApConfiguration cfg = null;

        WifiChannel.Band band = WifiChannel.Band.BAND_2_4GHZ;
        if (mBandIndex >= A_INDEX)
            band = WifiChannel.Band.BAND_5GHZ;
        mChannelIndex = 0;
        WifiChannel selectedChannel = null;
        if (mWifiConfig != null) {
            cfg = mWifiConfig.getWifiApConfigurationAdv();
            if (cfg != null)
                selectedChannel = cfg.mChannel;
        }

        WifiManager wManager = (WifiManager) getContext().getSystemService(Context.WIFI_SERVICE);
        List<String> userList = new ArrayList<String>();
        List<WifiChannel> channels = wManager.getWifiAuthorizedChannels();
        userList.add(getContext().getString(R.string.hotspot_channel_auto));
        int safeChannels = 0;
        try {
            if (mCwsServiceManager != null) {
                safeChannels = mCwsServiceManager.getWifiSafeChannelBitmap();
            } else {
                Log.e(TAG,"mCwsServiceManager is null");
            }
        } catch (Exception e) {
            // no need to do anything, we will use the full channel bitmap.
            Log.e(TAG, "populate w safe channels Exception: " + e.toString());
        }
        if (channels != null && cfg != null) {
            for (WifiChannel channel : channels) {
                if (channel.getBand() == band) {
                    if (cfg.mChannel.equals(channel))
                        mChannelIndex = userList.size();
                    if ((safeChannels & (1 << (channel.getChannel() -1 ))) == 0) {
                        userList.add(channel.toString());
                    }

                }
            }
        } else {
            Log.i(TAG, "getWifiAuthorizedChannels returned NULL, set channel 1-11");
            int chan = 1;
            for (chan = WIFI_DEFAULT_MIN_CHAN; chan <= WIFI_DEFAULT_MAX_CHAN; chan++) {
                if (selectedChannel != null) {
                    if ( chan == selectedChannel.getChannel()) {
                        mChannelIndex = userList.size();
                    }
                } else {
                    if (chan == WifiChannel.DEFAULT_2_4_CHANNEL) {
                        mChannelIndex = userList.size();
                    }
                }
                if ((safeChannels & (1 << (chan -1 ))) == 0) {
                    userList.add(Integer.toString(chan));
                }
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
                    InputType.TYPE_CLASS_TEXT | (mShowPassword
                    ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD :
                    InputType.TYPE_TEXT_VARIATION_PASSWORD));
            if (mPassword.isFocused()) {
                ((EditText) mPassword).setSelection(position);
            }
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
        else if (parent == mNetMaskSpinner) {
            mNetMaskIndex = position;
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
