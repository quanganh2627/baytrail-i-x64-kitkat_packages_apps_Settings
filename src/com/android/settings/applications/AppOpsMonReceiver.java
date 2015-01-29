package com.android.settings.applications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.UserHandle;

import android.app.AppOpsManager;
import android.os.SystemProperties;
import android.util.Log;
import com.android.settings.R;
import android.provider.Settings;


public class AppOpsMonReceiver extends BroadcastReceiver {
    private static final String TAG = "AppOpsMonReceiver";
    private static final boolean DBG = true;
    private static final boolean PEM_CONTROL = SystemProperties.getBoolean("persist.intel.pem.control", false);

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null) {
            String action = intent.getAction();
            if(DBG) Log.d(TAG, "action = " + action);
            // when user switched and then launch service
            
            if(PEM_CONTROL){
                 if(action.equals("android.intent.action.BOOT_COMPLETED")) {
                    if(AppOpsMonService.isAccMonOn(context) || cooldBoot(context)) {
                        AppOpsMonService.setAccMonitorOnOff(context, true);
                        startAppOpsMonService(context);
                    }
                }
           }
        }
    }
	
   private boolean cooldBoot(Context context) {
       return  (Settings.System.getInt(context.getContentResolver(), 
                AppOpsMonService.APP_OPS_MON_LAUNCH_STATE,
                context.getResources().getInteger(R.integer.default_launch_state)) == 0);
	
   }
    
    private void startAppOpsMonService(Context context) {

            boolean isOn = AppOpsMonService.isAccMonOn(context); 
            if(DBG) Log.d(TAG,"startAppOpsMonService isOn = " + isOn);
            if (isOn) {
                Intent intent = new Intent();
                intent.setAction(AppOpsMonService.START_SERVICE_ACTION);
                intent.setClass(context, AppOpsMonService.class);
                context.startService(intent);
            } else {
                AppOpsMonService.showHintNotify(context);
            }
        
    }
    

}
