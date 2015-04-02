/**
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.android.settings.applications;

import android.app.Activity;
import android.app.AppOpsManager;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.Utils;

import java.util.List;

import android.widget.ListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import android.content.pm.ApplicationInfo;

public class AppOpsDetails extends Fragment implements OnItemClickListener{
    static final String TAG = "AppOpsDetails";

    public static final String ARG_PACKAGE_NAME = "package";

    private AppOpsState mState;
    private PackageManager mPm;
    private AppOpsManager mAppOps;
    private PackageInfo mPackageInfo;
    private LayoutInflater mInflater;
    private View mRootView;
    private TextView mAppVersion;
    private LinearLayout mOperationsSection;

    AlertDialog mAlertDlg;
    private int mSavedSelectedIndex;
    private ListView mOpEntryListView;
    MyOpsAdapter mOpsAdapter;

    private OpsAsyncLoader mAsyncTask;

    // Utility method to set application label and icon.
    private void setAppLabelAndIcon(PackageInfo pkgInfo) {
        final View appSnippet = mRootView.findViewById(R.id.app_snippet);
        appSnippet.setPaddingRelative(0, appSnippet.getPaddingTop(), 0, appSnippet.getPaddingBottom());

        ImageView icon = (ImageView) appSnippet.findViewById(R.id.app_icon);
        icon.setImageDrawable(mPm.getApplicationIcon(pkgInfo.applicationInfo));
        // Set application name.
        TextView label = (TextView) appSnippet.findViewById(R.id.app_name);
        label.setText(mPm.getApplicationLabel(pkgInfo.applicationInfo));
        // Version number of application
        mAppVersion = (TextView) appSnippet.findViewById(R.id.app_size);

        if (pkgInfo.versionName != null) {
            mAppVersion.setVisibility(View.VISIBLE);
            mAppVersion.setText(getActivity().getString(R.string.version_text,
                    String.valueOf(pkgInfo.versionName)));
        } else {
            mAppVersion.setVisibility(View.INVISIBLE);
        }
    }

    private String retrieveAppEntry() {
        final Bundle args = getArguments();
        String packageName = (args != null) ? args.getString(ARG_PACKAGE_NAME) : null;
        if (packageName == null) {
            Intent intent = (args == null) ?
                    getActivity().getIntent() : (Intent) args.getParcelable("intent");
            if (intent != null) {
                packageName = intent.getData().getSchemeSpecificPart();
            }
        }
        try {
            mPackageInfo = mPm.getPackageInfo(packageName,
                    PackageManager.GET_DISABLED_COMPONENTS |
                    PackageManager.GET_UNINSTALLED_PACKAGES);
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Exception when retrieving package:" + packageName, e);
            mPackageInfo = null;
        }

        return packageName;
    }

    private boolean refreshUi() {
        if (mPackageInfo == null) {
            return false;
        }

        setAppLabelAndIcon(mPackageInfo);

        load();
/*
        Resources res = getActivity().getResources();

        mOperationsSection.removeAllViews();
        String lastPermGroup = "";
        for (AppOpsState.OpsTemplate tpl : AppOpsState.ALL_TEMPLATES) {
            List<AppOpsState.AppOpEntry> entries = mState.buildState(tpl,
                    mPackageInfo.applicationInfo.uid, mPackageInfo.packageName);
            for (final AppOpsState.AppOpEntry entry : entries) {
                final AppOpsManager.OpEntry firstOp = entry.getOpEntry(0);
                final View view = mInflater.inflate(R.layout.app_ops_details_item,
                        mOperationsSection, false);
                mOperationsSection.addView(view);
                String perm = AppOpsManager.opToPermission(firstOp.getOp());
                if (perm != null) {
                    try {
                        PermissionInfo pi = mPm.getPermissionInfo(perm, 0);
                        if (pi.group != null && !lastPermGroup.equals(pi.group)) {
                            lastPermGroup = pi.group;
                            PermissionGroupInfo pgi = mPm.getPermissionGroupInfo(pi.group, 0);
                            if (pgi.icon != 0) {
                                ((ImageView)view.findViewById(R.id.op_icon)).setImageDrawable(
                                        pgi.loadIcon(mPm));
                            }
                        }
                    } catch (NameNotFoundException e) {
                    }
                }
                ((TextView)view.findViewById(R.id.op_name)).setText(
                        entry.getSwitchText(mState));
                ((TextView)view.findViewById(R.id.op_time)).setText(
                        entry.getTimeText(res, true));
                Switch sw = (Switch)view.findViewById(R.id.switchWidget);
                final int switchOp = AppOpsManager.opToSwitch(firstOp.getOp());
                sw.setChecked(mAppOps.checkOp(switchOp, entry.getPackageOps().getUid(),
                        entry.getPackageOps().getPackageName()) == AppOpsManager.MODE_ALLOWED);
                sw.setOnCheckedChangeListener(new Switch.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        mAppOps.setMode(switchOp, entry.getPackageOps().getUid(),
                                entry.getPackageOps().getPackageName(), isChecked
                                ? AppOpsManager.MODE_ALLOWED : AppOpsManager.MODE_IGNORED);
                    }
                });
            }
        }
   */
        return true;
    }
    
        @Override
    public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {

        onCreateDialog(parent, pos).show();
    }
    
    
    private Dialog onCreateDialog(AdapterView<?> parent, int pos) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            ListView l = (ListView) parent;
            String[] dialogList;
            
            final AppOpsState.AppOpEntry entry = (AppOpsState.AppOpEntry) l.getAdapter().getItem(pos);

            
            final AppOpsManager.OpEntry firstOp = entry.getOpEntry(0);
            
            int mode = entry.getOpEntry(0).getMode();
            String permName = AppOpsManager.opToPermission(firstOp.getOp());
            final int switchOp = AppOpsManager.opToSwitch(firstOp.getOp());
            
            final int app_type = checkAppType(entry.getPackageOps().getPackageName());
          
            
            int index = 0;
            if(mode == AppOpsManager.MODE_CHECK)  //check
                index = 1;
            if((mode == 1 || mode == 2) && ( 2 == app_type))  //error or ignore
                index = 1;
            if((mode == 1 || mode == 2) && ( 3 == app_type))  //error or ignore
                index = 2;
            if(mode == 0)  //allowed
                index = 0;
            
            String[] title = getResources().getStringArray(R.array.app_ops_labels);
                
            mSavedSelectedIndex = index;
            builder.setTitle(title[firstOp.getOp()]);
            
            
            if(2 == app_type)
                dialogList = getResources().getStringArray(R.array.op_sys_status_entry);
            else if(3 == app_type)
                dialogList = getResources().getStringArray(R.array.op_app_status_entry);
            else
                dialogList = getResources().getStringArray(R.array.op_sys_status_entry);
                

            builder.setSingleChoiceItems(dialogList, index,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            Log.d(TAG, "selected which " + which);
                            
                            if(mSavedSelectedIndex == which) {
                                dialog.dismiss();
                                mOpsAdapter.notifyDataSetChanged();
                                return;
                            }
                            
                            mSavedSelectedIndex = which;
                            int chose_mode = AppOpsManager.MODE_ALLOWED;
                            if(which == 0)
                                chose_mode = AppOpsManager.MODE_ALLOWED;
                            else if((which == 1) && (app_type == 3))
                                chose_mode = AppOpsManager.MODE_CHECK;
                            else if((which == 1) && (app_type == 2))
                                chose_mode = AppOpsManager.MODE_IGNORED;
                            else
                                chose_mode = AppOpsManager.MODE_IGNORED;
                            Log.d(TAG, "setMode : " + " pkgName : " + entry.getPackageOps().getPackageName() + " switchOp = " + switchOp);
                            Log.d(TAG, "num of entry : " + entry.getNumOpEntry());
                            for(int i = 0; i < entry.getNumOpEntry(); i++) {
                               
                               mAppOps.setMode(entry.getOpEntry(i).getOp(), entry.getPackageOps().getUid(),
                                entry.getPackageOps().getPackageName(), chose_mode);
                            }    
                            entry.getOpEntry(0).setMode(chose_mode);
                            mOpsAdapter.notifyDataSetChanged();

                            dialog.dismiss();

                        }
                    });
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                public void onDismiss(DialogInterface dialog) {
                    Log.d(TAG, "dialog dismiss, remove it");

                    dialog.dismiss();

                }
            });
            mAlertDlg = builder.create();
            return mAlertDlg;
    }
    
    private int checkAppType(String pkgName) {
        try {
            PackageInfo pInfo = getActivity().getPackageManager().getPackageInfo(pkgName, 0);            
            if(isSystemApp(pInfo) || isSystemUpdateApp(pInfo)) {
                return 2; //SYSTEM_APP;
            } else {
                return 3; //USER_APP;
            }
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        return 0;//UNKNOWN_APP;
    }
    
    private boolean isSystemApp(PackageInfo pInfo) {
        return ((pInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
    }
    
    private boolean isSystemUpdateApp(PackageInfo pInfo) {
        return ((pInfo.applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0);
    }
    
    private boolean isUserApp(PackageInfo pInfo) {
        return (!isSystemApp(pInfo) && !isSystemUpdateApp(pInfo));
    }

    private void setIntentAndFinish(boolean finish, boolean appChanged) {
        Intent intent = new Intent();
        intent.putExtra(ManageApplications.APP_CHG, appChanged);
        SettingsActivity sa = (SettingsActivity)getActivity();
        sa.finishPreferencePanel(this, Activity.RESULT_OK, intent);
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mState = new AppOpsState(getActivity());
        mPm = getActivity().getPackageManager();
        mInflater = (LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mAppOps = (AppOpsManager)getActivity().getSystemService(Context.APP_OPS_SERVICE);

        retrieveAppEntry();

        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.app_ops_details, container, false);
        Utils.prepareCustomPreferencesList(container, view, view, false);

        mRootView = view;
        mOperationsSection = (LinearLayout)view.findViewById(R.id.operations_section);
        ListView lv = (ListView) view.findViewById(android.R.id.list);
        lv.setOnItemClickListener(this);
        lv.setSaveEnabled(true);
        lv.setItemsCanFocus(true);
        lv.setTextFilterEnabled(true);
        mOpEntryListView = lv;

        mOpsAdapter = new MyOpsAdapter();
        mOpEntryListView.setAdapter(mOpsAdapter);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!refreshUi()) {
            setIntentAndFinish(true, true);
        }
    }

    // View Holder used when displaying views
    class MyOpsAdapter extends BaseAdapter {
        private List<AppOpsState.AppOpEntry> mOpEntryList = new ArrayList<AppOpsState.AppOpEntry>();

        public MyOpsAdapter() {

        }

        public void setDataAndNotify(List<AppOpsState.AppOpEntry> opEntryList) {
            mOpEntryList = opEntryList;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            if (mOpEntryList != null) {
                return mOpEntryList.size();
            }
            return 0;
        }

        @Override
        public Object getItem(int position) {
            return mOpEntryList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            AppViewHolder holder;
            Resources res = getActivity().getResources();
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.app_ops_details_item, null);

                holder = new AppViewHolder();                
                holder.mOpIcon = (ImageView) convertView.findViewById(R.id.op_icon);
                holder.mOpName = (TextView) convertView.findViewById(R.id.op_name);
                holder.mOpTime = (TextView) convertView.findViewById(R.id.op_time);
                convertView.setTag(holder);
            } else {

                holder = (AppViewHolder) convertView.getTag();
            }

            // Bind the data efficiently with the holder
            String lastPermGroup = "";
            AppOpsState.AppOpEntry entry = mOpEntryList.get(position);
            final AppOpsManager.OpEntry firstOp = entry.getOpEntry(0);
            String perm = AppOpsManager.opToPermission(firstOp.getOp());
            if (perm != null) {
                    try {
                        PermissionInfo pi = mPm.getPermissionInfo(perm, 0);
                        if (pi.group != null && !lastPermGroup.equals(pi.group)) {
                            lastPermGroup = pi.group;
                            PermissionGroupInfo pgi = mPm.getPermissionGroupInfo(pi.group, 0);
                            if (pgi.icon != 0) {
                                holder.mOpIcon.setImageDrawable(
                                        pgi.loadIcon(mPm));
                            }
                        }
                    } catch (NameNotFoundException e) {
                    }
            }
            
            holder.mOpName.setText(entry.getSwitchText(mState));
            holder.mOpTime.setText(entry.getTimeText(res, true));

            return convertView;
        }
    }
    static class AppViewHolder {        
        ImageView mOpIcon;
        TextView mOpName;
        TextView mOpTime;
    }
    
    private class OpsAsyncLoader extends
            AsyncTask<Void, Integer, List<AppOpsState.AppOpEntry>> {
        @Override
        protected List<AppOpsState.AppOpEntry> doInBackground(Void... params) {

            List<AppOpsState.AppOpEntry> opEntryList = new ArrayList<AppOpsState.AppOpEntry>();

            if (isCancelled()) {
                Log.d(TAG, "the Async Task is cancled");
                return null;
            }

        for (AppOpsState.OpsTemplate tpl : AppOpsState.ALL_TEMPLATES) {
            List<AppOpsState.AppOpEntry> entries = mState.buildState(tpl,
                    mPackageInfo.applicationInfo.uid, mPackageInfo.packageName);            
            
            for (final AppOpsState.AppOpEntry entry : entries) {        
                opEntryList.add(entry);
            }
        }
            Log.d(TAG, "load size = " + opEntryList.size());
            // sort list by the defined op list
            Collections.sort(opEntryList, DEFINED_OPENTRY_COMPARATOR);
            return opEntryList;
        }

        @Override
        protected void onPostExecute(List<AppOpsState.AppOpEntry> opEntryList) {
            Log.d(TAG, "onPostExecute......");
            // as the alert dialog is on the top of listview ,so refresh it firstly
            updateAlertDialog();
            Log.d(TAG, "onPostExecute size = " + opEntryList.size());
            mOpsAdapter.setDataAndNotify(opEntryList);
        }
    }
    
    private void updateAlertDialog() {
        if (mAlertDlg == null || !mAlertDlg.isShowing()) {
            Log.d(TAG, "exit mAlertDlg = " + mAlertDlg);
            return;
        }
        Log.d(TAG, "set alertDialog select mSavedSelectedIndex = " + mSavedSelectedIndex);
        ListView listview = mAlertDlg.getListView();
        listview.setItemChecked(mSavedSelectedIndex, true);
        listview.setSelection(mSavedSelectedIndex);
    }

    private void load() {
         mAsyncTask = (OpsAsyncLoader)new OpsAsyncLoader().execute();
    }
    
    // comparator for sort the app's perm list by the defined controlled perm list
    public static final Comparator<AppOpsState.AppOpEntry> DEFINED_OPENTRY_COMPARATOR = new Comparator<AppOpsState.AppOpEntry>() {
        private final Collator sCollator = Collator.getInstance();
        @Override
        public int compare(AppOpsState.AppOpEntry object1, AppOpsState.AppOpEntry object2) {
            if (object1.getSwitchOrder() != object2.getSwitchOrder()) {
                return object1.getSwitchOrder() < object2.getSwitchOrder() ? -1 : 1;
            }
            if (object1.isRunning() != object2.isRunning()) {
                // Currently running ops go first.
                return object1.isRunning() ? -1 : 1;
            }
            if (object1.getTime() != object2.getTime()) {
                // More recent times go first.
                return object1.getTime() > object2.getTime() ? -1 : 1;
            }
            return sCollator.compare(object1.getAppEntry().getLabel(),
                    object2.getAppEntry().getLabel());
        }
    };

}
