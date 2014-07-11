package com.ampu;

import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.text.InputFilter;
import android.text.InputType;
import android.util.SeekBarPreference;
import android.widget.Toast;

import com.crearo.mpu.sdk.CameraThread;
import com.crearo.mpu.sdk.Common;
import com.crearo.mpu.sdk.client.VideoParam;

public class SetActivity extends PreferenceActivity {
	public static final String KEY_PREFERENCE = "pref_key";

	public static final String KEY_SERVER = "key_server";
	public static final CharSequence KEY_ADDRESS = "key_addr";
	public static final CharSequence KEY_PORT = "key_port";
	public static final CharSequence KEY_FIXADDR = "key_fixAddr";
	public static final CharSequence KEY_SYNC = "key_ia_sync";
	public static final CharSequence KEY_PASSWORD = "key_password";
	public static final CharSequence KEY_TIP_SWITCH = "key_switch2work";
	public static final CharSequence KEY_PUNAME = "key_puName";
	public static final CharSequence KEY_PUDESC = "key_desc";
	public static final CharSequence KEY_PUID = "key_puid";
	public static final CharSequence KEY_CAMNAME = "key_cam_name";
	public static final CharSequence KEY_CAMDESC = "key_cam_desc";
	public static final String KEY_AUTO_RERECORD = "key_auto_rerecord"; // 自动重新录像
	public static final String KEY_RECORD_INTERVAL = "key_record_interval";
	public static final String KEY_RECORD_SUPPORT = "key_recorder_soppurt";
	public static final String KEY_FPS1 = "key_fps";
	public static final String KEY_KBPS1 = "key_kbps";

	public static final String KEY_PORTRAIT_VIDEO = "key_video_portrait";

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.global_setting);
		initPrefernces();
	}

	private void initPrefernces() {
		// final String VALUE_DEFAULT_RESOLUTION =
		// getString(R.string.default_resolution);
		EditTextPreference intPreference = (EditTextPreference) findPreference(KEY_PORT);
		intPreference.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
		intPreference = (EditTextPreference) findPreference(KEY_RECORD_INTERVAL);
		intPreference.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
		intPreference = (EditTextPreference) findPreference(KEY_PUID);
		intPreference.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
		intPreference.getEditText().setFilters(
				new InputFilter[] { new InputFilter.LengthFilter(18) });
		String strValue = PreferenceManager.getDefaultSharedPreferences(this).getString(
				KEY_RECORD_INTERVAL, null);
		if (strValue == null) {
			Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
			editor.putString(KEY_RECORD_INTERVAL, "5");
			editor.commit();
		}
		EditTextPreference passwordPreference = (EditTextPreference) findPreference(KEY_PASSWORD);
		passwordPreference.getEditText().setInputType(
				InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_CLASS_TEXT);
		ListPreference resolution = (ListPreference) findPreference(Common.KEY_RESOLUTION);

		SeekBarPreference preference = (SeekBarPreference) findPreference(VideoParam.KEY_INT_VIDEO_QUALITY);
		strValue = PreferenceManager.getDefaultSharedPreferences(this).getString(
				CameraThread.KEY_SUPPORTED_PREVIEW_SIZE, null);
		if (strValue == null) {
			Toast.makeText(this, R.string.camera_init_error, Toast.LENGTH_SHORT).show();
			findPreference("key_video").setEnabled(false);
		} else {
			String[] entries = CameraThread.parseSupportedPreviewSize(strValue);
			resolution.setEntries(entries);
			resolution.setEntryValues(entries);
		}
		preference.setBase(1); // 从1开始显示
		preference.setMax(5); // 范围：0~4

		if (CameraThread.isSupportedRecordMode(this)) {
			// 硬编码，质量不使能
			preference.setEnabled(false);
		}
	}

	@Override
	public void finish() {
		super.finish();
		overridePendingTransition(0, R.anim.zoom_exit);
	}

	@Override
	public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
		boolean bRet = super.onPreferenceTreeClick(preferenceScreen, preference);
		overridePendingTransition(R.anim.zoom_enter, R.anim.zoom_exit);
		return bRet;
	}

}
