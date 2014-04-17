package com.xtw.smscfg;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.Toast;

public class MainActivity extends Activity implements OnClickListener {

	public static final String NUMBER = SMSApp.KEY_NUMBER;

	private static final int REQUEST_SET_SILENT = 0x1000;
	private static final int REQUEST_MDY_PWD = 0x1001;

	private EditText mNumberET;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		LinearLayout btns = (LinearLayout) findViewById(R.id.btns);
		mNumberET = new EditText(this);
		mNumberET.setHint("请输入手机号码");
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
				LayoutParams.WRAP_CONTENT);
		params.topMargin = getResources().getDimensionPixelOffset(R.dimen.activity_vertical_margin);
		mNumberET.setLayoutParams(params);
		mNumberET.setInputType(InputType.TYPE_CLASS_NUMBER);
		btns.addView(mNumberET);
		String number = PreferenceManager.getDefaultSharedPreferences(this).getString(NUMBER, null);
		if (TextUtils.isEmpty(number)) {
			mNumberET.requestFocus();
			getWindow()
					.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
		} else {
			mNumberET.setText(number);
		}
		for (int i = 0; i < SMSApp.default_codes.length; i += 2) {
			Button child = new Button(this);
			child.setText(SMSApp.default_codes[i]);
			child.setId(i);
			params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
					LayoutParams.WRAP_CONTENT);
			params.topMargin = getResources().getDimensionPixelOffset(
					R.dimen.activity_vertical_margin);
			child.setLayoutParams(params);
			child.setOnClickListener(this);
			btns.addView(child);
		}
	}

	@Override
	protected void onPause() {
		if (isFinishing()) {
			String number = mNumberET.getText().toString();
			PreferenceManager.getDefaultSharedPreferences(this).edit().putString(NUMBER, number)
					.commit();
		}
		super.onPause();
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();
		String number = mNumberET.getText().toString();
		if (TextUtils.isEmpty(number)) {
			mNumberET.requestFocus();
			Toast.makeText(this, "请输入号码", Toast.LENGTH_SHORT).show();
			return;
		}
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		String pwd = preferences.getString(SMSApp.KEY_PWD, "1919");
		String code = SMSApp.default_codes[id + 1];
		/*
		 * "修改密码", "*xgmm#", "电量查询", "dlcx#", "容量查询", "rlcx#", "信号查询", "xhcx#",
		 * "默认配置查询", "ztcx#", "静默定时", "ds", "静默开始", "jmks#", "静默停止", "jmtz#",
		 * "模块重启", "sbcq#", "恢复出厂设置", "h1f9c1c9#"
		 */
		if (id == 0) { // 修改密码 ...
			PreferenceManager.getDefaultSharedPreferences(this).edit().putString(NUMBER, number)
					.commit();
			Intent i = new Intent(this, ModifyPwdActivity.class);
			i.putExtra(NUMBER, number);
			startActivityForResult(i, REQUEST_MDY_PWD);
		} else if (id == 2) { // 电量查询
			SMSApp.sendSMS(this, number, String.format("*%s%s", pwd, SMSApp.default_codes[id + 1]));
		} else if (id == 4) { // 容量查询
			SMSApp.sendSMS(this, number, String.format("*%s%s", pwd, SMSApp.default_codes[id + 1]));
		} else if (id == 6) { // 信号查询
			SMSApp.sendSMS(this, number, String.format("*%s%s", pwd, SMSApp.default_codes[id + 1]));
		} else if (id == 8) { // 默认配置查询
			SMSApp.sendSMS(this, number, String.format("*%s%s", pwd, SMSApp.default_codes[id + 1]));
		} else if (id == 10) { // 静默定时 ...
			Intent i = new Intent(this, SetSilentTimeActivity.class);
			i.putExtra(NUMBER, number);
			startActivityForResult(i, REQUEST_SET_SILENT);
		} else if (id == 12) { // 静默开始
			SMSApp.sendSMS(this, number, String.format("*%s%s", pwd, SMSApp.default_codes[id + 1]));
		} else if (id == 14) { // 静默停止
			SMSApp.sendSMS(this, number, String.format("*%s%s", pwd, SMSApp.default_codes[id + 1]));
		} else if (id == 16) { // 模块重启
			SMSApp.sendSMS(this, number, String.format("*%s%s", pwd, SMSApp.default_codes[id + 1]));
		} else if (id == 18) { // 恢复出厂设置 ...
			SMSApp.sendSMS(this, number, SMSApp.default_codes[id + 1]);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode != RESULT_OK) {
			return;
		}
		if (REQUEST_MDY_PWD == requestCode) {
			String newPwd = data.getStringExtra(SMSApp.KEY_PWD);
			SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
			String pwd = preferences.getString(SMSApp.KEY_PWD, "1919");
			String number = mNumberET.getText().toString();
			preferences.edit().putString(SMSApp.KEY_TEMP_PWD, newPwd).commit();
			SMSApp.sendSMS(this, number,
					String.format("*%s%s%s", pwd, SMSApp.default_codes[1], newPwd));
		} else if (REQUEST_SET_SILENT == requestCode) {
			int time = data.getIntExtra(SMSApp.KEY_SILENT_TIME, 1);
			String number = mNumberET.getText().toString();
			SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
			String pwd = preferences.getString(SMSApp.KEY_PWD, "1919");
			SMSApp.sendSMS(this, number,
					String.format("*%s%s%d#", pwd, SMSApp.default_codes[11], time));
		}
	}

}
