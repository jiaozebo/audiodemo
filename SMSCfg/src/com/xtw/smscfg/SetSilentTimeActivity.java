package com.xtw.smscfg;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class SetSilentTimeActivity extends ModifyPwdActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		EditText time = (EditText) findViewById(R.id.et_mfy_pwd);
		time.setInputType(InputType.TYPE_CLASS_NUMBER);
		time.setHint("请输入静默分钟数");
	}

	@Override
	public void onClick(View v) {
		TextView edit = (TextView) findViewById(R.id.et_mfy_pwd);
		String msg = edit.getText().toString();
		int time = Integer.parseInt(msg);
		Intent data = new Intent();
		data.putExtra(SMSApp.KEY_SILENT_TIME, time);
		setResult(RESULT_OK, data);
		finish();
	}

}
