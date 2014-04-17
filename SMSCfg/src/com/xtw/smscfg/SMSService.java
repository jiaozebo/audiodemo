package com.xtw.smscfg;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.telephony.SmsMessage;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

public class SMSService extends Service {
	private MyBroadcastReceiver mMyBroadcastReceiver;

	public SMSService() {
	}

	private static final String ACTION = "android.provider.Telephony.SMS_RECEIVED";

	@Override
	public void onCreate() {
		super.onCreate();
		IntentFilter filter = new IntentFilter(ACTION);
		mMyBroadcastReceiver = new MyBroadcastReceiver();
		registerReceiver(mMyBroadcastReceiver, filter);
	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO: Return the communication channel to the service.
		throw new UnsupportedOperationException("Not yet implemented");
	}

	private class MyBroadcastReceiver extends BroadcastReceiver {

		/**
		 * 分钟数
		 */
		public static final String KEY_AIR_PLANE_TIME = "KEY_AIR_PLANE_TIME";

		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(ACTION)) {

				Bundle extras = intent.getExtras();
				if (extras != null) {
					Object[] smsextras = (Object[]) extras.get("pdus");

					for (int i = 0; i < smsextras.length; i++) {
						SmsMessage smsmsg = SmsMessage.createFromPdu((byte[]) smsextras[i]);

						String strMsgBody = smsmsg.getMessageBody().toString();
						String strMsgSrc = smsmsg.getOriginatingAddress();
						String strMessage = "SMS from " + strMsgSrc + " : " + strMsgBody;
						Log.e("SMS", strMessage);
						SharedPreferences preferences = PreferenceManager
								.getDefaultSharedPreferences(context);
						String n = preferences.getString(SMSApp.KEY_NUMBER, null);
						if (!TextUtils.isEmpty(n) && strMsgSrc.contains(n)
								&& strMsgBody.equals("yes")) {
							preferences.edit().putString(SMSApp.KEY_PWD,
									preferences.getString(SMSApp.KEY_TEMP_PWD, "1919")).commit();
						}
						Log.e("SMS", strMessage);
						Toast.makeText(context, strMsgBody, Toast.LENGTH_LONG).show();
					}
				}

			}
		}

	}
}
