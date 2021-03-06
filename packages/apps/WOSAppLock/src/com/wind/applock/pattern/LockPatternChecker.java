package com.wind.applock.pattern;

import java.util.List;

import android.os.AsyncTask;
import com.android.internal.widget.LockPatternUtils.RequestThrottledException;

public class LockPatternChecker {
	/**
	 * Interface for a callback to be invoked after security check.
	 */
	public interface OnCheckCallback {
		/**
		 * Invoked when a security check is finished.
		 * 
		 * @param matched
		 *            Whether the PIN/Password/Pattern matches the stored one.
		 * @param throttleTimeoutMs
		 *            The amount of time in ms to wait before reattempting the
		 *            call. Only non-0 if matched is false.
		 */
		void onChecked(boolean matched, int throttleTimeoutMs);
	}

	public static AsyncTask<?, ?, ?> checkPattern(
			final com.wind.applock.pattern.LockPatternUtil utils,
			final List<com.wind.applock.pattern.LockPatternView.Cell> pattern,
			final int userId,
			final com.wind.applock.pattern.LockPatternChecker.OnCheckCallback callback) {
		AsyncTask<Void, Void, Boolean> task = new AsyncTask<Void, Void, Boolean>() {
			private int mThrottleTimeout;

			@Override
			protected Boolean doInBackground(Void... args) {
				try {
					return utils.checkPattern(pattern, userId);
				} catch (RequestThrottledException ex) {
					mThrottleTimeout = ex.getTimeoutMs();
					return false;
				}
			}

			@Override
			protected void onPostExecute(Boolean result) {
				callback.onChecked(result, mThrottleTimeout);
			}
		};
		task.execute();
		return task;
	}

	/**
	 * Checks a password asynchronously.
	 * 
	 * @param utils
	 *            The LockPatternUtils instance to use.
	 * @param password
	 *            The password to check.
	 * @param userId
	 *            The user to check against the pattern.
	 * @param callback
	 *            The callback to be invoked with the check result.
	 */
	public static AsyncTask<?, ?, ?> checkPassword(
			final com.wind.applock.pattern.LockPatternUtil utils, final String password,
			final int userId, final OnCheckCallback callback) {
		AsyncTask<Void, Void, Boolean> task = new AsyncTask<Void, Void, Boolean>() {
			private int mThrottleTimeout;

			@Override
			protected Boolean doInBackground(Void... args) {
				try {
					return utils.checkPassword(password, userId);
				} catch (RequestThrottledException ex) {
					mThrottleTimeout = ex.getTimeoutMs();
					return false;
				}
			}

			@Override
			protected void onPostExecute(Boolean result) {
				callback.onChecked(result, mThrottleTimeout);
			}
		};
		task.execute();
		return task;
	}
}
