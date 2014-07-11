package com.ampu;

import java.io.File;

import util.CommonMethod;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.ACommonMethod;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Toast;

import com.crearo.mpu.sdk.Common;

/**
 * @author John
 * @version 1.0
 * @date 2011-12-9
 */
public class StorageFilesActivity extends Activity implements
		OnItemClickListener {

	protected static final String tag = "ReplayActivity";
	public static final String KEY_FILE = "key_file";
	public static final String[] FILTERS = new String[] { "avi", "jpg" };
	FileListView fileList;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.filelist);
		fileList = (FileListView) findViewById(R.id.file_list);
		if (!fileList.initWithPath(MPUApplication.PATH_STORAGE, FILTERS)) {
			fileList.post(new Runnable() {

				@Override
				public void run() {
					Toast.makeText(StorageFilesActivity.this,
							R.string.pleaseCheckSDCard, Toast.LENGTH_SHORT)
							.show();
					finish();
				}
			});
		}
		fileList.setOnItemClickListener(this);
	}

	@Override
	public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
		if (arg2 == 0) {
		} else {
			final File file = (File) arg1.getTag();
			if (file.isDirectory()) {
				fileList.post(new Runnable() {

					@Override
					public void run() {
						fileList.initWithFile(file, FILTERS);
					}
				});
			} else {
				if (CommonMethod.getSuffix(file).equalsIgnoreCase("avi")) {
					Intent intent = new Intent();
					intent.setClassName("com.crearo.mcu",
							"com.crearo.mcu.Replayer");
					intent.setData(Uri.fromFile(file));
					try {
						startActivity(intent);
					} catch (ActivityNotFoundException e) {
						Toast.makeText(this, R.string.noMCU2replay,
								Toast.LENGTH_SHORT).show();
					}
				} else {
					Intent intent = ACommonMethod.createOpenFileIntent(file);
					startActivity(intent);
				}
			}
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		boolean exist = true;
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			File root = fileList.getRoot();
			String path = root.getPath();
			if (!path.equals(MPUApplication.PATH_STORAGE)) {
				root = root.getParentFile();
				if (root != null) {
					exist = false;
					fileList.initWithFile(root, FILTERS);
				}
			}
		}
		if (exist) {
			return super.onKeyDown(keyCode, event);
		}
		return true;
	}

	@Override
	protected void onDestroy() {
		// repleyer.stopRend();
		super.onDestroy();
	}
}
