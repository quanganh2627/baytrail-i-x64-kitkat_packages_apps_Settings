/*
 * Copyright (C) 2013 Intel Corporation, All Rights Reserved
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

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

final public class EnablerItemHelper {
    private static final String TAG = "EnablerItemHelper";
    private static final boolean DBG = false;

    private EnablerItemHelper () {}

    public static Object getItemEnablerIns(final String packageName,
            final String className, final Context context) {
        Object itemEnabler = null;
        final String fullClassName = packageName +"."+className;

        if (DBG) Log.d(TAG, "create package :" + packageName + " fullClassName :" + fullClassName);

        try {
            final Context ctx = getDynamicContext(context, packageName);
            Class cls = ctx.getClassLoader().loadClass(fullClassName);

            Constructor constructor = cls.getConstructor(Context.class);
            itemEnabler = constructor.newInstance(context);
        } catch (NameNotFoundException e) {
            Log.w(TAG, "Got expected NameNotFoundException ", e);
        } catch (IllegalAccessException iae) {
            Log.w(TAG, "Got expected PackageAccess complaint", iae);
        } catch (InstantiationError ie) {
            Log.w(TAG, "Got Got expected InstantationError", ie);
        } catch (Exception ex) {
            Log.w(TAG, "Got unexpected MaybeAbstract failure", ex);
        }
        return itemEnabler;
    }

    public static void callItemEnablerMemeberMethod(Object obj,
            String methodName, Class[] params, Object[]  objParams) {
        Log.d(TAG, "callItemEnablerMemeberMethod: " + methodName);
        try {
          Method method = obj.getClass().getMethod(methodName, params);
          method.invoke(obj, objParams);
        } catch (IllegalAccessException iae) {
            Log.w(TAG, "Got expected PackageAccess complaint", iae);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Got Got expected IllegalArgumentException", e);
        } catch (NoSuchMethodException ee) {
            Log.w(TAG, "Got Got expected NoSuchMethodException", ee);
        } catch (Exception ex) {
            Log.w(TAG, "Got unexpected MaybeAbstract failure", ex);
        }
    }

    private static Context getDynamicContext(final Context context, final String packageName)
            throws NameNotFoundException {
        if (DBG) Log.d(TAG, "getDynamicContext packageName :" + packageName);
        return context.createPackageContext(packageName,
                Context.CONTEXT_IGNORE_SECURITY | Context.CONTEXT_INCLUDE_CODE);
    }

}
