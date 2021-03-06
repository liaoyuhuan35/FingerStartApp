package com.wind.applock.test;

import com.wind.applock.Wind;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class MainActivity extends Activity {
    private final static String TAG = "MainActivity";

    private Context mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        Wind.Log(TAG, "onCreate");
        Wind.getAppInfo(mContext);
    }

    @Override
    protected void onResume() {
        Wind.Log(TAG, "onResume");
        // TODO Auto-generated method stub
        super.onResume();
    }
}
