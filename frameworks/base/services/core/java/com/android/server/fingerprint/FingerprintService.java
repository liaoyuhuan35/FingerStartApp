/**
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.server.fingerprint;

import android.Manifest;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityManagerNative;
import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.app.IUserSwitchObserver;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.hardware.fingerprint.IFingerprintServiceLockoutResetCallback;
import android.os.Binder;
import android.os.DeadObjectException;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.IRemoteCallback;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SELinux;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Slog;

import com.android.internal.logging.MetricsLogger;
import com.android.server.SystemService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.hardware.fingerprint.Fingerprint;
import android.hardware.fingerprint.FingerprintManager;
import android.hardware.fingerprint.IFingerprintService;
import android.hardware.fingerprint.IFingerprintDaemon;
import android.hardware.fingerprint.IFingerprintDaemonCallback;
import android.hardware.fingerprint.IFingerprintServiceReceiver;

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
import static android.Manifest.permission.MANAGE_FINGERPRINT;
import static android.Manifest.permission.RESET_FINGERPRINT_LOCKOUT;
import static android.Manifest.permission.USE_FINGERPRINT;

import java.io.File;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
// Add by lizusheng@wind-mobi.com for silead_fingerprint 20150504 begin
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.os.SystemProperties;
// Add by lizusheng@wind-mobi.com for silead_fingerprint 20150504 end

/**
 * A service to manage multiple clients that want to access the fingerprint HAL API.
 * The service is responsible for maintaining a list of clients and dispatching all
 * fingerprint -related events.
 *
 * @hide
 */
public class FingerprintService extends SystemService implements IBinder.DeathRecipient {
    private static final String TAG = "FingerprintService";
    private static final boolean DEBUG = true;
    private static final String FP_DATA_DIR = "fpdata";
    private static final String FINGERPRINTD = "android.hardware.fingerprint.IFingerprintDaemon";
    private static final int MSG_USER_SWITCHING = 10;
    private static final int ENROLLMENT_TIMEOUT_MS = 60 * 1000; // 1 minute
    private static final String ACTION_LOCKOUT_RESET =
            "com.android.server.fingerprint.ACTION_LOCKOUT_RESET";

    private ClientMonitor mAuthClient = null;
    private ClientMonitor mEnrollClient = null;
    private ClientMonitor mRemoveClient = null;
    private final ArrayList<FingerprintServiceLockoutResetMonitor> mLockoutMonitors =
            new ArrayList<>();
    private final AppOpsManager mAppOps;

    private static final long MS_PER_SEC = 1000;
    private static final long FAIL_LOCKOUT_TIMEOUT_MS = 30*1000;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int FINGERPRINT_ACQUIRED_GOOD = 0;
    private final String mKeyguardPackage;
// Add by lizusheng@wind-mobi.com for silead_fingerprint 20150504 begin
    private static final boolean WIND_FINGERPRINT_SILEAD_ENABLE = SystemProperties.get("ro.wind_fingerprint_silead").equals("1");
// Add by lizusheng@wind-mobi.com for silead_fingerprint 20150504 end

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(android.os.Message msg) {
            switch (msg.what) {
                case MSG_USER_SWITCHING:
                    handleUserSwitching(msg.arg1);
                    break;

                default:
                    Slog.w(TAG, "Unknown message:" + msg.what);
            }
        }
    };

    private final FingerprintUtils mFingerprintUtils = FingerprintUtils.getInstance();
    private Context mContext;
    private long mHalDeviceId;
    private int mFailedAttempts;
    private IFingerprintDaemon mDaemon;
    private final PowerManager mPowerManager;
    private final AlarmManager mAlarmManager;

    private final BroadcastReceiver mLockoutReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_LOCKOUT_RESET.equals(intent.getAction())) {
                resetFailedAttempts();
            }
        }
    };

    private final Runnable mResetFailedAttemptsRunnable = new Runnable() {
        @Override
        public void run() {
            resetFailedAttempts();
        }
    };

    public FingerprintService(Context context) {
        super(context);
        mContext = context;
        mKeyguardPackage = ComponentName.unflattenFromString(context.getResources().getString(
                com.android.internal.R.string.config_keyguardComponent)).getPackageName();
        mAppOps = context.getSystemService(AppOpsManager.class);
        mPowerManager = mContext.getSystemService(PowerManager.class);
        mAlarmManager = mContext.getSystemService(AlarmManager.class);
        mContext.registerReceiver(mLockoutReceiver, new IntentFilter(ACTION_LOCKOUT_RESET),
                RESET_FINGERPRINT_LOCKOUT, null /* handler */);
    }

    @Override
    public void binderDied() {
        Slog.v(TAG, "fingerprintd died");
        mDaemon = null;
        handleError(mHalDeviceId, FingerprintManager.FINGERPRINT_ERROR_HW_UNAVAILABLE);
    }

    public IFingerprintDaemon getFingerprintDaemon() {
        if (mDaemon == null) {
            mDaemon = IFingerprintDaemon.Stub.asInterface(ServiceManager.getService(FINGERPRINTD));
            if (mDaemon != null) {
                try {
                    mDaemon.asBinder().linkToDeath(this, 0);
                    mDaemon.init(mDaemonCallback);
                    mHalDeviceId = mDaemon.openHal();
                    if (mHalDeviceId != 0) {
                        updateActiveGroup(ActivityManager.getCurrentUser());
                    } else {
                        Slog.w(TAG, "Failed to open Fingerprint HAL!");
                        mDaemon = null;
                    }
                } catch (RemoteException e) {
                    Slog.e(TAG, "Failed to open fingeprintd HAL", e);
                    mDaemon = null; // try again later!
                }
            } else {
                Slog.w(TAG, "fingerprint service not available");
            }
        }
        return mDaemon;
    }

    protected void handleEnumerate(long deviceId, int[] fingerIds, int[] groupIds) {
        if (fingerIds.length != groupIds.length) {
            Slog.w(TAG, "fingerIds and groupIds differ in length: f[]="
                    + fingerIds + ", g[]=" + groupIds);
            return;
        }
        if (DEBUG) Slog.w(TAG, "Enumerate: f[]=" + fingerIds + ", g[]=" + groupIds);
        // TODO: update fingerprint/name pairs
    }

    protected void handleRemoved(long deviceId, int fingerId, int groupId) {
        final ClientMonitor client = mRemoveClient;
        if (fingerId != 0) {
            removeTemplateForUser(mRemoveClient, fingerId);
        }
        if (client != null && client.sendRemoved(fingerId, groupId)) {
            removeClient(mRemoveClient);
        }
    }

    protected void handleError(long deviceId, int error) {
        if (mEnrollClient != null) {
            final IBinder token = mEnrollClient.token;
            if (mEnrollClient.sendError(error)) {
                stopEnrollment(token, false);
            }
        } else if (mAuthClient != null) {
            final IBinder token = mAuthClient.token;
            if (mAuthClient.sendError(error)) {
                stopAuthentication(token, false);
            }
        } else if (mRemoveClient != null) {
            if (mRemoveClient.sendError(error)) removeClient(mRemoveClient);
        }
    }

    protected void handleAuthenticated(long deviceId, int fingerId, int groupId) {
        WindLog(TAG,"handleAuthenticated mAuthClient="+mAuthClient );
        if (mAuthClient != null) {
            final IBinder token = mAuthClient.token;
            if (mAuthClient.sendAuthenticated(fingerId, groupId)) {
                //A:WOS WOS_FingerPrint 3.0 liaoyuhuan@wind-mobi.com 160415 begin
                stopAuthentication(token, true);
                //A:WOS WOS_FingerPrint 3.0 liaoyuhuan@wind-mobi.com 160415 end
                removeClient(mAuthClient);
              //A:WOS WOS_FingerPrint 3.0 liaoyuhuan@wind-mobi.com 160415 begin
                if(android.os.SystemProperties.get("ro.wos_app_fp3").equals("1") && fpFunctionStartAppOpened(mContext)){
                    sendFingerMSG(deviceId,fingerId,true);
                }
              //A:WOS WOS_FingerPrint 3.0 liaoyuhuan@wind-mobi.com 160415 end
            }
        }
    }
    
    //A:WOS WOS_FingerPrint 3.0 liaoyuhuan@wind-mobi.com 160415 begin
    //add for FingerStart/AppLock
    public final static String FINGER_ACTION_START = "com.wind.msg.fingerprintservice";
    public final static String KEY_FINGER_RESULT = "finger.result";
    public final static String KEY_FINGER_ID = "finger.id";
    public final static String KEY_DEVICE_ID = "device.id";


	private static final String PROP_FP_FUNCTION_START_APP = "prop.fp.function.startapp";
	protected boolean fpFunctionStartAppOpened(Context context){
	    return android.provider.Settings.System.getInt(context.getContentResolver(), PROP_FP_FUNCTION_START_APP, 0) == 1;
	}
    
    protected void sendFingerMSG(long deviceid,int fingerid,boolean result){
        WindLog(TAG,"sendFingerMSG deviceid="+deviceid + " fingerid="+fingerid + " result="+result );
        Intent intent = new Intent();
        intent.setAction(FINGER_ACTION_START);
        intent.putExtra(KEY_FINGER_RESULT, result);
        intent.putExtra(KEY_FINGER_ID, fingerid);
        intent.putExtra(KEY_DEVICE_ID, deviceid);
        mContext.sendBroadcast(intent);
    }

    protected void WindLog(String tag,String buf){
        Slog.v("wind/"+tag, buf);
    }
    //A:WOS WOS_FingerPrint 3.0 liaoyuhuan@wind-mobi.com 160415 end

    protected void handleAcquired(long deviceId, int acquiredInfo) {
        if (mEnrollClient != null) {
            if (mEnrollClient.sendAcquired(acquiredInfo)) {
                removeClient(mEnrollClient);
            }
        } else if (mAuthClient != null) {
            if (mAuthClient.sendAcquired(acquiredInfo)) {
                removeClient(mAuthClient);
            }
        }
    }

    protected void handleEnrollResult(long deviceId, int fingerId, int groupId, int remaining) {
        if (mEnrollClient != null) {
            if (mEnrollClient.sendEnrollResult(fingerId, groupId, remaining)) {
                if (remaining == 0) {
                    addTemplateForUser(mEnrollClient, fingerId);
                    removeClient(mEnrollClient);
                }
            }
        } else {
// Add by lizusheng@wind-mobi.com for silead_fingerprint 20150504 begin
            // Fix bug: when enrolled success at same time click BackKey,
            // it will leads add TemplateForUser not execute because of enrollClient is null,
            // fingerInfo is save in fpsvcd, but not display on activity
            Slog.d(TAG, " SLCODE dispatchEnrollResult, EnrollClient is null,"
                    + " but need to save fingerInfo to fingerlist.xml\n");
            Slog.d(TAG, " SLCODE dispatchEnrollResult, remaining: " + remaining);
			if(WIND_FINGERPRINT_SILEAD_ENABLE){
            if (remaining == 0) {
                mFingerprintUtils.addFingerprintForUser(mContext,
                        fingerId, getCurrentUserId());
            }
			}
// Add by lizusheng@wind-mobi.com for silead_fingerprint 20150504 end
        }
    }

// Add by lizusheng@wind-mobi.com for silead_fingerprint 20150504 begin
    private int getCurrentUserId() {
        try {
            return ActivityManagerNative.getDefault().getCurrentUser().id;
        } catch (RemoteException e) {
            Slog.w(TAG, "Failed to get current user id\n");
            return UserHandle.USER_NULL;
        }
    }
// Add by lizusheng@wind-mobi.com for silead_fingerprint 20150504 end

    private void userActivity() {
        long now = SystemClock.uptimeMillis();
        mPowerManager.userActivity(now, PowerManager.USER_ACTIVITY_EVENT_TOUCH, 0);
    }

    void handleUserSwitching(int userId) {
        updateActiveGroup(userId);
    }

    private void removeClient(ClientMonitor client) {
        if (client == null) return;
        client.destroy();
        if (client == mAuthClient) {
            mAuthClient = null;
        } else if (client == mEnrollClient) {
            mEnrollClient = null;
        } else if (client == mRemoveClient) {
            mRemoveClient = null;
        }
    }

    private boolean inLockoutMode() {
        return mFailedAttempts >= MAX_FAILED_ATTEMPTS;
    }

    private void scheduleLockoutReset() {
        mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + FAIL_LOCKOUT_TIMEOUT_MS, getLockoutResetIntent());
    }

    private void cancelLockoutReset() {
        mAlarmManager.cancel(getLockoutResetIntent());
    }

    private PendingIntent getLockoutResetIntent() {
        return PendingIntent.getBroadcast(mContext, 0,
                new Intent(ACTION_LOCKOUT_RESET), PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void resetFailedAttempts() {
        if (DEBUG && inLockoutMode()) {
            Slog.v(TAG, "Reset fingerprint lockout");
        }
        mFailedAttempts = 0;
        // If we're asked to reset failed attempts externally (i.e. from Keyguard), the alarm might
        // still be pending; remove it.
        cancelLockoutReset();
        notifyLockoutResetMonitors();
    }

    private boolean handleFailedAttempt(ClientMonitor clientMonitor) {
        mFailedAttempts++;
        if (inLockoutMode()) {
            // Failing multiple times will continue to push out the lockout time.
            scheduleLockoutReset();
            if (clientMonitor != null
                    && !clientMonitor.sendError(FingerprintManager.FINGERPRINT_ERROR_LOCKOUT)) {
                Slog.w(TAG, "Cannot send lockout message to client");
            }
            return true;
        }
        return false;
    }

    private void removeTemplateForUser(ClientMonitor clientMonitor, int fingerId) {
        mFingerprintUtils.removeFingerprintIdForUser(mContext, fingerId, clientMonitor.userId);
    }

    private void addTemplateForUser(ClientMonitor clientMonitor, int fingerId) {
        mFingerprintUtils.addFingerprintForUser(mContext, fingerId, clientMonitor.userId);
    }

    void startEnrollment(IBinder token, byte[] cryptoToken, int groupId,
            IFingerprintServiceReceiver receiver, int flags, boolean restricted) {
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon == null) {
            Slog.w(TAG, "enroll: no fingeprintd!");
            return;
        }
        stopPendingOperations(true);
        mEnrollClient = new ClientMonitor(token, receiver, groupId, restricted, token.toString());
        final int timeout = (int) (ENROLLMENT_TIMEOUT_MS / MS_PER_SEC);
        try {
            final int result = daemon.enroll(cryptoToken, groupId, timeout);
            if (result != 0) {
                Slog.w(TAG, "startEnroll failed, result=" + result);
                handleError(mHalDeviceId, FingerprintManager.FINGERPRINT_ERROR_HW_UNAVAILABLE);
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "startEnroll failed", e);
        }
    }

    public long startPreEnroll(IBinder token) {
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon == null) {
            Slog.w(TAG, "startPreEnroll: no fingeprintd!");
            return 0;
        }
        try {
            return daemon.preEnroll();
        } catch (RemoteException e) {
            Slog.e(TAG, "startPreEnroll failed", e);
        }
        return 0;
    }

    public int startPostEnroll(IBinder token) {
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon == null) {
            Slog.w(TAG, "startPostEnroll: no fingeprintd!");
            return 0;
        }
        try {
            return daemon.postEnroll();
        } catch (RemoteException e) {
            Slog.e(TAG, "startPostEnroll failed", e);
        }
        return 0;
    }

    private void stopPendingOperations(boolean initiatedByClient) {
        if (mEnrollClient != null) {
            stopEnrollment(mEnrollClient.token, initiatedByClient);
        }
        if (mAuthClient != null) {
            stopAuthentication(mAuthClient.token, initiatedByClient);
        }
        // mRemoveClient is allowed to continue
    }

    /**
     * Stop enrollment in progress and inform client if they initiated it.
     *
     * @param token token for client
     * @param initiatedByClient if this call is the result of client action (e.g. calling cancel)
     */
    void stopEnrollment(IBinder token, boolean initiatedByClient) {
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon == null) {
            Slog.w(TAG, "stopEnrollment: no fingeprintd!");
            return;
        }
        final ClientMonitor client = mEnrollClient;
        if (client == null || client.token != token) return;
        if (initiatedByClient) {
            try {
                int result = daemon.cancelEnrollment();
                if (result != 0) {
                    Slog.w(TAG, "startEnrollCancel failed, result = " + result);
                }
            } catch (RemoteException e) {
                Slog.e(TAG, "stopEnrollment failed", e);
            }
            client.sendError(FingerprintManager.FINGERPRINT_ERROR_CANCELED);
        }
        removeClient(mEnrollClient);
    }

    void startAuthentication(IBinder token, long opId, int groupId,
            IFingerprintServiceReceiver receiver, int flags, boolean restricted,
            String opPackageName) {
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon == null) {
            Slog.w(TAG, "startAuthentication: no fingeprintd!");
            return;
        }
        stopPendingOperations(true);
        mAuthClient = new ClientMonitor(token, receiver, groupId, restricted, opPackageName);
        if (inLockoutMode()) {
            Slog.v(TAG, "In lockout mode; disallowing authentication");
            if (!mAuthClient.sendError(FingerprintManager.FINGERPRINT_ERROR_LOCKOUT)) {
                Slog.w(TAG, "Cannot send timeout message to client");
            }
            mAuthClient = null;
            return;
        }
        try {
            final int result = daemon.authenticate(opId, groupId);
            if (result != 0) {
                Slog.w(TAG, "startAuthentication failed, result=" + result);
                handleError(mHalDeviceId, FingerprintManager.FINGERPRINT_ERROR_HW_UNAVAILABLE);
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "startAuthentication failed", e);
        }
    }

    /**
     * Stop authentication in progress and inform client if they initiated it.
     *
     * @param token token for client
     * @param initiatedByClient if this call is the result of client action (e.g. calling cancel)
     */
    void stopAuthentication(IBinder token, boolean initiatedByClient) {
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon == null) {
            Slog.w(TAG, "stopAuthentication: no fingeprintd!");
            return;
        }
        final ClientMonitor client = mAuthClient;
        if (client == null || client.token != token) return;
        if (initiatedByClient) {
            try {
                int result = daemon.cancelAuthentication();
                if (result != 0) {
                    Slog.w(TAG, "stopAuthentication failed, result=" + result);
                }
            } catch (RemoteException e) {
                Slog.e(TAG, "stopAuthentication failed", e);
            }
            client.sendError(FingerprintManager.FINGERPRINT_ERROR_CANCELED);
        }
        removeClient(mAuthClient);
    }

    void startRemove(IBinder token, int fingerId, int userId,
            IFingerprintServiceReceiver receiver, boolean restricted) {
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon == null) {
            Slog.w(TAG, "startRemove: no fingeprintd!");
            return;
        }

        stopPendingOperations(true);
        mRemoveClient = new ClientMonitor(token, receiver, userId, restricted, token.toString());
        // The fingerprint template ids will be removed when we get confirmation from the HAL
        try {
            final int result = daemon.remove(fingerId, userId);
            if (result != 0) {
                Slog.w(TAG, "startRemove with id = " + fingerId + " failed, result=" + result);
                handleError(mHalDeviceId, FingerprintManager.FINGERPRINT_ERROR_HW_UNAVAILABLE);
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "startRemove failed", e);
        }
    }

    public List<Fingerprint> getEnrolledFingerprints(int userId) {
        return mFingerprintUtils.getFingerprintsForUser(mContext, userId);
    }

    public boolean hasEnrolledFingerprints(int userId) {
        return mFingerprintUtils.getFingerprintsForUser(mContext, userId).size() > 0;
    }

    boolean hasPermission(String permission) {
        return getContext().checkCallingOrSelfPermission(permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    void checkPermission(String permission) {
        getContext().enforceCallingOrSelfPermission(permission,
                "Must have " + permission + " permission.");
    }

    int getEffectiveUserId(int userId) {
        UserManager um = UserManager.get(mContext);
        if (um != null) {
            final long callingIdentity = Binder.clearCallingIdentity();
            userId = um.getCredentialOwnerProfile(userId);
            Binder.restoreCallingIdentity(callingIdentity);
        } else {
            Slog.e(TAG, "Unable to acquire UserManager");
        }
        return userId;
    }

    boolean isCurrentUserOrProfile(int userId) {
        UserManager um = UserManager.get(mContext);

        // Allow current user or profiles of the current user...
        List<UserInfo> profiles = um.getEnabledProfiles(userId);
        final int n = profiles.size();
        for (int i = 0; i < n; i++) {
            if (profiles.get(i).id == userId) {
                return true;
            }
        }
        return false;
    }

    private boolean isForegroundActivity(int uid, int pid) {
        try {
            List<RunningAppProcessInfo> procs =
                    ActivityManagerNative.getDefault().getRunningAppProcesses();
            int N = procs.size();
            for (int i = 0; i < N; i++) {
                RunningAppProcessInfo proc = procs.get(i);
                if (proc.pid == pid && proc.uid == uid
                        && proc.importance == IMPORTANCE_FOREGROUND) {
                    return true;
                }
            }
        } catch (RemoteException e) {
            Slog.w(TAG, "am.getRunningAppProcesses() failed");
        }
        return false;
    }

    /**
     * @param opPackageName name of package for caller
     * @param foregroundOnly only allow this call while app is in the foreground
     * @return true if caller can use fingerprint API
     */
    private boolean canUseFingerprint(String opPackageName, boolean foregroundOnly) {
        checkPermission(USE_FINGERPRINT);
        final int uid = Binder.getCallingUid();
        final int pid = Binder.getCallingPid();
        if (opPackageName.equals(mKeyguardPackage)) {
            return true; // Keyguard is always allowed
        }
        if (!isCurrentUserOrProfile(UserHandle.getCallingUserId())) {
            Slog.w(TAG,"Rejecting " + opPackageName + " ; not a current user or profile");
            return false;
        }
        if (mAppOps.noteOp(AppOpsManager.OP_USE_FINGERPRINT, uid, opPackageName)
                != AppOpsManager.MODE_ALLOWED) {
            Slog.w(TAG, "Rejecting " + opPackageName + " ; permission denied");
            return false;
        }
        if (foregroundOnly && !isForegroundActivity(uid, pid)) {
            Slog.w(TAG, "Rejecting " + opPackageName + " ; not in foreground");
            return false;
        }
        return true;
    }

    private void addLockoutResetMonitor(FingerprintServiceLockoutResetMonitor monitor) {
        if (!mLockoutMonitors.contains(monitor)) {
            mLockoutMonitors.add(monitor);
        }
    }

    private void removeLockoutResetCallback(
            FingerprintServiceLockoutResetMonitor monitor) {
        mLockoutMonitors.remove(monitor);
    }

    private void notifyLockoutResetMonitors() {
        for (int i = 0; i < mLockoutMonitors.size(); i++) {
            mLockoutMonitors.get(i).sendLockoutReset();
        }
    }

    private class ClientMonitor implements IBinder.DeathRecipient {
        IBinder token;
        IFingerprintServiceReceiver receiver;
        int userId;
        boolean restricted; // True if client does not have MANAGE_FINGERPRINT permission
        String owner;

        public ClientMonitor(IBinder token, IFingerprintServiceReceiver receiver, int userId,
                boolean restricted, String owner) {
            this.token = token;
            this.receiver = receiver;
            this.userId = userId;
            this.restricted = restricted;
            this.owner = owner; // name of the client that owns this - for debugging
            try {
                token.linkToDeath(this, 0);
            } catch (RemoteException e) {
                Slog.w(TAG, "caught remote exception in linkToDeath: ", e);
            }
        }

        public void destroy() {
            if (token != null) {
                try {
                    token.unlinkToDeath(this, 0);
                } catch (NoSuchElementException e) {
                    // TODO: remove when duplicate call bug is found
                    Slog.e(TAG, "destroy(): " + this + ":", new Exception("here"));
                }
                token = null;
            }
            receiver = null;
        }

        @Override
        public void binderDied() {
            token = null;
            removeClient(this);
            receiver = null;
        }

        @Override
        protected void finalize() throws Throwable {
            try {
                if (token != null) {
                    if (DEBUG) Slog.w(TAG, "removing leaked reference: " + token);
                    removeClient(this);
                }
            } finally {
                super.finalize();
            }
        }

        /*
         * @return true if we're done.
         */
        private boolean sendRemoved(int fingerId, int groupId) {
            if (receiver == null) return true; // client not listening
            try {
                receiver.onRemoved(mHalDeviceId, fingerId, groupId);
                return fingerId == 0;
            } catch (RemoteException e) {
                Slog.w(TAG, "Failed to notify Removed:", e);
            }
            return false;
        }

        /*
         * @return true if we're done.
         */
        private boolean sendEnrollResult(int fpId, int groupId, int remaining) {
            if (receiver == null) return true; // client not listening
            FingerprintUtils.vibrateFingerprintSuccess(getContext());
            MetricsLogger.action(mContext, MetricsLogger.ACTION_FINGERPRINT_ENROLL);
            try {
                receiver.onEnrollResult(mHalDeviceId, fpId, groupId, remaining);
                return remaining == 0;
            } catch (RemoteException e) {
                Slog.w(TAG, "Failed to notify EnrollResult:", e);
                return true;
            }
        }

        /*
         * @return true if we're done.
         */
        private boolean sendAuthenticated(int fpId, int groupId) {
            boolean result = false;
            boolean authenticated = fpId != 0;
            if (receiver != null) {
                try {
                    MetricsLogger.action(mContext, MetricsLogger.ACTION_FINGERPRINT_AUTH,
                            authenticated);
                    if (!authenticated) {
                        receiver.onAuthenticationFailed(mHalDeviceId);
                    } else {
                        if (DEBUG) {
                            Slog.v(TAG, "onAuthenticated(owner=" + mAuthClient.owner
                                    + ", id=" + fpId + ", gp=" + groupId + ")");
                        }
                        Fingerprint fp = !restricted ?
                                new Fingerprint("" /* TODO */, groupId, fpId, mHalDeviceId) : null;
                        receiver.onAuthenticationSucceeded(mHalDeviceId, fp);
                    }
                } catch (RemoteException e) {
                    Slog.w(TAG, "Failed to notify Authenticated:", e);
                    result = true; // client failed
                }
            } else {
                result = true; // client not listening
	    }
	    if (fpId == 0) {
                if (receiver != null) {
                    FingerprintUtils.vibrateFingerprintError(getContext());
                }
                result |= handleFailedAttempt(this);
            } else {
                if (receiver != null) {
                    FingerprintUtils.vibrateFingerprintSuccess(getContext());
                }
                result |= true; // we have a valid fingerprint
                resetFailedAttempts();
            }
            return result;
        }

        /*
         * @return true if we're done.
         */
        private boolean sendAcquired(int acquiredInfo) {
            if (receiver == null) return true; // client not listening
            try {
                receiver.onAcquired(mHalDeviceId, acquiredInfo);
                return false; // acquisition continues...
            } catch (RemoteException e) {
                Slog.w(TAG, "Failed to invoke sendAcquired:", e);
                return true; // client failed
            }
            finally {
                // Good scans will keep the device awake
                if (acquiredInfo == FINGERPRINT_ACQUIRED_GOOD) {
                    userActivity();
                }
            }
        }

        /*
         * @return true if we're done.
         */
        private boolean sendError(int error) {
            if (receiver != null) {
                try {
                    receiver.onError(mHalDeviceId, error);
                } catch (RemoteException e) {
                    Slog.w(TAG, "Failed to invoke sendError:", e);
                }
            }
            return true; // errors always terminate progress
        }
    }

    private class FingerprintServiceLockoutResetMonitor {

        private final IFingerprintServiceLockoutResetCallback mCallback;

        public FingerprintServiceLockoutResetMonitor(
                IFingerprintServiceLockoutResetCallback callback) {
            mCallback = callback;
        }

        public void sendLockoutReset() {
            if (mCallback != null) {
                try {
                    mCallback.onLockoutReset(mHalDeviceId);
                } catch (DeadObjectException e) {
                    Slog.w(TAG, "Death object while invoking onLockoutReset: ", e);
                    mHandler.post(mRemoveCallbackRunnable);
                } catch (RemoteException e) {
                    Slog.w(TAG, "Failed to invoke onLockoutReset: ", e);
                }
            }
        }

        private final Runnable mRemoveCallbackRunnable = new Runnable() {
            @Override
            public void run() {
                removeLockoutResetCallback(FingerprintServiceLockoutResetMonitor.this);
            }
        };
    }

    private IFingerprintDaemonCallback mDaemonCallback = new IFingerprintDaemonCallback.Stub() {

        @Override
        public void onEnrollResult(final long deviceId, final int fingerId, final int groupId,
                final int remaining) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    handleEnrollResult(deviceId, fingerId, groupId, remaining);
                }
            });
        }

        @Override
        public void onAcquired(final long deviceId, final int acquiredInfo) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    handleAcquired(deviceId, acquiredInfo);
                }
            });
        }

        @Override
        public void onAuthenticated(final long deviceId, final int fingerId, final int groupId) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    handleAuthenticated(deviceId, fingerId, groupId);
                }
            });
        }

        @Override
        public void onError(final long deviceId, final int error) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    handleError(deviceId, error);
                }
            });
        }

        @Override
        public void onRemoved(final long deviceId, final int fingerId, final int groupId) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    handleRemoved(deviceId, fingerId, groupId);
                }
            });
        }

        @Override
        public void onEnumerate(final long deviceId, final int[] fingerIds, final int[] groupIds) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    handleEnumerate(deviceId, fingerIds, groupIds);
                }
            });
        }
    };

    private final class FingerprintServiceWrapper extends IFingerprintService.Stub {
        private static final String KEYGUARD_PACKAGE = "com.android.systemui";

        @Override // Binder call
        public long preEnroll(IBinder token) {
            checkPermission(MANAGE_FINGERPRINT);
            return startPreEnroll(token);
        }

        @Override // Binder call
        public int postEnroll(IBinder token) {
            checkPermission(MANAGE_FINGERPRINT);
            return startPostEnroll(token);
        }

        @Override // Binder call
        public void enroll(final IBinder token, final byte[] cryptoToken, final int groupId,
                final IFingerprintServiceReceiver receiver, final int flags) {
            checkPermission(MANAGE_FINGERPRINT);
            final int limit =  mContext.getResources().getInteger(
                    com.android.internal.R.integer.config_fingerprintMaxTemplatesPerUser);
            final int callingUid = Binder.getCallingUid();
            final int userId = UserHandle.getUserId(callingUid);
            final int enrolled = FingerprintService.this.getEnrolledFingerprints(userId).size();
            if (enrolled >= limit) {
                Slog.w(TAG, "Too many fingerprints registered");
                return;
            }
            final byte [] cryptoClone = Arrays.copyOf(cryptoToken, cryptoToken.length);

            // Group ID is arbitrarily set to parent profile user ID. It just represents
            // the default fingerprints for the user.
            final int effectiveGroupId = getEffectiveUserId(groupId);

            final boolean restricted = isRestricted();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    startEnrollment(token, cryptoClone, effectiveGroupId, receiver, flags, restricted);
                }
            });
        }

// Add by lizusheng@wind-mobi.com for silead_fingerprint 20150504 begin
        @Override
        public int setScreenStatus(int screenStatus) {
            return sileadSetScreenStatus(screenStatus);
        }

        @Override
        public int setFPEnableCredential(int index, int enable){
            return sileadSetFPEnableCredential(index,enable);
        }

        @Override
        public int getFPEnableCredential(int index){
            return sileadGetFPEnableCredential(index);
        }

        @Override
        public int getFPVirtualKeyCode(){
            return sileadGetFPVirtualKeyCode();
        }

        @Override
        public int setFPVirtualKeyCode(int VirtualKeyCode){
            return sileadSetFPVirtualKeyCode(VirtualKeyCode);
        }

        @Override
        public int getFPLongPressVirtualKeyCode(){
            return sileadGetFPLongPressVirtualKeyCode();
        }

        @Override
        public int setFPLongPressVirtualKeyCode(int VirtualKeyCode){
            return sileadSetFPLongPressVirtualKeyCode(VirtualKeyCode);
        }

        @Override
        public int getFPDouClickVirtualKeyCode(){
            return sileadGetFPDouClickVirtualKeyCode();
        }

        @Override
        public int setFPDouClickVirtualKeyCode(int VirtualKeyCode){
            return sileadSetFPDouClickVirtualKeyCode(VirtualKeyCode);
        }

        @Override
        public int getFPVirtualKeyState(){
            return sileadGetFPVirtualKeyState();
        }

        @Override
        public int setFPVirtualKeyState(int VirtualKeyState){
            return sileadSetFPVirtualKeyState(VirtualKeyState);
        }

        @Override
        public int getFPWakeUpState(){
            return sileadGetFPWakeUpState();
        }

        @Override
        public int setFPWakeUpState(int WakeUpState){
            return sileadSetFPWakeUpState(WakeUpState);
        }

    	@Override
    	public int getFingerPrintState(){
            return sileadGetFingerPrintState();
    	}

        @Override
        public int setFingerPrintState(int FingerPrintState){
            return sileadSetFingerPrintState(FingerPrintState);
        }

        @Override
        public int setFPPowerFuncKeyState(int FuncKeyState){
            return sileadSetFPPowerFuncKeyState(FuncKeyState);
        }

        @Override
        public int getFPPowerFuncKeyState(){
            return sileadGetFPPowerFuncKeyState();
        }

        @Override
        public int setFPIdleFuncKeyState(int FuncKeyState){
            return sileadSetFPIdleFuncKeyState(FuncKeyState);
        }

        @Override
        public int getFPIdleFuncKeyState(){
            return sileadGetFPIdleFuncKeyState();
        }

        @Override
        public int setFPWholeFuncKeyState(int FuncKeyState){
            return sileadSetFPWholeFuncKeyState(FuncKeyState);
        }

        @Override
        public int setFPFunctionKeyState(int index, int enable){
            return sileadSetFPFunctionKeyState(index,enable);
        }

        @Override
        public int getFPFunctionKeyState(int index){
            return sileadGetFPFunctionKeyState(index);
        }
// Add by lizusheng@wind-mobi.com for silead_fingerprint 20150504 end

        private boolean isRestricted() {
            // Only give privileged apps (like Settings) access to fingerprint info
            final boolean restricted = !hasPermission(MANAGE_FINGERPRINT);
            return restricted;
        }

        @Override // Binder call
        public void cancelEnrollment(final IBinder token) {
            checkPermission(MANAGE_FINGERPRINT);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    stopEnrollment(token, true);
                }
            });
        }

        @Override // Binder call
        public void authenticate(final IBinder token, final long opId, final int groupId,
                final IFingerprintServiceReceiver receiver, final int flags,
                final String opPackageName) {
            if (!canUseFingerprint(opPackageName, true /* foregroundOnly */)) {
                if (DEBUG) Slog.v(TAG, "authenticate(): reject " + opPackageName);
                return;
            }

            // Group ID is arbitrarily set to parent profile user ID. It just represents
            // the default fingerprints for the user.
            final int effectiveGroupId = getEffectiveUserId(groupId);

            final boolean restricted = isRestricted();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    MetricsLogger.histogram(mContext, "fingerprint_token", opId != 0L ? 1 : 0);
                    startAuthentication(token, opId, effectiveGroupId, receiver, flags, restricted,
                            opPackageName);
                }
            });
        }

        @Override // Binder call
        public void cancelAuthentication(final IBinder token, String opPackageName) {
            if (!canUseFingerprint(opPackageName, false /* foregroundOnly */)) {
                return;
            }
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    stopAuthentication(token, true);
                }
            });
        }

        @Override // Binder call
        public void remove(final IBinder token, final int fingerId, final int groupId,
                final IFingerprintServiceReceiver receiver) {
            checkPermission(MANAGE_FINGERPRINT); // TODO: Maybe have another permission
            final boolean restricted = isRestricted();

            // Group ID is arbitrarily set to parent profile user ID. It just represents
            // the default fingerprints for the user.
            final int effectiveGroupId = getEffectiveUserId(groupId);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    startRemove(token, fingerId, effectiveGroupId, receiver, restricted);
                }
            });

        }

        @Override // Binder call
        public boolean isHardwareDetected(long deviceId, String opPackageName) {
            if (!canUseFingerprint(opPackageName, false /* foregroundOnly */)) {
                return false;
            }
            return mHalDeviceId != 0;
        }

        @Override // Binder call
        public void rename(final int fingerId, final int groupId, final String name) {
            checkPermission(MANAGE_FINGERPRINT);

            // Group ID is arbitrarily set to parent profile user ID. It just represents
            // the default fingerprints for the user.
            final int effectiveGroupId = getEffectiveUserId(groupId);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mFingerprintUtils.renameFingerprintForUser(mContext, fingerId,
                            effectiveGroupId, name);
                }
            });
        }

        @Override // Binder call
        public List<Fingerprint> getEnrolledFingerprints(int userId, String opPackageName) {
            if (!canUseFingerprint(opPackageName, false /* foregroundOnly */)) {
                return Collections.emptyList();
            }
            int effectiveUserId = getEffectiveUserId(userId);

            return FingerprintService.this.getEnrolledFingerprints(effectiveUserId);
        }

        @Override // Binder call
        public boolean hasEnrolledFingerprints(int userId, String opPackageName) {
            if (!canUseFingerprint(opPackageName, false /* foregroundOnly */)) {
                return false;
            }

            int effectiveUserId  = getEffectiveUserId(userId);
            return FingerprintService.this.hasEnrolledFingerprints(effectiveUserId);
        }

        @Override // Binder call
        public long getAuthenticatorId(String opPackageName) {
            // In this method, we're not checking whether the caller is permitted to use fingerprint
            // API because current authenticator ID is leaked (in a more contrived way) via Android
            // Keystore (android.security.keystore package): the user of that API can create a key
            // which requires fingerprint authentication for its use, and then query the key's
            // characteristics (hidden API) which returns, among other things, fingerprint
            // authenticator ID which was active at key creation time.
            //
            // Reason: The part of Android Keystore which runs inside an app's process invokes this
            // method in certain cases. Those cases are not always where the developer demonstrates
            // explicit intent to use fingerprint functionality. Thus, to avoiding throwing an
            // unexpected SecurityException this method does not check whether its caller is
            // permitted to use fingerprint API.
            //
            // The permission check should be restored once Android Keystore no longer invokes this
            // method from inside app processes.

            return FingerprintService.this.getAuthenticatorId();
        }

        @Override // Binder call
        protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            if (mContext.checkCallingOrSelfPermission(Manifest.permission.DUMP)
                    != PackageManager.PERMISSION_GRANTED) {
                pw.println("Permission Denial: can't dump Fingerprint from from pid="
                        + Binder.getCallingPid()
                        + ", uid=" + Binder.getCallingUid());
                return;
            }

            final long ident = Binder.clearCallingIdentity();
            try {
                dumpInternal(pw);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
        @Override // Binder call
        public void resetTimeout(byte [] token) {
            checkPermission(RESET_FINGERPRINT_LOCKOUT);
            // TODO: confirm security token when we move timeout management into the HAL layer.
            mHandler.post(mResetFailedAttemptsRunnable);
        }

        @Override
        public void addLockoutResetCallback(final IFingerprintServiceLockoutResetCallback callback)
                throws RemoteException {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    addLockoutResetMonitor(
                            new FingerprintServiceLockoutResetMonitor(callback));
                }
            });
        }
		
	/* lizusheng@wind-mobi.com add for AsusNavigation 20160512 start */
		@Override // Binder call
		public void setNavMode(boolean nav) {
			FingerprintService.this.setNavMode(nav);
		}

		@Override // Binder call
		public boolean isNavMode() {
			return FingerprintService.this.isNavMode();
		}

		@Override // Binder call
		public void setTapTimeout(int timeout) {
			FingerprintService.this.setTapTimeout(timeout);
		}

		@Override // Binder call
		public void setDoubleTapMinTime(int min) {
			FingerprintService.this.setDoubleTapMinTime(min);
		}

		@Override // Binder call
        public void setDoubleTapTimeout(int timeout) {
            FingerprintService.this.setDoubleTapTimeout(timeout);
        }

        @Override // Binder call
        public void setLongPressTimeout(int timeout) {
            FingerprintService.this.setLongPressTimeout(timeout);
        }
	/* lizusheng@wind-mobi.com add for AsusNavigation 20160512 end */
    }

    private void dumpInternal(PrintWriter pw) {
        JSONObject dump = new JSONObject();
        try {
            dump.put("service", "Fingerprint Manager");

            JSONArray sets = new JSONArray();
            for (UserInfo user : UserManager.get(getContext()).getUsers()) {
                final int userId = user.getUserHandle().getIdentifier();
                final int N = mFingerprintUtils.getFingerprintsForUser(mContext, userId).size();
                JSONObject set = new JSONObject();
                set.put("id", userId);
                set.put("count", N);
                sets.put(set);
            }

            dump.put("prints", sets);
        } catch (JSONException e) {
            Slog.e(TAG, "dump formatting failure", e);
        }
        pw.println(dump);
    }
	
	 // Add by lizusheng@wind-mobi.com for silead_fingerprint 20150504 begin
    private int sileadSetScreenStatus(int screenStatus) {
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.SetFPScreenStatus(screenStatus);
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE sileadSetScreenStatus occurs exception: ", e);
            }
        }
        return -1;
    }

    private int sileadSetFPEnableCredential(int index, int enable){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.setFPEnableCredential(index,enable);
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE setFPEnableCredential occurs exception: ", e);
            }
        }
        return -1;
    }

    private int sileadGetFPEnableCredential(int index){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.getFPEnableCredential(index);
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE getFPEnableCredential occurs exception: ", e);
            }
        }
        return  0;
    }

    private int sileadGetFPVirtualKeyCode(){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.getFPVirtualKeyCode();
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE getFPVirtualKeyCode occurs exception: ", e);
            }
        }
        return -1;
    }

    private int sileadSetFPVirtualKeyCode(int VirtualKeyCode){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.setFPVirtualKeyCode(VirtualKeyCode);
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE setFPVirtualKeyCode occurs exception: ", e);
            }
        }

         return -1;
    }

    private int sileadGetFPLongPressVirtualKeyCode(){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.getFPLongPressVirtualKeyCode();
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE getFPLongPressVirtualKeyCode occurs exception: ", e);
            }
        }

         return 0;
    }

    private int sileadSetFPLongPressVirtualKeyCode(int VirtualKeyCode){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.setFPLongPressVirtualKeyCode(VirtualKeyCode);
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE setFPLongPressVirtualKeyCode occurs exception: ", e);
            }
        }

         return -1;
    }

    private int sileadGetFPDouClickVirtualKeyCode(){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.getFPDouClickVirtualKeyCode();
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE getFPDouClickVirtualKeyCode occurs exception: ", e);
            }
        }

         return 0;
    }

    private int sileadSetFPDouClickVirtualKeyCode(int VirtualKeyCode){
       IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.setFPDouClickVirtualKeyCode(VirtualKeyCode);
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE setFPDouClickVirtualKeyCode occurs exception: ", e);
            }
        }

         return -1;
    }

    private int sileadGetFPVirtualKeyState(){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.getFPVirtualKeyState();
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE getFPVirtualKeyState occurs exception: ", e);
            }
        }

         return 0;
    }

    private int sileadSetFPVirtualKeyState(int VirtualKeyState){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.setFPVirtualKeyState(VirtualKeyState);
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE setFPVirtualKeyState occurs exception: ", e);
            }
        }

         return -1;
    }

    private int sileadGetFPWakeUpState(){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.getFPWakeUpState();
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE getFPWakeUpState occurs exception: ", e);
            }
        }

         return 0;
    }

    private int sileadSetFPWakeUpState(int WakeUpState){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.setFPWakeUpState(WakeUpState);
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE setFPWakeUpState occurs exception: ", e);
            }
        }

         return -1;
    }

    private int sileadGetFingerPrintState(){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.getFingerPrintState();
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE getFingerPrintState occurs exception: ", e);
            }
        }

         return 0;
    }

    private int sileadSetFingerPrintState(int FingerPrintState){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.setFingerPrintState(FingerPrintState);
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE setFingerPrintState occurs exception: ", e);
            }
        }

         return 0;
    }

   private int sileadSetFPPowerFuncKeyState(int FuncKeyState){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.setFPPowerFuncKeyState(FuncKeyState);
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE setFPPowerFuncKeyState occurs exception: ", e);
            }
        }
        return -1;
    }

    private int sileadGetFPPowerFuncKeyState(){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.getFPPowerFuncKeyState();
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE getFPPowerFuncKeyState occurs exception: ", e);
            }
        }
        return 0;
    }

    private int sileadSetFPIdleFuncKeyState(int FuncKeyState){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.setFPIdleFuncKeyState(FuncKeyState);
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE getFPPowerFuncKeyState occurs exception: ", e);
            }
        }
        return 0;
    }

    private int sileadGetFPIdleFuncKeyState(){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.getFPIdleFuncKeyState();
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE getFPIdleFuncKeyState occurs exception: ", e);
            }
        }
        return 0;
    }

    private int sileadSetFPWholeFuncKeyState(int FuncKeyState){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.setFPWholeFuncKeyState(FuncKeyState);
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE setFPWholeFuncKeyState occurs exception: ", e);
            }
        }
        return 0;
    }

    private int sileadSetFPFunctionKeyState(int index, int enable){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.setFPFunctionKeyState(index,enable);
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE setFPFunctionKeyState occurs exception: ", e);
            }
        }
        return -1;
    }

    private int sileadGetFPFunctionKeyState(int index){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.getFPFunctionKeyState(index);
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE getFPFunctionKeyState occurs exception: ", e);
            }
        }
        return  0;
    }

    private BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Slog.v(TAG, " SLCODE screenReceiver recv action: " + action);
            if(Intent.ACTION_SCREEN_OFF.equals(action)) {
                //setScreenStatus(0);
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                //setScreenStatus(1);
            }
        }
    };
// Add by lizusheng@wind-mobi.com for silead_fingerprint 20150504 end

    @Override
    public void onStart() {
        publishBinderService(Context.FINGERPRINT_SERVICE, new FingerprintServiceWrapper());
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (DEBUG) Slog.v(TAG, "Fingerprint HAL id: " + mHalDeviceId);
        listenForUserSwitches();
// Add by lizusheng@wind-mobi.com for silead_fingerprint 20150504 begin
        if(WIND_FINGERPRINT_SILEAD_ENABLE){
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        mContext.registerReceiver(screenReceiver, filter);
		}
// Add by lizusheng@wind-mobi.com for silead_fingerprint 20150504 end
    }

    private void updateActiveGroup(int userId) {
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                userId = getEffectiveUserId(userId);
                final File systemDir = Environment.getUserSystemDirectory(userId);
                final File fpDir = new File(systemDir, FP_DATA_DIR);
                if (!fpDir.exists()) {
                    if (!fpDir.mkdir()) {
                        Slog.v(TAG, "Cannot make directory: " + fpDir.getAbsolutePath());
                        return;
                    }
                    // Calling mkdir() from this process will create a directory with our
                    // permissions (inherited from the containing dir). This command fixes
                    // the label.
                    if (!SELinux.restorecon(fpDir)) {
                        Slog.w(TAG, "Restorecons failed. Directory will have wrong label.");
                        return;
                    }
                }
                daemon.setActiveGroup(userId, fpDir.getAbsolutePath().getBytes());
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to setActiveGroup():", e);
            }
        }
    }

    private void listenForUserSwitches() {
        try {
            ActivityManagerNative.getDefault().registerUserSwitchObserver(
                new IUserSwitchObserver.Stub() {
                    @Override
                    public void onUserSwitching(int newUserId, IRemoteCallback reply) {
                        mHandler.obtainMessage(MSG_USER_SWITCHING, newUserId, 0 /* unused */)
                                .sendToTarget();
                    }
                    @Override
                    public void onUserSwitchComplete(int newUserId) throws RemoteException {
                        // Ignore.
                    }
                    @Override
                    public void onForegroundProfileSwitch(int newProfileId) {
                        // Ignore.
                    }
                });
        } catch (RemoteException e) {
            Slog.w(TAG, "Failed to listen for user switching event" ,e);
        }
    }

    public long getAuthenticatorId() {
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.getAuthenticatorId();
            } catch (RemoteException e) {
                Slog.e(TAG, "getAuthenticatorId failed", e);
            }
        }
        return 0;
    }
	
	/* lizusheng@wind-mobi.com add for AsusNavigation 20160512 start */
    public void setNavMode(boolean nav) {
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                daemon.setNavMode(nav ? 1 : 0);
            } catch (RemoteException e) {
                Slog.e(TAG, "setMode failed", e);
            }
        }
    }

    public boolean isNavMode() {
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.getMode() == 1 ? true : false;
            } catch (RemoteException e) {
                Slog.e(TAG, "isNavMode failed", e);
            }
        }
        return false;
    }

    public void setTapTimeout(int timeout) {
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                daemon.setTapTimeout(timeout);
            } catch (RemoteException e) {
                Slog.e(TAG, "setTapTimeout failed", e);
            }
        }
    }

    public void setDoubleTapMinTime(int min) {
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                daemon.setDoubleTapMinTime(min);
            } catch (RemoteException e) {
                Slog.e(TAG, "setDoubleTapMinTime failed", e);
            }
        }
    }

    public void setDoubleTapTimeout(int timeout) {
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                daemon.setDoubleTapTimeout(timeout);
            } catch (RemoteException e) {
                Slog.e(TAG, "setDoubleTapTimeout failed", e);
            }
        }
    }

    public void setLongPressTimeout(int timeout) {
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                daemon.setLongPressTimeout(timeout);
            } catch (RemoteException e) {
                Slog.e(TAG, "setLongPressTimeout failed", e);
            }
        }
    }
	/* lizusheng@wind-mobi.com add for AsusNavigation 20160512 end */

}
