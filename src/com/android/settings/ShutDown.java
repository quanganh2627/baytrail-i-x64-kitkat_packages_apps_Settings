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
package com.android.settings;

import android.os.Bundle;
import android.view.KeyEvent;
import android.app.Instrumentation;
import android.app.Activity;

/**
 * Created by archermind on 9/10/14.
 **/
public class ShutDown extends Activity {

    @Override
    public void onCreate(Bundle ic) {
        super.onCreate(ic);
        new Thread(new Runnable() {
            public void run() {
                Instrumentation inst = new Instrumentation();
                inst.sendKeyDownUpSync(KeyEvent.KEYCODE_POWER);
            }
        }).start();
        super.finish();
    }
}
