package com.wind.applock.activity;

import com.wind.applock.FingerPubDefs;
import com.wind.applock.GlobalVars;
import com.wind.applock.ILockResult;
import com.wind.applock.R;
import com.wind.applock.Wind;
import com.wind.applock.FingerprintHelper;
import com.wind.applock.fragments.PINCheckFragment;
import com.wind.applock.fragments.PasswdCheckFragment;
import com.wind.applock.fragments.PatternCheckFragment;
import com.wind.applock.passwd.KeyguardPasswordView;
import com.wind.applock.pattern.KeyguardPatternView;
import com.wind.applock.pin.PINCheckView;
import com.wind.applock.service.AppLockService;
import com.wind.applock.settings.AppLockUtil;
import com.wind.applock.util.LockStyleUtil;

import android.hardware.fingerprint.FingerprintManager;

import android.annotation.TargetApi;
import android.app.Activity;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewGroup.LayoutParams;
import android.view.View;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

public class AppLockActivity extends BaseActivity implements ILockResult,FingerprintHelper.Callback {
    private static final String TAG = "AppLockActivity";
    protected Context mContext;
    protected LockStyleUtil mLockStyleUtil;
    protected AppLockUtil mAppLockUtil;

    protected Fragment mPINCheckFragment;
    protected Fragment mPasswdCheckFragment;
    protected Fragment mPatternCheckFragment;
    protected FragmentManager mFragmentManager;

    protected String mLockPkg;
    
    private FingerprintHelper mFingerprintHelper;
    FingerprintHelper.FingerprintHelperBuilder mFingerprintHelperBuilder;
    private FingerprintManager.CryptoObject mCryptoObject;

    private BroadcastReceiver mFingerReceiver;
    private static final String MSG_FINGER_SUCCESS = "com.wind.msg.fingerprintservice";


    protected void initLockParams() {
        Wind.Log(TAG, "initLockParams");
        
        //set activity top
        Wind.Log(TAG, "set activity top");
        Window window = getWindow();
        window.setType(WindowManager.LayoutParams.TYPE_STATUS_BAR);
        
        mPasswdCheckFragment = new PasswdCheckFragment();
        mPatternCheckFragment = new PatternCheckFragment();
        mPINCheckFragment = new PINCheckFragment();
        initFragmentsLockCallBack(this);
        final String nullstr = mContext.getResources().getString(R.string.fp_fingerprint_emptystr);
        resetFragmentsMsg(nullstr);
        
        mFragmentManager = getSupportFragmentManager();
    }
    
    protected void initFragmentsLockCallBack(ILockResult call){
        if (null != mPasswdCheckFragment){
            ((PasswdCheckFragment) mPasswdCheckFragment).setLockCallback(call);
        }
        if (null != mPatternCheckFragment)
            ((PatternCheckFragment) mPatternCheckFragment).setLockCallback(call);
        if (null != mPINCheckFragment)
            ((PINCheckFragment) mPINCheckFragment).setLockCallback(call);
    }

    protected void initLockFragment() {
        Wind.Log(TAG, "initLockFragment");
        mLockStyleUtil = LockStyleUtil.getInstance(mContext);
        int nLockStyle = mLockStyleUtil.getCurrentLockStyle();
        if (nLockStyle == LockStyleUtil.KEY_UNLOCK_SET_PASSWORD) {
            replaceFragment(mPasswdCheckFragment);
        } else if (nLockStyle == LockStyleUtil.KEY_UNLOCK_SET_PIN) {
            replaceFragment(mPINCheckFragment);
        } else if (nLockStyle == LockStyleUtil.KEY_UNLOCK_SET_PATTERN) {
            replaceFragment(mPatternCheckFragment);
        } else {
            mLockStyleUtil.resetNewPasswd(mContext);
        }
    }

    protected void replaceFragment(Fragment fragment) {
        Wind.Log(TAG, "replaceFragment fragment=" + fragment);
        if (fragment != null) {
            FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();
            fragmentTransaction.replace(R.id.id_container, fragment);
            fragmentTransaction.addToBackStack(null);
            fragmentTransaction.commit();
        }
    }
    
    protected void initVariables(){
        mContext = this;
        mAppLockUtil = new AppLockUtil(mContext);
    }
    protected void initViews(Bundle savedInstanceState){
		setContentView(R.layout.activity_app_lock);
		initLockParams();
    }
    protected void loadData(){
        mFingerprintHelperBuilder = new FingerprintHelper.FingerprintHelperBuilder(getSystemService(FingerprintManager.class));
        mFingerprintHelper = mFingerprintHelperBuilder.build(this);

		mFingerReceiver = new FingerReceiver();
		IntentFilter filter = new IntentFilter();
		filter.addAction(MSG_FINGER_SUCCESS);
		registerReceiver(mFingerReceiver, filter);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Wind.Log(TAG, "onCreate mLockPkg=" + mLockPkg);
        initVariables();
        initViews(savedInstanceState);
        loadData();
    }

    @Override
    protected void onRestart() {
        Wind.Log(TAG, "onRestart");
        super.onRestart();
    }

    @Override
    protected void onStart() {
        Wind.Log(TAG, "onStart");
        super.onStart();
    }

    @Override
    protected void onPause() {
        Wind.Log(TAG, "onPause");
        mFingerprintHelper.stopListening();
        setSystemProperties(this,PROP_FP_FUNCTION_START_APP,true);
        super.onPause();
    }

    @Override
    protected void onResume() {
        setSystemProperties(this,PROP_FP_FUNCTION_START_APP,false);
        mLockPkg = GlobalVars.getInstance().getLockAppName();
        initLockFragment();
        mFingerprintHelper.startListening(mCryptoObject);
        Wind.Log(TAG, "onResume mLockPkg=" + mLockPkg);
        super.onResume();
    }
    

	@Override
	protected void onDestroy() {
    	mContext.unregisterReceiver(mFingerReceiver);
		super.onDestroy();
	}

    @Override
    public void onBackPressed() {
        Wind.Log(TAG, "onBackPressed");
        backToHome(mContext);
    }

    @Override
    public void lock() {
        Wind.Log(TAG, "onBackPressed");
    }

    @Override
    public void unlock() {
        Wind.Log(TAG, "unlock mLockPkg=" + mLockPkg);

        mAppLockUtil.setAppUnlocked(mContext, mLockPkg);
        this.finish();
    }

    @Override
    public boolean callKeycode(int keyCode) {
        Wind.Log(TAG, "callKeycode keyCode=" + keyCode);
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            backToHome(mContext);
            return true;
        }
        return false;
    }

    protected void backToHome(Context context) {
        Wind.Log(TAG, "backToHome  sleep 300");
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        startActivity(home);
        try {
            Thread.sleep(300);
        } catch (Exception e) {
            Wind.Log(TAG, "backToHome  sleep " + e);
        } finally {
            finish();
        }
    }

    private class FingerReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Wind.Log(TAG, "handleMessage " + action);
            if(MSG_FINGER_SUCCESS.equals(action)){
                int fingerid = intent.getIntExtra("fingerid", 0);
                Wind.Log(TAG, "handleMessage fingerid=" + fingerid);
                unlock();
            }
        }

    }

	private static final String PROP_FP_FUNCTION_START_APP = "prop.fp.function.startapp";

	public void setSystemProperties(Context context, String property, boolean value) {
		if (value) {
			android.provider.Settings.System.putInt( context.getContentResolver(), property, 1);
		} else {
			android.provider.Settings.System.putInt( context.getContentResolver(), property, 0);
		}
	}

	@Override
	public void onAuthenticated() {
        Wind.Log(TAG, "onAuthenticated" );
	}


	@Override
	public void onError() {
        Wind.Log(TAG, "onError" );
	}


	@Override
	public void onAuthenticationFailed() {
        Wind.Log(TAG, "onAuthenticationFailed" );
        final String autherfail = mContext.getResources().getString(R.string.fp_fingerprint_not_match);
        resetFragmentsMsg(autherfail);
	}
	
	public void resetFragmentsMsg(String msg){
        if (null != mPasswdCheckFragment)
            ((PasswdCheckFragment) mPasswdCheckFragment).setMessageDisplay(msg);
        if (null != mPatternCheckFragment)
            ((PatternCheckFragment) mPatternCheckFragment).setMessageDisplay(msg);
        if (null != mPINCheckFragment)
            ((PINCheckFragment) mPINCheckFragment).setMessageDisplay(msg);
	}

	@Override
	public void onAuthenticationSucceeded() {
        Wind.Log(TAG, "onAuthenticationSucceeded" );
		unlock();
	}

	@Override
	public void onAuthenticationError(int errMsgId, CharSequence errString) {
        Wind.Log(TAG, "onAuthenticationError errMsgId="+errMsgId + " " +errString.toString());
		switch (errMsgId) {
		case FingerPubDefs.FINGERPRINT_ERROR_LOCKOUT: {//error count too much
			MyToast(mContext, errString.toString());
			backToHome(mContext);
			
	        //clear attemps counts
			mContext.sendBroadcast(new Intent(FingerPubDefs.ACTION_LOCKOUT_RESET));
		}
			break;
		default:
			break;
		}
	}

	@Override
	public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
        Wind.Log(TAG, "onAuthenticationHelp helpMsgId="+helpMsgId + " " +helpString.toString());
	}

	@Override
	public void errorTooManyAttempts() {
		final String errStr = mContext.getResources().getString(R.string.fp_too_many_failed_attempts_countdown);
		MyToast(mContext, errStr);
        backToHome(mContext);
	}
}
