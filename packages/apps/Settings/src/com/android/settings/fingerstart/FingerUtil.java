package com.android.settings.fingerstart;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

public class FingerUtil {
    private static final String TAG = "FingerUtil";

    public static final String FINGER_INFO = WOSFinger.KEY_FINGER_INFO;
    public static SharedPreferences mFingerInfo;
    private SharedPreferences.Editor editor;

    private static final byte[] lock = new byte[0];

    public FingerUtil(Context context) {
        mFingerInfo = context.getSharedPreferences(FINGER_INFO,
                Context.MODE_PRIVATE);
        editor = mFingerInfo.edit();
    }

    public ComponentName getComponentNameByFingerId(int nFingerId) {
        Wind.Log(TAG, "getComponentNameByFingerId " + nFingerId);
        String packagename = mFingerInfo.getString(WOSFinger.KEY_FINGER_PKG
                + nFingerId, null);
        String classname = mFingerInfo.getString(WOSFinger.KEY_FINGER_ACTIVITY
                + nFingerId, null);

        if (packagename != null && classname != null)
            return new ComponentName(packagename, classname);
        else
            return null;
    }

    public void storeFingerIdStatus(int nFingerId, ComponentName componentName) {
        Wind.Log(TAG, "storeFingerIdStatus nFingerId=" + nFingerId
                + " componentName=" + componentName.toString());
        editor.putString(WOSFinger.KEY_FINGER_PKG + nFingerId,
                componentName.getPackageName());
        editor.putString(WOSFinger.KEY_FINGER_ACTIVITY + nFingerId,
                componentName.getClassName());
        editor.commit();
    }

    public void storeFingerIdPosition(int nFingerId, int position, String name) {
        Wind.Log(TAG, "storeFingerIdPosition position=" + position+" ,nFingerId="+nFingerId+" ,name="+name);
        editor.putInt(WOSFinger.KEY_FINGET_RESULT_ID_+nFingerId, position);
        editor.putString(WOSFinger.KEY_SELECT_NAME_+nFingerId, name);
        editor.commit();
    }

    public void removeFingerIdStatus(int nFingerId) {
        Wind.Log(TAG, "removeLockApp nFingerId=" + nFingerId);
        editor.putString(WOSFinger.KEY_FINGER_PKG + nFingerId, null);
        editor.putString(WOSFinger.KEY_FINGER_ACTIVITY + nFingerId, null);
        editor.commit();
    }

    // public static boolean isAppNeedLock(Context context, String pkg) {
    // Wind.Log(TAG, "isAppNeedLock context = " + context + ":" + pkg);
    // synchronized (lock) {
    // SharedPreferences sp = context.getSharedPreferences(FINGER_INFO,
    // Context.MODE_PRIVATE);
    // String needLocked = sp.getString(pkg, "");
    // Wind.Log(TAG, "isAppNeedLock needLocked = " + needLocked);
    // if (needLocked.equals(pkg)) {
    // return true;
    // }
    // return false;
    // }
    // }
}
