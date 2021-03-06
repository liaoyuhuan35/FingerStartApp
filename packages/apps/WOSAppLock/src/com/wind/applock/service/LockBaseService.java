package com.wind.applock.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;

public abstract class LockBaseService extends Service {
	protected Context mContext;

	protected Handler mHandler;

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		initVariables();
		registerObservers();
	}

	protected abstract void initVariables();

	protected abstract void registerObservers();

	protected class LockHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			// TODO Auto-generated method stub
			super.handleMessage(msg);
		}
	}
}
