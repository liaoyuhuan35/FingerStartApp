package com.android.settings.fingerprint;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceScreen;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import android.hardware.fingerprint.Fingerprint;
import android.hardware.fingerprint.FingerprintManager;
import android.hardware.fingerprint.FingerprintManager.AuthenticationCallback;
import android.hardware.fingerprint.FingerprintManager.AuthenticationResult;
import android.hardware.fingerprint.FingerprintManager.RemovalCallback;

import com.android.internal.logging.MetricsLogger;

import com.android.settings.ChooseLockGeneric;
import com.android.settings.ChooseLockSettingsHelper;
import com.android.settings.R;
import com.android.settings.fingerprint.FingerprintSettings.FingerprintPreference;
//import com.android.settings.fingerprint.FingerprintSettings.FingerprintSettingsFragment;
//import com.android.settings.fingerprint.FingerprintSettings.FingerprintSettingsFragment.ConfirmLastDeleteDialog;
//import com.android.settings.fingerprint.FingerprintSettings.FingerprintSettingsFragment.RenameDeleteDialog;
//import com.wind.fingerstart.DemoActivity;
import com.android.settings.fingerstart.FingerStartSetActivity;
import com.android.settings.fingerstart.WOSFinger;
import com.android.settings.fingerstart.Wind;
import com.android.settings.fingerstart.FingerUtil;

public class FingerprintPreferenceActivity extends BasePreferenceActivity
		implements
		// OnPreferenceChangeListener,
		OnPreferenceClickListener {

	private static final String TAG = "wind/FingerprintPreferenceActivity";

	private Context mContext;
	private PreferenceScreen mFingerList = null;
	private PreferenceScreen mRoot = null;
	private CheckBoxPreference mFingerShot = null;
	private CheckBoxPreference mFingerCall = null;
	private Preference mFingerShort = null;
	private Preference mFingerAppLock = null;

	private SharedPreferences mPre;

	private static final String KEY_FINGERPRINT_ITEM_PREFIX = "key_fingerprint_item";
	private static final String KEY_FINGERPRINT_ADD = "KEY_FINGERPRINT_ADD";
	

	private boolean mLaunchedConfirm;
	private static final String KEY_LAUNCHED_CONFIRM = "launched_confirm";

	private static final int CONFIRM_REQUEST = 101;
	private static final int CHOOSE_LOCK_GENERIC_REQUEST = 102;
	
    private static final int ADD_FINGERPRINT_REQUEST = 10;
    
	static final int RESULT_FINISHED = RESULT_FIRST_USER;

	private FingerprintManager mFingerprintManager;

    private CancellationSignal mFingerprintCancel;
    private byte[] mToken;

    private boolean mInFingerprintLockout;

    private static final int MSG_REFRESH_FINGERPRINT_TEMPLATES = 1000;
    private static final int MSG_FINGER_AUTH_SUCCESS = 1001;
    private static final int MSG_FINGER_AUTH_FAIL = 1002;
    private static final int MSG_FINGER_AUTH_ERROR = 1003;
    private static final int MSG_FINGER_AUTH_HELP = 1004;
    
    private static final int MSG_FINGER_RENAME = 2000;

    private static final int FINGERE_MAX = 5;
    private FingerUtil mFingerUtil;

	@Override
	protected void initVariables() {
		mContext = this;
		mFingerprintManager = (FingerprintManager) mContext.getSystemService(Context.FINGERPRINT_SERVICE);
		mPre = mContext.getSharedPreferences(FingerUtil.FINGER_INFO,Context.MODE_PRIVATE);
		mFingerUtil = new FingerUtil(this);
	}

	@Override
	protected void initViews(Bundle savedInstanceState) {
		if (savedInstanceState != null) {
            mToken = savedInstanceState.getByteArray(
                    ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN);
			mLaunchedConfirm = savedInstanceState.getBoolean( KEY_LAUNCHED_CONFIRM, false);
		}
		if (mLaunchedConfirm == false) {
			mLaunchedConfirm = true;
			launchChooseOrConfirmLock();
		}
		
		addPreferencesFromResource(R.layout.wos_fingerprint_preference_activity);

		mRoot = (PreferenceScreen) findPreference(getString(R.string.key_root));
	}
	

    private static String genKey(int id) {
        return KEY_FINGERPRINT_ITEM_PREFIX + "_" + id;
    }
	
    private void addSettingsItemPreferences(PreferenceGroup root){
    	CheckBoxPreference hangon = new CheckBoxPreference(root.getContext());
    	hangon.setKey(getString(R.string.key_fp_call));
    	hangon.setTitle(R.string.fp_function_hangon);
    	root.addPreference(hangon);
		if (hangon != null) {
			hangon.setOnPreferenceClickListener(this);
			hangon.setChecked(fpFunctionCallOpened(mContext));
		}

    	CheckBoxPreference shot = new CheckBoxPreference(root.getContext());
    	shot.setKey(getString(R.string.key_fp_shot));
    	shot.setTitle(R.string.fp_function_shot);
    	root.addPreference(shot);
		if (shot != null) {
			shot.setOnPreferenceClickListener(this);
			shot.setChecked(fpFunctionCameraOpened(mContext));
		}

        Preference applock = new Preference(root.getContext());
        applock.setKey(getString(R.string.key_fp_applock));
        applock.setTitle(R.string.fp_function_applock);
        root.addPreference(applock);
		if (applock != null) {
	        applock.setOnPreferenceClickListener(this);
		}
    }
    
    private void addFingerprintItemPreferences(PreferenceGroup root) {
        root.removeAll();

    	PreferenceCategory functionCategory = new PreferenceCategory(mContext);
    	functionCategory.setTitle(R.string.security_settings_fp_function_preference_title);
    	mRoot.addPreference(functionCategory);
        
        addSettingsItemPreferences(root);
        

    	PreferenceCategory fc = new PreferenceCategory(mContext);
    	fc.setTitle(R.string.str_fingerprint);
    	mRoot.addPreference(fc);
        
        final List<Fingerprint> items = mFingerprintManager.getEnrolledFingerprints();
        final int fingerprintCount = items.size();
        for (int i = 0; i < fingerprintCount; i++) {
            final Fingerprint item = items.get(i);
            FingerprintPreference pref = new FingerprintPreference(root.getContext());
            pref.setKey(genKey(item.getFingerId()));
            pref.setTitle(item.getName());
            pref.setFingerprint(item);
            pref.setPersistent(false);
            final String mPackageName = mPre.getString(WOSFinger.KEY_SELECT_NAME_ + item.getFingerId(), getResources().getString(R.string.lock_string));
            pref.setSummary(mPackageName);
            pref.setOnPreferenceClickListener(this);
            root.addPreference(pref);

//            pref.setOnPreferenceChangeListener(this);
        }
        
        Preference addPreference = new Preference(root.getContext());
        addPreference.setKey(KEY_FINGERPRINT_ADD);
        addPreference.setTitle(R.string.fingerprint_add_title);
        addPreference.setIcon(R.drawable.ic_add_24dp);
        addPreference.setOnPreferenceClickListener(this);
        root.addPreference(addPreference);
//        addPreference.setOnPreferenceChangeListener(this);
        updateAddPreference();
    }
    
    private void updateAddPreference() {
        /* Disable preference if too many fingerprints added */
        final int max = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_fingerprintMaxTemplatesPerUser);
        boolean tooMany = mFingerprintManager.getEnrolledFingerprints().size() >= max;
        CharSequence maxSummary = tooMany ? mContext.getString(R.string.fingerprint_add_max, max) : "";
        Preference addPreference = findPreference(KEY_FINGERPRINT_ADD);
        addPreference.setSummary(maxSummary);
        addPreference.setEnabled(!tooMany);
    }

	@Override
	protected void loadData() {
		Log.e(TAG, "loadData ");
		addFingerprintItemPreferences(mRoot);
	}

	@Override
	public boolean onPreferenceClick(Preference preference) {
		final String key = preference.getKey();
		Log.e(TAG, "onPreferenceClick "+ preference.getKey().toString());
		if (key.equals(getString(R.string.key_fp_shot))) {
			CheckBoxPreference cbp = (CheckBoxPreference) preference;
			final boolean checked = cbp.isChecked();
			Log.e(TAG, getString(R.string.key_fp_shot) + " "+checked);
			setSystemProperties(mContext,PROP_FP_FUNCTION_CAMERA,checked);
		} else if (key.equals(getString(R.string.key_fp_call))) {
			CheckBoxPreference cbp = (CheckBoxPreference) preference;
			final boolean checked = cbp.isChecked();
			Log.e(TAG, getString(R.string.key_fp_call) + " "+checked);
			setSystemProperties(mContext,PROP_FP_FUNCTION_CALL,checked);
		} else if (key.equals(getString(R.string.key_fp_applock))) {
			Log.e(TAG, getString(R.string.key_fp_applock));
			try{
				startActivity(mContext, new ComponentName("com.wind.applock", "com.wind.applock.settings.AppLockSettingsActivity"));
			}catch( ActivityNotFoundException e){
				
			}
		} 
//		else if (key.equals(KEY_FINGERPRINT_ADD)){
//            Intent intent = new Intent();
//            intent.setClassName("com.android.settings",
//                    FingerprintEnrollEnrolling.class.getName());
//            intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN, mToken);
//            startActivityForResult(intent, ADD_FINGERPRINT_REQUEST);
//		}
		return false;
	}
	
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference pref) {
        final String key = pref.getKey();
		Log.e(TAG, "onPreferenceTreeClick "+ pref.getKey().toString());
        if (KEY_FINGERPRINT_ADD.equals(key)) {
            Intent intent = new Intent();
            intent.setClassName("com.android.settings", FingerprintEnrollEnrolling.class.getName());
    		Log.e(TAG, "onPreferenceTreeClick mToken"+ mToken);
            intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN, mToken);
            startActivityForResult(intent, ADD_FINGERPRINT_REQUEST);
        } else if (pref instanceof FingerprintPreference) {
            FingerprintPreference fpref = (FingerprintPreference) pref;
            final Fingerprint fp =fpref.getFingerprint();
            showRenameDeleteDialog(fp);
            return super.onPreferenceTreeClick(preferenceScreen, pref);
        }
        return true;
    }
    
    private void startFingerStartSet(int fingerId) {
		Wind.Log(TAG, "startFingerStartSet");
		Intent intent = new Intent(mContext, FingerStartSetActivity.class);
		intent.putExtra(WOSFinger.KEY_FINGER_ID, fingerId);
		this.startActivityForResult(intent, ADD_FINGERPRINT_REQUEST);
	}
    
    private void showRenameDeleteDialog(final Fingerprint fp) {
        RenameDeleteDialog renameDeleteDialog = new RenameDeleteDialog(mContext);
        Bundle args = new Bundle();
        args.putParcelable("fingerprint", fp);
        renameDeleteDialog.setArguments(args);
//        renameDeleteDialog.setTargetFragment(this, 0);
        renameDeleteDialog.show(getFragmentManager(), RenameDeleteDialog.class.getName());
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
    public void onSaveInstanceState(final Bundle outState) {
        android.util.Log.i(TAG, "onSaveInstanceState mToken=" + mToken);
        outState.putByteArray(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN, mToken);
        outState.putBoolean(KEY_LAUNCHED_CONFIRM, mLaunchedConfirm);
    }
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		
        android.util.Log.i(TAG, "onActivityResult requestCode=" + requestCode + " resultCode="+resultCode);
        retryFingerprint();
		if(requestCode == ADD_FINGERPRINT_REQUEST ){		
			loadData();
			return;
		}
		
		if (requestCode == CHOOSE_LOCK_GENERIC_REQUEST || requestCode == CONFIRM_REQUEST) {
			if (resultCode == RESULT_FINISHED || resultCode == RESULT_OK) {
				// The lock pin/pattern/password was set. Start enrolling!
				if (data != null) {
					mToken = data.getByteArrayExtra(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN);
			        android.util.Log.i(TAG, "onActivityResult mToken=" + mToken );
				}
			}
//			loadData();
		}
		if (mToken == null) {
			// Didn't get an authentication, finishing
			this.finish();
		}
	}
	
    @Override
    public void onPause() {
        super.onPause();
        android.util.Log.i("wind/fingerprint", "onPause ");
        stopFingerprint();
    }
	
	@Override
	protected void onDestroy() {
        android.util.Log.i("wind/fingerprint", "onDestroy ");
		super.onDestroy();
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
    
    public static class FingerprintPreference extends Preference {
        private Fingerprint mFingerprint;
        private View mView;

        public FingerprintPreference(Context context, AttributeSet attrs, int defStyleAttr,
                int defStyleRes) {
            super(context, attrs, defStyleAttr, defStyleRes);
        }
        public FingerprintPreference(Context context, AttributeSet attrs, int defStyleAttr) {
            this(context, attrs, defStyleAttr, 0);
        }

        public FingerprintPreference(Context context, AttributeSet attrs) {
            this(context, attrs, com.android.internal.R.attr.preferenceStyle);
        }

        public FingerprintPreference(Context context) {
            this(context, null);
        }

        public View getView() { return mView; }

        public void setFingerprint(Fingerprint item) {
            mFingerprint = item;
        }

        public Fingerprint getFingerprint() {
            return mFingerprint;
        }

        @Override
        protected void onBindView(View view) {
            super.onBindView(view);
            mView = view;
        }
    };
    
    public void deleteFingerPrint(Fingerprint fingerPrint) {
    	mFingerUtil.removeFingerIdPosition(fingerPrint.getFingerId());
        mFingerprintManager.remove(fingerPrint, mRemoveCallback);
    }

    private void updatePreferences() {
        loadData();
//        retryFingerprint();
    }
    
    public void renameFingerPrint(int fingerId, String newName) {
        mFingerprintManager.rename(fingerId, newName);
        updatePreferences();
    }
    
    private void retryFingerprint() {
        android.util.Log.i("wind/fingerprint", "retryFingerprint ");
//        if (!mInFingerprintLockout) {
            mFingerprintCancel = new CancellationSignal();
            mFingerprintManager.authenticate(null, mFingerprintCancel, 0 /* flags */,
                    mAuthCallback, null);
//        }
    }

    private void stopFingerprint() {
        android.util.Log.i("wind/fingerprint", "stopFingerprint ");
        if (mFingerprintCancel != null && !mFingerprintCancel.isCanceled()) {
            android.util.Log.i("wind/fingerprint", "stopFingerprint cancel");
            mFingerprintCancel.cancel();
        }
        mFingerprintCancel = null;
    }
    
    
    protected void removeFingerprintPreference(int fingerprintId) {
        String name = genKey(fingerprintId);
        Preference prefToRemove = findPreference(name);
        if (prefToRemove != null) {
            if (!getPreferenceScreen().removePreference(prefToRemove)) {
                Log.w(TAG, "Failed to remove preference with key " + name);
            }
        } else {
            Log.w(TAG, "Can't find preference to remove: " + name);
        }
    }
    
    private RemovalCallback mRemoveCallback = new RemovalCallback() {

        @Override
        public void onRemovalSucceeded(Fingerprint fingerprint) {
            mHandler.obtainMessage(MSG_REFRESH_FINGERPRINT_TEMPLATES,
                    fingerprint.getFingerId(), 0).sendToTarget();
        }

        @Override
        public void onRemovalError(Fingerprint fp, int errMsgId, CharSequence errString) {
//            final Activity activity = getActivity();
//            if (activity != null) {
                Toast.makeText(mContext, errString, Toast.LENGTH_SHORT);
//            }
        }
    };
    
    public class ConfirmLastDeleteDialog extends DialogFragment {

        private Fingerprint mFp;

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            mFp = getArguments().getParcelable("fingerprint");
            final AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.fingerprint_last_delete_title)
                    .setMessage(R.string.fingerprint_last_delete_message)
                    .setPositiveButton(R.string.fingerprint_last_delete_confirm,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
//                                    FingerprintSettingsFragment parent
//                                            = (FingerprintSettingsFragment) getTargetFragment();
                                    deleteFingerPrint(mFp);
                                    dialog.dismiss();
                                }
                            })
                    .setNegativeButton(
                            R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            })
                    .create();
            return alertDialog;
        }
    }
    
    public class RenameDeleteDialog extends DialogFragment {
        private Context mContext;
        private Fingerprint mFp;
        private EditText mDialogTextField;
        private String mFingerName;
        private Boolean mTextHadFocus;
        private int mTextSelectionStart;
        private int mTextSelectionEnd;

        public RenameDeleteDialog(Context context) {
            mContext = context;
        }

        public RenameDeleteDialog() {
            super();
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            mFp = getArguments().getParcelable("fingerprint");
            if (savedInstanceState != null) {
                mFingerName = savedInstanceState.getString("fingerName");
                mTextHadFocus = savedInstanceState.getBoolean("textHadFocus");
                mTextSelectionStart = savedInstanceState.getInt("startSelection");
                mTextSelectionEnd = savedInstanceState.getInt("endSelection");
            }
            final AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
                    .setView(R.layout.fingerprint_rename_dialog)
                    .setPositiveButton(R.string.security_settings_fingerprint_enroll_dialog_ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    final String newName =
                                            mDialogTextField.getText().toString();
                                    final CharSequence name = mFp.getName();
                                    if (!newName.equals(name)) {
                                        Log.v(TAG, "rename " + name + " to " + newName);
                                        MetricsLogger.action(mContext,
                                                MetricsLogger.ACTION_FINGERPRINT_RENAME,
                                                mFp.getFingerId());
                                        
                                        Message msg = new Message();
                                        msg.what = MSG_FINGER_RENAME;
                                        msg.arg1 = mFp.getFingerId();
                                        Bundle data = new Bundle();
                                        data.putString("name", newName);
                                        msg.setData(data);
                                        mHandler.sendMessage(msg);
                                    }
                                    dialog.dismiss();
                                }
                            })
                    .setNegativeButton(
                            R.string.security_settings_fingerprint_enroll_dialog_delete,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    onDeleteClick(dialog);
                                }
                            })
                    .setNeutralButton(
                            R.string.fp_function_shotcut,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    startFingerStartSet(mFp.getFingerId());
                                    dialog.dismiss();
                                    }
                                })
                    .create();
            alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialog) {
                    mDialogTextField = (EditText) alertDialog.findViewById(
                            R.id.fingerprint_rename_field);
                    CharSequence name = mFingerName == null ? mFp.getName() : mFingerName;
                    mDialogTextField.setText(name);
                    if (mTextHadFocus == null) {
                        mDialogTextField.selectAll();
                    } else {
                        mDialogTextField.setSelection(mTextSelectionStart, mTextSelectionEnd);
                    }
                }
            });
            if (mTextHadFocus == null || mTextHadFocus) {
                // Request the IME
                alertDialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
            return alertDialog;
        }

        private void onDeleteClick(DialogInterface dialog) {
            Log.v(TAG, "Removing fpId=" + mFp.getFingerId());
            MetricsLogger.action(mContext, MetricsLogger.ACTION_FINGERPRINT_DELETE, mFp.getFingerId());
//            FingerprintSettingsFragment parent
//                    = (FingerprintSettingsFragment) getTargetFragment();
            if (mFingerprintManager.getEnrolledFingerprints().size() > 1) {
                deleteFingerPrint(mFp);
            } else {
                ConfirmLastDeleteDialog lastDeleteDialog = new ConfirmLastDeleteDialog();
                Bundle args = new Bundle();
                args.putParcelable("fingerprint", mFp);
                lastDeleteDialog.setArguments(args);
//                lastDeleteDialog.setTargetFragment(getTargetFragment(), 0);
                lastDeleteDialog.show(getFragmentManager(),
                        ConfirmLastDeleteDialog.class.getName());
            }
            dialog.dismiss();
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            if (mDialogTextField != null) {
                outState.putString("fingerName", mDialogTextField.getText().toString());
                outState.putBoolean("textHadFocus", mDialogTextField.hasFocus());
                outState.putInt("startSelection", mDialogTextField.getSelectionStart());
                outState.putInt("endSelection", mDialogTextField.getSelectionEnd());
            }
        }
    }
    
    private AuthenticationCallback mAuthCallback = new AuthenticationCallback() {
        @Override
        public void onAuthenticationSucceeded(AuthenticationResult result) {
            int fingerId = result.getFingerprint().getFingerId();
            mHandler.obtainMessage(MSG_FINGER_AUTH_SUCCESS, fingerId, 0).sendToTarget();
        }

        @Override
        public void onAuthenticationFailed() {
            mHandler.obtainMessage(MSG_FINGER_AUTH_FAIL).sendToTarget();
        };

        @Override
        public void onAuthenticationError(int errMsgId, CharSequence errString) {
            mHandler.obtainMessage(MSG_FINGER_AUTH_ERROR, errMsgId, 0, errString)
                    .sendToTarget();
        }

        @Override
        public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
            mHandler.obtainMessage(MSG_FINGER_AUTH_HELP, helpMsgId, 0, helpString)
                    .sendToTarget();
        }
    };
    
    
    public final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(android.os.Message msg) {
            switch (msg.what) {
                case MSG_REFRESH_FINGERPRINT_TEMPLATES:
                    removeFingerprintPreference(msg.arg1);
                    updateAddPreference();
                    retryFingerprint();
                break;
                case MSG_FINGER_AUTH_SUCCESS:
                    mFingerprintCancel = null;
//                    highlightFingerprintItem(msg.arg1);
                    retryFingerprint();
                break;
                case MSG_FINGER_AUTH_FAIL:
                    // No action required... fingerprint will allow up to 5 of these
                break;
                case MSG_FINGER_AUTH_ERROR:
//                    handleError(msg.arg1 /* errMsgId */, (CharSequence) msg.obj /* errStr */ );
                break;
                case MSG_FINGER_AUTH_HELP: {
                    // Not used
                }
                break;
                case MSG_FINGER_RENAME:{
                    renameFingerPrint(msg.arg1, msg.getData().getString("name"));
                }break;
            }
        };
    };

}
