package com.ampu;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Window;

import com.crearo.mpu.sdk.CameraThread;
import com.crearo.mpu.sdk.Common;

/**
 * @author John
 * @version 1.0
 * @date 2011-11-18
 */
public class MPUSplasher extends Activity {

	private Thread mJumpThread;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.splasher);
		mJumpThread = new Thread() {
			@Override
			public void run() {
				long time = System.currentTimeMillis();
				globalInit(MPUSplasher.this);
				long interval = System.currentTimeMillis() - time;
				if (interval < 1000) {
					try {
						Thread.sleep(1000 - interval);
					} catch (InterruptedException e) {
						return;
					}
				}
				runOnUiThread(new Runnable() {

					@Override
					public void run() {
						mJumpThread = null;
						startActivity(new Intent(MPUSplasher.this, OperationItems.class));
						finish();
					}
				});
			}
		};
		mJumpThread.start();
	}

	static void globalInit(Context c) {
		SharedPreferences preference = PreferenceManager.getDefaultSharedPreferences(c);
		if (!preference.getBoolean(Common.KEY_GLOBAL_INITED, false)) {
			Editor editor = preference.edit();
			editor.putBoolean(Common.KEY_GLOBAL_INITED, true);
			editor.putString(SetActivity.KEY_PUNAME.toString(), Common.getPeerUnitName());
			editor.putString(SetActivity.KEY_PUDESC.toString(), Common.getPeerUnitDesc());
			editor.putString(SetActivity.KEY_PUID.toString(), Common.getPuid(c));
			editor.putString(SetActivity.KEY_CAMNAME.toString(), Common.getCameraName(c));
			editor.putString(SetActivity.KEY_CAMDESC.toString(), Common.getCameraDesc(c));
// editor.putBoolean(SetActivity.KEY_RECORD_SUPPORT, true);
			editor.putBoolean(SetActivity.KEY_TIP_SWITCH.toString(), false);

			editor.commit();
		}
		if (preference.getString(CameraThread.KEY_SUPPORTED_PREVIEW_SIZE, null) == null) {
			String formatSize = null, finalSize = null;
			Camera ca = CameraThread.openCameraOf(0);
			if (ca != null) {
				List<Camera.Size> sizes = new ArrayList<Camera.Size>();
				formatSize = CameraThread.formatSupportedPreviewSize(ca, sizes);
				ca.release();
				Editor editor = preference.edit();
				editor.putString(CameraThread.KEY_SUPPORTED_PREVIEW_SIZE, formatSize);
				Iterator<Size> it = sizes.iterator();
				while (it.hasNext()) {
					Camera.Size size = (Camera.Size) it.next();
					if (size.width == 320 && size.height == 240 || size.width == 352
							&& size.height == 288 || size.width == 640 && size.height == 480) {
						finalSize = String.format("%d%s%d", size.width, CameraThread.MULTIP,
								size.height);
					}
				}
				if (finalSize == null && !sizes.isEmpty()) {
					Size size = sizes.get(0);
					finalSize = String.format("%d%s%d", size.width, CameraThread.MULTIP,
							size.height);
				}
				if (finalSize != null) {
					editor.putString(Common.KEY_RESOLUTION, finalSize);
				}
				editor.commit();
			}
		}
	}

	@Override
	protected void onDestroy() {
		final Thread theThread = mJumpThread;
		if (theThread != null) {
			theThread.interrupt();
			try {
				theThread.join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		super.onDestroy();

	}

}
