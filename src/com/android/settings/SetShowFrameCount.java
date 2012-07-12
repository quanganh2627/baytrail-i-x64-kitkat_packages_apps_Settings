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

package com.android.settings;

import android.app.Activity;
import android.app.backup.IBackupManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.CheckBox;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.WindowManager;
import android.view.Display;

public class SetShowFrameCount extends Activity {
    static final String TAG = "SetShowFrameCount";

    private TextView mXCoordinate, mYCoordinate;
    private Button mCancel, mOk;
    private static final String SHOW_SCREEN_FRAME_COUNT_KEY = "show_screen_frame_count";
    private CheckBox mShowScreenFrameCount;
    private int mDebugFrameCountFlag;
    private int mDebugFrameCountXCoordinate;
    private int mDebugFrameCountYCoordinate;
    private int mDebugFrameCountBit;
    private int mWidth;
    private int mHeight;

    public void onDestroy() {
        super.onDestroy();
    }

    private OnClickListener mClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
               case R.id.show_frame_count_cancel_button:
                  finish();
                  break;

               case R.id.show_frame_count_ok_button:
                  mDebugFrameCountXCoordinate = Integer.parseInt(mXCoordinate.getText().toString());
                  mDebugFrameCountYCoordinate = Integer.parseInt(mYCoordinate.getText().toString());
                  String str = "";
                  if (mDebugFrameCountXCoordinate < 0) {
                      mDebugFrameCountXCoordinate = 0;
                      str += "X Coordinate is not less than 0.\n";
                  } else if (mDebugFrameCountXCoordinate > mWidth) {
                      mDebugFrameCountXCoordinate = mWidth;
                      str += "X Coordinate is not more than " + mDebugFrameCountXCoordinate + ".\n";
                  }

                  if (mDebugFrameCountYCoordinate < 0) {
                      mDebugFrameCountYCoordinate = 0;
                      str += "Y Coordinate is not less than 0.\n";
                  } else if (mDebugFrameCountYCoordinate > mHeight) {
                      mDebugFrameCountYCoordinate = mHeight;
                      str += "Y Coordinate is not more than " + mDebugFrameCountYCoordinate + ".\n";
                  }

                  if (str != null && str.trim().length() > 0) {
                      Toast.makeText(SetShowFrameCount.this, str, Toast.LENGTH_LONG).show();
                      if (mXCoordinate != null)
                         mXCoordinate.setText(String.valueOf(mDebugFrameCountXCoordinate), TextView.BufferType.EDITABLE);
                      if (mYCoordinate != null)
                         mYCoordinate.setText(String.valueOf(mDebugFrameCountYCoordinate), TextView.BufferType.EDITABLE);
                  }
                  break;

            case R.id.show_screen_frame_count:
                  triggerFlingerFrameCount();
                  enableFramecountInc(mShowScreenFrameCount.isChecked() ? 1 : 0);
                  break;
           }
        }
    };

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setContentView(R.layout.set_show_frame_count);

        mXCoordinate = (TextView) findViewById(R.id.show_frame_count_x_coordinate_edit_text);
        mYCoordinate = (TextView) findViewById(R.id.show_frame_count_y_coordinate_edit_text);
        mCancel = (Button) findViewById(R.id.show_frame_count_cancel_button);
        mOk = (Button) findViewById(R.id.show_frame_count_ok_button);
        if (mCancel != null)
           mCancel.setOnClickListener(mClickListener);
        if (mOk != null)
           mOk.setOnClickListener(mClickListener);

        mShowScreenFrameCount = (CheckBox) findViewById(R.id.show_screen_frame_count);
        if (mShowScreenFrameCount != null)
           mShowScreenFrameCount.setOnClickListener(mClickListener);

        getFlingerFrameCountOptions();
        if (mXCoordinate != null)
           mXCoordinate.setText(String.valueOf(mDebugFrameCountXCoordinate), TextView.BufferType.EDITABLE);
        if (mYCoordinate != null)
           mYCoordinate.setText(String.valueOf(mDebugFrameCountYCoordinate), TextView.BufferType.EDITABLE);
        if (mDebugFrameCountFlag != 0) {
            if (mShowScreenFrameCount != null)
               mShowScreenFrameCount.setChecked(true);
        } else {
            if (mShowScreenFrameCount != null)
               mShowScreenFrameCount.setChecked(false);
        }

        WindowManager windowManager = getWindowManager();
        Display display = windowManager.getDefaultDisplay();
        if (display != null) {
           mWidth = display.getWidth();
           mHeight = display.getHeight();
        }
    }

    private void getFlingerFrameCountOptions() {
        // magic communication with surface flinger.
        try {
            IBinder flinger = ServiceManager.getService("SurfaceFlinger");
            if (flinger != null) {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                data.writeInterfaceToken("android.ui.ISurfaceComposer");
                flinger.transact(1012, data, reply, 0);
                mDebugFrameCountFlag = reply.readInt();
                mDebugFrameCountXCoordinate = reply.readInt();
                mDebugFrameCountYCoordinate = reply.readInt();
                data.recycle();
            }
        } catch (RemoteException ex) {
            Log.e(TAG, "RemoteException in getFlingerFrameCountOptions()", ex);
        }
    }

    private void triggerFlingerFrameCount() {
        try {
            IBinder flinger = ServiceManager.getService("SurfaceFlinger");
            if (flinger != null) {
                Parcel data = Parcel.obtain();
                data.writeInterfaceToken("android.ui.ISurfaceComposer");
                data.writeInt(mShowScreenFrameCount.isChecked() ? 1 : 0);
                data.writeInt(mDebugFrameCountXCoordinate);
                data.writeInt(mDebugFrameCountYCoordinate);
                flinger.transact(1011, data, null, 0);
                data.recycle();
            }
        } catch (RemoteException ex) {
            Log.e(TAG, "RemoteException in triggerFlingerFrameCount()", ex);
        }
    }

   private void enableFramecountInc(int value) {
        try {
            IBinder flinger = ServiceManager.getService("SurfaceFlinger");
            if (flinger != null) {
                Parcel data = Parcel.obtain();
                data.writeInterfaceToken("android.ui.ISurfaceComposer");
                data.writeInt(value);
                flinger.transact(1016, data, null, 0);
                data.recycle();
            }
        } catch (RemoteException ex) {
          Log.e(TAG, "RemoteException in enableFramecountInc()", ex);
        }
   }
}
