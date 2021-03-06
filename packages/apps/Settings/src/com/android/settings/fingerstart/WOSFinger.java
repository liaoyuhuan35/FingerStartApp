package com.android.settings.fingerstart;







public class WOSFinger {
    public final static String TAG = "WOSFinger";

    public final static String KEY_COMPONENT_NAME = "KEY_COMPONENT_NAME";
    public final static String KEY_PACKAGE_NAME = "KEY_PACKAGE_NAME";
    public final static String KEY_ACTIVITY_NAME = "KEY_ACTIVITY_NAME";

    //framework 
    public final static String FINGER_ACTION_START = "com.wind.msg.fingerprintservice";
    public final static String KEY_FINGER_RESULT = "finger.result";
    public final static String KEY_FINGER_ID = "finger.id";
    public final static String KEY_DEVICE_ID = "device.id";

    //local
//    public final static String KEY_FINGER = "wos.finger";
    public final static String KEY_FINGER_PKG = "finger.pkg";
    public final static String KEY_FINGER_ACTIVITY = "finger.activity";
    public static final String KEY_FINGER_INFO = "finger.id.info";

	public static String KEY_FINGET_RESULT_ID_ = "finger.position.id_";
	//public static String KEY_ONLY_UNLOCK_RESULT_STATUS_ = "only.unlock.status_";
	public static String KEY_SELECT_NAME_  = "select.name_";


    public static boolean isFingerIdValid(int requestCode) {
        return (requestCode >= 1 && requestCode <= 5);
    }
}
