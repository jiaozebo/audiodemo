package com.xtw.msrd;

import java.io.IOException;
import java.util.List;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.crearo.puserver.PUServerThread;
import com.xtw.msrd.G.LoginStatus;

/**
 * 直连与WIFI服务器服务，这是一个持久服务
 * 
 * @author John
 * 
 */
public class WifiAndPuServerService extends Service {

	private static final String SMS_PWD = "SMS_PWD";
	private MyBroadcastReceiver mMyBroadcastReceiver;
	protected int mCurrentSignalLength;
	protected int mEVDO, mCDMA, mGsm;
	private PhoneStateListener mMyPhoneListener;

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	private static final String ACTION = "android.provider.Telephony.SMS_RECEIVED";

	@Override
	public void onCreate() {
		super.onCreate();
		IntentFilter filter = new IntentFilter(ACTION);
		filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		mMyBroadcastReceiver = new MyBroadcastReceiver();
		registerReceiver(mMyBroadcastReceiver, filter);
		getPhoneState();
		MyMPUEntity entity = G.mEntity;
		if (entity != null) {

			// 默认录像
			entity.startNewFile();
			entity.checkThread();
		}
	}

	// 获取信号强度
	public void getPhoneState() {
		// 1. 创建telephonyManager 对象。
		TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		// 2. 创建PhoneStateListener 对象
		mMyPhoneListener = new PhoneStateListener() {

			@Override
			public void onSignalStrengthsChanged(SignalStrength signalStrength) {
				if (signalStrength.isGsm()) {
					mGsm = signalStrength.getGsmSignalStrength();
				} else {
					mGsm = -1;
				}
				mEVDO = signalStrength.getEvdoDbm();
				mCDMA = signalStrength.getCdmaDbm();
				super.onSignalStrengthsChanged(signalStrength);
			}
		};
		// 3. 监听信号改变
		telephonyManager.listen(mMyPhoneListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);

		/*
		 * 可能需要的权限 <uses-permission
		 * android:name="android.permission.WAKE_LOCK"></uses-permission>
		 * <uses-permission
		 * android:name="android.permission.ACCESS_COARSE_LOCATION"/>
		 * <uses-permission
		 * android:name="android.permission.ACCESS_FINE_LOCATION"/>
		 * <uses-permission android:name="android.permission.READ_PHONE_STATE"
		 * /> <uses-permission
		 * android:name="android.permission.ACCESS_NETWORK_STATE" />
		 */
	}

	public int getSignal() {
		if (mGsm <= 0) {
			return mCDMA <= 0 ? mEVDO : mCDMA;
		} else {
			return mGsm;
		}
	}

	private class MyBroadcastReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(ACTION)) {

				String strMessage = "..........................";
				Bundle extras = intent.getExtras();
				if (extras != null) {
					Object[] smsextras = (Object[]) extras.get("pdus");

					for (int i = 0; i < smsextras.length; i++) {
						SmsMessage smsmsg = SmsMessage.createFromPdu((byte[]) smsextras[i]);

						String strMsgBody = smsmsg.getMessageBody().toString();
						String strMsgSrc = smsmsg.getOriginatingAddress();

						strMessage = "SMS from " + strMsgSrc + " : " + strMsgBody;
						Toast.makeText(context, strMessage, Toast.LENGTH_LONG).show();
						String cmdCode = checkMsg(strMsgBody);
						if (TextUtils.isEmpty(cmdCode)) {
							return;
						}
						if (cmdCode.startsWith("*xgmm#")) {
							cmdCode = cmdCode.substring(6);
							if (cmdCode.length() != 4) {
								return;
							}
							PreferenceManager
									.getDefaultSharedPreferences(WifiAndPuServerService.this)
									.edit().putString(SMS_PWD, cmdCode).commit();
						} else if (cmdCode.endsWith("dlcx#")) { // 电量
							// 拆分短信内容（手机短信长度限制）
							float percent = ConfigServer
									.getBaterryPecent(WifiAndPuServerService.this);
							String p = String.format("%.2f", percent * 100);
							p += "%";

							sendSMS(strMsgSrc, p);
						} else if (cmdCode.endsWith("rlcx#")) { // 容量查询
							long available = ConfigServer.storageAvailable();
							sendSMS(strMsgSrc,
									String.format("%.2fG", available * 1.0f / 1073741824f));
						} else if (cmdCode.endsWith("xhcx#")) { // 信号查询
							sendSMS(strMsgSrc, String.valueOf(getSignal()));
						} else if (cmdCode.endsWith("sbcq#")) { // 设备重启
							// Intent i1 = new Intent(Intent.ACTION_REBOOT);
							// i1.putExtra("nowait", 1);
							// i1.putExtra("interval", 1);
							// i1.putExtra("window", 0);
							// sendBroadcast(i1);
							try {
								Process process = Runtime.getRuntime().exec("su");
								process.getOutputStream().write("reboot".getBytes());
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}

						}
					}
				}

			} else if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
				ConnectivityManager mng = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
				NetworkInfo info = mng.getActiveNetworkInfo();
				if (info != null && info.isAvailable()) {
					String name = info.getTypeName();
					Log.d("mark", "当前网络名称：" + name);
					G g = (G) getApplication();
					if (g.getLoginStatus() != LoginStatus.STT_LOGINED) {
						startService(new Intent(WifiAndPuServerService.this, MsrdService.class));
					}
				} else {
					Log.d("mark", "没有可用网络");
				}
			}

		}

		private void sendSMS(String src, String msg) {
			android.telephony.SmsManager smsManager = android.telephony.SmsManager.getDefault();

			List<String> divideContents = smsManager.divideMessage(msg);
			for (String text : divideContents) {
				PendingIntent pi = PendingIntent.getActivity(WifiAndPuServerService.this, 0,
						new Intent(), 0);
				smsManager.sendTextMessage(src, null, text, pi, null);
			}
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		G g = (G) getApplication();
		if (g.checkParam(true)) {
			startService(new Intent(this, MsrdService.class));
		}
		return super.onStartCommand(intent, flags, startId);
	}

	public String checkMsg(String strMsgBody) {
		if (TextUtils.isEmpty(strMsgBody)) {
			return null;
		}
		if (!(strMsgBody.startsWith("*"))) {
			return null;
		}
		strMsgBody = strMsgBody.substring(1);
		String pwd = PreferenceManager.getDefaultSharedPreferences(this).getString(SMS_PWD, "1919");
		if (!strMsgBody.startsWith(pwd)) {
			return null;
		}
		strMsgBody = strMsgBody.substring(pwd.length());
		return strMsgBody;
	}

}
