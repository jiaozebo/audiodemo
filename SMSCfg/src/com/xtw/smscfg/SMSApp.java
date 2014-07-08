package com.xtw.smscfg;

import java.util.List;

import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

public class SMSApp extends Application {
	public static final String[] default_codes = new String[] { "修改密码", "*xgmm#", "电量查询", "dlcx#",
			"容量查询", "rlcx#", "信号查询", "xhcx#", "默认配置查询", "ztcx#", "静默定时", "ds", "静默开始", "jmks#",
			"静默停止", "jmtz#" };
	public static final String KEY_PWD = "key_pwd";
	public static final String KEY_NUMBER = "key_number";
	public static final String KEY_SILENT_TIME = "key_silent_time";
	public static final String KEY_TEMP_PWD = "key_temp_pwd";
	public static final String SENT_SMS_ACTION = "SENT_SMS_ACTION";
	public static final String DELIVERED_SMS_ACTION = "DELIVERED_SMS_ACTION";

	public static void sendSMS(Context c, String src, String msg) {
		android.telephony.SmsManager smsManager = android.telephony.SmsManager.getDefault();
		// create the sentIntent parameter
		Intent sentIntent = new Intent(SENT_SMS_ACTION);
		PendingIntent sentPI = PendingIntent.getBroadcast(c, 0, sentIntent, 0);

		// create the deilverIntent parameter
		Intent deliverIntent = new Intent(DELIVERED_SMS_ACTION);
		PendingIntent deliverPI = PendingIntent.getBroadcast(c, 0, deliverIntent, 0);
		List<String> divideContents = smsManager.divideMessage(msg);
		for (String text : divideContents) {
			smsManager.sendTextMessage(src, null, text, sentPI, deliverPI);
		}
	}

	@Override
	public void onCreate() {
		super.onCreate();
		startService(new Intent(this, SMSService.class));
	}

}
