package com.wind.applock;

/*
 * 当前文件中 需要与FingerStart APP保持一致的属性为：
 * FINGER_ACTION_START：指纹校验后，FingerService 发送校验消息
 * KEY_FINGER_RESULT： 校验结果关键字                  boolean
 * KEY_FINGER_ID： 指纹ID关键字                                int
 * KEY_DEVICE_ID:设备id                   long
 * */

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

    public static boolean isFingerIdValid(int requestCode) {
        return (requestCode >= 1 && requestCode <= 5);
    }
}
