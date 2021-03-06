package com.android.settings.fingerprint;

import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import android.hardware.fingerprint.FingerprintManager;

import com.android.settings.ChooseLockGeneric;
import com.android.settings.ChooseLockSettingsHelper;
import com.android.settings.R;

public class FingerprintUseforActivity extends PreferenceActivity implements
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
		android.util.Log.i("wind/fingerprint", "onCreate");
		mContext = this;
		addPreferencesFromResource(R.layout.activity_fingerprint_usefor);
		initPreferences();

		mFingerprintManager = (FingerprintManager) mContext.getSystemService(Context.FINGERPRINT_SERVICE);

		if (savedInstanceState != null) {
			mLaunchedConfirm = savedInstanceState.getBoolean(KEY_LAUNCHED_CONFIRM, false);
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
		if (!helper.launchConfirmationActivity(CONFIRM_REQUEST,
						getString(R.string.security_settings_fingerprint_preference_title),null, null, challenge)) {
			intent.setClassName("com.android.settings",ChooseLockGeneric.class.getName());
			intent.putExtra(ChooseLockGeneric.ChooseLockGenericFragment.MINIMUM_QUALITY_KEY,
					DevicePolicyManager.PASSWORD_QUALITY_SOMETHING);
			intent.putExtra(ChooseLockGeneric.ChooseLockGenericFragment.HIDE_DISABLED_PREFS,true);
			intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_HAS_CHALLENGE,true);
			intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE,challenge);
			startActivityForResult(intent, CHOOSE_LOCK_GENERIC_REQUEST);
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		android.util.Log.i("wind/fingerprint", "onActivityResult");
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
		CheckBoxPreference unlock = (CheckBoxPreference) findPreference(FingerprintKeyDefs.key_fp_usefor_unlock);
		if (unlock != null) {
			
			if(0 == android.provider.Settings.System.getInt(mContext.getContentResolver(), "ro.finger.unlock",0)){
				unlock.setChecked(false);
			}else{
				unlock.setChecked(true);
			}
			unlock.setOnPreferenceChangeListener(this);
			unlock.setOnPreferenceClickListener(this);
//			unlock.setEnabled(false);
		}

		CheckBoxPreference pay = (CheckBoxPreference) findPreference(FingerprintKeyDefs.key_fp_usefor_pay);
		if (pay != null)
			pay.setEnabled(false);
	}
	
	@Override
	protected void onResume() {
		android.util.Log.i("wind/fingerprint", "onResume");
		initPreferences();
		super.onResume();
	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		android.util.Log.i("wind/fingerprint", "onPreferenceChange");
		return false;
	}

	@Override
	public boolean onPreferenceClick(Preference preference) {
		final String key = preference.getKey();
		android.util.Log.i("wind/fingerprint", "onPreferenceClick "+key);

		if (FingerprintKeyDefs.key_fp_usefor_pay.equals(key)) {

		} else if (key.equals(FingerprintKeyDefs.key_fp_usefor_unlock)) {
			CheckBoxPreference unlock = (CheckBoxPreference) preference;
			if (!unlock.isChecked()) {
				unlock.setChecked(true);
				android.provider.Settings.System.putInt(mContext.getContentResolver(), "ro.finger.unlock", 1);
			} else {
				unlock.setChecked(false);
				android.provider.Settings.System.putInt(mContext.getContentResolver(), "ro.finger.unlock", 0);
			}
			android.util.Log.i("wind/fingerprint", "ro.finger.unlock="+ 
			android.provider.Settings.System.getInt(mContext.getContentResolver(),"ro.finger.unlock", 0));
		}
		return false;
	}
}
