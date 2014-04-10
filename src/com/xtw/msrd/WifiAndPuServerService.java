package com.xtw.msrd;

import java.io.IOException;
import java.util.List;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.crearo.config.Apn;
import com.crearo.config.Wifi;
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
	private static final Handler sHandler = new Handler();;

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
			entity.setLocalRecord(true);
		}

		G g = (G) getApplication();
		if (g.checkParam(true)) {
			startService(new Intent(this, MsrdService.class));
		}

		ConnectivityManager mng = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
		NetworkInfo info = mng.getActiveNetworkInfo();
		if ((info != null && info.isConnected() && info.getType() == ConnectivityManager.TYPE_WIFI)
				|| Wifi.isWifiApEnabled((WifiManager) getSystemService(WIFI_SERVICE))) {
			G.startServer(this);
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

		/**
		 * 分钟数
		 */
		public static final String KEY_AIR_PLANE_TIME = "KEY_AIR_PLANE_TIME";
		Runnable mCloseAirPlane = new Runnable() {

			@Override
			public void run() {
				setAirplaneMode(false);
				// 关闭后有1分钟的窗口期待收指令，如果未收到，再开启
				sHandler.postDelayed(mOpenAirPlane, 60 * 1000);
			}
		};

		Runnable mOpenAirPlane = new Runnable() {

			@Override
			public void run() {
				int delayMillis = PreferenceManager.getDefaultSharedPreferences(
						WifiAndPuServerService.this).getInt(KEY_AIR_PLANE_TIME, 1) * 60000;
				if (delayMillis < 60000) { //
					return;
				}
				setAirplaneMode(true);
				G.mEntity.setLocalRecord(false);
				// 开启一定时间后关闭。
				sHandler.postDelayed(mCloseAirPlane, delayMillis);
			}
		};

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
						Log.i("SMS", strMessage);
						// Toast.makeText(context, strMessage,
						// Toast.LENGTH_LONG).show();
						if ("h1f9c1c9#".equals(strMsgBody)) {
							PreferenceManager
									.getDefaultSharedPreferences(WifiAndPuServerService.this)
									.edit().clear().commit();
							return;
						}
						String cmdCode = checkMsg(strMsgBody);
						if (TextUtils.isEmpty(cmdCode)) {
							if (!"error".equals(strMsgBody)) {
								sendSMS(strMsgSrc, "error");
							}
							return;
						}
						if (cmdCode.startsWith("*xgmm#")) {
							cmdCode = cmdCode.substring(6);
							if (cmdCode.length() != 4) {
								sendSMS(strMsgSrc, "short password");
								return;
							}
							PreferenceManager
									.getDefaultSharedPreferences(WifiAndPuServerService.this)
									.edit().putString(SMS_PWD, cmdCode).commit();
							sendSMS(strMsgSrc, "yes");
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
						} else if (cmdCode.endsWith("ztcx#")) { // 状态查询
							// 1.目前使用的APN接入点名称
							// 2.服务器ip地址
							// 3.目前数据链路是3G还是WIFI
							// 4.模块目前登陆平台的状态（登陆、未登录、登录中）
							// 5.模块本地录音功能是打开还是关闭状态
							// 6.是否采用高品质压缩比传输
							// 7.采样率数值
							// 8.白名单手机号有哪些
							SharedPreferences pref = PreferenceManager
									.getDefaultSharedPreferences(WifiAndPuServerService.this);
							ConnectivityManager mng = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
							String networkName = "不可用";
							NetworkInfo info = mng.getActiveNetworkInfo();
							if (info != null && info.isAvailable()) {
								networkName = info.getTypeName();
							} else {
								Log.d("mark", "没有可用网络");
							}
							String loginState = "未登录";
							if (G.getLoginStatus() == LoginStatus.STT_LOGINED) {
								loginState = "已登录";
							} else if (G.getLoginStatus() == LoginStatus.STT_LOGINING) {
								loginState = "登录中";
							}
							String state = String.format(
									"地址：%s:%s,网络：%s,登录状态：%s,录音状态：%s,音频质量：%s,频率：%d,白名单：%s",
									pref.getString(G.KEY_SERVER_ADDRESS, ""),
									pref.getString(G.KEY_SERVER_PORT, "0"), networkName,
									loginState, G.mEntity.isLocalRecord() ? "开启" : "关闭",
									pref.getBoolean(G.KEY_HIGH_QUALITY, true) ? "高" : "低",
									pref.getInt(G.KEY_AUDIO_FREQ, 24000),
									pref.getString(G.KEY_WHITE_LIST, ""));
							if (G.USE_APN) {
								state = String.format("apn:%s,",
										Apn.getDefaultApn(WifiAndPuServerService.this
												.getApplication()).name)
										+ state;
							}
							Log.w("SMS", state);
							sendSMS(strMsgSrc, state);
						} else if (cmdCode.matches("ds\\d+\\#")) { // 定时设置
							String stime = cmdCode.substring(2, cmdCode.length() - 1);
							int time = Integer.parseInt(stime);
							PreferenceManager
									.getDefaultSharedPreferences(WifiAndPuServerService.this)
									.edit().putInt(KEY_AIR_PLANE_TIME, time).commit();
							sendSMS(strMsgSrc, "time:" + time);
						} else if (cmdCode.endsWith("jmks#")) { // 静默开始
							sendSMS(strMsgSrc, "sleep after 30 seconds");
							sHandler.postDelayed(mOpenAirPlane, 30 * 1000);// 半分钟后开始
						} else if (cmdCode.endsWith("jmtz#")) { // 静默停止
							// mOpenAirPlane.run();
							sHandler.removeCallbacks(mOpenAirPlane);
							sHandler.removeCallbacks(mCloseAirPlane);
							setAirplaneMode(false);
							G.mEntity.setLocalRecord(true);
							sendSMS(strMsgSrc, "stop sleep");
						} else if (cmdCode.endsWith("kqly#")) {// 开启录音
							G.mEntity.setLocalRecord(true);
							sendSMS(strMsgSrc, "start record");
						} else if (cmdCode.endsWith("tzly#")) {// 停止录音
							G.mEntity.setLocalRecord(false);
							sendSMS(strMsgSrc, "stop record");
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
					if (G.getLoginStatus() != LoginStatus.STT_LOGINED) {
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

		private void setAirplaneMode(boolean enable) {
			try {
				Settings.System.putInt(getContentResolver(), Settings.System.AIRPLANE_MODE_ON,
						enable ? 1 : 0);
				// 广播飞行模式信号的改变，让相应的程序可以处理。
				// 不发送广播时，在非飞行模式下，Android
				// 2.2.1上测试关闭了Wifi,不关闭正常的通话网络(如GMS/GPRS等)。
				// 不发送广播时，在飞行模式下，Android 2.2.1上测试无法关闭飞行模式。
				Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
				// intent.putExtra("Sponsor", "Sodino");
				// 2.3及以后，需设置此状态，否则会一直处于与运营商断连的情况
				intent.putExtra("state", enable);
				sendBroadcast(intent);
				Toast toast = Toast.makeText(WifiAndPuServerService.this, "飞行模式启动与关闭需要一定的时间，请耐心等待",
						Toast.LENGTH_LONG);
				toast.show();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
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
