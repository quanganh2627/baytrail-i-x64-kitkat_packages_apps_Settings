/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.settings.ethernet;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.*;
import android.net.LinkAddress;
import android.net.NetworkInfo.DetailedState;
import android.net.ProxyInfo;
import com.android.internal.ethernet.EthernetInfo;
import com.android.internal.ethernet.EthernetManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.widget.TextView;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import java.net.InetAddress;

public class EthernetSettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceClickListener,
            DialogInterface.OnClickListener {
    private static final String TAG = "EthernetSettings";
    private static final int ETHERNET_DIALOG_ID = 1;

    private final IntentFilter mFilter;
    private final BroadcastReceiver mReceiver;
    private EthernetManager mEthernetManager;
    private EthernetDialog mDialog;
    private TextView mEmptyView;

    public EthernetSettings() {
        mFilter = new IntentFilter();
        mFilter.addAction(EthernetManager.INTERFACE_STATE_CHANGED_ACTION);
        mFilter.addAction(EthernetManager.INTERFACE_REMOVED_ACTION);

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                EthernetInfo ei = null;
                if (EthernetManager.INTERFACE_STATE_CHANGED_ACTION.equals(action)) {
                    Log.d(TAG, "INTERFACE_STATE_CHANGED_ACTION");
                    ei = intent.getParcelableExtra( EthernetManager.EXTRA_ETHERNET_INFO);
                } else if (EthernetManager.INTERFACE_REMOVED_ACTION.equals(action)) {
                    Log.d(TAG, "INTERFACE_REMOVED_ACTION");
                    ei = null;
                }
                updatePreferences(ei);
            }
        };
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        mEthernetManager = (EthernetManager) getSystemService(Context.ETHERNET_SERVICE);
        mEmptyView = (TextView) getView().findViewById(android.R.id.empty);
        getListView().setEmptyView(mEmptyView);

        // After confirming PreferenceScreen is available, we call super.
        super.onActivityCreated(savedInstanceState);
    }

    public boolean onPreferenceClick(Preference preference) {
        if (preference.getKey().equals("ethernet_change_settings")) {
            showDialog(ETHERNET_DIALOG_ID);
        }
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().registerReceiver(mReceiver, mFilter);
        updatePreferences(mEthernetManager.getCurrentInterface());
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(mReceiver);
    }

    @Override
    public Dialog onCreateDialog(int dialogId) {
        if (dialogId == ETHERNET_DIALOG_ID) {
            mDialog = new EthernetDialog(getActivity(),
                    (DialogInterface.OnClickListener)this,
                    mEthernetManager);
            return mDialog;
        }
        return super.onCreateDialog(dialogId);
    }

    private void updatePreferencesScanning(EthernetInfo info) {
        findPreference("ethernet_ip_settings").setSummary("Waiting for connection");

        findPreference("ethernet_ip_address").setEnabled(false);
        findPreference("ethernet_netmask").setEnabled(false);
        findPreference("ethernet_default_gateway").setEnabled(false);
        findPreference("ethernet_dns").setEnabled(false);
        findPreference("ethernet_proxy_server").setEnabled(false);
        findPreference("ethernet_proxy_port").setEnabled(false);
        findPreference("ethernet_proxy_exclusion").setEnabled(false);
    }

    private void updatePreferences(EthernetInfo info) {
        // Safeguard from some delayed event handling
        if (getActivity() == null) return;

        PreferenceScreen ps = getPreferenceScreen();
        if (ps != null) {
            ps.removeAll();
        }

        if (info != null) {
            addPreferencesFromResource(R.xml.ethernet_settings);
            Preference changeSettings = findPreference("ethernet_change_settings");
            changeSettings.setOnPreferenceClickListener(this);

            findPreference("ethernet_interface_name").setSummary(info.getName());
            findPreference("ethernet_mac_address").setSummary(info.getHwAddress());

            if (info.getDetailedState() != DetailedState.CONNECTED) {
                updatePreferencesScanning(info);
                return;
            }

            String ipAssignment = "Static";
            if (info.getIpAssignment() == EthernetInfo.IpAssignment.DHCP) {
                ipAssignment = "DHCP";
            }

            String ipAddress = "";
            String netmask = "";
            LinkAddress la = info.getLinkAddress();
            if (la != null) {
                ipAddress = la.getAddress().getHostAddress();
                netmask = Integer.toString(la.getNetworkPrefixLength());
            }

            String defaultGateway = "";
            InetAddress dgw = info.getDefaultGateway();
            if (dgw != null) {
                defaultGateway = dgw.getHostAddress();
            }
            String dns = "";
            InetAddress dnsIa = info.getDNS1();
            if (dnsIa != null) {
                dns = dnsIa.getHostAddress();
                dnsIa = info.getDNS2();
                if (dnsIa != null) {
                    dns += ", " + dnsIa.getHostAddress();
                }
            }

            String proxyHost = "";
            String proxyPort = "";
            String proxyExclusion = "";

            if (info.getProxySettings() == EthernetInfo.ProxySettings.STATIC) {
                ProxyInfo pp = info.getLinkProperties().getHttpProxy();
                proxyHost = pp.getHost();
                proxyPort = Integer.toString(pp.getPort());
                proxyExclusion = pp.getExclusionListAsString();
            }

            findPreference("ethernet_ip_settings").setSummary(ipAssignment);
            findPreference("ethernet_ip_address").setSummary(ipAddress);
            findPreference("ethernet_netmask").setSummary(netmask);
            findPreference("ethernet_default_gateway").setSummary(defaultGateway);
            findPreference("ethernet_dns").setSummary(dns);
            findPreference("ethernet_proxy_server").setSummary(proxyHost);
            findPreference("ethernet_proxy_port").setSummary(proxyPort);
            findPreference("ethernet_proxy_exclusion").setSummary(proxyExclusion);
        }
    }

    @Override
    public void onClick(DialogInterface dialogInterface, int button) {
        if (button == AlertDialog.BUTTON_POSITIVE) {
            if (mDialog == null) {
                Log.d(TAG, "mDialog == null");
            } else if (mDialog.getController() == null) {
                Log.d(TAG, "mDialog.getController() == null");
            } else if (mDialog.getController().getInfo() == null) {
                Log.d(TAG, "mDialog.getController().getInfo()");
            }
            mEthernetManager.updateInterface(mDialog.getController().getInfo());
        }
    }
}
