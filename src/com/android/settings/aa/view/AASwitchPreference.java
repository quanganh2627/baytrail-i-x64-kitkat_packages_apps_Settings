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
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
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

    public AASwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs); // we used this constructor
        mContext = context;
        L.i("AASwitchPreference(Context context, AttributeSet attrs)");
        setLayoutResource(R.layout.preference_aa_edit);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        L.i("onBindView(View view)");
        View textLayout = view.findViewById(R.id.aa_edit_pref);
        textLayout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                L.i("#########clicked to edit###########");
                mAACtr.launchAdaptiveAuthActivity(AAController.LAUNCH_FOR_AUTHENTICATION_AND_EDIT);
            }
        });
        mSwitchButton = (Switch) view.findViewById(R.id.sz_settings);
        mSwitchButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View clickedView) {
                L.i("#############toggle clicked################" + mSwitchButton.isChecked());
                if (mSwitchButton.isChecked()) {
                    mAACtr.launchAdaptiveAuthActivity(AAController.LAUNCH_FOR_AUTHENTICATION);
                } else {
                    mAACtr.doTurnOffAA();
                }
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
