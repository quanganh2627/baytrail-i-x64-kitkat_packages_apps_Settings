package com.android.settings.applications;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;
import android.provider.Settings;

import android.app.NotificationManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;

import android.app.AppOpsManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import com.android.internal.app.IAppOpsService;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import com.android.settings.R;

import com.android.internal.app.IAccessReqCallback;

import android.preference.PreferenceActivity;
import com.android.settings.applications.AppOpsState.AppOpEntry;
import com.android.settings.applications.AppOpsState.OpsTemplate;
import com.android.settings.applications.AppOpsState.AppEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import android.util.SparseArray;
import java.util.HashMap;
import android.os.Process;

public class AppOpsMonService extends AppOpsAsyncService implements OnClickListener, OnDismissListener {
   
    private static final String TAG = "AppOpsMonService";
    private static final boolean DBG = false;

    private static final int DELAY_TIME = 1000;
    private static final int MSG_RESET = 101;
    private static final int MSG_SHOW_TOAST = MSG_RESET + 1;
    private static final int MSG_SHOW_CONF_DLG = MSG_RESET + 2;
    private static final int MSG_COUNT_DOWN = MSG_RESET + 3;
    private static final int COUNT_DOWN_TICK = 20;
    private static final int EXTRA_TIMER = 5000;
    public static final int MAX_WATI_TIME = 20000;  //in mill-seconds
    private static final int NOTIFY_ID = 1200;
    private static final int ALLOWD_FLAG = 1;
    private static final int DENIED_FLAG = 1<<1;
    private static final int CHECK_FLAG = 1<<2;
    private static final int REMEMBER_FLAG = 1<<3;

    public static final String START_SERVICE_ACTION = "com.intel.security.ACTION_START_ACC_MON";
    public static final String STOP_SERVICE_ACTION = "com.intel.security.ACTION_STOP_ACC_MON";
    public static final String ACC_MON_STATE = "access_monitor_state";
    public static final String APP_OPS_MON_LAUNCH_STATE = "app_ops_mon_launch_state";

	
    public static final String PACKAGE_NAME = "exta_package_name";
    public static final String OP_ID = "extra_op_id";
    public static final String PERMISSION_FLAG = "extra_permission_flag";
    public static final String PERM_CONTROL_DATA_UPDATE = "com.intel.security.action.DATA_UPDATE";
    //for block the thread to wait user confirm
    private Object mUserConfirmLock = new Object();
    
    private IAppOpsService  mAppOpsService;
	
    private volatile int mUserConfirmResult;
	
    private static final SparseArray<HashMap<String, Integer>> mMostRecentActivePerm
            = new SparseArray<HashMap<String, Integer>>();

    String mCurPkgName;
    int mCurCode;
	
    private CheckBox mCheckBox;
    private TextView mTickCountDown;
    private AlertDialog mAlertDlg;
	
	
    private RadioGroup mRadioGroup; 
    private RadioButton mRadio_high,mRadio_mid, mRadio_low;
    
    private static final int NOTIFY_FOREGROUND_ID = 1201;
	public static int mLaunchState;
 
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_SHOW_TOAST) {
                handleDenyToastMsg(msg.getData());
            } else if (msg.what == MSG_SHOW_CONF_DLG) {
                handleConfirmDlgMsg(mCurPkgName, mCurCode);
            } else if (msg.what == MSG_COUNT_DOWN) {
                handleCountDownMsg(msg);
            }
        }
    };
    
    public AppOpsMonService() {
        super();  
		if(DBG) Log.d(TAG, "AppOpsMonService");
    }


    protected void handleCountDownMsg(Message msg) {
        int tick = msg.arg1 - 1;
        if(DBG) Log.d(TAG,"tick is = " + tick);
        if (tick > 0) {
            countDown(tick);
        } else {
            if(DBG) Log.d(TAG,"time out and deny the permission");
            // time out dismiss dialog and return false
            showDenyToast(getApplicationContext(), mCurPkgName, mCurCode); 
                                       
            mUserConfirmResult = DENIED_FLAG;
            if (mAlertDlg != null) {
                mAlertDlg.dismiss();
            }   
        }
    }

    protected void handleDenyToastMsg(Bundle data) {
        if (data != null) {
            if(DBG) Log.d(TAG,"handleDenyToastMsg");
            String pkgName = data.getString(PACKAGE_NAME);
            int op = data.getInt(OP_ID);
            showDenyToast(getApplicationContext(), pkgName, op);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if(DBG) Log.d(TAG,"onCreate()");
        initService();
    }
    
    /**
     * Must call before access any further AppOps apis
     */
    private void initService() {
        if(DBG) Log.d(TAG,"initService()" + " PID : " + Process.myPid());
        
        if (mAppOpsService == null) {
            if(DBG) Log.d(TAG, "mAppOpsService is null");
            mAppOpsService = IAppOpsService.Stub.asInterface(ServiceManager.getService(Context.APP_OPS_SERVICE));
        }
        setupBootStatus();
        setServiceInforeground();  
    }
    
  

    // for increase the adj of process so set service in foreground
    private void setServiceInforeground() {
		if(DBG) Log.d(TAG, "setServiceInforeground");
        Notification notification = new Notification();
        String titleStr = getString(R.string.foreground_notification_title);
        String summaryStr = getString(R.string.foreground_notification_summary);
        notification.icon = R.drawable.ic_settings_security;
        notification.tickerText = titleStr;
        notification.when = 0;
        notification.flags = Notification.FLAG_NO_CLEAR;
        Intent intent = new Intent();
        intent.setClassName("com.android.settings","com.android.settings.Settings");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                       | Intent.FLAG_ACTIVITY_CLEAR_TASK
                       | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT,
				                  "com.android.settings.applications.AppOpsSummary");
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                intent, 0);
        notification.setLatestEventInfo(this, titleStr, summaryStr, pendingIntent);
        startForeground(NOTIFY_FOREGROUND_ID, notification);
    }



    private synchronized int accessCheck(String pkgName, int code, int mode, int level) {
        if(DBG) Log.d(TAG,"accessCheck() pkgName : " + pkgName + " code = " + code);
        synchronized (mUserConfirmLock) {
            try {  
                    mCurCode = code;
                    mCurPkgName = pkgName;
                    mUserConfirmResult = CHECK_FLAG;
                    showConfirmDlg();
                    
                    mUserConfirmLock.wait(MAX_WATI_TIME + EXTRA_TIMER);
                    if(DBG) Log.d(TAG,"release the lock");

            } catch (InterruptedException e) {
                if(DBG)Log.d(TAG,"error");
            }
           if(DBG) Log.d(TAG,"mUserConfirmResult " + mUserConfirmResult);

          return mUserConfirmResult;
        }
    }
	
     class AccessReqCallback extends IAccessReqCallback.Stub {
         @Override 
	 public int onAccessReqCb(String pkgName, int code, int mode,int level) { 
            if(DBG)Log.d(TAG,"onAccessReqCb pkg = " + pkgName + " " + 
                        AppOpsManager.opToName(code) + " " + " mode " +
                        mode);
            int result = accessCheck(pkgName, code, mode, level);  
            return result;
        }
    }

    private void registerAppOps() {        
        try {
            if(DBG) Log.i(TAG, "register listerner " + " PID : " + Process.myPid());
            mAppOpsService.registerAccessReqCallback(new AccessReqCallback());
        } catch (RemoteException e) {
            e.printStackTrace();
            return;
        }        
    }
	
    protected void stopAppOpsMonService() {
        if(DBG) Log.d(TAG,"stopAppOpsMonService");
        try {
            mAppOpsService.unRegisterAccessReqCallback();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        stopSelf();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            String action = intent.getAction();
            if(DBG) Log.d(TAG,"onHandleIntent() action = " + action);
            if (action == null) {
                handleAppOpsLaunch();
            } else if (START_SERVICE_ACTION.equals(action)) {
               handleAppLaunch(intent);
            } else if (STOP_SERVICE_ACTION.equals(action)) {
                stopAppOpsMonService();
            }
        } else {
            //Handle if service is killed
            if(DBG) Log.d(TAG,"intent = null servie is killed and relaunched by system");
            registerAppOps();
        }
    }

    private void handleAppLaunch(Intent intent) {
        if(DBG) Log.d(TAG,"handleAppLaunch()");
        // In case the notification may still exist, need to removed first 
        cancelNotification(this);
        registerAppOps();  
        sendLoadFinishBroadcast();
    }

    private void handleAppOpsLaunch() {
        if(DBG) Log.d(TAG,"handleAppOpsLaunch()");
        if (isAccMonOn(this)) {
            
            showHintNotify(this);
            
            registerAppOps();
            
        } else {
            stopSelf();
        }
    }
    
    /**
     * Show a system confirm dialog from service 
	 * @pkgName the package name
     * @param code the request operation
     */
    private void handleConfirmDlgMsg(String pkgName, int code) {
        if(DBG) Log.d(TAG,"Show confirm dialog pkgName : " + pkgName);
        Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.notify_dialog_title);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setPositiveButton(R.string.accept_perm, this);
        builder.setNegativeButton(R.string.deny_perm, this);
        builder.setCancelable(false);
        
        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.notification_dialog, null);
        builder.setView(view);
        
        TextView messageText = (TextView)view.findViewById(R.id.message);
        mTickCountDown = (TextView) view.findViewById(R.id.count_tick);

        mCheckBox = (CheckBox)view.findViewById(R.id.notify_checkbox);
        
        String label = getApplicationName(this, pkgName);

        String msg = getString(R.string.notify_dialog_msg_body,label,
                 getMessageBody(this, code));
        messageText.setText(msg);
        
        mAlertDlg = builder.create();
        mAlertDlg.setOnDismissListener(this);
        mAlertDlg.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);

        setStatusBarEnableStatus(false);
        
        mAlertDlg.show();
        countDown(COUNT_DOWN_TICK);
    }
	
    private void countDown(int tick) {
        setCountText(tick);
        Message msg = Message.obtain();
        msg.what = MSG_COUNT_DOWN;
        msg.arg1 = tick;
        mHandler.sendMessageDelayed(msg, DELAY_TIME);
    }
    
    private void setCountText(int tick) {
        String msg = getString(R.string.time_count_down_hint,String.valueOf(tick));
        mTickCountDown.setText(msg);
    }
	
	
	/*
     * The toast have to show in a main thread so show the toast via a handler
     */
    private void showIgnorToast(String pkgName, int code) {
        Message msg = Message.obtain();
        Bundle data = new Bundle();
        data.putCharSequence(PACKAGE_NAME, pkgName);
        data.putInt(OP_ID, code);
        msg.setData(data);
        msg.what = MSG_SHOW_TOAST;
        mHandler.sendMessage(msg);
    }

    /*
     * Because the system dialog need to show in main thread of service so show the dialog via a handler
     */
    private void showConfirmDlg() {
        Message msg = Message.obtain();

        msg.what = MSG_SHOW_CONF_DLG;
        mHandler.sendMessage(msg);
    }
    
     
    private void setStatusBarEnableStatus(boolean enabled) {
        if(DBG) Log.i(TAG, "setStatusBarEnableStatus(" + enabled + ")");
        StatusBarManager statusBarManager;
        statusBarManager = (StatusBarManager)getSystemService(Context.STATUS_BAR_SERVICE);
        if (statusBarManager != null) {
            if (enabled) {
                statusBarManager.disable(StatusBarManager.DISABLE_NONE);
            } else {
                statusBarManager.disable(StatusBarManager.DISABLE_EXPAND |
                                         StatusBarManager.DISABLE_RECENT |
                                         StatusBarManager.DISABLE_HOME);
            }
        } else {
            Log.e(TAG, "Fail to get status bar instance");
        }
    }
    
    

    private void sendLoadFinishBroadcast() {
        Intent intent = new Intent();
        intent.setAction(PERM_CONTROL_DATA_UPDATE);
        sendBroadcast(intent);
    }
    

    /**
     * true for grant and false for deny
     * @param status enable or not the permission
     */
    public void releaseLock() {
        synchronized (mUserConfirmLock) {
            mUserConfirmLock.notifyAll();
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if(DBG) Log.d(TAG,"onDestroy");
        stopForeground(true);
 
        setStatusBarEnableStatus(true);
        if (mAppOpsService != null) {
            try {
                if(DBG) Log.d(TAG,"Service destroy and disable permission control");
            } catch (SecurityException e) {
                Log.e(TAG,"catch log as AccMonitor has been detached");
            }
        }
    }
    
    @Override
    public void onClick(DialogInterface dialog, int which) {

        int mode = DENIED_FLAG;
        if (which == DialogInterface.BUTTON_POSITIVE) {
            mode = ALLOWD_FLAG;
        } else{
            mode = DENIED_FLAG;
        }    

        if (mCheckBox.isChecked()) {
            mUserConfirmResult =  REMEMBER_FLAG | mode;  //bit 4 for checkbox
        } else {
            mUserConfirmResult = mode;
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        if(DBG) Log.d(TAG,"Dialog dimissed");
        setStatusBarEnableStatus(true);
        mHandler.removeMessages(MSG_COUNT_DOWN);
        releaseLock();
    }
	
    public static void showDenyToast(Context context, String pkgName, int op) {
        String label = getApplicationName(context,pkgName);
        if(DBG) Log.d(TAG,"showDenyToast() pkgName = " + pkgName + " label = " + label);
        if (label != null) {
            String msg = context.getString(R.string.toast_deny_msg_body,label,getMessageBody(context, op));
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
        }
    }
	
    public static void showHintNotify(Context context) {
        int state = Settings.System.getInt(context.getContentResolver(), 
                ACC_MON_STATE,
                context.getResources().getInteger(R.integer.default_enable_state));

        if(DBG) Log.d(TAG,"showHintNotify state = " + state);
        if (state == context.getResources().getInteger(R.integer.default_enable_state)) {
            NotificationManager notificationManager = 
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            String titleStr = context.getString(R.string.notification_control_state_title);
            String summaryStr = context.getString(R.string.notification_control_state_summary);
            Notification notification = new Notification();
            notification.icon = R.drawable.ic_settings_security;
            notification.when = 0;
            notification.flags = Notification.FLAG_AUTO_CANCEL;
            notification.tickerText = titleStr;
            notification.defaults = 0; // please be quiet
            notification.sound = null;
            notification.vibrate = null;
            notification.priority = Notification.PRIORITY_DEFAULT;
            Intent intent = new Intent();
			
            intent.setClassName("com.android.settings",
			  "com.android.settings.Settings");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
				  | Intent.FLAG_ACTIVITY_CLEAR_TASK
				  | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT,
				  "com.android.settings.applications.AppOpsSummary");
         
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0,intent, 0);
            notification.setLatestEventInfo(context, titleStr, summaryStr,pendingIntent);
            notificationManager.notify(NOTIFY_ID, notification);
        }
    }
	
	public void cancelNotification(Context context) {
        NotificationManager notifyManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notifyManager.cancel(NOTIFY_ID);
    }
	

	
	public static boolean isAccMonOn(Context context) {
        return Settings.System.getInt(context.getContentResolver(), 
                ACC_MON_STATE,
                context.getResources().getInteger(R.integer.default_enable_state)) > 0;
    }
	
	public static void setAccMonitorOnOff(Context ctx, boolean state) {
		 Settings.System.putInt(ctx.getContentResolver(), ACC_MON_STATE, state ? 1 : 0);
	}
	

	
	public static String getApplicationName(Context context, String pkgName) {
		String appName = null;
        if (pkgName == null) {
            return null;
        }
        
        try {
          if(pkgName.equals("com.android.keyguard"))  //jw
             appName = pkgName;
            else {
               PackageManager pkgManager = context.getPackageManager();
               ApplicationInfo info = pkgManager.getApplicationInfo(pkgName, 0);
               appName = pkgManager.getApplicationLabel(info).toString();
           }
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        return appName;
    }

	
    public static String getMessageBody(Context context, int op) {
        String msgArray[] = context.getResources().getStringArray(R.array.app_ops_summaries);

        String msg = msgArray[op];
        if(DBG) Log.d(TAG, "msg : " + msg);
        return msg;
    }
	
    public void setupBootStatus() {
       if(DBG) Log.d(TAG, "initUtil,set up Operation name to operation map");
       final Context context = getApplicationContext();
       mLaunchState = Settings.System.getInt(context.getContentResolver(), 
                      APP_OPS_MON_LAUNCH_STATE,
                      context.getResources().getInteger(R.integer.default_launch_state));
       if(DBG) Log.d(TAG,"mLaunchState = " + mLaunchState);
       if(mLaunchState == 0) {  //it means the first lunch after reset to factory
              mLaunchState = 1;
              if(DBG) Log.d(TAG,"mLaunchState = " + mLaunchState);
              Settings.System.putInt(context.getContentResolver(), APP_OPS_MON_LAUNCH_STATE, mLaunchState);
       }
    }
	   
}
