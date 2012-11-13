
package com.android.internalstoragemgmt;

import android.media.MediaFile;
import android.media.MediaFile.MediaFileType;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;
import android.os.Bundle;
import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.database.Cursor;
import android.hardware.usb.UsbManager;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.storage.StorageEventListener;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.MediaStore;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;

import com.android.internalstoragemgmt.R;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;

public class StorageManagement extends AlertActivity implements DialogInterface.OnClickListener,
        DialogInterface.OnCancelListener {

    final static String TAG = "StorageManagement";
    private Context mContext = null;
    private ProgressDialog mProgressDialog;
    private boolean mIsProgressShow = false;
    private boolean mIsCancelingShow = false;
    private boolean mBatteryLow = false;
    private StorageManager mStorageManager = null;
    private StorageVolume mSourceStorage = null;
    private StorageVolume mTargetStorage = null;
    private PowerManager.WakeLock mWakeLock;
    private workerHandler mWorkerHandler = null;
    private HandlerThread mWorkerThread = null;

    private View mDlgView;
    private CheckBox mDcimCheckBox;
    private CheckBox mMusicCheckBox;
    private TextView mFreeSizeText;

    private long mDcimSize = 0;
    private long mMusicSize = 0;
    private long mTotalSize = 0;
    private long mFreeSize = 0;
    private boolean mCanceled = false;
    private boolean mMtpEnabled = false;
    private boolean mScanning = false;
    private boolean mFileMoved = false;
    private int mProgress = 0;
    private final int PROGRESS_MAX = 100;
    private int mLenInStep = 0;
    private int mOneStepSize;

    static final int SUCCESS = 0;
    static final int ERR_PERM = -1;
    static final int ERR_SPACE = -2;
    static final int ERR_OTHER = -3;

    private static final int DLG_NOSD = 1;
    private static final int DLG_MUSIC_CHECK = 2;
    private static final int DLG_SPACE_WARNING = 3;
    private static final int DLG_CANCEL = 4;
    private static final int DLG_ERR_SPACE = 5;
    private static final int DLG_SUCCESS = 6;
    private static final int DLG_ERR_OTHER = 7;
    private static final int DLG_PROGRESS = 8;
    private static final int DLG_CANCELING = 9;
    private static final int DLG_BAT_LOW = 10;
    private static final int DLG_MTP_ENABLED = 11;
    private static final int DLG_NO_FILE_MOVED = 12;

    private static final int MSG_UI_UPDATE_MEASURE = 1;
    private static final int MSG_UI_UPDATE_STATUS = 2;
    private static final int MSG_UI_UPDATE_PROGRESS = 3;
    private static final int MSG_UI_RESET = 4;
    private static final int MSG_UI_RESULT = 5;
    private static final int MSG_UI_UPDATE_SCANNING = 6;
    private static final int MSG_UI_NOSD = 7;

    private static final int MSG_WORK_MEASURE = 11;
    private static final int MSG_WORK_MOVE = 12;

    private static final String FREE_SIZE = "free_size";
    private static final String DCIM_SIZE = "dcim_size";
    private static final String MUSIC_SIZE = "music_size";
    private static final String MTP_ENABLED = "mtp_enabled";
    private static final String BAT_LOW = "battery_low";
    private static final String PROGRESS = "progress";
    private static final String RESULT = "result";

    /*
     * Directory list for DCIM(Picture, movies) and Audio files Align with
     * StorageVolumePreferenceCategory.sMediaCategories
     */
    private final static ArrayList<String> mDcimDirs = new ArrayList<String>(
            Arrays.asList(Environment.DIRECTORY_DCIM,
                    Environment.DIRECTORY_MOVIES,
                    Environment.DIRECTORY_PICTURES));
    private final static ArrayList<String> mMusicDirs = new ArrayList<String>(
            Arrays.asList(Environment.DIRECTORY_MUSIC,
                    Environment.DIRECTORY_ALARMS,
                    Environment.DIRECTORY_NOTIFICATIONS,
                    Environment.DIRECTORY_RINGTONES,
                    Environment.DIRECTORY_PODCASTS));

    /*
     * MediaScanner interface, copied from
     * packages/apps/Gallery2/src/com/android/gallery3d/data/MtpContext.java
     */
    private ScannerClient mScannerClient;

    private static final class ScannerClient implements MediaScannerConnectionClient {
        ArrayList<String> mPaths = new ArrayList<String>();
        MediaScannerConnection mScannerConnection;
        Context mContext;
        Object mLock = new Object();

        public ScannerClient(Context context) {
            mContext = context;
            mScannerConnection = new MediaScannerConnection(context, this);
        }

        public void scanPath(String path) {
            try {
                synchronized (mLock) {
                    if (mScannerConnection.isConnected()) {
                        mScannerConnection.scanFile(path, null);
                    } else {
                        mPaths.add(path);
                        mScannerConnection.connect();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Media Scan failed for file " + path);
            }
        }

        public void removeDbEntry(String path) {
            Uri volumeUri = null;
            try {
                MediaFileType type = MediaFile.getFileType(path);
                if (type == null) {
                    // Non-Media file DB entry should be moved for MTP object syncing up.
                    volumeUri = MediaStore.Files.getContentUri("external");
                } else {
                    if (MediaFile.isAudioFileType(type.fileType))
                        volumeUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                    else if (MediaFile.isImageFileType(type.fileType))
                        volumeUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                    else if (MediaFile.isVideoFileType(type.fileType))
                        volumeUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                    else
                        volumeUri = MediaStore.Files.getContentUri("external");
                }

                ContentResolver resolver = mContext.getContentResolver();
                Cursor cursor = resolver.query(volumeUri, null, "_data=\"" + path + "\"", null,
                        null);
                if (cursor != null && cursor.getCount() == 1) {
                    cursor.moveToFirst();
                    String id = cursor.getString(cursor.getColumnIndex("_id"));
                    cursor.close();
                    Uri deleteUri = Uri.parse(volumeUri.toString() + "/" + id);
                    Log.i(TAG, "deleting URI:" + deleteUri.toString());
                    int ret = resolver.delete(deleteUri, null, null);
                    if (ret == 0)
                        Log.w(TAG, "DB entry delete failed");
                } else {
                    Log.e(TAG, "query error for file " + path);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error while deleting DB entry for file " + path, e);
            }
        }

        @Override
        public void onMediaScannerConnected() {
            synchronized (mLock) {
                if (!mPaths.isEmpty()) {
                    for (String path : mPaths) {
                        mScannerConnection.scanFile(path, null);
                    }
                    mPaths.clear();
                }
            }
        }

        @Override
        public void onScanCompleted(String path, Uri uri) {
            // Do nothing here
        }

        public void unbind() {
            if (mScannerConnection.isConnected())
                mScannerConnection.disconnect();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (mStorageManager == null) {
            mStorageManager = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
            mStorageManager.registerListener(mStorageListener);
        }

        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mDlgView = inflater.inflate(R.layout.file_moving_dlg, null);
        final AlertController.AlertParams ap = mAlertParams;
        ap.mTitle = getString(R.string.file_moving);
        ap.mView = mDlgView;
        ap.mPositiveButtonText = getString(R.string.button_move);
        ap.mPositiveButtonListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                spaceCheck();
            }
        };
        ap.mNegativeButtonText = getString(R.string.cancel);
        ap.mNegativeButtonListener = this;

        mContext = this;

        mDcimCheckBox = (CheckBox) ap.mView.findViewById(R.id.move_dcim);
        mMusicCheckBox = (CheckBox) ap.mView.findViewById(R.id.move_music);
        mFreeSizeText = (TextView) ap.mView.findViewById(R.id.sd_freespace);
        mScannerClient = new ScannerClient(mContext);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        mWorkerThread = new HandlerThread("handler_thread");
        mWorkerThread.start();
        Looper lo = mWorkerThread.getLooper();
        if (lo != null)
            mWorkerHandler = new workerHandler(lo);
        setupAlert();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setCheckBoxListener();
        mUpdateHandler.sendEmptyMessage(MSG_UI_RESET);
        mUpdateHandler.sendEmptyMessage(MSG_UI_UPDATE_SCANNING);
        if (mWorkerHandler != null)
            mWorkerHandler.sendEmptyMessageDelayed(MSG_WORK_MEASURE, 1000);

        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        intentFilter.addAction(UsbManager.ACTION_USB_STATE);
        registerReceiver(mStorageReceiver, intentFilter);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.i(TAG, "Configuration changed");
    }

    StorageEventListener mStorageListener = new StorageEventListener() {
        @Override
        public void onStorageStateChanged(String path, String oldState, String newState) {
            if ((mSourceStorage != null && mTargetStorage != null)
                    && (mTargetStorage.getPath().equals(path)
                    || mSourceStorage.getPath().equals(path))
                    && !newState.equals(Environment.MEDIA_MOUNTED)) {
                Log.i(TAG, "Received storage state changed notification that " +
                        path + " changed state from " + oldState +
                        " to " + newState);
                if (mIsProgressShow) {
                    mCanceled = true;
                    dismissDialog(DLG_PROGRESS);
                    showDialog(DLG_CANCELING);
                } else if (!isFinishing()) {
                    showDialog(DLG_NOSD);
                }
            }
        }
    };

    private final BroadcastReceiver mStorageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED)) {
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                        BatteryManager.BATTERY_STATUS_UNKNOWN);
                if (status == BatteryManager.BATTERY_STATUS_DISCHARGING) {

                    int level = intent.getIntExtra("level", 0);
                    int scale = intent.getIntExtra("scale", 100);
                    int batLevel = level * 100 / scale;
                    int batLow = getResources().getInteger(
                            com.android.internal.R.integer.config_lowBatteryWarningLevel);

                    if (batLevel <= batLow) {
                        Log.i(TAG, "Battery is low, stop moving");
                        mBatteryLow = true;
                    }
                } else if (mBatteryLow) {
                    mBatteryLow = false;
                    mUpdateHandler.sendEmptyMessage(MSG_UI_RESET);
                }

                Bundle bundle = new Bundle();
                bundle.putBoolean(BAT_LOW, mBatteryLow);
                final Message message = mUpdateHandler.obtainMessage(MSG_UI_UPDATE_STATUS);
                message.setData(bundle);
                mUpdateHandler.sendMessage(message);

            } else if (intent.getAction().equals(UsbManager.ACTION_USB_STATE)) {
                UsbManager usbMgr = (UsbManager) getSystemService(Context.USB_SERVICE);
                if (intent.getBooleanExtra(UsbManager.USB_CONNECTED, false)
                        && (usbMgr.isFunctionEnabled(UsbManager.USB_FUNCTION_MTP) ||
                        usbMgr.isFunctionEnabled(UsbManager.USB_FUNCTION_PTP))) {
                    mMtpEnabled = true;
                } else if (mMtpEnabled) {
                    mMtpEnabled = false;
                    if (mWorkerHandler != null)
                        mWorkerHandler.sendEmptyMessage(MSG_WORK_MEASURE);
                }
                Bundle bundle = new Bundle();
                bundle.putBoolean(MTP_ENABLED, mMtpEnabled);
                final Message message = mUpdateHandler.obtainMessage(MSG_UI_UPDATE_STATUS);
                message.setData(bundle);
                mUpdateHandler.sendMessage(message);
            }
        }
    };

    @Override
    protected void onPause() {
        if (isFinishing()) {
            if (mWorkerHandler != null)
                mWorkerHandler.removeCallbacksAndMessages(null);
            mUpdateHandler.removeCallbacksAndMessages(null);

            if (mStorageManager != null) {
                mStorageManager.unregisterListener(mStorageListener);
            }
        }

        // Cancel moving due to the potential of killed by Android");
        mCanceled = true;
        if (mIsProgressShow) {
            dismissDialog(DLG_PROGRESS);
            showDialog(DLG_CANCELING);
        }

        unregisterReceiver(mStorageReceiver);
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        mScannerClient.unbind();

        if (mWorkerHandler != null)
            mWorkerHandler.removeCallbacksAndMessages(null);
        mUpdateHandler.removeCallbacksAndMessages(null);

        if (mStorageManager != null) {
            mStorageManager.unregisterListener(mStorageListener);
        }

        super.onDestroy();
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DLG_NOSD:
                return new AlertDialog.Builder(mContext)
                        .setTitle(R.string.no_sd_title)
                        .setNeutralButton(R.string.dlg_ok, this)
                        .setOnCancelListener(this)
                        .setMessage(R.string.no_sd_summary)
                        .create();
            case DLG_MUSIC_CHECK:
                return new AlertDialog.Builder(mContext)
                        .setTitle(R.string.song_check_title)
                        .setMessage(R.string.song_check_summary)
                        .setIcon(android.R.drawable.stat_sys_warning)
                        .setNeutralButton(android.R.string.ok, null)
                        .create();
            case DLG_SPACE_WARNING:
                return new AlertDialog.Builder(mContext)
                        .setTitle(R.string.space_warning_title)
                        .setPositiveButton(R.string.dlg_ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                if (mWorkerHandler != null)
                                    mWorkerHandler.sendEmptyMessage(MSG_WORK_MOVE);
                            }
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .setMessage(R.string.space_warning_summary)
                        .create();
            case DLG_CANCEL:
                return new AlertDialog.Builder(mContext)
                        .setTitle(R.string.cancel_dlg_title)
                        .setPositiveButton(R.string.dlg_ok, null)
                        .setMessage(R.string.cancel_dlg_summary)
                        .create();
            case DLG_ERR_SPACE:
                return new AlertDialog.Builder(mContext)
                        .setTitle(R.string.space_warning_title)
                        .setPositiveButton(R.string.dlg_ok, null)
                        .setMessage(R.string.space_warning_result)
                        .create();
            case DLG_SUCCESS:
                return new AlertDialog.Builder(mContext)
                        .setTitle(R.string.success_dlg_title)
                        .setPositiveButton(R.string.dlg_ok, null)
                        .setMessage(R.string.success_dlg_summary)
                        .create();
            case DLG_ERR_OTHER:
                return new AlertDialog.Builder(mContext)
                        .setTitle(R.string.err_dlg_title)
                        .setPositiveButton(R.string.dlg_ok, null)
                        .setMessage(R.string.err_dlg_summary)
                        .create();
            case DLG_PROGRESS:
                mProgressDialog = new ProgressDialog(mContext);
                mProgressDialog.setIndeterminate(false);
                mProgressDialog.setCancelable(true);
                mProgressDialog.setCanceledOnTouchOutside(false);
                mProgressDialog.getWindow().setType(
                        WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                mProgressDialog.getWindow().setFlags(
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                                | WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                                | WindowManager.LayoutParams.FLAG_SECURE);
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mProgressDialog.setTitle(getString(R.string.progress_title));
                mProgressDialog.setMessage(getString(R.string.progress_summary));
                mProgressDialog.setMax(PROGRESS_MAX);
                mProgressDialog.setProgressNumberFormat(null);
                mProgressDialog.setButton(BUTTON_NEGATIVE, getString(R.string.cancel),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                mCanceled = true;
                                Log.i(TAG, "moving process is canceled by user");
                                showDialog(DLG_CANCELING);
                            }
                        });
                mProgressDialog.setOnCancelListener(new OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        mCanceled = true;
                        Log.i(TAG, "moving process is canceled by user");
                        showDialog(DLG_CANCELING);
                    }
                });
                mProgressDialog.setOnDismissListener(new OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        mIsProgressShow = false;
                    }
                });
                return mProgressDialog;
            case DLG_CANCELING:
                ProgressDialog cd = new ProgressDialog(mContext);
                cd.setIndeterminate(true);
                cd.setCancelable(false);
                cd.getWindow().setType(
                        WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                cd.setTitle(getString(R.string.canceling_title));
                cd.setMessage(getString(R.string.canceling_title));
                cd.setOnDismissListener(new OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        mIsCancelingShow = false;
                    }
                });
                return cd;
            case DLG_BAT_LOW:
                return new AlertDialog.Builder(mContext)
                        .setTitle(R.string.bat_low_title)
                        .setPositiveButton(R.string.dlg_ok, this)
                        .setOnCancelListener(this)
                        .setMessage(R.string.bat_low_summary)
                        .create();
            case DLG_MTP_ENABLED:
                return new AlertDialog.Builder(mContext)
                        .setTitle(R.string.mtp_enabled_title)
                        .setPositiveButton(R.string.dlg_ok, this)
                        .setOnCancelListener(this)
                        .setMessage(R.string.mtp_enabled_summary)
                        .create();
            case DLG_NO_FILE_MOVED:
                return new AlertDialog.Builder(mContext)
                        .setTitle(R.string.no_file_moved_title)
                        .setPositiveButton(R.string.dlg_ok, null)
                        .setMessage(R.string.no_file_moved_summary)
                        .create();
        }
        return null;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        if (id == DLG_CANCELING) {
            mIsCancelingShow = true;
        } else if (id == DLG_PROGRESS) {
            mIsProgressShow = true;
            mProgressDialog.setProgress(mProgress);
        }
    }

    /*
     * Handler for UI update
     */
    private Handler mUpdateHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UI_UPDATE_SCANNING: {
                    String scanning = getString(R.string.media_file_scanning);
                    mFreeSizeText.setText(getString(R.string.sd_freespace_text) + scanning);
                    mMusicCheckBox
                            .setText(getString(R.string.memory_music_usage) + "\n" + scanning);
                    mMusicCheckBox.setEnabled(false);
                    mDcimCheckBox.setText(getString(R.string.memory_dcim_usage) + "\n" + scanning);
                    mDcimCheckBox.setEnabled(false);
                    mScanning = true;
                    break;
                }
                case MSG_UI_UPDATE_MEASURE: {
                    Bundle bundle = msg.getData();
                    final long freeSize = bundle.getLong(FREE_SIZE);
                    final long dcimSize = bundle.getLong(DCIM_SIZE);
                    final long musicSize = bundle.getLong(MUSIC_SIZE);
                    updateUiByMeasure(freeSize, dcimSize, musicSize);
                    break;
                }
                case MSG_UI_UPDATE_STATUS: {
                    Bundle bundle = msg.getData();
                    final boolean batLow = bundle.getBoolean(BAT_LOW);
                    final boolean mtpEnabled = bundle.getBoolean(MTP_ENABLED);
                    updateUiByStatus(batLow, mtpEnabled);
                    break;
                }
                case MSG_UI_UPDATE_PROGRESS: {
                    int progress = (Integer) msg.obj;
                    if (mIsProgressShow && mProgressDialog != null) {
                        mProgressDialog.setProgress(progress);
                    } else {
                        showDialog(DLG_PROGRESS);
                        mProgressDialog.setProgress(progress);
                    }
                    break;
                }
                case MSG_UI_RESET: {
                    resetUi();
                    break;
                }
                case MSG_UI_RESULT: {
                    final int ret = (Integer) msg.obj;
                    showResult(ret);
                    break;
                }
                case MSG_UI_NOSD: {
                    if (!isFinishing())
                        showDialog(DLG_NOSD);
                    break;
                }
            }
        }
    };

    private void resetUi() {
        mDcimCheckBox.setChecked(false);
        mMusicCheckBox.setChecked(false);
        if (mScanning) {
            mDcimCheckBox.setEnabled(false);
            mMusicCheckBox.setEnabled(false);
        }
    }

    private void updateUiByStatus(boolean batLow, boolean mtpEnabled) {
        if (batLow || mtpEnabled) {
            if (mIsProgressShow) {
                mCanceled = true;
                dismissDialog(DLG_PROGRESS);
                showDialog(DLG_CANCELING);
            } else {
                resetUi();
                if (batLow && !isFinishing())
                    showDialog(DLG_BAT_LOW);
                else if (mtpEnabled && !isFinishing())
                    showDialog(DLG_MTP_ENABLED);
            }
            mDcimCheckBox.setChecked(false);
            mMusicCheckBox.setChecked(false);
            mDcimCheckBox.setEnabled(false);
            mMusicCheckBox.setEnabled(false);
        } else if (!mScanning) {
            if (mDcimSize != 0)
                mDcimCheckBox.setEnabled(true);
            if (mMusicSize != 0)
                mMusicCheckBox.setEnabled(true);
        }
    }

    private void setCheckBoxListener() {
        final Button moveButton = (Button) mAlert.getButton(BUTTON_POSITIVE);
        if (moveButton == null)
            return;
        moveButton.setEnabled(false);

        mDcimCheckBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    moveButton.setEnabled(true);
                } else if (!mMusicCheckBox.isChecked()) {
                    moveButton.setEnabled(false);
                }
            }
        });

        mMusicCheckBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView,
                    boolean isChecked) {
                if (isChecked) {
                    moveButton.setEnabled(true);
                    showDialog(DLG_MUSIC_CHECK);
                } else if (!mDcimCheckBox.isChecked()) {
                    moveButton.setEnabled(false);
                }

            }
        });
    }

    private void spaceCheck() {
        mTotalSize = 0;

        if (mDcimCheckBox.isChecked()) {
            mTotalSize += mDcimSize;
        }
        if (mMusicCheckBox.isChecked()) {
            mTotalSize += mMusicSize;
        }

        Log.i(TAG, "Move files to External Storage, file size:" + mTotalSize + "; free space:"
                + mFreeSize);
        if (mTotalSize > mFreeSize) {
            Log.w(TAG, "SD Card space insufficient, move part files only");
            showDialog(DLG_SPACE_WARNING);
        } else if (mWorkerHandler != null) {
            mWorkerHandler.sendEmptyMessage(MSG_WORK_MOVE);
        }
    }

    @Override
    public void dismiss() {
        // Do nothing for delay dialog close
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        super.dismiss();
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.dismiss();
    }

    /*
     * Background worker handler, for time-consuming job(Measure and file
     * moving. start a new HandlerThread in onCreat and bind to worker handler
     */
    protected class workerHandler extends Handler {
        public workerHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_WORK_MEASURE: {
                    measure();
                    break;
                }
                case MSG_WORK_MOVE: {
                    move();
                    break;
                }
            }
        }
    };

    private void measure() {
        mFreeSize = 0;
        mDcimSize = 0;
        mMusicSize = 0;

        mUpdateHandler.sendEmptyMessage(MSG_UI_UPDATE_SCANNING);
        Log.i(TAG, "Starting mesurement");

        StorageVolume[] volumes = mStorageManager.getVolumeList();
        if (volumes != null) {
            for (int i = 0; i < volumes.length; i++) {
                String path = volumes[i].getPath();
                if (Environment.MEDIA_MOUNTED.equals(mStorageManager.getVolumeState(path))
                        && Environment.getExternalStorageDirectory().toString().equals(path)) {
                    mSourceStorage = volumes[i];
                    continue;
                } else if (Environment.MEDIA_MOUNTED.equals(mStorageManager
                        .getVolumeState(path))
                        && !Environment.getExternalStorageDirectory().toString().equals(path)
                        && !path.contains("usb")) {
                    mTargetStorage = volumes[i];
                }
            }
        }
        if (mSourceStorage == null || mTargetStorage == null) {
            mUpdateHandler.sendEmptyMessage(MSG_UI_NOSD);
        } else {
            doMeasure();
        }
    }

    /*
     * measure target(SD Card) with statfs measure media files with
     * MeasurementUtils(copy from DefaultContainerService)
     */
    protected void doMeasure() {
        StatFs targetFs = null;
        long freeSize = 0;
        long dcimSize = 0;
        long musicSize = 0;
        mCanceled = false;

        targetFs = new StatFs(mTargetStorage.getPath());
        freeSize = (long) targetFs.getAvailableBlocks() * (long) targetFs.getBlockSize();

        for (String dir : mDcimDirs) {
            String path = mSourceStorage.getPath() + "/" + dir;
            try {
                if (mCanceled)
                    break;
                if (new File(path).exists()) {
                    dcimSize += MeasurementUtils.measureDirectory(path);
                }
            } catch (Exception e) {
                Log.w(TAG, "measure failed for " + path);
            }
        }

        for (String dir : mMusicDirs) {
            String path = mSourceStorage.getPath() + "/" + dir;
            try {
                if (mCanceled)
                    break;
                if (new File(path).exists()) {
                    musicSize += MeasurementUtils.measureDirectory(path);
                }
            } catch (Exception e) {
                Log.w(TAG, "measure failed for " + path);
            }
        }
        Log.w(TAG, "FreeSize:" + freeSize + "; mDcimSize:" + dcimSize
                + "; mMusicSize:" + musicSize);

        Bundle bundle = new Bundle();
        bundle.putLong(FREE_SIZE, freeSize);
        bundle.putLong(DCIM_SIZE, dcimSize);
        bundle.putLong(MUSIC_SIZE, musicSize);
        final Message message = mUpdateHandler.obtainMessage(MSG_UI_UPDATE_MEASURE);
        message.setData(bundle);
        mUpdateHandler.sendMessage(message);
    }

    protected void updateUiByMeasure(long freeSize, long dcimSize, long musicSize) {
        mFreeSize = freeSize;
        mDcimSize = dcimSize;
        mMusicSize = musicSize;

        mFreeSizeText.setText(getString(R.string.sd_freespace_text)
                + Formatter.formatFileSize(mContext, mFreeSize));

        mDcimCheckBox.setText(getString(R.string.memory_dcim_usage)
                + "\n"
                + Formatter.formatFileSize(mContext, mDcimSize));
        if (mDcimSize == 0) {
            mDcimCheckBox.setEnabled(false);
            mDcimCheckBox.setChecked(false);
        } else {
            mDcimCheckBox.setEnabled(true);
        }

        mMusicCheckBox.setText(getString(R.string.memory_music_usage)
                + "\n"
                + Formatter.formatFileSize(mContext, mMusicSize));
        if (mMusicSize == 0) {
            mMusicCheckBox.setEnabled(false);
            mMusicCheckBox.setChecked(false);
        } else {
            mMusicCheckBox.setEnabled(true);
        }

        if (mFreeSize < mDcimSize + mMusicSize)
            mFreeSizeText.setTextColor(getResources().getColor(
                    android.R.color.holo_red_light));
        else
            mFreeSizeText.setTextColor(getResources().getColor(
                    android.R.color.holo_blue_light));
        mScanning = false;
    }

    private int moveDir(String source, String target) {

        String rename_prefix = "copy_of_";
        File sourceDir = new File(source);
        File targetDir = new File(target);
        int ret;

        if (!sourceDir.exists() || sourceDir.isHidden()) {
            Log.w(TAG, "Ignore hidden or non-exist dir: " + source);
            return SUCCESS;
        }

        if (sourceDir.isDirectory() && sourceDir.canRead()) {
            Log.i(TAG, "Moving " + source + " to " + target);
            if (!targetDir.exists()) {
                Log.i(TAG, "target Dir:" + target + " not exist, mkdir");
                if (!targetDir.mkdir()) {
                    Log.w(TAG, "mkdir " + target + "failed");
                    return ERR_PERM;
                }
            } else if (!targetDir.isDirectory()) {
                String newTarget = targetDir.getParent() + "/" + rename_prefix
                        + targetDir.getName();
                return moveDir(source, newTarget);
            } else if (!targetDir.canWrite()) {
                return ERR_PERM;
            }
            String[] list = sourceDir.list();
            for (String file : list) {
                String sourcePath = source + "/" + file;
                String targetPath = target + "/" + file;
                File sourceFile = new File(sourcePath);
                File targetFile = new File(targetPath);

                if (sourceFile.isDirectory()) {
                    ret = moveDir(sourcePath, targetPath);
                    if (ret != SUCCESS) {
                        return ret;
                    }
                } else if (sourceFile.canRead()) {
                    ret = moveFile(sourcePath, targetPath);
                    if (mCanceled)
                        break;
                    if (ret != SUCCESS)
                        return ret;
                } else {
                    Log.e("TAG", "File cannot read: " + sourcePath);
                    return ERR_PERM;
                }
            }
        } else {
            return ERR_PERM;
        }
        return SUCCESS;
    }

    private int moveFile(String source, String target) {
        String rename_prefix = "copy_of_";
        File sourceFile = new File(source);
        File targetFile = new File(target);

        Log.i(TAG, "Moving " + source + " to " + target);
        if (!sourceFile.exists() || sourceFile.isHidden())
            return SUCCESS;

        StatFs targetFs = new StatFs(mTargetStorage.getPath());
        if (sourceFile.isFile() && sourceFile.canRead()) {
            if ((long) targetFs.getAvailableBlocks() * (long) targetFs.getBlockSize() < sourceFile
                    .length()) {
                Log.e(TAG, "Space insufficient while moving file " + source);
                return ERR_SPACE;
            }
            if (targetFile.exists()) {
                String newTarget = targetFile.getParent() + "/" + rename_prefix
                        + targetFile.getName();
                return moveFile(source, newTarget);
            }
            BufferedInputStream sourceStream = null;
            BufferedOutputStream targetStream = null;
            FileInputStream fin = null;
            FileOutputStream fout = null;
            final int BUF_SIZE = 8 * 1024;
            try {
                fin = new FileInputStream(source);
                fout = new FileOutputStream(target);
                sourceStream = new BufferedInputStream(fin);
                targetStream = new BufferedOutputStream(fout);
                byte[] buf = new byte[BUF_SIZE];
                int len;
                while ((len = sourceStream.read(buf, 0, BUF_SIZE)) > 0) {
                    if (!mCanceled) {
                        targetStream.write(buf, 0, len);
                    } else {
                        break;
                    }
                    mLenInStep += len;
                    if (mLenInStep >= mOneStepSize) {
                        // progress step up by 1% of total size
                        mProgress++;
                        if (mProgress > PROGRESS_MAX)
                            mProgress = PROGRESS_MAX;
                        if (!mCanceled) {
                            final Message message = mUpdateHandler.obtainMessage(
                                    MSG_UI_UPDATE_PROGRESS, mProgress);
                            mUpdateHandler.sendMessage(message);
                        }
                        mLenInStep = mLenInStep - mOneStepSize;
                    }
                }
                if (!mCanceled) {
                    targetStream.flush();
                    mScannerClient.scanPath(target);
                }
            } catch (Exception e) {
                Log.e(TAG, "file copy error. catch Exeception", e);
                return ERR_OTHER;
            } finally {
                try {
                    if (fout != null)
                        fout.close();
                    if (targetStream != null)
                        targetStream.close();

                    if (fin != null)
                        fin.close();
                    if (sourceStream != null)
                        sourceStream.close();

                    if (!mCanceled) {
                        if (sourceFile.delete()) {
                            mScannerClient.removeDbEntry(source);
                            mFileMoved = true;
                        } else {
                            Log.e(TAG, "file delete error:" + source);
                            return ERR_PERM;
                        }
                    } else {
                        if (!targetFile.delete()) {
                            Log.e(TAG, "file delete error:" + source);
                            return ERR_PERM;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "close stream error, IOException", e);
                    return ERR_OTHER;
                }
            }
        } else {
            return ERR_PERM;
        }
        return SUCCESS;
    }

    private void move() {
        int ret = SUCCESS;
        mWakeLock.acquire();
        mOneStepSize = (int) (mTotalSize / PROGRESS_MAX);
        mCanceled = false;
        mFileMoved = false;
        mProgress = 0;
        final Message message = mUpdateHandler.obtainMessage(MSG_UI_UPDATE_PROGRESS, mProgress);
        mUpdateHandler.sendMessage(message);

        try {
            if (mDcimCheckBox.isChecked()) {
                for (String dir : mDcimDirs) {
                    String sourcePath = mSourceStorage.getPath() + "/" + dir;
                    String targetPath = mTargetStorage.getPath() + "/" + dir;
                    ret = moveDir(sourcePath, targetPath);
                    if (ret != SUCCESS || mCanceled)
                        break;
                }
            }
            if (mMusicCheckBox.isChecked() && !mCanceled) {
                for (String dir : mMusicDirs) {
                    String sourcePath = mSourceStorage.getPath() + "/" + dir;
                    String targetPath = mTargetStorage.getPath() + "/" + dir;
                    ret = moveDir(sourcePath, targetPath);
                    if (ret != SUCCESS || mCanceled)
                        break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception ossurs.", e);
        } finally {
            mWakeLock.release();
            final Message resultMsg = mUpdateHandler.obtainMessage(MSG_UI_RESULT, ret);
            mUpdateHandler.sendMessage(resultMsg);
        }
    }

    private void showResult(int ret) {
        try {
            if (mIsProgressShow)
                removeDialog(DLG_PROGRESS);
            if (mIsCancelingShow)
                dismissDialog(DLG_CANCELING);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Error while dismissing/remove dialog", e);
        }

        if (mBatteryLow && !isFinishing()) {
            showDialog(DLG_BAT_LOW);
        } else if (mMtpEnabled && !isFinishing()) {
            showDialog(DLG_MTP_ENABLED);
        } else if (mCanceled) {
            showDialog(DLG_CANCEL);
        } else if (ret == ERR_SPACE) {
            showDialog(DLG_ERR_SPACE);
        } else if (!mFileMoved) {
            showDialog(DLG_NO_FILE_MOVED);
        } else if (ret == SUCCESS) {
            showDialog(DLG_SUCCESS);
        } else {
            showDialog(DLG_ERR_OTHER);
        }

        mUpdateHandler.sendEmptyMessage(MSG_UI_RESET);
        if (mWorkerHandler != null)
            mWorkerHandler.sendEmptyMessage(MSG_WORK_MEASURE);
    }

}
