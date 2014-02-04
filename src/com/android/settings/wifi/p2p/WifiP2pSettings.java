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

package com.android.settings.wifi.p2p;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pGroupList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.net.wifi.p2p.WifiP2pManager.GroupInfoListener;
import android.net.wifi.p2p.WifiP2pManager.PersistentGroupInfoListener;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.text.InputFilter;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import java.util.Arrays;
import java.util.List;
import java.util.Collection;

/*
 * Displays Wi-fi p2p settings UI
 */
public class WifiP2pSettings extends SettingsPreferenceFragment
        implements PersistentGroupInfoListener, GroupInfoListener, ConnectionInfoListener, PeerListListener {
    private static final String TAG = "WifiP2pSettings";
    private static final boolean DBG = false;
    private static final int MENU_ID_SEARCH = Menu.FIRST;
    private static final int MENU_ID_RENAME = Menu.FIRST + 1;

    private final IntentFilter mIntentFilter = new IntentFilter();
    private WifiP2pManager mWifiP2pManager;
    ConnectivityManager mCManager;
    private WifiP2pManager.Channel mChannel;
    private OnClickListener mRenameListener;
    private OnClickListener mDisconnectListener;
    private OnClickListener mCancelConnectListener;
    private OnClickListener mDeleteGroupListener;
    private WifiP2pPeer mSelectedWifiPeer;
    private WifiP2pPersistentGroup mSelectedGroup;
    private String mSelectedGroupName;
    private EditText mDeviceNameText;

    private boolean mWifiP2pEnabled;
    private boolean mWifiP2pSearching;
    private int mConnectedDevices;
    private WifiP2pGroup mConnectedGroup;
    private boolean mLastGroupFormed = false;

    private PreferenceGroup mPeersGroup;
    private PreferenceGroup mPersistentGroup;
    private Preference mThisDevicePref;

    private static final int DIALOG_DISCONNECT  = 1;
    private static final int DIALOG_CANCEL_CONNECT = 2;
    private static final int DIALOG_RENAME = 3;
    private static final int DIALOG_DELETE_GROUP = 4;

    private static final String SAVE_DIALOG_PEER = "PEER_STATE";
    private static final String SAVE_DEVICE_NAME = "DEV_NAME";
    private static final String SAVE_SELECTED_GROUP = "GROUP_NAME";
    private static final String PEER_IP_ADDRESS = "PEER_IP";
    private static final String FILE_TO_SHARE = "FILE_TO_SEND";
    private static final String GROUP_NAME = "GROUP_NAME";
    private static final String NO_FILE_TO_SHARE = "NO_FILE_TO_SHARE";
    private static final int SERVER_SOCKET_PORT = 8988;

    private WifiP2pDevice mThisDevice;
    private WifiP2pDeviceList mPeers = new WifiP2pDeviceList();

    private String mSavedDeviceName;

    private String fileToShare;
    private String peerIpAddress;
    NotificationManager notificationManager;
    SharedPreferences settings;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                mWifiP2pEnabled = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE,
                    WifiP2pManager.WIFI_P2P_STATE_DISABLED) == WifiP2pManager.WIFI_P2P_STATE_ENABLED;
                handleP2pStateChanged();
            } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                mPeers = (WifiP2pDeviceList) intent.getParcelableExtra(
                        WifiP2pManager.EXTRA_P2P_DEVICE_LIST);
                handlePeersChanged();
            } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                if (mWifiP2pManager == null) return;
                NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(
                        WifiP2pManager.EXTRA_NETWORK_INFO);
                WifiP2pInfo wifip2pinfo = (WifiP2pInfo) intent.getParcelableExtra(
                        WifiP2pManager.EXTRA_WIFI_P2P_INFO);
                if (mWifiP2pManager != null) {
                    mWifiP2pManager.requestGroupInfo(mChannel, WifiP2pSettings.this);
                }
                if (networkInfo.isConnected()) {
                    if (DBG) Log.d(TAG, "Connected");
                    mWifiP2pManager.requestConnectionInfo(mChannel, WifiP2pSettings.this);
                } else if (mLastGroupFormed != true) {
                    peerIpAddress = null;
                    //start a search when we are disconnected
                    //but not on group removed broadcast event
                    startSearch();
                }
                mLastGroupFormed = wifip2pinfo.groupFormed;
            } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
                mThisDevice = (WifiP2pDevice) intent.getParcelableExtra(
                        WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
                if (DBG) Log.d(TAG, "Update device info: " + mThisDevice);
                updateDevicePref();
            } else if (WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION.equals(action)) {
                int discoveryState = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE,
                    WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED);
                if (DBG) Log.d(TAG, "Discovery state changed: " + discoveryState);
                if (discoveryState == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED) {
                    updateSearchMenu(true);
                } else {
                    updateSearchMenu(false);
                }
            } else if (WifiP2pManager.WIFI_P2P_PERSISTENT_GROUPS_CHANGED_ACTION.equals(action)) {
                if (mWifiP2pManager != null) {
                    mWifiP2pManager.requestPersistentGroupInfo(mChannel, WifiP2pSettings.this);
                }
            } else if (action.equals(WifiManager.WIFI_AP_STA_TETHER_CONNECT_ACTION)) { // A client connected to the GO
                String deviceMacAddr = intent.getStringExtra(WifiManager.EXTRA_WIFI_AP_DEVICE_ADDRESS);
                String deviceIpAddress = intent.getStringExtra(WifiManager.EXTRA_WIFI_AP_IP_ADDRESS);
                Log.i(TAG, "A client has connected, the infos are: devicemacaddress: "+ deviceMacAddr+" deviceipaddress: "+deviceIpAddress);
                if (deviceMacAddr != null && deviceIpAddress != null && mConnectedGroup != null) {
                    peerIpAddress = deviceIpAddress;
                    Log.i(TAG, "Assigned ip address: "+peerIpAddress);
                    SharedPreferences.Editor editor = settings.edit();
                    editor.putString(PEER_IP_ADDRESS, peerIpAddress);
                    Log.i(TAG, "Assigned ip address in editor: "+peerIpAddress);
                    editor.commit();
                    if (peerIpAddress != null && fileToShare != null &&!fileToShare.equals(NO_FILE_TO_SHARE)) {
                        Log.i(TAG, "Ip address has been given to a client, starting transfer...");
                        startTransferService();
                        fileToShare = NO_FILE_TO_SHARE;
                    }
                } else {
                    Log.e(TAG, "No mac address or no IP address");
                }
            }
        }
    };

    private void startTransferService() {
        Intent serviceIntent = new Intent(getActivity(), FileTransferService.class);
        serviceIntent.setAction(FileTransferService.ACTION_SEND_FILE);
        serviceIntent.putExtra(FileTransferService.EXTRAS_FILE_PATH, fileToShare);
        serviceIntent.putExtra(FileTransferService.PEER_ADDRESS,
                peerIpAddress);
        serviceIntent.putExtra(FileTransferService.PEER_PORT, SERVER_SOCKET_PORT);
        getActivity().startService(serviceIntent);
    }

    private void startTransferServerService() {
        Intent serviceIntent = new Intent(getActivity(),FileTransferServerService.class);
        serviceIntent.setAction(FileTransferServerService.ACTION_START_SERVER_THREAD);
        serviceIntent.putExtra(FileTransferServerService.PEER_PORT, SERVER_SOCKET_PORT);
        getActivity().startService(serviceIntent);
    }

    public WifiP2pSettings() {
        if (DBG) Log.d(TAG, "Creating WifiP2pSettings ...");
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        Log.i(TAG, "in onactivity created");
        addPreferencesFromResource(R.xml.wifi_p2p_settings);
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(PEER_IP_ADDRESS)) {
                peerIpAddress = savedInstanceState.getString(PEER_IP_ADDRESS);
                Log.i(TAG, "peerIpAddress restored from saved instance: "+peerIpAddress);
            }
        }
        this.settings = getActivity().getPreferences(Activity.MODE_PRIVATE);
        Intent intent =  getActivity().getIntent();
        if (intent != null && savedInstanceState == null) {
            String action = intent.getAction();
            if (action != null && action.equals(Intent.ACTION_SEND)) {
                final Uri stream = (Uri)intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (stream != null)
                    fileToShare = stream.toString();
                Log.i(TAG, "Action send received, the file to share is: "+stream);
                if (peerIpAddress != null && mConnectedGroup != null) {
                    startTransferService();
                    fileToShare = NO_FILE_TO_SHARE;
                } else {
                    Log.i(TAG, "Send received, but we don't have the needed conditions to send the file");
                }
            }
        }

        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PERSISTENT_GROUPS_CHANGED_ACTION);
        mIntentFilter.addAction(WifiManager.WIFI_AP_STA_TETHER_CONNECT_ACTION);

        final Activity activity = getActivity();
        mWifiP2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        if (mWifiP2pManager != null) {
            mChannel = mWifiP2pManager.initialize(activity, getActivity().getMainLooper(), null);
            if (mChannel == null) {
                //Failure to set up connection
                Log.e(TAG, "Failed to set up connection with wifi p2p service");
                mWifiP2pManager = null;
            }
        } else {
            Log.e(TAG, "mWifiP2pManager is null !");
        }

        if (savedInstanceState != null && savedInstanceState.containsKey(SAVE_DIALOG_PEER)) {
            WifiP2pDevice device = savedInstanceState.getParcelable(SAVE_DIALOG_PEER);
            mSelectedWifiPeer = new WifiP2pPeer(getActivity(), device);
        }
        if (savedInstanceState != null && savedInstanceState.containsKey(SAVE_DEVICE_NAME)) {
            mSavedDeviceName = savedInstanceState.getString(SAVE_DEVICE_NAME);
        }
        if (savedInstanceState != null && savedInstanceState.containsKey(SAVE_SELECTED_GROUP)) {
            mSelectedGroupName = savedInstanceState.getString(SAVE_SELECTED_GROUP);
        }

        mRenameListener = new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    if (mWifiP2pManager != null) {
                        mWifiP2pManager.setDeviceName(mChannel,
                                mDeviceNameText.getText().toString(),
                                new WifiP2pManager.ActionListener() {
                            public void onSuccess() {
                                if (DBG) Log.d(TAG, " device rename success");
                            }
                            public void onFailure(int reason) {
                                Toast.makeText(getActivity(),
                                        R.string.wifi_p2p_failed_rename_message,
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }
            }
        };

        //disconnect dialog listener
        mDisconnectListener = new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    if (mWifiP2pManager != null) {
                        mWifiP2pManager.removeGroup(mChannel, new WifiP2pManager.ActionListener() {
                            public void onSuccess() {
                                if (DBG) Log.d(TAG, " remove group success");
                            }
                            public void onFailure(int reason) {
                                if (DBG) Log.d(TAG, " remove group fail " + reason);
                            }
                        });
                    }
                }
            }
        };

        //cancel connect dialog listener
        mCancelConnectListener = new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    if (mWifiP2pManager != null) {
                        mWifiP2pManager.cancelConnect(mChannel,
                                new WifiP2pManager.ActionListener() {
                            public void onSuccess() {
                                if (DBG) Log.d(TAG, " cancel connect success");
                            }
                            public void onFailure(int reason) {
                                if (DBG) Log.d(TAG, " cancel connect fail " + reason);
                            }
                        });
                    }
                }
            }
        };

        //delete persistent group dialog listener
        mDeleteGroupListener = new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    if (mWifiP2pManager != null) {
                        if (mSelectedGroup != null) {
                            if (DBG) Log.d(TAG, " deleting group " + mSelectedGroup.getGroupName());
                            mWifiP2pManager.deletePersistentGroup(mChannel,
                                    mSelectedGroup.getNetworkId(),
                                    new WifiP2pManager.ActionListener() {
                                public void onSuccess() {
                                    if (DBG) Log.d(TAG, " delete group success");
                                }
                                public void onFailure(int reason) {
                                    if (DBG) Log.d(TAG, " delete group fail " + reason);
                                }
                            });
                            mSelectedGroup = null;
                        } else {
                            if (DBG) Log.w(TAG, " No selected group to delete!" );
                        }
                    }
                } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                    if (DBG) {
                        Log.d(TAG, " forgetting selected group " + mSelectedGroup.getGroupName());
                    }
                    mSelectedGroup = null;
                }
            }
        };

        setHasOptionsMenu(true);

        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        preferenceScreen.removeAll();

        preferenceScreen.setOrderingAsAdded(true);
        mThisDevicePref = new Preference(getActivity());
        preferenceScreen.addPreference(mThisDevicePref);

        mPeersGroup = new PreferenceCategory(getActivity());
        mPeersGroup.setTitle(R.string.wifi_p2p_peer_devices);

        mPersistentGroup = new PreferenceCategory(getActivity());
        mPersistentGroup.setTitle(R.string.wifi_p2p_remembered_groups);

        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().registerReceiver(mReceiver, mIntentFilter);
        if (mChannel != null) {
            mWifiP2pManager.requestPeers(mChannel, WifiP2pSettings.this);
        }
    }

    @Override
    public void onPause() {
        Log.i(TAG, "in onpause");
        super.onPause();
        mWifiP2pManager.stopPeerDiscovery(mChannel, null);
        getActivity().unregisterReceiver(mReceiver);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        int textId = mWifiP2pSearching ? R.string.wifi_p2p_menu_searching :
                R.string.wifi_p2p_menu_search;
        menu.add(Menu.NONE, MENU_ID_SEARCH, 0, textId)
            .setEnabled(mWifiP2pEnabled)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add(Menu.NONE, MENU_ID_RENAME, 0, R.string.wifi_p2p_menu_rename)
            .setEnabled(mWifiP2pEnabled)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        MenuItem searchMenu = menu.findItem(MENU_ID_SEARCH);
        MenuItem renameMenu = menu.findItem(MENU_ID_RENAME);
        if (mWifiP2pEnabled) {
            searchMenu.setEnabled(true);
            renameMenu.setEnabled(true);
        } else {
            searchMenu.setEnabled(false);
            renameMenu.setEnabled(false);
        }

        if (mWifiP2pSearching) {
            searchMenu.setTitle(R.string.wifi_p2p_menu_searching);
        } else {
            searchMenu.setTitle(R.string.wifi_p2p_menu_search);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ID_SEARCH:
                startSearch();
                return true;
            case MENU_ID_RENAME:
                showDialog(DIALOG_RENAME);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen screen, Preference preference) {
        if (preference instanceof WifiP2pPeer) {
            mSelectedWifiPeer = (WifiP2pPeer) preference;
            if (mSelectedWifiPeer.device.status == WifiP2pDevice.CONNECTED) {
                showDialog(DIALOG_DISCONNECT);
            } else if (mSelectedWifiPeer.device.status == WifiP2pDevice.INVITED) {
                showDialog(DIALOG_CANCEL_CONNECT);
            } else {
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = mSelectedWifiPeer.device.deviceAddress;

                int forceWps = SystemProperties.getInt("wifidirect.wps", -1);

                if (forceWps != -1) {
                    config.wps.setup = forceWps;
                } else {
                    if (mSelectedWifiPeer.device.wpsPbcSupported()) {
                        config.wps.setup = WpsInfo.PBC;
                    } else if (mSelectedWifiPeer.device.wpsKeypadSupported()) {
                        config.wps.setup = WpsInfo.KEYPAD;
                    } else {
                        config.wps.setup = WpsInfo.DISPLAY;
                    }
                }

                mWifiP2pManager.connect(mChannel, config,
                        new WifiP2pManager.ActionListener() {
                            public void onSuccess() {
                                if (DBG) Log.d(TAG, " connect success");
                            }
                            public void onFailure(int reason) {
                                Log.e(TAG, " connect fail " + reason);
                                Toast.makeText(getActivity(),
                                        R.string.wifi_p2p_failed_connect_message,
                                        Toast.LENGTH_SHORT).show();
                            }
                    });
            }
        } else if (preference instanceof WifiP2pPersistentGroup) {
            mSelectedGroup = (WifiP2pPersistentGroup) preference;
            showDialog(DIALOG_DELETE_GROUP);
        }
        return super.onPreferenceTreeClick(screen, preference);
    }

    @Override
    public Dialog onCreateDialog(int id) {
        if (id == DIALOG_DISCONNECT) {
            String deviceName = TextUtils.isEmpty(mSelectedWifiPeer.device.deviceName) ?
                    mSelectedWifiPeer.device.deviceAddress :
                    mSelectedWifiPeer.device.deviceName;
            String msg;
            if (mConnectedDevices > 1) {
                msg = getActivity().getString(R.string.wifi_p2p_disconnect_multiple_message,
                        deviceName, mConnectedDevices - 1);
            } else {
                msg = getActivity().getString(R.string.wifi_p2p_disconnect_message, deviceName);
            }
            AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.wifi_p2p_disconnect_title)
                .setMessage(msg)
                .setPositiveButton(getActivity().getString(R.string.dlg_ok), mDisconnectListener)
                .setNegativeButton(getActivity().getString(R.string.dlg_cancel), null)
                .create();
            return dialog;
        } else if (id == DIALOG_CANCEL_CONNECT) {
            int stringId = R.string.wifi_p2p_cancel_connect_message;
            String deviceName = TextUtils.isEmpty(mSelectedWifiPeer.device.deviceName) ?
                    mSelectedWifiPeer.device.deviceAddress :
                    mSelectedWifiPeer.device.deviceName;

            AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.wifi_p2p_cancel_connect_title)
                .setMessage(getActivity().getString(stringId, deviceName))
                .setPositiveButton(getActivity().getString(R.string.dlg_ok), mCancelConnectListener)
                .setNegativeButton(getActivity().getString(R.string.dlg_cancel), null)
                .create();
            return dialog;
        } else if (id == DIALOG_RENAME) {
            mDeviceNameText = new EditText(getActivity());
            mDeviceNameText.setFilters(new InputFilter[] {new InputFilter.LengthFilter(30)});
            if (mSavedDeviceName != null) {
                mDeviceNameText.setText(mSavedDeviceName);
                mDeviceNameText.setSelection(mSavedDeviceName.length());
            } else if (mThisDevice != null && !TextUtils.isEmpty(mThisDevice.deviceName)) {
                mDeviceNameText.setText(mThisDevice.deviceName);
                mDeviceNameText.setSelection(0, mThisDevice.deviceName.length());
            }
            mSavedDeviceName = null;
            AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.wifi_p2p_menu_rename)
                .setView(mDeviceNameText)
                .setPositiveButton(getActivity().getString(R.string.dlg_ok), mRenameListener)
                .setNegativeButton(getActivity().getString(R.string.dlg_cancel), null)
                .create();
            return dialog;
        } else if (id == DIALOG_DELETE_GROUP) {
            int stringId = R.string.wifi_p2p_delete_group_message;

            AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setMessage(getActivity().getString(stringId))
                .setPositiveButton(getActivity().getString(R.string.dlg_ok), mDeleteGroupListener)
                .setNegativeButton(getActivity().getString(R.string.dlg_cancel),
                        mDeleteGroupListener).create();
            return dialog;
        }
        return null;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        Log.i(TAG, "In onsaveinstanceState");
        if (mSelectedWifiPeer != null) {
            outState.putParcelable(SAVE_DIALOG_PEER, mSelectedWifiPeer.device);
        }
        if (mDeviceNameText != null) {
            outState.putString(SAVE_DEVICE_NAME, mDeviceNameText.getText().toString());
        }
        if (peerIpAddress != null) {
            outState.putString(PEER_IP_ADDRESS, peerIpAddress);
        }
        if (fileToShare != null && !fileToShare.equals(NO_FILE_TO_SHARE)) {
            outState.putString(FILE_TO_SHARE, fileToShare);
        }
        if (mSelectedGroup != null) {
            outState.putString(SAVE_SELECTED_GROUP, mSelectedGroup.getGroupName());
        }
    }

    private boolean isPreferenceAttached(Preference preference) {
        if (preference == null || preference.getSharedPreferences() == null) {
            return false;
        }
        return true;
    }

    private void handlePeersChanged() {
        mPeersGroup.removeAll();

        mConnectedDevices = 0;
        if (DBG) Log.d(TAG, "List of available peers");

        if (!isPreferenceAttached(mPeersGroup)) {
            return;
        }

        for (WifiP2pDevice peer: mPeers.getDeviceList()) {
            if (DBG) Log.d(TAG, "-> " + peer);
            mPeersGroup.addPreference(new WifiP2pPeer(getActivity(), peer));
            if (peer.status == WifiP2pDevice.CONNECTED) mConnectedDevices++;
        }
        if (DBG) Log.d(TAG, " mConnectedDevices " + mConnectedDevices);
    }

    public void onPersistentGroupInfoAvailable(WifiP2pGroupList groups) {
        mPersistentGroup.removeAll();

        for (WifiP2pGroup group: groups.getGroupList()) {
            if (DBG) Log.d(TAG, " group " + group);
            Activity currentActivity = getActivity();
            if (currentActivity != null) {
                WifiP2pPersistentGroup wppg = new WifiP2pPersistentGroup(currentActivity, group);
                if (mPersistentGroup != null && wppg != null) {
                    if(mPersistentGroup.getPreferenceManager() != null)
                        mPersistentGroup.addPreference(wppg);
                    if (wppg.getGroupName().equals(mSelectedGroupName)) {
                        if (DBG) Log.d(TAG, "Selecting group " + wppg.getGroupName());
                        mSelectedGroup = wppg;
                        mSelectedGroupName = null;
                    }
                }
            }
        }
        if (mSelectedGroupName != null) {
            // Looks like there's a dialog pending getting user confirmation to delete the
            // selected group. When user hits OK on that dialog, we won't do anything; but we
            // shouldn't be in this situation in first place, because these groups are persistent
            // groups and they shouldn't just get deleted!
            Log.w(TAG, " Selected group " + mSelectedGroupName + " disappered on next query ");
        }
    }

    public void onGroupInfoAvailable(WifiP2pGroup group) {
        Log.i(TAG, "Group info available : "+group);
        if (DBG) Log.d(TAG, " group " + group);
        mConnectedGroup = group;
        SharedPreferences.Editor editor = this.settings.edit();
        editor.putString(GROUP_NAME, mConnectedGroup == null? null : mConnectedGroup.getNetworkName());
        editor.commit();
        updateDevicePref();
    }

    private void handleP2pStateChanged() {
        updateSearchMenu(false);
        if (mWifiP2pEnabled) {
            final PreferenceScreen preferenceScreen = getPreferenceScreen();
            preferenceScreen.removeAll();

            preferenceScreen.setOrderingAsAdded(true);
            preferenceScreen.addPreference(mThisDevicePref);

            mPeersGroup.setEnabled(true);
            preferenceScreen.addPreference(mPeersGroup);

            mPersistentGroup.setEnabled(true);
            preferenceScreen.addPreference(mPersistentGroup);
        }
    }

    private void updateSearchMenu(boolean searching) {
       mWifiP2pSearching = searching;
       Activity activity = getActivity();
       if (activity != null) activity.invalidateOptionsMenu();
    }

    private void startSearch() {
        if (mWifiP2pManager != null && !mWifiP2pSearching) {
            mWifiP2pManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
                public void onSuccess() {
                }
                public void onFailure(int reason) {
                    if (DBG) Log.d(TAG, " discover fail " + reason);
                }
            });
        }
    }

    private void updateDevicePref() {
        if (mThisDevice != null) {
            if (TextUtils.isEmpty(mThisDevice.deviceName)) {
                mThisDevicePref.setTitle(mThisDevice.deviceAddress);
            } else {
                mThisDevicePref.setTitle(mThisDevice.deviceName);
            }

            mThisDevicePref.setPersistent(false);
            mThisDevicePref.setEnabled(true);
            mThisDevicePref.setSelectable(false);
        }
    }

    @Override
    public void onPeersAvailable(WifiP2pDeviceList peers) {
        mPeers = peers;
        handlePeersChanged();
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        if (info != null && info.groupOwnerAddress != null) {
            Log.i(TAG, "The connection info is available!, just before starting transfer server service");
            startTransferServerService();
            if (!info.isGroupOwner) {// We are not the group owner, peer address is group owner's
                peerIpAddress = info.groupOwnerAddress.getHostAddress();
                if (fileToShare != null && !fileToShare.equals(NO_FILE_TO_SHARE)) {
                    startTransferService();
                    fileToShare = NO_FILE_TO_SHARE;
                } else {
                    Log.i(TAG, "Ready to transfer but no file to share!");
                }
            }
            else {// We are the group owner, peer address needs to be determined
                if (mConnectedGroup != null) {
                    Log.i(TAG, "mconnectedgroup != null");
                    String groupName = settings.getString(GROUP_NAME, null);
                    Log.i(TAG, "group name in editor is: "+groupName);
                    if (groupName != null && mConnectedGroup.getNetworkName().equals(groupName)) {
                        peerIpAddress = settings.getString(PEER_IP_ADDRESS, null);
                        Log.i(TAG, "peer ip address in editor is: "+peerIpAddress);
                    } else {
                        Log.i(TAG, "Did not get ip address: group names mismatch");
                    }
                }
                if (peerIpAddress == null) {// Otherwise, we hope that ip address has been sent through intents of clients connecting to this device which is GO
                    Log.i(TAG, "Device is GO, waiting for client to obtain ip address");
                } else {
                    if (fileToShare != null && !fileToShare.equals(NO_FILE_TO_SHARE)) {
                        startTransferService();
                        Log.i(TAG, "Transfer service started");
                        fileToShare = NO_FILE_TO_SHARE;
                    }
                }
            }
        }
    }
}
