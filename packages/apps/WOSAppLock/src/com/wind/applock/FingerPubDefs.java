package com.wind.applock;

public class FingerPubDefs {
    /**
     * The hardware is unavailable. Try again later.
     */
    public static final int FINGERPRINT_ERROR_HW_UNAVAILABLE = 1;

    /**
     * Error state returned when the sensor was unable to process the current image.
     */
    public static final int FINGERPRINT_ERROR_UNABLE_TO_PROCESS = 2;

    /**
     * Error state returned when the current request has been running too long. This is intended to
     * prevent programs from waiting for the fingerprint sensor indefinitely. The timeout is
     * platform and sensor-specific, but is generally on the order of 30 seconds.
     */
    public static final int FINGERPRINT_ERROR_TIMEOUT = 3;

    /**
     * Error state returned for operations like enrollment; the operation cannot be completed
     * because there's not enough storage remaining to complete the operation.
     */
    public static final int FINGERPRINT_ERROR_NO_SPACE = 4;

    /**
     * The operation was canceled because the fingerprint sensor is unavailable. For example,
     * this may happen when the user is switched, the device is locked or another pending operation
     * prevents or disables it.
     */
    public static final int FINGERPRINT_ERROR_CANCELED = 5;
    /**
     * The {@link FingerprintManager#remove(Fingerprint, RemovalCallback)} call failed. Typically
     * this will happen when the provided fingerprint id was incorrect.
     *
     * @hide
     */
    public static final int FINGERPRINT_ERROR_UNABLE_TO_REMOVE = 6;

   /**
     * The operation was canceled because the API is locked out due to too many attempts.
     */
    public static final int FINGERPRINT_ERROR_LOCKOUT = 7;

    

    /**
      * The msg was clear the count of too many attempts.
      */
    public static final String ACTION_LOCKOUT_RESET = "com.android.server.fingerprint.ACTION_LOCKOUT_RESET";
    

    /**
      * The value of max attempts.
      */
    public static final int ERROR_AUTH_ATTEMP_MAX = 5;
}
