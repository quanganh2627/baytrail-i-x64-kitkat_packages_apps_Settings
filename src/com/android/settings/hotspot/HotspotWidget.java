package com.android.settings.hotspot;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;
import com.android.settings.R;
import com.android.settings.WirelessSettings;

public class HotspotWidget extends AppWidgetProvider {
    static final String TAG = "HotspotWidget";

    static final ComponentName THIS_APPWIDGET =
        new ComponentName("com.android.settings", "com.android.settings.hotspot.HotspotWidget");

    // If the widget gets more buttons we will define it here
    private static final int BUTTON_HOTSPOT = 0;

    // This widget keeps track of two sets of states:
    // "3-state": STATE_DISABLED, STATE_ENABLED, STATE_INTERMEDIATE
    // "5-state": STATE_DISABLED, STATE_ENABLED, STATE_TURNING_ON, STATE_TURNING_OFF, STATE_UNKNOWN
    private static final int STATE_DISABLED = 0;
    private static final int STATE_ENABLED = 1;
    private static final int STATE_TURNING_ON = 2;
    private static final int STATE_TURNING_OFF = 3;
    private static final int STATE_UNKNOWN = 4;
    private static final int STATE_INTERMEDIATE = 5;

    private static final StateTracker sHotspotState = new HotspotStateTracker();

    /**
     * The state machine for a setting's toggling, tracking reality
     * versus the user's intent.
     *
     * This is necessary because reality moves relatively slowly
     * (turning on &amp; off radio drivers), compared to user's
     * expectations.
     */
    private abstract static class StateTracker {
        // Is the state in the process of changing?
        private boolean mInTransition = false;
        private Boolean mActualState = null;  // initially not set
        private Boolean mIntendedState = null;  // initially not set

        // Did a toggle request arrive while a state update was
        // already in-flight?  If so, the mIntendedState needs to be
        // requested when the other one is done, unless we happened to
        // arrive at that state already.
        private boolean mDeferredStateChangeRequestNeeded = false;

        /**
         * User pressed a button to change the state.  Something
         * should immediately appear to the user afterwards, even if
         * we effectively do nothing.  Their press must be heard.
         */
        public final void toggleState(Context context) {
            int currentState = getTriState(context);
            boolean newState = false;
            switch (currentState) {
                case STATE_ENABLED:
                    newState = false;
                    break;
                case STATE_DISABLED:
                    newState = true;
                    break;
                case STATE_INTERMEDIATE:
                    if (mIntendedState != null) {
                        newState = !mIntendedState;
                    }
                    break;
            }
            mIntendedState = newState;
            if (mInTransition) {
                // We don't send off a transition request if we're
                // already transitioning.  Makes our state tracking
                // easier, and is probably nicer on lower levels.
                // (even though they should be able to take it...)
                mDeferredStateChangeRequestNeeded = true;
            } else {
                mInTransition = true;
                requestStateChange(context, newState);
            }
        }

        /**
         * Update internal state from a broadcast state change.
         */
        public abstract void onActualStateChange(Context context, Intent intent);

        /**
         * Sets the value that we're now in.  To be called from onActualStateChange.
         *
         * @param newState one of STATE_DISABLED, STATE_ENABLED, STATE_TURNING_ON,
         *                 STATE_TURNING_OFF, STATE_UNKNOWN
         */
        protected final void setCurrentState(Context context, int newState) {
            final boolean wasInTransition = mInTransition;
            switch (newState) {
                case STATE_DISABLED:
                    mInTransition = false;
                    mActualState = false;
                    break;
                case STATE_ENABLED:
                    mInTransition = false;
                    mActualState = true;
                    break;
                case STATE_TURNING_ON:
                    mInTransition = true;
                    mActualState = false;
                    break;
                case STATE_TURNING_OFF:
                    mInTransition = true;
                    mActualState = true;
                    break;
            }

            if (wasInTransition && !mInTransition) {
                if (mDeferredStateChangeRequestNeeded) {
                    Log.v(TAG, "processing deferred state change");
                    if (mActualState != null && mIntendedState != null &&
                        mIntendedState.equals(mActualState)) {
                        Log.v(TAG, "... but intended state matches, so no changes.");
                    } else if (mIntendedState != null) {
                        mInTransition = true;
                        requestStateChange(context, mIntendedState);
                    }
                    mDeferredStateChangeRequestNeeded = false;
                }
            }
        }


        /**
         * If we're in a transition mode, this returns true if we're
         * transitioning towards being enabled.
         */
        public final boolean isTurningOn() {
            return mIntendedState != null && mIntendedState;
        }

        /**
         * Returns simplified 3-state value from underlying 5-state.
         *
         * @param context
         * @return STATE_ENABLED, STATE_DISABLED, or STATE_INTERMEDIATE
         */
        public final int getTriState(Context context) {
            if (mInTransition) {
                // If we know we just got a toggle request recently
                // (which set mInTransition), don't even ask the
                // underlying interface for its state.  We know we're
                // changing.  This avoids blocking the UI thread
                // during UI refresh post-toggle if the underlying
                // service state accessor has coarse locking on its
                // state (to be fixed separately).
                return STATE_INTERMEDIATE;
            }
            switch (getActualState(context)) {
                case STATE_DISABLED:
                    return STATE_DISABLED;
                case STATE_ENABLED:
                    return STATE_ENABLED;
                default:
                    return STATE_INTERMEDIATE;
            }
        }

        /**
         * Gets underlying actual state.
         *
         * @param context
         * @return STATE_ENABLED, STATE_DISABLED, STATE_ENABLING, STATE_DISABLING,
         *         or or STATE_UNKNOWN.
         */
        public abstract int getActualState(Context context);

        /**
         * Actually make the desired change to the underlying radio
         * API.
         */
        protected abstract void requestStateChange(Context context, boolean desiredState);
    }

    /**
     * Subclass of StateTracker to get/set hotspot state.
     */
    private static final class HotspotStateTracker extends StateTracker {
        @Override
        public int getActualState(Context context) {
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                return wifiApStateToFiveState(wifiManager.getWifiApState());
            }
            return STATE_UNKNOWN;
        }

        @Override
        protected void requestStateChange(final Context context, final boolean desiredState) {
            final WifiManager wifiManager =
                    (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager == null) {
                Log.d(TAG, "No wifiManager.");
                return;
            }

            // Actually request the wifi change and persistent
            // settings write off the UI thread, as it can take a
            // user-noticeable amount of time, especially if there's
            // disk contention.
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... args) {
                    /**
                     * Disable Wifi if enabling hotspot
                     */
                    int wifiState = wifiManager.getWifiState();
                    final ContentResolver cr = context.getContentResolver();
                    if (desiredState && ((wifiState == WifiManager.WIFI_STATE_ENABLING) ||
                                         (wifiState == WifiManager.WIFI_STATE_ENABLED))) {
                        wifiManager.setWifiEnabled(false);
                        Settings.Secure.putInt(cr, Settings.Global.WIFI_SAVED_STATE, 1);
                    }

                    wifiManager.setWifiApEnabled(null,desiredState);

                    // If needed, restore Wifi on hotspot disable
                    if (!desiredState) {
                        int wifiSavedState = 0;
                        try {
                            wifiSavedState = Settings.Secure.getInt(cr, Settings.Global.WIFI_SAVED_STATE);
                        } catch (Settings.SettingNotFoundException e) {
                            ;
                        }
                        if (wifiSavedState == 1) {
                            wifiManager.setWifiEnabled(true);
                            Settings.Secure.putInt(cr, Settings.Global.WIFI_SAVED_STATE, 0);
                        }
                    }

                    return null;
                }
            }.execute();

        }

        @Override
        public void onActualStateChange(Context context, Intent intent) {
            if (!WifiManager.WIFI_AP_STATE_CHANGED_ACTION.equals(intent.getAction())) {
                return;
            }
            int wifiApState = intent.getIntExtra(WifiManager.EXTRA_WIFI_AP_STATE, -1);
            setCurrentState(context, wifiApStateToFiveState(wifiApState));
        }

        /**
         * Converts WifiManager's state values into our
         * hotspot state values.
         */
        private static int wifiApStateToFiveState(int wifiApState) {
            switch (wifiApState) {
                case WifiManager.WIFI_AP_STATE_DISABLED:
                    return STATE_DISABLED;
                case WifiManager.WIFI_AP_STATE_ENABLED:
                    return STATE_ENABLED;
                case WifiManager.WIFI_AP_STATE_DISABLING:
                    return STATE_TURNING_OFF;
                case WifiManager.WIFI_AP_STATE_ENABLING:
                    return STATE_TURNING_ON;
                default:
                    return STATE_UNKNOWN;
            }
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager,
            int[] appWidgetIds) {
        // Update each requested appWidgetId
        RemoteViews view = buildUpdate(context);

        for (int i = 0; i < appWidgetIds.length; i++) {
            appWidgetManager.updateAppWidget(appWidgetIds[i], view);
        }
    }

    @Override
    public void onEnabled(Context context) {
        Class clazz = com.android.settings.hotspot.HotspotWidget.class;
        PackageManager pm = context.getPackageManager();
        pm.setComponentEnabledSetting(
                new ComponentName(context.getPackageName(), clazz.getName()),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
    }


    @Override
    public void onDisabled(Context context) {
        Class clazz = com.android.settings.hotspot.HotspotWidget.class;
        PackageManager pm = context.getPackageManager();
        pm.setComponentEnabledSetting(
                new ComponentName(context.getPackageName(), clazz.getName()),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    /**
     * Load image for given widget and build {@link RemoteViews} for it.
     */
    static RemoteViews buildUpdate(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_hotspot);
        views.setOnClickPendingIntent(R.id.btn_hotspot, getLaunchPendingIntent(context, BUTTON_HOTSPOT));
        updateButtons(views, context);
        return views;
    }

    /**
     * Updates the widget when something changes, or when a button is pushed.
     *
     * @param context
     */
    public static void updateWidget(Context context) {
        RemoteViews views = buildUpdate(context);
        // Update specific list of appWidgetIds if given, otherwise default to all
        final AppWidgetManager gm = AppWidgetManager.getInstance(context);
        gm.updateAppWidget(THIS_APPWIDGET, views);
    }

    /**
     * Updates the buttons based on the underlying states of wifi, etc.
     *
     * @param views   The RemoteViews to update.
     * @param context
     */
    private static void updateButtons(RemoteViews views, Context context) {
        switch (sHotspotState.getTriState(context)) {
            case STATE_DISABLED:
                views.setImageViewResource(R.id.img_hotspot,
                                           R.drawable.ic_appwidget_settings_hotspot_off_holo);
                views.setImageViewResource(R.id.ind_hotspot,
                                           R.drawable.appwidget_settings_ind_off_c_holo);
                break;
            case STATE_ENABLED:
                views.setImageViewResource(R.id.img_hotspot,
                                           R.drawable.ic_appwidget_settings_hotspot_on_holo);
                views.setImageViewResource(R.id.ind_hotspot,
                                           R.drawable.appwidget_settings_ind_on_c_holo);
                break;
            case STATE_INTERMEDIATE:
                // In the transitional state, the bottom green bar
                // shows the tri-state (on, off, transitioning), but
                // the top dark-gray-or-bright-white logo shows the
                // user's intent.  This is much easier to see in
                // sunlight.
                if (sHotspotState.isTurningOn()) {
                    views.setImageViewResource(R.id.img_hotspot,
                                               R.drawable.ic_appwidget_settings_hotspot_off_holo);
                    views.setImageViewResource(R.id.ind_hotspot,
                                               R.drawable.appwidget_settings_ind_mid_c_holo);
                } else {
                    views.setImageViewResource(R.id.img_hotspot,
                                               R.drawable.ic_appwidget_settings_hotspot_on_holo);
                    views.setImageViewResource(R.id.ind_hotspot,
                                               R.drawable.appwidget_settings_ind_off_c_holo);
                }
                break;
        }
    }

    /**
     * Creates PendingIntent to notify the widget of a button click.
     *
     * @param context
     * @return
     */
    private static PendingIntent getLaunchPendingIntent(Context context, int buttonId) {
        Intent launchIntent = new Intent();
        launchIntent.setClass(context, HotspotWidget.class);
        launchIntent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        launchIntent.setData(Uri.parse("custom:" + buttonId));
        PendingIntent pi = PendingIntent.getBroadcast(context, 0 /* no requestCode */,
                launchIntent, 0 /* no flags */);
        return pi;
    }

    /**
     * Receives and processes a button pressed intent or state change.
     *
     * @param context
     * @param intent  Indicates the pressed button.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (WifiManager.WIFI_AP_STATE_CHANGED_ACTION.equals(action)) {
            sHotspotState.onActualStateChange(context, intent);
        } else if (intent.hasCategory(Intent.CATEGORY_ALTERNATIVE)) {
            Uri data = intent.getData();
            int buttonId = Integer.parseInt(data.getSchemeSpecificPart());
            if (buttonId == BUTTON_HOTSPOT) {
                boolean isWifiAllowed = WirelessSettings.
                                        isRadioAllowed(context, Settings.System.RADIO_WIFI);
                boolean isCellDataAllowed = WirelessSettings.
                                        isRadioAllowed(context, Settings.System.RADIO_CELL);
                if (isWifiAllowed && isCellDataAllowed)
                    sHotspotState.toggleState(context);
                else
                    Toast.makeText(context, R.string.wifi_in_airplane_mode, Toast.LENGTH_SHORT).show();
            }
        } else {
            // Don't fall-through to updating the widget.  The Intent
            // was something unrelated or that our super class took
            // care of.
            return;
        }

        // State changes fall through
        updateWidget(context);
    }
}
