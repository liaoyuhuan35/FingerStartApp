/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.wind.applock;

import android.hardware.fingerprint.FingerprintManager;
import android.os.CancellationSignal;
import android.widget.ImageView;
import android.widget.TextView;


/**
 * Small helper class to manage text/icon around fingerprint authentication UI.
 */
public class FingerprintHelper extends FingerprintManager.AuthenticationCallback {
    private static final String TAG = "FingerprintHelper";

    private final FingerprintManager mFingerprintManager;
    private final Callback mCallback;
    private CancellationSignal mCancellationSignal;


    /**
     * Builder class for {@link FingerprintHelper} in which injected fields from Dagger
     * holds its fields and takes other arguments in the {@link #build} method.
     */
    public static class FingerprintHelperBuilder {
        private final FingerprintManager mFingerPrintManager;

        public FingerprintHelperBuilder(FingerprintManager fingerprintManager) {
            mFingerPrintManager = fingerprintManager;
        }

        public FingerprintHelper build(Callback callback) {
            return new FingerprintHelper(mFingerPrintManager, callback);
        }
    }

    /**
     * Constructor for {@link FingerprintHelper}. This method is expected to be called from
     * only the {@link FingerprintHelperBuilder} class.
     */
    private FingerprintHelper(FingerprintManager fingerprintManager,Callback callback) {
        mFingerprintManager = fingerprintManager;
        mCallback = callback;
    }

    public boolean isFingerprintAuthAvailable() {
        return mFingerprintManager.isHardwareDetected()
                && mFingerprintManager.hasEnrolledFingerprints();
    }

    public void startListening(FingerprintManager.CryptoObject cryptoObject) {
		Wind.Log(TAG, "startListening cryptoObject="+cryptoObject);
        if (!isFingerprintAuthAvailable()) {
            return;
        }
		Wind.Log(TAG, "startListening mFingerprintManager.authenticate");
        mCancellationSignal = new CancellationSignal();
        mFingerprintManager.authenticate(cryptoObject, mCancellationSignal, 0 /* flags */, this, null);
    }

    public void stopListening() {
		Wind.Log(TAG, "stopListening mCancellationSignal="+mCancellationSignal);
        if (mCancellationSignal != null) {
            mCancellationSignal.cancel();
            mCancellationSignal = null;
        }
    }

    @Override
    public void onAuthenticationError(int errMsgId, CharSequence errString) {
    	mCallback.onAuthenticationError(errMsgId,errString);
    }

    @Override
    public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
    	mCallback.onAuthenticationHelp(helpMsgId,helpString);
        showError(helpString);
    }

    @Override
    public void onAuthenticationFailed() {
        mCallback.onAuthenticationFailed();
    }

    @Override
    public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
        mCallback.onAuthenticationSucceeded();
    }

    private void showError(CharSequence error) {
    }

    public interface Callback {

        void onAuthenticated();

        void onError();

        public void onAuthenticationFailed();

        public void onAuthenticationSucceeded();
        
        public void onAuthenticationError(int errMsgId, CharSequence errString);

        public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) ;
    }
}
