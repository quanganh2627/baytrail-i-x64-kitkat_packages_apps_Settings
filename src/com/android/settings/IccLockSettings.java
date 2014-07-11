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

package com.android.settings;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.DialogInterface.OnKeyListener;
import android.content.res.Resources;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyIntents2;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.TelephonyProperties2;

import android.os.SystemProperties;

/**
 * Implements the preference screen to enable/disable ICC lock and
 * also the dialogs to change the ICC PIN. In the former case, enabling/disabling
 * the ICC lock will prompt the user for the current PIN.
 * In the Change PIN case, it prompts the user for old pin, new pin and new pin
 * again before attempting to change it. Calls the SimCard interface to execute
 * these operations.
 *
 */
public class IccLockSettings extends PreferenceActivity
        implements EditPinPreference.OnPinEnteredListener {
    private static final String TAG = "IccLockSettings";
    private static final boolean DBG = true;

    private static final int OFF_MODE = 0;
    // State when enabling/disabling ICC lock
    private static final int ICC_LOCK_MODE = 1;
    // State when entering the old pin
    private static final int ICC_OLD_MODE = 2;
    // State when entering the new pin - first time
    private static final int ICC_NEW_MODE = 3;
    // State when entering the new pin - second time
    private static final int ICC_REENTER_MODE = 4;

    // Keys in xml file
    private static final String PIN_DIALOG = "sim_pin";
    private static final String PIN_TOGGLE = "sim_toggle";
    private static final String SIM_UNLOCK_TOGGLE = "sim_unlock_toggle";
    // Keys in icicle
    private static final String DIALOG_STATE = "dialogState";
    private static final String DIALOG_PIN = "dialogPin";
    private static final String DIALOG_ERROR = "dialogError";
    private static final String ENABLE_TO_STATE = "enableState";

    // Save and restore inputted PIN code when configuration changed
    // (ex. portrait<-->landscape) during change PIN code
    private static final String OLD_PINCODE = "oldPinCode";
    private static final String NEW_PINCODE = "newPinCode";

    private static final int MIN_PIN_LENGTH = 4;
    private static final int MAX_PIN_LENGTH = 8;
    // Which dialog to show next when popped up
    private int mDialogState = OFF_MODE;

    private String mPin;
    private String mOldPin;
    private String mNewPin;
    private String mError;
    // Are we trying to enable or disable ICC lock?
    private boolean mToState;

    private Phone mPhone;
    private int   mSlotId;

    private int UNLOCK_PIN = 1;
    private int UNLOCK_PUK = 2;

    private EditPinPreference mPinDialog;
    private CheckBoxPreference mPinToggle;
    private Preference mUnlockToggle;
    private PreferenceScreen screen;

    private Resources mRes;
    private TelephonyManager mTelephonyManager;
    private TelephonyManager mTelephonyManager2;

    // For async handler to identify request type
    private static final int MSG_ENABLE_ICC_PIN_COMPLETE = 100;
    private static final int MSG_CHANGE_ICC_PIN_COMPLETE = 101;
    private static final int MSG_SIM_STATE_CHANGED = 102;
    private static final int MSG_AIRPLANE_MODE_CHANGED = 103;

    // For replies from IccCard interface
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            switch (msg.what) {
                case MSG_ENABLE_ICC_PIN_COMPLETE:
                    iccLockChanged(ar.exception == null, msg.arg1);
                    break;
                case MSG_CHANGE_ICC_PIN_COMPLETE:
                    iccPinChanged(ar.exception == null, msg.arg1);
                    break;
                case MSG_AIRPLANE_MODE_CHANGED:
                case MSG_SIM_STATE_CHANGED:
                    updatePreferences();
                    break;
            }

            return;
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action) ||
                    TelephonyIntents2.ACTION_SIM_STATE_CHANGED.equals(action)) {
                int slotId = intent.getIntExtra(TelephonyConstants.EXTRA_SLOT, 0);
                if (mSlotId == slotId) {
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_SIM_STATE_CHANGED));
                }
            } else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                mHandler.sendMessage(mHandler.obtainMessage(MSG_AIRPLANE_MODE_CHANGED));
            }
        }
    };

    // For top-level settings screen to query
    static boolean isIccLockEnabled() {
        return PhoneFactory.getDefaultPhone().getIccCard().getIccLockEnabled();
    }

    static String getSummary(Context context) {
        Resources res = context.getResources();
        String summary = isIccLockEnabled()
                ? res.getString(R.string.sim_lock_on)
                : res.getString(R.string.sim_lock_off);
        return summary;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Utils.isMonkeyRunning()) {
            finish();
            return;
        }
        mSlotId = getIntent().getIntExtra(TelephonyConstants.EXTRA_SLOT, 0);

        mTelephonyManager = (TelephonyManager) (getApplicationContext().getSystemService(
                                                            Context.TELEPHONY_SERVICE));
        if (TelephonyConstants.IS_DSDS) {
            mTelephonyManager2 = TelephonyManager.get2ndTm();
        }

        addPreferencesFromResource(R.xml.sim_lock_settings);

        mPinDialog = (EditPinPreference) findPreference(PIN_DIALOG);
        mPinToggle = (CheckBoxPreference) findPreference(PIN_TOGGLE);
        mUnlockToggle = (Preference) findPreference(SIM_UNLOCK_TOGGLE);

        if (!TelephonyConstants.IS_DSDS) {
            getPreferenceScreen().removePreference(mUnlockToggle);
            mUnlockToggle = null;
        }

        if (savedInstanceState != null && savedInstanceState.containsKey(DIALOG_STATE)) {
            mDialogState = savedInstanceState.getInt(DIALOG_STATE);
            mPin = savedInstanceState.getString(DIALOG_PIN);
            mError = savedInstanceState.getString(DIALOG_ERROR);
            mToState = savedInstanceState.getBoolean(ENABLE_TO_STATE);

            // Restore inputted PIN code
            switch (mDialogState) {
                case ICC_NEW_MODE:
                    mOldPin = savedInstanceState.getString(OLD_PINCODE);
                    break;

                case ICC_REENTER_MODE:
                    mOldPin = savedInstanceState.getString(OLD_PINCODE);
                    mNewPin = savedInstanceState.getString(NEW_PINCODE);
                    break;

                case ICC_LOCK_MODE:
                case ICC_OLD_MODE:
                default:
                    break;
            }
        }

        mPinDialog.setOnPinEnteredListener(this);

        // Don't need any changes to be remembered
        getPreferenceScreen().setPersistent(false);

        mPhone = Utils.isPrimaryId(this, mSlotId) ?
                     PhoneFactory.getDefaultPhone() : PhoneFactory.get2ndPhone();
        mRes = getResources();
        updatePreferences();
    }

    private int getCallState() {
        return Utils.isPrimaryId(getApplicationContext(), mSlotId) ?
                         mTelephonyManager.getCallState() : mTelephonyManager2.getCallState();
    }

    private int getSimState() {
        return Utils.isPrimaryId(getApplicationContext(), mSlotId) ?
                         mTelephonyManager.getSimState() : mTelephonyManager2.getSimState();
    }

    private void updateUnLockToggle() {
        switch (getSimState()) {
            case TelephonyManager.SIM_STATE_ABSENT:
                final boolean isSimOff = TelephonyManager.getDefault().isSimOff(mSlotId);
                if (isSimOff) {
                    mUnlockToggle.setTitle(R.string.sim_off);
                    mUnlockToggle.setEnabled(true);
                    mUnlockToggle.setSelectable(true);
                } else {
                    mUnlockToggle.setTitle(R.string.sim_absent);
                    mUnlockToggle.setEnabled(false);
                    mUnlockToggle.setSelectable(false);
                }
                mUnlockToggle.setSummary(null);
            break;
            case TelephonyManager.SIM_STATE_PUK_REQUIRED:
                mUnlockToggle.setTitle(R.string.sim_unlock_puk_toggle);
                mUnlockToggle.setSummary(R.string.sim_puk_lock_on);
                mUnlockToggle.setEnabled(true);
                mUnlockToggle.setSelectable(true);
            break;
            case TelephonyManager.SIM_STATE_PIN_REQUIRED:
                mUnlockToggle.setTitle(R.string.sim_unlock_pin_toggle);
                mUnlockToggle.setSummary(R.string.sim_pin_lock_on);
                mUnlockToggle.setEnabled(true);
                mUnlockToggle.setSelectable(true);
            break;
            default:
                mUnlockToggle.setTitle(R.string.sim_unlocked);
                mUnlockToggle.setSummary(null);
                mUnlockToggle.setEnabled(false);
                mUnlockToggle.setSelectable(false);
            break;
        }
    }

    private void updatePreferences() {

        if (getCallState() != TelephonyManager.CALL_STATE_IDLE) {
            mPinDialog.cancelPinDialog();
            getPreferenceScreen().setEnabled(false);
            if (TelephonyConstants.IS_DSDS) {
                updateUnLockToggle();
            }
            return;
        }

        boolean isAirplaneModeOn = Settings.Global.getInt(getContentResolver(),
                                                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;

        if (isAirplaneModeOn) {
            mPinDialog.cancelPinDialog();
            if (!TelephonyConstants.IS_DSDS) {
                getPreferenceScreen().setEnabled(false);
                return;
            }
        }

        getPreferenceScreen().setEnabled(true);
        if (DBG) Log.d(TAG, "sim " + mSlotId + " state " + getSimState());
        switch (getSimState()) {
            case TelephonyManager.SIM_STATE_ABSENT:
            case TelephonyManager.SIM_STATE_PUK_REQUIRED:
            case TelephonyManager.SIM_STATE_PIN_REQUIRED:
                mPinToggle.setEnabled(false);
                mPinDialog.setEnabled(false);
                mPinToggle.setSelectable(false);
                mPinDialog.setSelectable(false);
            break;

            default:
                mPinToggle.setChecked(mPhone.getIccCard().getIccLockEnabled());
                mPinToggle.setEnabled(true);
                mPinDialog.setEnabled(true);
                mPinToggle.setSelectable(true);
                mPinDialog.setSelectable(mPhone.getIccCard().getIccLockEnabled());
            break;
        }
        if (TelephonyConstants.IS_DSDS) {
            updateUnLockToggle();
        }
    }

    private boolean isPukLocked() {
        int simState = TelephonyManager.getTmBySlot(mSlotId).getSimState();
        return (TelephonyManager.SIM_STATE_PUK_REQUIRED == simState);
    }

    private boolean isPinLocked() {
        int simState = TelephonyManager.getTmBySlot(mSlotId).getSimState();
        return (TelephonyManager.SIM_STATE_PIN_REQUIRED == simState);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (TelephonyConstants.IS_DSDS) {
            updatePreferences();
        }
        // ACTION_SIM_STATE_CHANGED is sticky, so we'll receive current state after this call,
        // which will call updatePreferences().
        final IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        if (TelephonyConstants.IS_DSDS) {
             filter.addAction(TelephonyIntents2.ACTION_SIM_STATE_CHANGED);
        }
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        registerReceiver(mReceiver, filter);

        if (mDialogState != OFF_MODE) {
            showPinDialog();
        } else {
            // Prep for standard click on "Change PIN"
            resetDialogState();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mReceiver);
    }

    protected void onStop() {
        super.onStop();
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        if (TelephonyConstants.IS_DSDS) {
            mTelephonyManager2.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
    }

    private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onServiceStateChanged(ServiceState state) {
            if (Utils.isPrimaryId(getApplicationContext(), mSlotId) ?
                               mTelephonyManager.getSimState() == TelephonyManager.SIM_STATE_ABSENT
                               : mTelephonyManager2.getSimState() == TelephonyManager.SIM_STATE_ABSENT)
                // shall not finish()
                mPinToggle.setEnabled(false);
                mPinToggle.setSelectable(false);
            if (Utils.isPrimaryId(getApplicationContext(), mSlotId) ?
                    mTelephonyManager.getSimState() == TelephonyManager.SIM_STATE_PUK_REQUIRED
                    : mTelephonyManager2.getSimState() == TelephonyManager.SIM_STATE_PUK_REQUIRED) {
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_SIM_STATE_CHANGED));
            }
        }
    };

    @Override
    protected void onSaveInstanceState(Bundle out) {
        // Need to store this state for slider open/close
        // There is one case where the dialog is popped up by the preference
        // framework. In that case, let the preference framework store the
        // dialog state. In other cases, where this activity manually launches
        // the dialog, store the state of the dialog.
        if (mPinDialog.isDialogOpen()) {
            out.putInt(DIALOG_STATE, mDialogState);
            out.putString(DIALOG_PIN, mPinDialog.getEditText().getText().toString());
            out.putString(DIALOG_ERROR, mError);
            out.putBoolean(ENABLE_TO_STATE, mToState);

            // Save inputted PIN code
            switch (mDialogState) {
                case ICC_NEW_MODE:
                    out.putString(OLD_PINCODE, mOldPin);
                    break;

                case ICC_REENTER_MODE:
                    out.putString(OLD_PINCODE, mOldPin);
                    out.putString(NEW_PINCODE, mNewPin);
                    break;

                case ICC_LOCK_MODE:
                case ICC_OLD_MODE:
                default:
                    break;
            }
        } else {
            super.onSaveInstanceState(out);
        }
    }

    private void showPinDialog() {
        if (mDialogState == OFF_MODE) {
            return;
        }
        setDialogValues();

        mPinDialog.showPinDialog();
    }

    private void setDialogValues() {
        mPinDialog.setText(mPin);
        String message = "";
        switch (mDialogState) {
            case ICC_LOCK_MODE:
                String prop = Utils.isPrimaryId(this, mSlotId) ?
                    TelephonyProperties.PROPERTY_SIM_PIN_RETRY_LEFT :
                    TelephonyProperties2.PROPERTY_SIM_PIN_RETRY_LEFT;
                int numOfRetry = SystemProperties.getInt(prop,0);
                if (numOfRetry > 0) {
                    message = mRes.getString(R.string.sim_enter_pin_times, numOfRetry);
                } else {
                    message = mRes.getString(R.string.sim_enter_pin);
                }
                mPinDialog.setDialogTitle(mToState
                        ? mRes.getString(R.string.sim_enable_sim_lock)
                        : mRes.getString(R.string.sim_disable_sim_lock));
                break;
            case ICC_OLD_MODE:
                message = mRes.getString(R.string.sim_enter_old);
                mPinDialog.setDialogTitle(mRes.getString(R.string.sim_change_pin));
                break;
            case ICC_NEW_MODE:
                message = mRes.getString(R.string.sim_enter_new);
                mPinDialog.setDialogTitle(mRes.getString(R.string.sim_change_pin));
                break;
            case ICC_REENTER_MODE:
                message = mRes.getString(R.string.sim_reenter_new);
                mPinDialog.setDialogTitle(mRes.getString(R.string.sim_change_pin));
                break;
        }
        if (mError != null) {
            message = mError + "\n" + message;
            mError = null;
        }
        mPinDialog.setDialogMessage(message);
    }

    public void onPinEntered(EditPinPreference preference, boolean positiveResult) {
        if (!positiveResult) {
            resetDialogState();
            return;
        }

        mPin = preference.getText();
        if (!reasonablePin(mPin)) {
            // inject error message and display dialog again
            mError = mRes.getString(R.string.sim_bad_pin);
            showPinDialog();
            return;
        }
        switch (mDialogState) {
            case ICC_LOCK_MODE:
                tryChangeIccLockState();
                break;
            case ICC_OLD_MODE:
                mOldPin = mPin;
                mDialogState = ICC_NEW_MODE;
                mError = null;
                mPin = null;
                showPinDialog();
                break;
            case ICC_NEW_MODE:
                mNewPin = mPin;
                mDialogState = ICC_REENTER_MODE;
                mPin = null;
                showPinDialog();
                break;
            case ICC_REENTER_MODE:
                if (!mPin.equals(mNewPin)) {
                    mError = mRes.getString(R.string.sim_pins_dont_match);
                    mDialogState = ICC_NEW_MODE;
                    mPin = null;
                    showPinDialog();
                } else {
                    mError = null;
                    tryChangePin();
                }
                break;
        }
    }

    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mPinToggle) {
            // Get the new, preferred state
            mToState = mPinToggle.isChecked();
            // Flip it back and pop up pin dialog
            mPinToggle.setChecked(!mToState);
            mPinDialog.setSelectable(!mToState);
            mDialogState = ICC_LOCK_MODE;
            showPinDialog();
        } else if (preference == mPinDialog) {
            mDialogState = ICC_OLD_MODE;
            return false;
        } else if (preference == mUnlockToggle) {
            if (isPukLocked()) {
                showUnlockDialog(TelephonyManager.SIM_STATE_PUK_REQUIRED);
            } else if (isPinLocked()) {
                showUnlockDialog(TelephonyManager.SIM_STATE_PIN_REQUIRED);
            }
        }
        return true;
    }

    private void tryChangeIccLockState() {
        // Try to change icc lock. If it succeeds, toggle the lock state and
        // reset dialog state. Else inject error message and show dialog again.
        Message callback = Message.obtain(mHandler, MSG_ENABLE_ICC_PIN_COMPLETE);
        mPhone.getIccCard().setIccLockEnabled(mToState, mPin, callback);
        // Disable the setting till the response is received.
        mPinToggle.setEnabled(false);
        mPinToggle.setSelectable(false);
    }

    private void iccLockChanged(boolean success, int attemptsRemaining) {
        if (success) {
            mPinToggle.setChecked(mToState);
            mPinDialog.setSelectable(mToState);
        } else {
            Toast.makeText(this, getPinPasswordErrorMessage(attemptsRemaining), Toast.LENGTH_LONG)
                    .show();
        }
        updatePreferences();
        resetDialogState();
    }

    private String getRetryTip(int resId) {
        String ret = mRes.getString(resId);
        String prop = Utils.isPrimaryId(this, mSlotId) ?
            TelephonyProperties.PROPERTY_SIM_PIN_RETRY_LEFT :
            TelephonyProperties2.PROPERTY_SIM_PIN_RETRY_LEFT;
        int numOfRetry = SystemProperties.getInt(prop, 0);
        if (numOfRetry > 0) {
            ret += "\n" + mRes.getString(R.string.sim_enter_pin_times, numOfRetry);
        }
        return ret;
    }

    private void iccPinChanged(boolean success, int attemptsRemaining) {
        if (!success) {
            Toast.makeText(this, getPinPasswordErrorMessage(attemptsRemaining),
                    Toast.LENGTH_LONG)
                    .show();
        } else {
            Toast.makeText(this, mRes.getString(R.string.sim_change_succeeded),
                    Toast.LENGTH_SHORT)
                    .show();

        }
        resetDialogState();
    }

    private void tryChangePin() {
        Message callback = Message.obtain(mHandler, MSG_CHANGE_ICC_PIN_COMPLETE);
        mPhone.getIccCard().changeIccLockPassword(mOldPin,
                mNewPin, callback);
    }

    private String getPinPasswordErrorMessage(int attemptsRemaining) {
        String displayMessage;

        if (attemptsRemaining == 0) {
            displayMessage = mRes.getString(R.string.wrong_pin_code_pukked);
        } else if (attemptsRemaining > 0) {
            displayMessage = mRes
                    .getQuantityString(R.plurals.wrong_pin_code, attemptsRemaining,
                            attemptsRemaining);
        } else {
            displayMessage = mRes.getString(R.string.pin_failed);
        }
        if (DBG) Log.d(TAG, "getPinPasswordErrorMessage:"
                + " attemptsRemaining=" + attemptsRemaining + " displayMessage=" + displayMessage);
        return displayMessage;
    }

    private boolean reasonablePin(String pin) {
        if (pin == null || pin.length() < MIN_PIN_LENGTH || pin.length() > MAX_PIN_LENGTH) {
            return false;
        } else {
            return true;
        }
    }

    private void resetDialogState() {
        mError = null;
        mDialogState = ICC_OLD_MODE; // Default for when Change PIN is clicked
        mPin = "";
        setDialogValues();
        mDialogState = OFF_MODE;
    }

    private void showUnlockDialog(int type) {
        if (DBG) Log.d(TAG, "Enter showUnlockDialog type=" + type + ", slot=" + mSlotId);
        Intent intent = new Intent(TelephonyConstants.INTENT_SHOW_PIN_PUK);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent. FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        intent.putExtra("type", type);
        intent.putExtra(TelephonyConstants.EXTRA_SLOT, mSlotId);
        startActivity(intent);
    }
}
