package com.android.settings.fingerstart;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import com.android.settings.R;
import android.widget.TextView;
import android.widget.RadioButton;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ArrayAdapter;
import android.content.SharedPreferences;

public class FingerStartSetActivity extends Activity implements OnItemClickListener{

    private static final String TAG = "hyy FingerStartSetActivity";
    private ListView mList;
    private ArrayList<AppInfo> mApplist;
    private Context mContext;
    private FingerUtil mFingerUtil;
    private int mFingerId = -1;
	private ArrayList<String> mAppNameList;
	private AppsAdapter mAppsAdapter;
	public static int mCheckedIndex = 0;
	private SharedPreferences mPre;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Wind.Log(TAG, "onCreate mFingerId=" + mFingerId);
		initVariables();
		initViews(savedInstanceState);
		loadData();
	}

	private void initVariables() {
		mContext = this;
		
		mFingerId = getIntent().getIntExtra(WOSFinger.KEY_FINGER_ID, -1);
		if (!WOSFinger.isFingerIdValid(mFingerId)) {
			Wind.Log(TAG, "onCreate mFingerId is invalid " + mFingerId);
			FingerStartSetActivity.this.finish();
			return;
		}
		
		mFingerUtil = new FingerUtil(mContext.getApplicationContext());
		mPre = mContext.getSharedPreferences(mFingerUtil.FINGER_INFO, Context.MODE_PRIVATE);
	}

	private void initViews(Bundle savedInstanceState) {
		setContentView(R.layout.activity_settings);
		mList = (ListView) findViewById(R.id.list);
	}

	private void loadData() {
		AllAppInfos mAllAppInfos = new AllAppInfos(this);
		mApplist = mAllAppInfos.getAppsInfos();
		mAppsAdapter = new AppsAdapter(this, mApplist);
		mAppsAdapter.setListView(mList);
		mList.setAdapter(mAppsAdapter);
		mList.setOnItemClickListener(this);
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		ListView lv = (ListView) parent;
		mAppsAdapter.setListView(lv);
		if (mCheckedIndex != position) {
			int childId = mCheckedIndex - lv.getFirstVisiblePosition();
			if (childId >= 0) {
				View item = lv.getChildAt(childId);
				if (item != null) {
					RadioButton rb = (RadioButton) item.findViewById(mCheckedIndex);
					if (rb != null) {
						rb.setChecked(false);
					}
				}
			}
			RadioButton rb1 = (RadioButton) view.findViewById(position);
			if (rb1 != null)
				rb1.setChecked(true);
			mCheckedIndex = position;
		}
		mAppsAdapter.setSelectedIndex(position);
		setActivityResult(position);
		Wind.Log(TAG, "onItemClick  endposition=" + position + "  id=" + id);

	}

	@Override
	protected void onPause() {
		Wind.Log(TAG, "onPause");
		try {
			int position = mPre.getInt(WOSFinger.KEY_FINGET_RESULT_ID_ + mFingerId, 0);
			Wind.Log(TAG, "onPause position=" + position);
			setActivityResult(position);
		} catch (ClassCastException ex) {
			Wind.Log(TAG, "onPause ex");
		}
		super.onPause();
	}

    @Override
    protected void onResume() {
        Wind.Log(TAG, "onResume");
		try {
			int position = mPre.getInt(WOSFinger.KEY_FINGET_RESULT_ID_ + mFingerId, 0);
			Wind.Log(TAG, "onResume position=" + position);
			mCheckedIndex = position;
		} catch (ClassCastException ex) {
			Wind.Log(TAG, "onResume ex");
		}
        super.onResume();
    }

    protected void setActivityResult(int position) {
        Wind.Log(TAG, "setActivityResult position="+position);

        Intent data = new Intent();
		data.putExtra(WOSFinger.KEY_COMPONENT_NAME, mApplist.get(position).mComponentName);
		data.putExtra(WOSFinger.KEY_PACKAGE_NAME, mApplist.get(position).getTitle());
		FingerStartSetActivity.this.setResult(mFingerId, data);
		mFingerUtil.storeFingerIdPosition(mFingerId, position, mApplist.get(position).getTitle());
		mFingerUtil.storeFingerIdStatus(mFingerId, mApplist.get(position).mComponentName);
		Wind.Log(TAG, "setActivityResult end");
		FingerStartSetActivity.this.finish();
    }

//	public void removeActivity() {
//		FingerStartSetActivity.this.finish();
//	}

	// mList.setAdapter(new ArrayAdapter<String>(this,
	// android.R.layout.simple_list_item_single_choice,
	// getAppNameList(mApplist)));

	/*
	 * mList.setOnItemClickListener(new OnItemClickListener() {
	 * 
	 * @Override public void onItemClick(AdapterView<?> parent, View view,
	 * int position, long id) { Wind.Log(TAG, "onItemClick position=" +
	 * position + "  id=" + id); mAppsAdapter.setSelectedIndex(position);
	 * RadioButton radiobutton =
	 * (RadioButton)view.findViewById(R.id.id_radio);
	 * radiobutton.setChecked(true); setActivityResult(position); } });
	 */
}
