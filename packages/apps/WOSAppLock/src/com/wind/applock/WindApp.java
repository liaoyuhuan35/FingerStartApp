package com.wind.applock;

import java.util.LinkedList;
import java.util.List;


import android.app.Activity;
import android.app.Application;
import android.content.Context;

public class WindApp extends Application {
	private final static String TAG = "WindApp";

	private List<Activity> activityList = new LinkedList<Activity>();
	private static WindApp instance;
	private Context mContext;

	private WindApp(Context context) {
		mContext = context;
	}

	public static WindApp getInstance(Context context) {
		Wind.Log(TAG, "getInstance");
		if (null == instance) {
			synchronized (WindApp.class) {
				instance = new WindApp(context);
			}
		}
		return instance;
	}

	public Context getApplicationContext() {
		return mContext;
	}

	public void addActivity(Activity activity) {
		Wind.Log(TAG, "addActivity");
		activityList.add(activity);
	}

	public void removeActivity(Activity activity) {
		Wind.Log(TAG, "removeActivity");
		activityList.remove(activity);
	}

	public void exitActivitys() {
		Wind.Log(TAG, "exitActivitys");
		try {
			for (Activity activity : activityList) {
				if (activity != null)
					activity.finish();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onLowMemory() {
		Wind.Log(TAG, "onLowMemory");
		super.onLowMemory();
		System.gc();
	}
}

// private MainHallActivity mHallAcitvity =null;
// private IncalluiActivity mIncalluiActivity =null;
// private MusicPlayerActivity mMusicPlayerActivity = null;
// private CallLogActivity mCallLogActivity = null;
// private SmsReaderActivity mSmsReaderActivity = null;
// private ClockPreviewActivity mClockPreviewActivity = null;
// public void exitActivitys(){
// if(mHallAcitvity != null){
// mHallAcitvity.exitThisActivity();
// mHallAcitvity=null;
// }
// if(mIncalluiActivity != null){
// mIncalluiActivity.exitThisActivity();
// mIncalluiActivity = null;
// }
// if(mMusicPlayerActivity != null){
// mMusicPlayerActivity.exitThisActivity();
// mMusicPlayerActivity = null;
// }
// if(mCallLogActivity != null){
// mCallLogActivity.exitThisActivity();
// mCallLogActivity=null;
// }
// if(mSmsReaderActivity != null){
// mSmsReaderActivity.exitThisActivity();
// mSmsReaderActivity = null;
// }
// if(mClockPreviewActivity != null){
// mClockPreviewActivity.exitThisActivity();
// mClockPreviewActivity = null;
// }
//
//
// }
