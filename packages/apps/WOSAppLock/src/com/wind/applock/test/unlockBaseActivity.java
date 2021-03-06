package com.wind.applock.test;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;

import com.wind.applock.Wind;
import com.wind.applock.activity.BaseActivity;
import com.wind.applock.util.AppLockUtils;

import android.app.KeyguardManager;

import com.android.internal.widget.LockPatternChecker;
import com.android.internal.widget.LockPatternUtils;

public abstract class unlockBaseActivity extends BaseActivity {
	private static final String TAG = "unlockBaseActivity";
	protected Context mContext;

	protected AsyncTask<?, ?, ?> mPendingLockCheck; // check lock

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		initViews();
		initServices();
		initLocks();
	}

	@Override
	public void onResume() {
		super.onResume();
		Wind.Log(TAG, "$$$$ onResume $$$$ ");
	}

	@Override
	public void onPause() {
		super.onPause();

		// 退出当前activity时，取消校验
		if (mPendingLockCheck != null) {
			mPendingLockCheck.cancel(false);
			mPendingLockCheck = null;
		}
		Wind.Log(TAG, "$$$$ onPause $$$$ ");
	}

	@Override
	public void onBackPressed() {
		Wind.Log(TAG, "onBackPressed");
//		backToHome(mContext);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Wind.Log(TAG, "$$$$ onDestroy $$$$ ");
	}

	protected abstract void initLocks();

	protected abstract void initViews();

	protected abstract void initServices();

	protected abstract void unlock();

}
