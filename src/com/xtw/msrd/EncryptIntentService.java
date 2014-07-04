package com.xtw.msrd;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import nochump.util.zip.EncryptZipEntry;
import nochump.util.zip.EncryptZipOutput;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class EncryptIntentService extends IntentService {
	// TODO: Rename actions, choose action names that describe tasks that this
	// IntentService can perform, e.g. ACTION_FETCH_NEW_ITEMS
	private static final String ACTION_FOO = "com.xtw.msrd.action.FOO";
	private static final String ACTION_BAZ = "com.xtw.msrd.action.BAZ";

	// TODO: Rename parameters
	private static final String EXTRA_PARAM1 = "com.xtw.msrd.extra.PARAM1";
	private static final String EXTRA_PARAM2 = "com.xtw.msrd.extra.PARAM2";

	/**
	 * 
	 * @param context
	 * @param src
	 *            要存储的wav文件名称
	 * @param dst
	 *            zip文件路径
	 */
	public static void startActionFoo(Context context, String src, String dst) {
		Intent intent = new Intent(context, EncryptIntentService.class);
		intent.setAction(ACTION_FOO);
		intent.putExtra(EXTRA_PARAM1, src);
		intent.putExtra(EXTRA_PARAM2, dst);
		context.startService(intent);
	}

	public EncryptIntentService() {
		super("EncryptIntentService");
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		if (intent != null) {
			final String action = intent.getAction();
			if (ACTION_FOO.equals(action)) {
				final String param1 = intent.getStringExtra(EXTRA_PARAM1);
				final String param2 = intent.getStringExtra(EXTRA_PARAM2);
				handleActionFoo(param1, param2);
			}
		}
	}

	/**
	 * Handle action Foo in the provided background thread with the provided
	 * parameters.
	 */
	private void handleActionFoo(String param1, String zipPath) {
		String srcName = param1;

		G.log("startNewEncrypt : " + srcName);
		FileInputStream fis = null;
		EncryptZipOutput mZipOutput = null;
		try {
			mZipOutput = new EncryptZipOutput(new FileOutputStream(zipPath), "123");
			mZipOutput.putNextEntry(new EncryptZipEntry(new File(srcName).getName()));

			// fis = new FileInputStream(path);
			fis = openFileInput(srcName);

			byte[] buffer = new byte[10 * 1024];
			while (true) {
				int len = fis.read(buffer, 0, buffer.length);
				if (len < 0) {
					break;
				}
				mZipOutput.write(buffer, 0, len);
			}
			mZipOutput.flush();
			mZipOutput.closeEntry();
		} catch (IOException e) {
			if (mZipOutput != null) {
				try {
					mZipOutput.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				mZipOutput = null;
			}
			e.printStackTrace();
		} finally {
			if (fis != null) {
				try {
					fis.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			if (mZipOutput != null) {
				try {
					mZipOutput.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			deleteFile(srcName);
			PreferenceManager.getDefaultSharedPreferences(this).edit().remove(srcName).commit();
			G.log("endEncrypt : " + srcName);
		}
	}
}
