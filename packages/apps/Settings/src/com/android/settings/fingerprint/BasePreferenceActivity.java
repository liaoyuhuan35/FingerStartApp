package com.android.settings.fingerprint;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public abstract class BasePreferenceActivity extends PreferenceActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initVariables();
		initViews(savedInstanceState);
		loadData();
	}

	protected abstract void initVariables();

	protected abstract void initViews(Bundle savedInstanceState);

	protected abstract void loadData();
}
