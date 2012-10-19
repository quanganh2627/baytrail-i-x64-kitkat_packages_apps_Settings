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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.widget.CompoundButton;
import android.net.NetworkInfo;
import android.widget.Switch;
import com.android.internal.ethernet.EthernetInfo;
import com.android.internal.ethernet.EthernetManager;
import java.util.List;

public class EthernetEnabler implements CompoundButton.OnCheckedChangeListener {
    private static final String TAG = "EthernetEnabler";
    private final Context mContext;
    private Switch mSwitch;
    private boolean mStateMachineEvent;

    private final EthernetManager mEthernetManager;
    private final IntentFilter mIntentFilter;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(EthernetManager.INTERFACE_STATE_CHANGED_ACTION)) {
                EthernetInfo ei = (EthernetInfo) intent.getParcelableExtra(
                        EthernetManager.EXTRA_ETHERNET_INFO);
                NetworkInfo ni = ei.getNetworkInfo();
                switch (ni.getState()) {
                    case CONNECTING:
                        setSwitchChecked(true);
                        mSwitch.setEnabled(false);
                        break;
                    case CONNECTED:
                        setSwitchChecked(true);
                        mSwitch.setEnabled(true);
                        break;
                    case SUSPENDED:
                        setSwitchChecked(true);
                        mSwitch.setEnabled(true);
                        break;
                    case DISCONNECTING:
                        if (mSwitch.isChecked()) {
                            mEthernetManager.reconnect();
                        }
                        setSwitchChecked(true);
                        mSwitch.setEnabled(false);
                        break;
                    case DISCONNECTED:
                        setSwitchChecked(false);
                        mSwitch.setEnabled(true);
                        break;
                    case UNKNOWN:
                        setSwitchChecked(false);
                        mSwitch.setEnabled(true);
                        break;
                    default:
                }
            }
        }
    };

    private void setSwitchChecked(boolean checked) {
        if (checked != mSwitch.isChecked()) {
            mStateMachineEvent = true;
            mSwitch.setChecked(checked);
            mStateMachineEvent = false;
        }
    }

    public EthernetEnabler(Context context, Switch switch_) {
        mContext = context;
        mSwitch = switch_;

        mEthernetManager = (EthernetManager) context.getSystemService(Context.ETHERNET_SERVICE);
        mIntentFilter = new IntentFilter(EthernetManager.INTERFACE_STATE_CHANGED_ACTION);
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean enabled) {
        if (mStateMachineEvent) {
            return;
        }

        if (enabled) {
            mEthernetManager.reconnect();
        } else {
            mEthernetManager.teardown();
        }
    }

    public void setSwitch(Switch switch_) {
        if (mSwitch == switch_) return;
        mSwitch.setOnCheckedChangeListener(null);
        mSwitch = switch_;
        mSwitch.setOnCheckedChangeListener(this);

        final boolean isEnabled = mEthernetManager.isEnabled();
        mSwitch.setChecked(isEnabled);
    }

    public void resume() {
        mContext.registerReceiver(mReceiver, mIntentFilter);
        mSwitch.setOnCheckedChangeListener(this);
    }

    public void pause() {
        mContext.unregisterReceiver(mReceiver);
        mSwitch.setOnCheckedChangeListener(null);
    }
}
