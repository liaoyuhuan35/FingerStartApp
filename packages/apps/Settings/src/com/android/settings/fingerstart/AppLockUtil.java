package com.android.settings.fingerstart;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

public class AppLockUtil {
	private static final String TAG = "AppLockUtil";

	private static final String PRE_APP_LOCK = "PreAppLock";
	private SharedPreferences mPreAppLock;
	private SharedPreferences.Editor editor;

	private static final byte[] lock = new byte[0];

	public AppLockUtil(Context context) {
		mPreAppLock = context.getSharedPreferences(PRE_APP_LOCK,
				Context.MODE_PRIVATE);
		editor = mPreAppLock.edit();
	}

	public boolean getAppLockState(ComponentName componentName) {
		Wind.Log(TAG, "getAppLockState " + componentName.toString());
		String packagename = mPreAppLock.getString(
				componentName.getPackageName(), null);
		String classname = mPreAppLock.getString(componentName.getClassName(),
				null);
		if (componentName.getPackageName().equals(packagename)
				&& componentName.getClassName().equals(classname)) {
			return true;
		}
		return false;

	}

	public void storeLockApp(ComponentName componentName) {
		Wind.Log(TAG, "storeLockApp " + componentName.toString());
		editor.putString(componentName.getPackageName(),
				componentName.getPackageName());
		editor.putString(componentName.getClassName(),
				componentName.getClassName());
		editor.commit();
	}

	public void removeLockApp(ComponentName componentName) {
		Wind.Log(TAG, "removeLockApp " + componentName.toString());
		editor.putString(componentName.getPackageName(), null);
		editor.putString(componentName.getClassName(), null);
		editor.commit();
	}

	public static boolean isAppNeedLock(Context context, String pkg) {
		Wind.Log(TAG, "isAppNeedLock context = " + context + ":" + pkg);
		synchronized (lock) {
			SharedPreferences sp = context.getSharedPreferences(PRE_APP_LOCK,
					Context.MODE_PRIVATE);
			String needLocked = sp.getString(pkg, "");
			Wind.Log(TAG, "isAppNeedLock needLocked = " + needLocked);
			if (needLocked.equals(pkg)) {
				return true;
			}
			return false;
		}
	}
}
