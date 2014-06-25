/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.settings.deviceinfo;

import android.app.Activity;
import android.app.TabActivity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Window;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;

import com.android.settings.R;

import com.android.internal.telephony.TelephonyConstants;
import android.content.pm.PackageManager.NameNotFoundException;

public class StatusTab extends TabActivity implements TabHost.OnTabChangeListener {
    private static final String LOG_TAG = "StatusTab";
    private static final boolean DBG = false;

    private static final int TAB_INDEX_SIM_A = 0;
    private static final int TAB_INDEX_SIM_B = 1;

    private static final String PREF_LAST_MANUALLY_SELECTED_TAB = "last_device_status_tab";
    private static final int PREF_LAST_MANUALLY_SELECTED_TAB_DEFAULT = TAB_INDEX_SIM_A;

    private TabHost mTabHost;

    private int mLastManuallySelectedTab;
    private    Context mPhoneContext;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        final Intent intent = getIntent();

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dual_icc_lock_tab);

        mTabHost = getTabHost();
        mTabHost.setOnTabChangedListener(this);

        // Setup the tabs
        setupTab(0);
        setupTab(1);
        setTabColor();

        restoreLastTab();
    }

    SharedPreferences getPhoneDefaultPref() {
        SharedPreferences prefs = null;
        if (DBG) log("getPhoneDefaultPref");
        if (mPhoneContext == null) {
            if (DBG) log("get phone context");
            try {
                mPhoneContext = createPackageContext("com.android.phone", Context.CONTEXT_IGNORE_SECURITY);
            } catch (NameNotFoundException e) {
                if (DBG) Log.w(LOG_TAG, "NameNotFoundException:" + e);
            }
        }
        if (mPhoneContext != null) {
            prefs = PreferenceManager.getDefaultSharedPreferences(mPhoneContext);
        }
        return prefs;
    }

    void restoreLastTab() {
        // Load the last manually loaded tab
        if (DBG) log("getPref by PhoneApplication's context");
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mLastManuallySelectedTab = prefs.getInt(PREF_LAST_MANUALLY_SELECTED_TAB,
                PREF_LAST_MANUALLY_SELECTED_TAB_DEFAULT);
        if (DBG) log("restoreLastTab, mLastManuallySelectedTab: " + mLastManuallySelectedTab);
        setCurrentTab(mLastManuallySelectedTab);
    }

    void setTabColor() {
        TabWidget tabWidget = mTabHost.getTabWidget();
        for (int i = 0; i < tabWidget.getChildCount(); i++) {
            TextView tv = ((TextView)tabWidget.getChildAt(i).findViewById(android.R.id.title));
            if (i == 0) {
                tv.setTextColor(TelephonyConstants.DSDS_TEXT_COLOR_SLOT_1);
            } else if (i == 1) {
                tv.setTextColor(TelephonyConstants.DSDS_TEXT_COLOR_SLOT_2);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        final int currentTabIndex = mTabHost.getCurrentTab();
        final SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putInt(PREF_LAST_MANUALLY_SELECTED_TAB, mLastManuallySelectedTab);

        editor.commit();
    }

    private void setupTab(int slot) {
        Intent intent = new Intent("android.intent.action.MAIN");
        intent.putExtra(TelephonyConstants.EXTRA_SLOT, slot);
        intent.setClassName("com.android.settings", "com.android.settings.deviceinfo.Status");

        String title = slot == 1 ? getString(R.string.tab_b_title) :
            getString(R.string.tab_a_title);

        int iconId = slot == 0 ? R.drawable.ic_tab_icc_lock_a : R.drawable.ic_tab_icc_lock_b;

        mTabHost.addTab(mTabHost.newTabSpec("SIM " + slot)
                .setIndicator(title,
                        getResources().getDrawable(iconId)) .setContent(intent));
    }


    /**
     * Sets the current tab based on the intent's request type
     *
     * @param intent Intent that contains information about which tab should be selected
     */
    private void setCurrentTab(int tab) {
        mTabHost.setCurrentTab(tab);
    }

    @Override
    public void onNewIntent(Intent newIntent) {
        restoreLastTab();
    }

    /** {@inheritDoc} */
    public void onTabChanged(String tabId) {
        // Because we're using Activities as our tab children, we trigger
        // onWindowFocusChanged() to let them know when they're active.  This may
        // seem to duplicate the purpose of onResume(), but it's needed because
        // onResume() can't reliably check if a keyguard is active.
        Activity activity = getLocalActivityManager().getActivity(tabId);
        if (activity != null) {
            activity.onWindowFocusChanged(true);
        }

        // Remember this tab index. This function is also called, if the tab is set automatically
        // in which case the setter (setCurrentTab) has to set this to its old value afterwards
        mLastManuallySelectedTab = mTabHost.getCurrentTab();
        if (DBG) log("current tab: " + mLastManuallySelectedTab);
    }

    private static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }

}
