package com.wind.applock.service;

import com.wind.applock.FingerPubDefs;
import com.wind.applock.GlobalVars;
import com.wind.applock.R;
import com.wind.applock.Wind;
import com.wind.applock.WindApp;
import com.wind.applock.activity.AppLockActivity;
import com.wind.applock.settings.AllAppInfos;
import com.wind.applock.settings.AppLockUtil;
import com.wind.applock.test.ConfirmPasswordActivity;
import com.wind.applock.util.LockStyleUtil;

import android.app.IProcessObserver;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.LinearLayout;

public class AppLockService extends LockBaseService implements Handler.Callback {
	private static final String TAG = "AppLockService";

	private static final byte[] lock = new byte[0];
	private ActivityManager mActivityManager;

	private static final int SHOW_PASSWORD_ACTIVITY_DELAY = 10;
	private int mCurrentPid = -1;

	// appLockUI
	WindowManager.LayoutParams mParams;

	// 鍙栧緱褰撳墠閿佸睆鏂瑰紡
	protected LockStyleUtil mLockStyleUtil;
	protected AppLockUtil mAppLockUtil;

	private BroadcastReceiver mAppLockReceiver;
	private String mRunningTopPackage;

	// 璇ュ簲鐢ㄦ槸鍚﹂渶瑕佷笂閿�
    protected boolean isAppNeedLock(String pkg) {
        boolean isNeedLock = mAppLockUtil.isAppNeedLock(mContext, pkg);
        boolean isAlreadUnlocked = mAppLockUtil.isAppAlreadyUnlocked(mContext,
                pkg);
        return isNeedLock && !isAlreadUnlocked;
    }
    protected boolean isAppNeedLockLog(String pkg) {
        boolean isNeedLock = mAppLockUtil.isAppNeedLock(mContext, pkg);
        boolean isAlreadUnlocked = mAppLockUtil.isAppAlreadyUnlocked(mContext,
                pkg);
        Wind.Log(TAG, "isAppNeedLock pkg=" + pkg + " isNeedLock=" + isNeedLock
                + " !isAlreadUnlocked=" + !isAlreadUnlocked);
        return isNeedLock && !isAlreadUnlocked;
    }

	// 搴旂敤閿佸紑鍏虫槸鍚︽墦寮�
	protected boolean isAppLockOn() {
		return true;
	}

	@Override
	protected void initVariables() { 
		Wind.Log(TAG, "initVariables ");
		mContext = this;
		mHandler = new LockHandler();
		mActivityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		mAppLockUtil = new AppLockUtil(this.getApplicationContext());
		
		mAppLockUtil.clearAppAlreadyUnlocked(mContext);
		WindApp.getInstance(mContext.getApplicationContext());

		mAppLockReceiver = new AppLockReceiver();
		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_SCREEN_OFF);
		registerReceiver(mAppLockReceiver, filter);
	}

	protected void initNewLayoutParams() {
		Wind.Log(TAG, "initNewLayoutParams ");
		mParams = new WindowManager.LayoutParams();
		mParams.type = WindowManager.LayoutParams.TYPE_PHONE;
		mParams.flags = LayoutParams.FLAG_NOT_TOUCH_MODAL
				// | LayoutParams.FLAG_NOT_FOCUSABLE
				| LayoutParams.FLAG_TRANSLUCENT_STATUS
				| LayoutParams.FLAG_LAYOUT_IN_SCREEN
				| LayoutParams.FLAG_LAYOUT_NO_LIMITS
				;
		mParams.width = LayoutParams.MATCH_PARENT;
	}

	@Override
	protected void registerObservers() {
		Wind.Log(TAG, "registerObservers ");
		try {
			ActivityManagerNative.getDefault().registerProcessObserver(
					new IProcessObserver.Stub() {

						@Override
						public void onForegroundActivitiesChanged(int pid,
								int uid, boolean foregroundActivities)
								throws RemoteException {
							mRunningTopPackage = mActivityManager.getRunningTasks(1).get(0).topActivity.getPackageName();
							Wind.Log(TAG, "onForegroundActivitiesChanged runintTopPackage=" + mRunningTopPackage + " foregroundActivities="+foregroundActivities);
							
							//check package
							if(mRunningTopPackage.equals("com.wind.applock")){
								return;
							}
							
							synchronized (lock) {
									mHandler.post(mFPKeyCodeRunable);
									mHandler.post(mShowPwdRunnable);
							}
						}

						@Override
						public void onProcessStateChanged(int pid, int uid,
								int procState) throws RemoteException {
						}

						@Override
						public void onProcessDied(int pid, int uid)
								throws RemoteException {
						}

					});
		} catch (RemoteException e) {
			Wind.Log(TAG, "registerObservers " + e.toString());
		}
	}
	protected Runnable mShowPwdRunnable = new Runnable() {
		public void run() {
			synchronized (lock) {
				Wind.Log(TAG, "mShowPwdRunnable ");
				//checklockstyle
                boolean isCurStyleNeedLock = LockStyleUtil.getInstance(mContext).isCurrentLockSupportAppLock();
                if(!isCurStyleNeedLock)
                    return;
				
				Wind.Log(TAG, "mShowPwdRunnable runningTopPackage="+mRunningTopPackage);
				if (isAppNeedLock(mRunningTopPackage) && isAppLockOn()) {
				    startLockActivity(mRunningTopPackage);
				}else{
					Wind.Log(TAG, "mShowPwdRunnable isAppNeedLock(runningTopPackage)="+isAppNeedLockLog(mRunningTopPackage));
				}
			}
		}
	};
	
	

	
	protected Runnable mFPKeyCodeRunable = new Runnable() {
		public void run() {
			synchronized (lock) {
				Wind.Log(TAG, "mFPKeyCodeRunable");
				if(PKG_CAMERA.equals(mRunningTopPackage) || PKG_CAMERA2.equals(mRunningTopPackage)){
					putFingerKeyCode(KeyEvent.KEYCODE_CAMERA);
				}else if (PKG_PHONE.equals(mRunningTopPackage)|| PKG_PHONE2.equals(mRunningTopPackage)){
					putFingerKeyCode(KeyEvent.KEYCODE_CALL);
				}else{
					putFingerKeyCode(KeyEvent.KEYCODE_F10);
				}
			}
		}
	};
    private void putFingerKeyCode(int nKeycode){
        android.provider.Settings.System.putInt(mContext.getContentResolver(), "wos.finger.enrollment", nKeycode);
    }

	public final static String PKG_CAMERA	 = "com.asus.camera";
	public final static String PKG_CAMERA2	 = "com.mediatek.camera";
	public final static String PKG_PHONE	 = "com.asus.contacts";
	public final static String PKG_PHONE2	 = "com.android.dialer";

    protected void startLockActivity(String topPackageName) {
        Wind.Log(TAG, "startLockActivity  packageName: " +topPackageName);
        //clear attemps counts
		mContext.sendBroadcast(new Intent(FingerPubDefs.ACTION_LOCKOUT_RESET));

        //start activity
        Intent intent = new Intent(mContext,AppLockActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        GlobalVars.getInstance().updateLockAppName(topPackageName);
        startActivity(intent);
    }

	@Override
	public boolean handleMessage(Message msg) {
		Wind.Log(TAG, "handleMessage " + msg.what);
		switch (msg.what) {
		default:
			break;
		}
		return false;
	}


	private class AppLockReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			Wind.Log(TAG, "handleMessage " + intent.getAction());
			
			if(Intent.ACTION_SCREEN_OFF.equals(intent.getAction())){
			    mAppLockUtil.clearAppAlreadyUnlocked(mContext);
			}
		}

	}
}
