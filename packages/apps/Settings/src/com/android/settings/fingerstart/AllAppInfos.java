package com.android.settings.fingerstart;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

public class AllAppInfos {
	public static final String APP_LOCK_PACKAGE_NAME = "com.wind.fingerstart";
	
	private static final String TAG = "hyy AllAppInfos";
	private String only_unlock_activityName = "OnlyUnlock";

	private Context mContext;
	private ArrayList<AppInfo> mApplist;

	public AllAppInfos(Context context) {
		mContext = context;
		mApplist = new ArrayList<AppInfo>();
		getAllAppInfosByPM();
	}

	private void getAllAppInfosByPM() {
		Wind.Log(TAG, "getAllAppInfosByPM");

		int N = Integer.MAX_VALUE;
		final PackageManager pm = mContext.getPackageManager();
		final Intent intent = new Intent(Intent.ACTION_MAIN, null);
		intent.addCategory(Intent.CATEGORY_LAUNCHER);

		List<ResolveInfo> apps = null;
		apps = pm.queryIntentActivities(intent, 0);
		N = apps.size();
		Wind.Log(TAG, "getAllAppInfosByPM +++++++N ="+N);
		mApplist.add(new AppInfo(mContext, APP_LOCK_PACKAGE_NAME, only_unlock_activityName));
		for (int i = 0; i < N; i++) {
			Wind.Log(TAG, "getAllAppInfosByPM " + apps.get(i).activityInfo.applicationInfo.packageName);
			if (!apps.get(i).activityInfo.applicationInfo.packageName.equals(APP_LOCK_PACKAGE_NAME)) {
				mApplist.add(new AppInfo(mContext, apps.get(i)));
			} else {
				Wind.Log(TAG, "getAllAppInfosByPM remove " + APP_LOCK_PACKAGE_NAME);
			}
		}
	}

	public ArrayList<AppInfo> getAppsInfos() {
		Wind.Log(TAG, "getAppsInfos " + mApplist.size());
		return mApplist;
	}

}
