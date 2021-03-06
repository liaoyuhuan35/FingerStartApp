package com.android.settings.fingerprint;

import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;

import android.hardware.fingerprint.FingerprintManager;

import com.android.settings.ChooseLockGeneric;
import com.android.settings.ChooseLockSettingsHelper;
import com.android.settings.R;

public class FingerprintFunctionActivity extends PreferenceActivity implements
		Preference.OnPreferenceChangeListener,
		Preference.OnPreferenceClickListener {

	private boolean mLaunchedConfirm;
	private static final String KEY_LAUNCHED_CONFIRM = "launched_confirm";

	private static final int CONFIRM_REQUEST = 101;
	private static final int CHOOSE_LOCK_GENERIC_REQUEST = 102;
	static final int RESULT_FINISHED = RESULT_FIRST_USER;

	private FingerprintManager mFingerprintManager;
	private byte[] mToken;

	private Context mContext;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mContext = this;
		addPreferencesFromResource(R.layout.activity_fingerprint_function);
		initPreferences();

		mFingerprintManager = (FingerprintManager) mContext.getSystemService(Context.FINGERPRINT_SERVICE);

		if (savedInstanceState != null) {
			mLaunchedConfirm = savedInstanceState.getBoolean( KEY_LAUNCHED_CONFIRM, false);
		}

		if (mLaunchedConfirm == false) {
			mLaunchedConfirm = true;
			launchChooseOrConfirmLock();
		}
	}

	private void launchChooseOrConfirmLock() {
		Intent intent = new Intent();
		long challenge = mFingerprintManager.preEnroll();
		ChooseLockSettingsHelper helper = new ChooseLockSettingsHelper(this);
		if (!helper.launchConfirmationActivity( CONFIRM_REQUEST, 
						getString(R.string.security_settings_fingerprint_preference_title), null, null, challenge)) {
			intent.setClassName("com.android.settings", ChooseLockGeneric.class.getName());
			intent.putExtra( ChooseLockGeneric.ChooseLockGenericFragment.MINIMUM_QUALITY_KEY,
					DevicePolicyManager.PASSWORD_QUALITY_SOMETHING);
			intent.putExtra( ChooseLockGeneric.ChooseLockGenericFragment.HIDE_DISABLED_PREFS, true);
			intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_HAS_CHALLENGE, true);
			intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE, challenge);
			startActivityForResult(intent, CHOOSE_LOCK_GENERIC_REQUEST);
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == CHOOSE_LOCK_GENERIC_REQUEST
				|| requestCode == CONFIRM_REQUEST) {
			if (resultCode == RESULT_FINISHED || resultCode == RESULT_OK) {
				// The lock pin/pattern/password was set. Start enrolling!
				if (data != null) {
					mToken = data.getByteArrayExtra(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN);
				}
			}
		}

		if (mToken == null) {
			// Didn't get an authentication, finishing
			this.finish();
		}
	}

	private void initPreferences() {
		CheckBoxPreference hangon = (CheckBoxPreference) findPreference(FingerprintKeyDefs.key_fp_function_hangon);
		if (hangon != null) {
			hangon.setOnPreferenceClickListener(this);
			hangon.setChecked(fpFunctionCallOpened(mContext));
		}

		CheckBoxPreference shot = (CheckBoxPreference) findPreference(FingerprintKeyDefs.key_fp_function_shot);
		if (shot != null) {
			shot.setOnPreferenceClickListener(this);
			shot.setChecked(fpFunctionCameraOpened(mContext));
		}

		Preference shotcut = findPreference(FingerprintKeyDefs.key_fp_function_shotcut);
		if (shotcut != null){
			shotcut.setOnPreferenceClickListener(this);
		}

		Preference directionkey = findPreference(FingerprintKeyDefs.key_fp_function_direction_key);
		if (directionkey != null){
			directionkey.setOnPreferenceClickListener(this);
		}

		Preference applock = findPreference(FingerprintKeyDefs.key_fp_function_applock);
		if (applock != null){
			applock.setOnPreferenceClickListener(this);
		}
	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		return false;
	}

	@Override
	public boolean onPreferenceClick(Preference preference) {
		final String key = preference.getKey();
        android.util.Log.i("wind/fingerprint", "onPreferenceClick "+key);

		if (FingerprintKeyDefs.key_fp_function_hangon.equals(key) && preference instanceof CheckBoxPreference) {
			CheckBoxPreference cbp = (CheckBoxPreference) preference;
			cbp.setChecked(!cbp.isChecked());
			setSystemProperties(mContext,PROP_FP_FUNCTION_CALL,cbp.isChecked());
		} else if (FingerprintKeyDefs.key_fp_function_shot.equals(key) && preference instanceof CheckBoxPreference) {
			CheckBoxPreference cbp = (CheckBoxPreference) preference;
			cbp.setChecked(!cbp.isChecked());
			setSystemProperties(mContext,PROP_FP_FUNCTION_CAMERA,cbp.isChecked());
		} else if (FingerprintKeyDefs.key_fp_function_shotcut.equals(key)) {
			startActivity(mContext, new ComponentName("com.wind.fingerstart", "com.wind.fingerstart.DemoActivity"));
		} else if (FingerprintKeyDefs.key_fp_function_direction_key.equals(key)) {

		} else if(FingerprintKeyDefs.key_fp_function_applock.equals(key)){
			try{
				startActivity(mContext, new ComponentName("com.wind.applock", "com.wind.applock.settings.AppLockSettingsActivity"));
			}catch( ActivityNotFoundException e){
				
			}
		}

		return false;
	}


	private static final String PROP_FP_FUNCTION_CAMERA = "prop.fp.function.camera";
	private static final String PROP_FP_FUNCTION_CALL = "prop.fp.function.call";

	public void setSystemProperties(Context context, String property,
			boolean value) {
		if (value) {
			android.provider.Settings.System.putInt( context.getContentResolver(), property, 1);
		} else {
			android.provider.Settings.System.putInt( context.getContentResolver(), property, 0);
		}
	}
	public boolean fpFunctionCameraOpened(Context context){
	    return android.provider.Settings.System.getInt(context.getContentResolver(), PROP_FP_FUNCTION_CAMERA, 0) == 1;
	}
	public boolean fpFunctionCallOpened(Context context){
	    return android.provider.Settings.System.getInt(context.getContentResolver(), PROP_FP_FUNCTION_CALL, 0) == 1;
	}
	
    protected void startActivity(Context context, ComponentName componentName) {
        android.util.Log.i("wind/fingerprint", "startActivity componentName=" + componentName.toString());
        Intent intent = new Intent();
        intent.setComponent(componentName);
//        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            android.util.Log.i("wind/fingerprint", "startActivity error=" + e.toString());
        }
    }
}
