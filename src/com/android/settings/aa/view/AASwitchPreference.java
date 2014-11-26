/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.settings.aa.view;

import android.content.Context;
import android.content.res.Resources;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Switch;
import com.android.settings.R;
import com.intel.internal.widget.aa.utils.L;
import com.intel.settings.aa.ctr.AAController;
import com.intel.settings.aa.ctr.AASwitchPreferenceController;
import com.intel.settings.aa.ctr.IUiUpdateCallBack;

/**
 * Implement a AA specific preference
 * 1, to avoid most changes in AOSP
 * 2, we can edit and turn on/off AA in this SwitchPreference
 * @author Duan, Qin <qin.duan@intel.com>
 */
public class AASwitchPreference extends Preference implements IUiUpdateCallBack {
    private AAController mAACtr;
    private Switch mSwitchButton;
    private Context mContext = null;
    private boolean mIsTouchStarted = false;
    public AASwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs); // we used this constructor
        mContext = context;
        final Resources res = mContext.getResources();
        int resid = res.getIdentifier(
            "com.android.settings:layout/preference_aa_edit",
            null, null);
        setLayoutResource(resid);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        L.i("onBindView(View view)");
        final Resources res = mContext.getResources();
        int resid = res.getIdentifier(
            "com.android.settings:id/aa_edit_pref",
            null, null);
        View textLayout = view.findViewById(resid);
        textLayout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                L.i("#########clicked to edit###########");
                mAACtr.launchAdaptiveAuthActivity(AAController.LAUNCH_FOR_AUTHENTICATION_AND_EDIT);
            }
        });
        resid = res.getIdentifier(
            "com.android.settings:id/sz_settings",
            null, null);
        mSwitchButton = (Switch) view.findViewById(resid);
        mSwitchButton.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                    boolean isChecked) {
                L.i("#############onCheckedChanged################" + isChecked);
                if (mIsTouchStarted) {
                    if (isChecked) {
                        mAACtr.launchAdaptiveAuthActivity(AAController.LAUNCH_FOR_AUTHENTICATION);
                    } else {
                        mAACtr.doTurnOffAA();
                    }
                } else {
                    L.i("Not a real button touch, ignore this");
                }
                mIsTouchStarted = false;
            }
        });
        mSwitchButton.setOnTouchListener(new OnTouchListener() {

            @Override
            public boolean onTouch(View arg0, MotionEvent arg1) {
                mIsTouchStarted = true;
                return false;
            }
        });
        mAACtr = new AASwitchPreferenceController(mContext);
        mAACtr.CallbackRegister(this);
        mAACtr.doUpdateUI();
    }

    @Override
    public void onListUpdate() {

    }

    @Override
    public void onButtonUpdate(boolean on) {
        mSwitchButton.setChecked(on);
        mSwitchButton.invalidate();
    }

    @Override
    public boolean isButtonChecked() {
        return false;
    }
}
