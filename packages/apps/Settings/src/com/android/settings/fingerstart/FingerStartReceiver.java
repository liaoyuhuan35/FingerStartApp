package com.android.settings.fingerstart;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

public class FingerStartReceiver extends BroadcastReceiver {
    private static final String TAG = "FingerStartReceiver";
    private Context mContext;

    @Override
    public void onReceive(Context context, Intent intent) {
        mContext = context;
        String action = intent.getAction();
        Wind.Log(TAG, "onReceive " + action);
        if (WOSFinger.FINGER_ACTION_START.equals(action)) {
            parseFingerMSG(intent);
        }
    }
    
    protected void parseFingerMSG(Intent intent){
        boolean mbResult = intent.getBooleanExtra(WOSFinger.KEY_FINGER_RESULT, false) ;
        if(mbResult){
            int fingerID = intent.getIntExtra(WOSFinger.KEY_FINGER_ID, -1);
            if (WOSFinger.isFingerIdValid(fingerID)) {
                FingerUtil fingerUtil = new FingerUtil(
                        mContext.getApplicationContext());
                ComponentName componentName = fingerUtil
                        .getComponentNameByFingerId(fingerID);
                if (componentName != null) {
                    startActivity(mContext, componentName);
                }
            }
        }
    }

    protected void startActivity(Context context, ComponentName componentName) {
        Wind.Log(TAG, "startActivity componentName=" + componentName.toString());
        Intent intent = new Intent();
        intent.setComponent(componentName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Wind.Log(TAG, "startActivity error=" + e.toString());
        }
    }

    protected void startActivity(Context context, String pkg,
            String activityName) {
        Wind.Log(TAG, "startActivity pkg=" + pkg + "   activityName="
                + activityName);
        startActivity(context, new ComponentName(pkg, activityName));
    }
}
