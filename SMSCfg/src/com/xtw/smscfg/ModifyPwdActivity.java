package com.xtw.smscfg;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class ModifyPwdActivity extends Activity implements OnClickListener {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_modify_pwd);
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

		TextWatcher watcher = new TextWatcher() {

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				View btn = findViewById(R.id.send);
				if (s.length() == 4) {
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
		TextView edit = (TextView) findViewById(R.id.et_mfy_pwd);
		edit.addTextChangedListener(watcher);
		View btn = findViewById(R.id.send);
		btn.setOnClickListener(this);
	}

	@Override
	public void onClick(View v) {
		String number = getIntent().getStringExtra(MainActivity.NUMBER);
		TextView edit = (TextView) findViewById(R.id.et_mfy_pwd);
		String msg = edit.getText().toString();
		Intent data = new Intent();
		data.putExtra(SMSApp.KEY_PWD, msg);
		setResult(RESULT_OK, data);
		finish();
	}

}
