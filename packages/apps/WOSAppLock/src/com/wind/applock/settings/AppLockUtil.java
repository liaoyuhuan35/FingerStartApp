package com.wind.applock.settings;

import com.wind.applock.Wind;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

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
//        Wind.Log(TAG, "isAppNeedLock context = " + context + ":" + pkg);
        synchronized (lock) {
            SharedPreferences sp = context.getSharedPreferences(PRE_APP_LOCK,
                    Context.MODE_PRIVATE);
            String needLocked = sp.getString(pkg, "");
//            Wind.Log(TAG, "isAppNeedLock needLocked = " + needLocked);
            if (needLocked.equals(pkg)) {
                return true;
            }
            return false;
        }
    }

    /*
     * 标记该package已上锁，不再继续上锁
     */
    private static final String APP_ALREADY_LOCK_FLAG = "AppAlreadyLockFlag";
    public static final int FLAG_APP_IS_UNLOCKED = 1;
    public static final int FLAG_APP_NOT_UNLOCKED = 0;

    public static void clearAppAlreadyUnlocked(Context context) {
        context.getSharedPreferences(APP_ALREADY_LOCK_FLAG,
                Context.MODE_PRIVATE).edit().clear().commit();
    }

    public static boolean isAppAlreadyUnlocked(Context context, String pkg) {
//        Wind.Log(TAG, "isAppAlreadyUnlocked pkg = " + pkg);

        SharedPreferences flagSP = context.getSharedPreferences(
                APP_ALREADY_LOCK_FLAG, Context.MODE_PRIVATE);
        synchronized (lock) {
            int unlocked = flagSP.getInt(pkg, 0);
//            Wind.Log(TAG, "isAppAlreadyUnlocked pkg = " + pkg
//                    + " -- unlocked: " + unlocked);
            if (unlocked == FLAG_APP_IS_UNLOCKED) {
                return true;
            }
            return false;
        }
    }

    /*
     * 设置该App不需要再上锁
     * */
    public static boolean setAppUnlocked(Context context, String pkg) {
        return setAppUnlockedFlag(context, pkg, FLAG_APP_IS_UNLOCKED);
    }

    public static boolean clearAppUnlocked(Context context, String pkg) {
        return setAppUnlockedFlag(context, pkg, FLAG_APP_NOT_UNLOCKED);
    }

    public static boolean setAppUnlockedFlag(Context context, String pkg,
            int flag) {
        Wind.Log(TAG, "setAppUnlockedFlag pkg = " + pkg + ",flag = " + flag);
        SharedPreferences flagSP = context.getSharedPreferences(
                APP_ALREADY_LOCK_FLAG, Context.MODE_PRIVATE);
        SharedPreferences.Editor flagED = flagSP.edit();
        synchronized (lock) {
            int unlocked = flagSP.getInt(pkg, FLAG_APP_NOT_UNLOCKED);
            Wind.Log(TAG, "setAppUnlockedFlag pkg = " + pkg + " -- unlocked: "
                    + unlocked);
            if (unlocked != FLAG_APP_NOT_UNLOCKED) {
                flagED.remove(pkg);
                flagED.commit();
            }
            flagED.putInt(pkg, flag);
            return flagED.commit();
        }
    }
}
