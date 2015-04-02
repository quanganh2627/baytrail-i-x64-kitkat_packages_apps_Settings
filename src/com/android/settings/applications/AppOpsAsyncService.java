package com.android.settings.applications;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;


public abstract class AppOpsAsyncService extends Service {
    private volatile Looper mSrvLooper;
    private volatile IntentHandler mSrvHandler;


    private final class IntentHandler extends Handler {
        public IntentHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            onHandleIntent((Intent)msg.obj);
        }
    }

    public AppOpsAsyncService() {
        super();      
    }


    @Override
    public void onCreate() {

        super.onCreate();
        HandlerThread thread = new HandlerThread("AppOpsAsyncService");
        thread.start();

        mSrvLooper = thread.getLooper();
        mSrvHandler = new IntentHandler(mSrvLooper);
    }
	
	@Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public void onStart(Intent i, int startId) {
        Message msg = mSrvHandler.obtainMessage();
        msg.arg1 = startId;
        msg.obj = i;
        mSrvHandler.sendMessage(msg);
    }

    

    
    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        onStart(i, startId);
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        mSrvLooper.quit();
    }




    protected abstract void onHandleIntent(Intent i);
}
