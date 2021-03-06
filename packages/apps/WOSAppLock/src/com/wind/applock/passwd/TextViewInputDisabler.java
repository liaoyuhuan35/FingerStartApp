package com.wind.applock.passwd;

import android.text.InputFilter;
import android.text.Spanned;
import android.widget.TextView;

/**
 * Helper class to disable input on a TextView. The input is disabled by swapping in an InputFilter
 * that discards all changes. Use with care if you have customized InputFilter on the target
 * TextView.
 */
public class TextViewInputDisabler {
    private TextView mTextView;
    private InputFilter[] mDefaultFilters;
    private InputFilter[] mNoInputFilters = new InputFilter[] {
            new InputFilter () {
                @Override
                public CharSequence filter(CharSequence source, int start, int end, Spanned dest,
                        int dstart, int dend) {
                    return "";
                }
            }
    };

    public TextViewInputDisabler(TextView textView) {
        mTextView = textView;
        mDefaultFilters = mTextView.getFilters();
    }

    public void setInputEnabled(boolean enabled) {
        mTextView.setFilters(enabled ? mDefaultFilters : mNoInputFilters);
    }
}
