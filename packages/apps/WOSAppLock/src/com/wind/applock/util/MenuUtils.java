package com.wind.applock.util;

import android.content.Context;
import android.widget.Toast;

public class MenuUtils {

	public static void MyToast(Context context, String str) {
		Toast.makeText(context, str, Toast.LENGTH_SHORT).show();
	}
}
