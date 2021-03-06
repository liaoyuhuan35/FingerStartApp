package com.android.settings.fingerstart;

import java.util.ArrayList;


import android.content.ComponentName;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ToggleButton;
import com.android.settings.R;
import android.widget.RadioButton;
import android.widget.ListView;

public class AppsAdapter extends BaseAdapter {

	private static final String TAG = "hyy AppsAdapter";

	private ArrayList<AppInfo> mApplist;
	private Context mContext;
	private AppLockUtil mAppLockUtil;
	public int mIndex = -1;
	private FingerStartSetActivity mFingerStartSetActivity;
	private ListView mListView;

	public AppsAdapter(Context context, ArrayList<AppInfo> appInfos) {
		mContext = context;
		mApplist = appInfos;
		mAppLockUtil = new AppLockUtil(context);
	}

	public void setListView(ListView listView){
		mListView = listView;
	}

	public void setSelectedIndex(int position){
		mIndex = position;
	}
	
	public int getCount() {
		// TODO Auto-generated method stub
		return mApplist.size();
	}

	public Object getItem(int pos) {
		// TODO Auto-generated method stub
		return pos;
	}

	public long getItemId(int pos) {
		// TODO Auto-generated method stub
		return pos;
	}

	public View getView(final int pos, View conventView, ViewGroup parent) {
		Wind.Log(TAG, "getView pos=" + pos);

		ViewHolder holder;
		if (conventView == null) {
			conventView = LayoutInflater.from(mContext).inflate(
					R.layout.wos_fp_app_item, null);
			holder = new ViewHolder(conventView);
			conventView.setTag(holder);
		} else {
			holder = (ViewHolder) conventView.getTag();
		}

		//holder.mSelectBtn.setFocusable(false);
		holder.mSelectBtn.setId(pos);
		holder.mSelectBtn.setChecked(pos == mFingerStartSetActivity.mCheckedIndex);
		holder.mSelectBtn.setOnCheckedChangeListener(new OnCheckedChangeListener(){
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked){
			Wind.Log(TAG, "getView isChecked=" + isChecked+"mCheckedIndex="+mFingerStartSetActivity.mCheckedIndex);
				if(isChecked){
					if(mFingerStartSetActivity.mCheckedIndex != -1){

						if(mListView != null){
							int childId = mIndex - mListView.getFirstVisiblePosition();

							if(childId >=0){
								View item = mListView.getChildAt(childId);
								if(item != null){
									RadioButton rb = (RadioButton)item.findViewById(mFingerStartSetActivity.mCheckedIndex);
									if(rb != null){
										rb.setChecked(false);
									}
								}
							}
						}
							
					}
					mFingerStartSetActivity.mCheckedIndex = buttonView.getId();
					//notifyDataSetChanged();
				}
		}
	});

		Wind.Log(TAG, "getView pos=" + pos+"mIndex="+mIndex);
		/*if(mIndex == pos){
			holder.mSelectBtn.setChecked(true);
			mApplist.get(pos).setSelectedStatus(true);
		}else{
			holder.mSelectBtn.setChecked(false);
			mApplist.get(pos).setSelectedStatus(false);
		}*/

		holder.resetViews(mApplist.get(pos),
//				mAppLockUtil.getAppLockState(mApplist.get(pos).mComponentName),
				mApplist.get(pos).mComponentName);

		return conventView;
	}

	private class ViewHolder {

		private View view;
		private TextView tvAppName;
		private ImageView ivAppIcon;
		private ToggleButton mSwitch;
		private ComponentName mComponentName;
		private RadioButton mSelectBtn;

		public ViewHolder(View v) {
			view = v;
			ivAppIcon = (ImageView) view.findViewById(R.id.iv);
			tvAppName = (TextView) view.findViewById(R.id.tv);
			mSwitch = (ToggleButton) view.findViewById(R.id.id_switch);
			mSelectBtn = (RadioButton) view.findViewById(R.id.id_radio);
			view.setTag(this);
		}

		public void resetViews(AppInfo appInfo,
				ComponentName componentName) {
			mComponentName = componentName;
			tvAppName.setText(appInfo.getTitle());
			ivAppIcon.setBackground(appInfo.getAppIcon());

//			mSwitch.setChecked(mbIsLock);

//			mSwitch.setOnCheckedChangeListener(new OnCheckedChangeListener() {
//
//				public void onCheckedChanged(CompoundButton compound,
//						boolean bset) {
//					if (bset) {
//						mAppLockUtil.storeLockApp(mComponentName);
//					} else {
//						mAppLockUtil.removeLockApp(mComponentName);
//					}

//				}

//			});
		}
	}
}
