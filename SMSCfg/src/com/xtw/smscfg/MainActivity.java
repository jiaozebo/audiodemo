package com.xtw.smscfg;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ScrollView;
import android.widget.Toast;

public class MainActivity extends Activity implements OnClickListener {

	public static final String NUMBER = SMSApp.KEY_NUMBER;

	private static final int REQUEST_SET_SILENT = 0x1000;
	private static final int REQUEST_MDY_PWD = 0x1001;

	private EditText mNumberET;
	private EditText mContentET;

	private BroadcastReceiver mSmsBroadcast;

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

		FrameLayout content = (FrameLayout) getLayoutInflater().inflate(
				R.layout.content_send_layout, btns, false);
		btns.addView(content);

		TextWatcher watcher = new TextWatcher() {

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				View btn = findViewById(R.id.send);
				if (s.length() > 0) {
					btn.setVisibility(View.VISIBLE);
				} else {
					btn.setVisibility(View.GONE);
				}
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {

			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		};
		mContentET = (EditText) content.findViewById(R.id.et_content);
		mContentET.setHint("发送内容");
		mContentET.addTextChangedListener(watcher);
		View btn = content.findViewById(R.id.send);
		btn.setOnClickListener(this);

		for (int i = 0; i < SMSApp.default_codes.length; i += 2) {
			View child = new Button(this);
			if (i == 10) { // 静默定时 ...
				child = getLayoutInflater().inflate(R.layout.mdy_silent_time_layout, btns, false);
				child.findViewById(R.id.set_selient_time).setOnClickListener(this);
			} else if (i == 0) { // 修改密码 ...
				child = getLayoutInflater().inflate(R.layout.mdy_pwd_layout, btns, false);
				child.findViewById(R.id.mdf_set_pwd).setOnClickListener(this);
			} else {
				Button bt = new Button(this);
				bt.setText(SMSApp.default_codes[i]);
				child = bt;
				child.setOnClickListener(this);
			}
			child.setId(i);
			params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
					LayoutParams.WRAP_CONTENT);
			params.topMargin = getResources().getDimensionPixelOffset(
					R.dimen.activity_vertical_margin);
			child.setLayoutParams(params);
			btns.addView(child);
		}

		// 注册广播 发送消息
		mSmsBroadcast = new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				if (intent.getAction().equals(SMSApp.SENT_SMS_ACTION)) {
					switch (getResultCode()) {
					case Activity.RESULT_OK:
						Toast.makeText(context, "短信发送成功", Toast.LENGTH_SHORT).show();
						break;
					default:
						Toast.makeText(context, "发送失败", Toast.LENGTH_LONG).show();
						break;
					}
				} else if (intent.getAction().equals(SMSApp.DELIVERED_SMS_ACTION)) {
					Toast.makeText(context, "对方接收成功", Toast.LENGTH_LONG).show();
				}

			}
		};
		IntentFilter filter = new IntentFilter(SMSApp.SENT_SMS_ACTION);
		filter.addAction(SMSApp.DELIVERED_SMS_ACTION);
		registerReceiver(mSmsBroadcast, filter);
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
	protected void onDestroy() {
		if (mSmsBroadcast != null) {
			unregisterReceiver(mSmsBroadcast);
			mSmsBroadcast = null;
		}
		super.onDestroy();
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();
		final String number = mNumberET.getText().toString();
		if (TextUtils.isEmpty(number)) {
			mNumberET.requestFocus();
			Toast.makeText(this, "请输入号码", Toast.LENGTH_SHORT).show();
			return;
		}
		final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		String pwd = preferences.getString(SMSApp.KEY_PWD, "1919");
		/*
		 * "修改密码", "*xgmm#", "电量查询", "dlcx#", "容量查询", "rlcx#", "信号查询", "xhcx#",
		 * "默认配置查询", "ztcx#", "静默定时", "ds", "静默开始", "jmks#", "静默停止", "jmtz#",
		 * "模块重启", "sbcq#", "恢复出厂设置", "h1f9c1c9#"
		 */
		if (id == R.id.mdf_set_pwd) { // 修改密码 ...
			EditText etMdyPwd = (EditText) findViewById(R.id.et_mdy_set_pwd);
			String newPwd = etMdyPwd.getText().toString();
			if (TextUtils.isEmpty(newPwd)) {
				etMdyPwd.requestFocus();
			} else {
				// etMdyPwd.setText("");
				mContentET.setText(String.format("*%s%s%s", pwd, SMSApp.default_codes[1], newPwd));
			}

		} else if (id == 2) { // 电量查询
			mContentET.setText(String.format("*%s%s", pwd, SMSApp.default_codes[id + 1]));
		} else if (id == 4) { // 容量查询
			mContentET.setText(String.format("*%s%s", pwd, SMSApp.default_codes[id + 1]));
		} else if (id == 6) { // 信号查询
			mContentET.setText(String.format("*%s%s", pwd, SMSApp.default_codes[id + 1]));
		} else if (id == 8) { // 默认配置查询
			mContentET.setText(String.format("*%s%s", pwd, SMSApp.default_codes[id + 1]));
		} else if (id == R.id.set_selient_time) { // 静默定时 ...
			EditText etMdyTime = (EditText) findViewById(R.id.et_time_minute);
			String newPwd = etMdyTime.getText().toString();
			if (TextUtils.isEmpty(newPwd)) {
				etMdyTime.requestFocus();
			} else {
				etMdyTime.setText("");
				mContentET.setText(String.format("*%s%s%s#", pwd, SMSApp.default_codes[11], newPwd));
			}
		} else if (id == 12) { // 静默开始
			mContentET.setText(String.format("*%s%s", pwd, SMSApp.default_codes[id + 1]));
		} else if (id == 14) { // 静默停止
			mContentET.setText(String.format("*%s%s", pwd, SMSApp.default_codes[id + 1]));
		} else if (id == 16) { // 模块重启
			mContentET.setText(String.format("*%s%s", pwd, SMSApp.default_codes[id + 1]));
		} else if (id == 18) { // 恢复出厂设置 ...
			mContentET.setText(SMSApp.default_codes[id + 1]);
		} else if (id == R.id.send) {
			new AlertDialog.Builder(this).setMessage("确定要发送吗？")
					.setPositiveButton("确定", new DialogInterface.OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							String content = mContentET.getText().toString();
							if (content.contains(SMSApp.default_codes[1])) { // 修改密码
								EditText etMdyPwd = (EditText) findViewById(R.id.et_mdy_set_pwd);
								String newPwd = etMdyPwd.getText().toString();
								preferences.edit().putString(SMSApp.KEY_TEMP_PWD, newPwd).commit();
								etMdyPwd.setText("");
							} else if (content.contains(SMSApp.default_codes[11])) { // 静默定时
								EditText etMdyTime = (EditText) findViewById(R.id.et_time_minute);
								etMdyTime.setText("");
							}
							SMSApp.sendSMS(MainActivity.this, number, content);
							mContentET.setText("");
						}
					}).setNegativeButton("取消", null).show();

		}
		
		ScrollView scroller = (ScrollView) findViewById(R.id.root_scroller);
		scroller.scrollTo(0, 0);
	}

}
