package com.xtw.msrd;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
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
import com.crearo.config.Apn.ApnNode;
import com.crearo.config.NanoHTTPD;
import com.crearo.config.NanoHTTPD.Response.Status;
import com.crearo.config.Wifi;
import com.crearo.puserver.PUCommandChannel;

public class ConfigServer extends NanoHTTPD {
	public static final String KEY_ACTION_START_AUDIO = "KEY_ACTION_START";
	private static final String SMS_PWD = "SMS_PWD";
	private MyBroadcastReceiver mMyBroadcastReceiver;
	protected int mCurrentSignalLength;
	protected int mEVDO, mCDMA, mGsm;
	private PhoneStateListener mMyPhoneListener;

	private static final String ACTION = "android.provider.Telephony.SMS_RECEIVED";
	private static final Handler sHandler = new Handler();;

	private class MyBroadcastReceiver extends BroadcastReceiver {

		/**
		 * 分钟数
		 */
		public static final String KEY_AIR_PLANE_TIME = "KEY_AIR_PLANE_TIME";
		Runnable mCloseAirPlane = new Runnable() {

			@Override
			public void run() {
				G.log("receive msg window opening...");
				setAirplaneMode(false);
				// 关闭后有1分钟的窗口期待收指令，如果未收到，再开启
				sHandler.postDelayed(mOpenAirPlane, 60 * 1000);
			}
		};

		Runnable mOpenAirPlane = new Runnable() {

			@Override
			public void run() {
				int delayMillis = PreferenceManager.getDefaultSharedPreferences(ConfigServer.this)
						.getInt(KEY_AIR_PLANE_TIME, 1) * 60000;
				G.log("mOpenAirPlane, aireplane mode interval: " + delayMillis);
				if (delayMillis < 60000) { //
					return;
				}
				setAirplaneMode(true);
				G.log("mOpenAirPlane, stop record...");
				RecordService.stop(ConfigServer.this);
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
					if (smsextras == null || smsextras.length == 0) {
						G.log("action : " + ACTION + " received, but pdus is "
								+ (smsextras == null ? null : 0));
					} else {
						G.log("action : " + ACTION + " received, smsextra length :"
								+ smsextras.length);
					}
					for (int i = 0; i < smsextras.length; i++) {
						byte[] pdu = (byte[]) smsextras[i];
						if (pdu == null || pdu.length == 0) {
							G.log("sms, pud is " + (pdu == null ? null : 0));
							continue;
						}
						StringBuffer sb = new StringBuffer();
						for (byte b : pdu) {
							sb.append(b);
							sb.append(',');
						}
						G.log("sms, pud is " + sb.toString());
						SmsMessage smsmsg = SmsMessage.createFromPdu(pdu);
						if (smsmsg == null) {
							G.log(String.format("createFromPdu return null ! param : %s",
									sb.toString()));
							continue;
						}

						String strMsgBody = smsmsg.getMessageBody();
						String strMsgSrc = smsmsg.getOriginatingAddress();
						String strMessage = "SMS from " + strMsgSrc + " : " + strMsgBody;
						Log.i("SMS", strMessage);
						// Toast.makeText(context, strMessage,
						// Toast.LENGTH_LONG).show();
						if ("h1f9c1c9#".equals(strMsgBody)) {
							PreferenceManager.getDefaultSharedPreferences(ConfigServer.this).edit()
									.clear().commit();
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
							PreferenceManager.getDefaultSharedPreferences(ConfigServer.this).edit()
									.putString(SMS_PWD, cmdCode).commit();
							sendSMS(strMsgSrc, "yes");
						} else if (cmdCode.endsWith("dlcx#")) { // 电量
							// 拆分短信内容（手机短信长度限制）
							float percent = ConfigServer.getBaterryPecent(ConfigServer.this);
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
									.getDefaultSharedPreferences(ConfigServer.this);
							ConnectivityManager mng = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
							String networkName = "不可用";
							NetworkInfo info = mng.getActiveNetworkInfo();
							if (info != null && info.isAvailable()) {
								networkName = info.getTypeName();
							} else {
								Log.d("mark", "没有可用网络");
							}
							if (info.getType() == ConnectivityManager.TYPE_WIFI) {
								try {
									networkName += (" " + Wifi.getCurrentSSID(ConfigServer.this));
								} catch (Exception e) {
									e.printStackTrace();
								}
							}
							String loginState = "未登录";
							if (G.getLoginStatus() == G.STT_LOGINED) {
								loginState = "已登录";
							} else if (G.getLoginStatus() == G.STT_LOGINING) {
								loginState = "登录中";
							}

							MyMPUEntity entity = G.sEntity;
							boolean isRecord = entity != null && entity.isLocalRecord();
							String state = String.format(
									"地址：%s:%s,网络：%s,登录状态：%s,录音状态：%s,音频质量：%s,频率：%d,白名单：%s",
									pref.getString(G.KEY_SERVER_ADDRESS, ""),
									pref.getString(G.KEY_SERVER_PORT, "0"), networkName,
									loginState, isRecord ? "开启" : "关闭",
									pref.getBoolean(G.KEY_HIGH_QUALITY, true) ? "高" : "低",
									pref.getInt(G.KEY_AUDIO_FREQ, 44100),
									pref.getString(G.KEY_WHITE_LIST, ""));
							if (G.USE_APN) {
								String apnname = null;
								try {
									apnname = Apn.getDefaultApn(ConfigServer.this.getApplication()).name;
								} catch (Exception e) {
									e.printStackTrace();
									apnname = "" + e.getMessage();
								}
								state = String.format("apn:%s,", apnname) + state;
							}
							Log.w("SMS", state);
							sendSMS(strMsgSrc, state);
						} else if (cmdCode.matches("ds\\d+\\#")) { // 定时设置
							String stime = cmdCode.substring(2, cmdCode.length() - 1);
							int time = Integer.parseInt(stime);
							PreferenceManager.getDefaultSharedPreferences(ConfigServer.this).edit()
									.putInt(KEY_AIR_PLANE_TIME, time).commit();
							sendSMS(strMsgSrc, "time:" + time);
						} else if (cmdCode.endsWith("jmks#")) { // 静默开始
							sendSMS(strMsgSrc, "sleep after 30 seconds");
							sHandler.postDelayed(mOpenAirPlane, 30 * 1000);// 半分钟后开始
						} else if (cmdCode.endsWith("jmtz#")) { // 静默停止
							// mOpenAirPlane.run();
							sHandler.removeCallbacks(mOpenAirPlane);
							sHandler.removeCallbacks(mCloseAirPlane);
							setAirplaneMode(false);
							G.log("closing airplane mode...");
							RecordService.start(ConfigServer.this);
							sendSMS(strMsgSrc, "stop sleep");
						} else if (cmdCode.endsWith("kqly#")) {// 开启录音
							RecordService.start(ConfigServer.this);
							sendSMS(strMsgSrc, "start record");
						} else if (cmdCode.endsWith("tzly#")) {// 停止录音
							RecordService.stop(ConfigServer.this);
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
								G.log("rebooting device received: " + e.getMessage());
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
					if (G.getLoginStatus() == G.STT_PRELOGIN) {
						NCIntentService
								.startNC(context.getApplicationContext(), G.mAddres, G.mPort);
					}
				} else {
					Log.d("mark", "没有可用网络");
					NCIntentService.stopNC(context.getApplicationContext());
				}
			}

		}

		private void sendSMS(String src, String msg) {
			android.telephony.SmsManager smsManager = android.telephony.SmsManager.getDefault();

			List<String> divideContents = smsManager.divideMessage(msg);
			for (String text : divideContents) {
				PendingIntent pi = PendingIntent.getActivity(ConfigServer.this, 0, new Intent(), 0);
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
				Toast toast = Toast.makeText(ConfigServer.this, "飞行模式启动与关闭需要一定的时间，请耐心等待",
						Toast.LENGTH_LONG);
				toast.show();
			} catch (Exception e) {
				e.printStackTrace();
				G.log("setAirplaneMode " + enable + ", exception: " + e.getMessage());
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

	// 获取信号强度
	public void registPhoneState(PhoneStateListener listener) {
		// 1. 创建telephonyManager 对象。
		TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		// 2. 创建PhoneStateListener 对象
		// 3. 监听信号改变
		telephonyManager.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);

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

	public void unregistPhoneState(PhoneStateListener listener) {
		// 1. 创建telephonyManager 对象。
		TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		// 2. 创建PhoneStateListener 对象
		// 3. 监听信号改变
		telephonyManager.listen(listener, PhoneStateListener.LISTEN_NONE);
	}

	public int getSignal() {
		if (mGsm <= 0) {
			return mCDMA <= 0 ? mEVDO : mCDMA;
		} else {
			return mGsm;
		}
	}

	@Override
	public void onCreate() {
		super.onCreate();

		IntentFilter filter = new IntentFilter(ACTION);
		filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		// filter.addAction(IntentFilter.)
		mMyBroadcastReceiver = new MyBroadcastReceiver();
		registerReceiver(mMyBroadcastReceiver, filter);

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
		registPhoneState(mMyPhoneListener);
	}

	@Override
	public void onDestroy() {
		unregisterReceiver(mMyBroadcastReceiver);
		mMyBroadcastReceiver = null;
		unregistPhoneState(mMyPhoneListener);
		mMyPhoneListener = null;
		super.onDestroy();
	}

	public ConfigServer() {
		super("ConfigServer");
	}

	private static final String PWD = "123";
	private static final String TAG = "Config";

	public static void start(Context c, String hostname, int port) {
		Intent intent = new Intent(c, ConfigServer.class);
		intent.setAction(ACTION_FOO);
		intent.putExtra(EXTRA_PARAM1, hostname);
		intent.putExtra(EXTRA_PARAM2, port);
		c.startService(intent);
	}

	public static void stop(Context c) {
		Intent intent = new Intent(c, ConfigServer.class);
		c.stopService(intent);
	}

	@Override
	public Response serve(String uri, Method method, Map<String, String> header,
			Map<String, String> parms, Map<String, String> files) {

		MyMPUEntity entity = G.sEntity;
		if (uri.equals("/")) {
			if (parms.containsKey("address")) {
				StringBuilder sb = new StringBuilder();
				String addr = parms.get("address");
				String port = parms.get("port");
				int nPort = -1;
				if (TextUtils.isEmpty(addr) || TextUtils.isEmpty(port)) {
					sb.append("<html>\n<meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n<meta http-equiv='refresh' content='1;url=/' />\n<body>配置参数不合法。</body></html>");
				} else {
					try {
						nPort = Integer.parseInt(port);
					} catch (NumberFormatException e) {
					}
					if (nPort == -1) {
						sb.append("<html>\n<meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n<meta http-equiv='refresh' content='1;url=/' />\n<body>端口不合法</body></html>");
					} else {
						Editor edit = PreferenceManager.getDefaultSharedPreferences(this).edit();
						boolean ret = edit.putString(G.KEY_SERVER_ADDRESS, addr)
								.putString(G.KEY_SERVER_PORT, port).commit();
						Log.e(TAG, "config server :" + ret);
						sb.append("<html>\n<meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n<meta http-equiv='refresh' content='1;url=/' />\n<body>参数修改成功，已退出登录!</body></html>");
					}
				}
				return new Response(sb.toString());
			} else if (parms.containsKey("audio_cfg")) {
				boolean highQuality = !TextUtils.isEmpty(parms.get("quality"));
				String audio_fre = parms.get("audio_fre");
				int fre = PreferenceManager.getDefaultSharedPreferences(this).getInt(
						G.KEY_AUDIO_FREQ, 44100);
				try {
					float f = Float.parseFloat(audio_fre);
					fre = (int) (f * 1000f);
				} catch (Exception e) {
					e.printStackTrace();
				}
				Editor edit = PreferenceManager.getDefaultSharedPreferences(this).edit();
				edit.putBoolean(G.KEY_HIGH_QUALITY, highQuality).putInt(G.KEY_AUDIO_FREQ, fre)
						.commit();
				StringBuffer sb = new StringBuffer();
				sb.append("<html>\n<meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n<meta http-equiv='refresh' content='1;url=/' />\n<body>修改成功</body></html>");

				boolean record = entity != null && entity.isLocalRecord();
				if (record) {
					RecordService.stop(this);
					RecordService.start(this);
				}
				return new Response(sb.toString());
			} else if (parms.containsKey("mdy_date_time")) {
				String millis = parms.get("time");
				StringBuilder sb = new StringBuilder();
				boolean localRecord = entity != null && entity.isLocalRecord();
				try {

					long milliseconds = Long.parseLong(millis);
					AlarmManager am = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
					am.setTime(milliseconds);
					if (localRecord) {
						entity.setLocalRecord(false);
					}
					sb.append("<html>\n<meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n<meta http-equiv='refresh' content='1;url=/' />\n<body>修改成功</body></html>");
				} catch (Exception e) {
					sb.append(String
							.format("<html>\n<meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n<meta http-equiv='refresh' content='5;url=/' />\n<body>修改失败(%s)</body></html>",
									"" + e.getMessage()));
				} finally {
					if (localRecord) {
						entity.setLocalRecord(true);
					}
				}
				return new Response(sb.toString());
			} else if (parms.containsKey("login") || parms.containsKey("query_login_status")) {
				// 登录
				String state = "正在登录";
				if (G.getLoginStatus() == G.STT_LOGINED) {
					state = "已经登录";
				} else if (G.getLoginStatus() == G.STT_LOGINED) {
				} else {// preLogin
					if (parms.containsKey("login")) {
						// Intent service = new Intent(this,
						// MsrdService.class);
						// this.startService(service);
						NCIntentService.startNC(this, G.mAddres, G.mPort);
						state = "正在登录";
					} else {
						state = "未登录";
					}
				}
				StringBuilder sb = new StringBuilder();
				sb.append("<html>\n<meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n<body>");
				sb.append(state);
				sb.append("<br /><a href='?query_login_status=1'>查询登录状态</a><hr /></body></html>");
				return new Response(sb.toString());
			} else if (parms.containsKey("logout")) {
				// 登出
				G g = (G) this.getApplicationContext();
				NCIntentService.stopNC(this);
				StringBuilder sb = new StringBuilder();
				sb.append("<html>\n<meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n<meta http-equiv='refresh' content='1;url=' />\n<body>已退出登录.</body></html>");
				return new Response(sb.toString());
			} else if (parms.containsKey("list_files")) {
				return serveFile(uri, header, G.sRootPath, true);
			} else if (parms.containsKey("add_white_number")) {
				String newNumber = parms.get("phone_number");
				if (!TextUtils.isEmpty(newNumber)) {
					SharedPreferences preferences = PreferenceManager
							.getDefaultSharedPreferences(this);
					String white_list = preferences.getString(G.KEY_WHITE_LIST, "");
					preferences.edit().putString(G.KEY_WHITE_LIST, white_list + "," + newNumber)
							.commit();
				}
				return new Response(
						"<html>\n<meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n<meta http-equiv='refresh' content='1;url=/?white_list=1' />\n<body>正在处理...</body></html>");
			} else if (parms.containsKey("white_list")) { // 白名单
				StringBuilder sb = new StringBuilder();
				sb.append("<html>\n<meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n");
				sb.append("<body>\n<form method='post' action=''>\n<label >来电白名单:</label><br/>\n");
				String white_list = PreferenceManager.getDefaultSharedPreferences(this).getString(
						G.KEY_WHITE_LIST, null);
				if (white_list != null) {
					String[] array = white_list.split(",");
					Set<String> sets = new HashSet<String>();
					for (int i = 0; i < array.length; i++) {
						sets.add(array[i]);
					}
					for (Iterator<String> iterator = sets.iterator(); iterator.hasNext();) {
						String string = (String) iterator.next();
						if (!TextUtils.isEmpty(string)) {
							sb.append(string);
							sb.append(String.format(
									"<a href='?delete_white_number=%s'>删除</a><br/>", string));
						}
					}
				}
				sb.append("<input type='number' name='phone_number' value=''/>\n");
				sb.append("<input type='submit' name='add_white_number' value='添加新号码'>\n");
				sb.append("</form>\n</body>\n</html>");
				return new Response(sb.toString());
			} else if (parms.containsKey("delete_white_number")) {
				String number = parms.get("delete_white_number");
				SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
				String white_list = preferences.getString(G.KEY_WHITE_LIST, null);
				if (white_list != null) {
					String[] array = white_list.split(",");
					Set<String> sets = new HashSet<String>();
					for (int i = 0; i < array.length; i++) {
						sets.add(array[i]);
					}
					sets.remove(number);
					StringBuffer all = new StringBuffer();
					for (Iterator<String> iterator = sets.iterator(); iterator.hasNext();) {
						String string = (String) iterator.next();
						all.append(string);
						if (iterator.hasNext()) {
							all.append(',');
						}
					}
					preferences.edit().putString(G.KEY_WHITE_LIST, all.toString()).commit();
				}
				return new Response(
						"<html>\n<meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n<meta http-equiv='refresh' content='1;url=/?white_list=1' />\n<body>正在处理...</body></html>");
			} else if (parms.containsKey("add_apn")) {
				String name = parms.get("name");
				String number = parms.get("number");
				int id = -1;
				if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(number)) {
					if (number.length() == 4) {
						try {
							ApnNode node = Apn.getDefaultApn(this);
							if (node == null) {
								node = new ApnNode();
							}
							node.name = name;
							node.apn = "#777";
							node.user = number + "@nsa.vpdn.sh";
							node.password = number + "716";
							node.mnc = "03";
							node.mcc = "460";
							id = node.id;
							if (id == -1) {
								Uri uri1 = Apn.addApn(this, node);
								if (uri1 != null) {
									String idStr = uri1.getLastPathSegment();
									if (idStr != null) {
										id = Integer.parseInt(idStr);
									}
								}
							} else {
								Apn.updateApn(this, id, node);
							}
							id = Apn.setDefault(this, id);
						} catch (Exception e) {
							e.printStackTrace();
							return new Response(
									String.format(
											"<html>\n<meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n<meta http-equiv='refresh' content='1;url=/' />\n<body>%s</body></html>",
											e.getMessage()));
						}
					}
				}
				return new Response(
						"<html>\n<meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n<meta http-equiv='refresh' content='1;url=/' />\n<body>"
								+ (id != -1 ? "成功." : "未成功") + "</body></html>");
			} else if (parms.containsKey("apn_config")) {
				StringBuilder sb = new StringBuilder();
				sb.append("<html>\n<meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n");
				sb.append("<body>\n<form method='post' action=''>\n<label >apn:</label><br/>\n");

				sb.append("<label>名称:</label>");
				sb.append("<input type='text' name='name' value=''/>");
				sb.append("<label>apn:</label>");
				sb.append("<input type='text' name='apn' value=''/>");
				sb.append("<label>用户:</label>");
				sb.append("<input type='text' name='user' value=''/>");
				sb.append("<label>密码:</label>");
				sb.append("<input type='text' name='pwd' value=''/>");
				sb.append("<br/>");
				sb.append("<input type='submit' name='add_apn' value='确定'>");

				sb.append("</form>\n</body>\n</html>");
				return new Response(sb.toString());
			} else if (parms.containsKey("switch_apn")) {
				String name = parms.get("apn_name");
				if (TextUtils.isEmpty(name)) {
					return new Response(
							String.format(
									"<html>\n<meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n<meta http-equiv='refresh' content='1;url=?confirm=1&pwd=%s' />\n<body>未成功.</body></html>",
									PWD));
				}
				try {
					int apn = Apn.getApn(this, name);
					if (apn != -1) {
						Apn.setDefault(this, apn);
					}
					return new Response(
							String.format(
									"<html>\n<meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n<meta http-equiv='refresh' content='1;url=?confirm=1&pwd=%s' />\n<body>正在处理...</body></html>",
									PWD));
				} catch (Exception e) {
					e.printStackTrace();
					return new Response(
							String.format(
									"<html>\n<meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n<meta http-equiv='refresh' content='1;url=?confirm=1&pwd=%s' />\n<body>%s</body></html>",
									PWD, ("未成功：" + e.getMessage())));
				}

			} else if (parms.containsKey("list_allfiles_xml")) {
				String xml = null;
				try {
					DocumentBuilder builder = DocumentBuilderFactory.newInstance()
							.newDocumentBuilder();
					Document doc = builder.newDocument();
					File rootFile = new File(G.sRootPath);
					Element root = doc.createElement("File");
					addFiles2Node(rootFile, root, doc);
					doc.appendChild(root);
					xml = PUCommandChannel.node2String(doc);
				} catch (ParserConfigurationException e) {
					e.printStackTrace();
					xml = "error: " + e.getMessage();
				}
				return new Response(Status.OK, "text/xml", xml);
			} else if (parms.containsKey("wifi")) {
				final String ssid = parms.get("ssid");
				final String pwd = parms.get("password");
				new Thread() {
					@Override
					public void run() {
						Wifi.connectWifi(ConfigServer.this, ssid, pwd);
						super.run();
					}

				}.start();
				PreferenceManager.getDefaultSharedPreferences(this).edit()
						.putString(WifiStateReceiver.KEY_DEFAULT_SSID, ssid)
						.putString(WifiStateReceiver.KEY_DEFAULT_SSID_PWD, pwd).commit();
				return new Response(
						"<html>\n<meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n<meta http-equiv='refresh' content='1;url=' />\n<body>正在处理...请切换至新的WIFI再连接。</body></html>");
			} else if (parms.containsKey("record")) {
				final String record_state = parms.get("record_state");

				if (record_state.equals("on")) {
					RecordService.start(this);
				} else {
					RecordService.stop(this);
				}
				return new Response(
						"<html>\n<meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n<meta http-equiv='refresh' content='1;url=' />\n<body>正在处理...</body></html>");
			} else if (parms.containsKey("cfg_wifi_list")) { // cfg wifi list
				StringBuilder sb = new StringBuilder();
				sb.append("<html>\n");
				sb.append("<meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n");
				sb.append("<body>\n");

				sb.append("<script type='text/javascript' language='javascript'>\n");
				// check ssid pwd
				sb.append("function check_ssid_pwd(){\n");
				sb.append("var ssip_pwd = document.getElementById('ssid_pwd');\n");
				sb.append("if (ssip_pwd.value.length < 8){\n");
				sb.append("alert('密码不能小于8位！');\n");
				sb.append("return false;\n");
				sb.append("}\n");
				sb.append("return true;\n");
				sb.append("}\n");
				sb.append("</script>\n");

				String cSSID = Wifi.getCurrentSSID(this);
				Iterable<ScanResult> configuredNetworks = Wifi.getConfiguredNetworks(this);
				if (configuredNetworks != null) {
					sb.append("\t\t\t<form method='post' action='' onsubmit='return check_ssid_pwd()'>\n");
					sb.append("\t\t<h3 style='margin:0;padding:0'>周边WIFI接入点名称:</h3>\n");
					Iterator<ScanResult> it1 = configuredNetworks.iterator();
					while (it1.hasNext()) {
						ScanResult cfg = (ScanResult) it1.next();
						String sSID = cfg.SSID;
						if (sSID.startsWith("\"")) {
							sSID = sSID.substring(1);
						}
						if (sSID.endsWith("\"")) {
							sSID = sSID.substring(0, sSID.length() - 1);
						}
						sb.append(String.format("%s(%d)", sSID, 100 + cfg.level));
						// <a href='?ssid=TP-LINK_TINA&foget_pwd=1'>忘记密码</a>
						sb.append("<a href='?ssid=");
						sb.append(sSID);
						sb.append("&foget_ssid_pwd=1'>忘记密码</a>");
						sb.append("<input type='radio' name='ssid' ");
						String valueString = String.format("value='%s'", sSID);
						sb.append(valueString);
						if (cSSID.equals(sSID) || cSSID.equals(String.format("\"%s\"", sSID))) {
							sb.append("checked='checked'");
						}
						sb.append("/><br />\n");
					}
					sb.append("\t\t\t\t密码: <input id='ssid_pwd' type='password' name='password' value=''/>\n");
					sb.append("\t\t\t\t<input type='submit' name='wifi' value='连接'>\n");
					sb.append("\t\t\t\t<a href='?cfg_wifi_list=1'>刷新</a><br/>\n");
					sb.append("\t\t\t</form>\n");
				}
				sb.append("</body></html>\n");
				return new Response(sb.toString());
			} else if (parms.containsKey("foget_ssid_pwd")) { // 忘记wifi密码

				final String ssid = parms.get("ssid");
				new Thread() {
					@Override
					public void run() {
						Wifi.forgetWifi(ConfigServer.this, ssid);
						super.run();
					}

				}.start();

				return new Response(
						"<html>\n<meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n<meta http-equiv='refresh' content='1;url=/?cfg_wifi_list=1' />\n<body>正在处理...</body></html>");
			} else if (parms.containsKey("confirm")) {
				final String pwd = parms.get("pwd");
				if (pwd.equals(PWD)) {
					SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
					StringBuilder sb = new StringBuilder();
					sb.append("<html xmlns='http://www.w3.org/1999/xhtml'>\n");
					sb.append("<meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n");
					// head begin
					sb.append("<head>\n");
					sb.append("<script type='text/javascript' language='javascript'>\n");
					// mdf date time
					sb.append("function mdf_date_time(){\n");
					sb.append("var time = document.getElementById('date_time_text');\n");
					sb.append("var date = new Date();\n");
					sb.append("time.value=date.getTime();\n");
					sb.append("return true;\n");
					sb.append("};\n");
					sb.append("</script>\n");
					sb.append("</head>\n");

					// head end
					sb.append("\t<body>\n");
					sb.append("\t\t<h1 align='center'>配置与管理</h1>\n");

					// 添加APN
					sb.append("\t\t\t<form method='post' action=''><br/>\n");
					sb.append("\t\t<h3 style='margin:0;padding:0'>配置APN</h3>\n");
					sb.append("<label>名称:</label><input type='text' name='name' value=''/><label>卡号后四位:</label><input type='number' name='number' value=''/><input type='submit' name='add_apn' value='添加'>");
					sb.append("<br/>");
					sb.append("\t\t\t</form>\n");
					// 12、APN配置，但不显示所有可选运营商列表；
					if (G.USE_APN) {
						// 切换APN
						sb.append("\t\t\t<form method='post' action=''><br/>\n");
						sb.append("\t\t\t<h3 style='margin:0;padding:0'>切换APN</h3>\n");
						List<ApnNode> nodes = new ArrayList<Apn.ApnNode>();
						ApnNode defaultApn = null;
						try {
							Apn.getAll(this, nodes);
							defaultApn = Apn.getDefaultApn(this);
						} catch (Exception e) {
							e.printStackTrace();
						}
						if (defaultApn == null) {
							defaultApn = new ApnNode();
						}
						Iterator<Apn.ApnNode> it = nodes.iterator();
						while (it.hasNext()) {
							Apn.ApnNode node = (Apn.ApnNode) it.next();
							sb.append(node.name);
							sb.append("<input type='radio' name='apn_name' ");
							String valueString = String.format("value='%s'", node.name);
							sb.append(valueString);
							if (defaultApn.name.equals(node.name)) {
								sb.append("checked='checked'");
							}
							sb.append("/><br />");
						}
						sb.append("\t\t\t<input type='submit' name='switch_apn' value='确定'><br/></form>\n");
					}

					// 平台地址
					sb.append("\t\t\t<form  method='post' action=''>\n");
					sb.append("\t\t<h3 style='margin:0;padding:0'>平台</h3>\n");
					sb.append(String.format(
							"\t\t\t\t服务器IP地址: <input type='text' name='address' value='%s'/>\n",
							G.mAddres));
					sb.append(String.format(
							"\t\t\t\t端口: <input type='number' name='port' value='%s'/>\n", G.mPort));
					sb.append("\t\t\t\t<input type='submit' value='配置' />\n");
					sb.append("\t\t\t</form>\n");

					// 音频质量

					sb.append("\t\t\t<form  method='post' action=''>\n");
					sb.append("\t\t\t<h3 style='margin:0;padding:0'>音频</h3>\n");
					// freq begin
					if (G.USE_APN) {
						int freq = PreferenceManager.getDefaultSharedPreferences(this).getInt(
								G.KEY_AUDIO_FREQ, 44100);
						sb.append(String.format(
								"\t\t\t8khz<input type='radio' name='audio_fre' value='8' %s/>",
								freq == 8000 ? "checked='checked'" : ""));
						sb.append(String
								.format("\t\t\t16khz<input type='radio' name='audio_fre' value='16' %s/><br/>",
										freq == 16000 ? "checked='checked'" : ""));
						sb.append(String.format(
								"\t\t\t24khz<input type='radio' name='audio_fre' value='24' %s/>",
								freq == 24000 ? "checked='checked'" : ""));
						sb.append(String
								.format("\t\t\t44.1khz<input type='radio' name='audio_fre' value='44.1' %s/><br/>",
										freq == 44100 ? "checked='checked'" : ""));
					}
					// freq end
					if (pref.getBoolean(G.KEY_HIGH_QUALITY, true)) {
						sb.append("\t\t\t\t高品质音频：<input type='checkbox' name='quality' checked='1'/><br />\n");
					} else {
						sb.append("\t\t\t\t高品质音频：<input type='checkbox' name='quality'/><br />\n");
					}
					sb.append("\t\t\t\t<input type='submit' name='audio_cfg' value='配置' />\n");
					sb.append("\t\t\t</form>\n");

					// 登录状态
					sb.append("\t\t\t<form  method='post' action=''>\n");
					sb.append("\t\t<h3 style='margin:0;padding:0'>控制模块登录或退出“3G侦控平台”</h3>\n");
					sb.append("\t\t\t\t<input type='submit' name='login' value='登录'>\n");
					sb.append("\t\t\t\t<input type='submit'  name='logout'value='退出登录'>\n");
					sb.append("\t\t\t</form>\n");
					sb.append("\t\t<a href='?query_login_status=1'>查询登录状态</a><br/><br/>\n");

					// storage available begin
					sb.append("\t\t<h3 style='margin:0;padding:0'>设备存储</h3>\n");
					long available = storageAvailable();
					if (available == -1l) {
						sb.append(String.format("\t\t\t<label >可用存储空间:未知</label><br/>\n"));
					} else {
						sb.append(String.format("\t\t\t<label >可用存储空间:%.2fG</label><br/>\n",
								available * 1.0f / 1073741824f));
					}
					// storage available end

					// baterry begin
					sb.append("\t\t<h3 style='margin:0;padding:0'>设备电量</h3>\n");
					float percent = getBaterryPecent(this);
					String p = String.format("%.2f", percent * 100);
					p += "%";
					sb.append(String.format("\t\t\t<label >当前电池电量:%s</label><br/>\n", p));
					// baterry end

					// record switch
					sb.append("\t\t\t<form method='post' action=''>\n");
					sb.append("\t\t<h3 style='margin:0;padding:0'>模块本地录音功能:</h3>\n");
					boolean islocalRecord = false;
					islocalRecord = entity != null && entity.isLocalRecord();
					if (islocalRecord) {
						sb.append("\t\t\t\t开启<input type='radio' checked='checked' name='record_state' value='on' /><br />\n");
						sb.append("\t\t\t\t关闭<input type='radio' name='record_state' value='off' /><br />\n");
					} else {
						sb.append("\t\t\t\t开启<input type='radio' name='record_state' value='on' /><br />\n");
						sb.append("\t\t\t\t关闭<input type='radio'  checked='checked' name='record_state' value='off' /><br />\n");
					}
					sb.append("\t\t\t\t<input type='submit' name='record' value='修改'><br/>\n");
					sb.append("\t\t\t</form>\n");
					// record switch end

					// modify date time begin

					sb.append("<form id='mdf_dt' method='post' action='' onsubmit='return mdf_date_time()'><br/>\n");
					sb.append("<h3 style='margin:0;padding:0'>同步当前时间到模块</h3>\n");
					sb.append("<input type='text' id='date_time_text' name='time' value='0' style='display:none;'/>\n");
					sb.append("<input type='submit' name='mdy_date_time' value='同步' />\n");
					sb.append("</form>\n");

					// modify date time end
					sb.append("\t\t<a href='?list_files=1'>模块录制文件下载</a><br/>\n");
					sb.append("\t\t<a href='?white_list=1'>设置来电白名单</a><br/>\n");
					sb.append("\t\t<a href='?cfg_wifi_list=1'>刷新或管理WIFI列表</a><br/>\n");
					sb.append("\t</body>\n");
					sb.append("</html>\n");

					return new Response(sb.toString());
				} else {

				}
			}
			StringBuilder sb = new StringBuilder();
			sb.append("<html xmlns='http://www.w3.org/1999/xhtml'>\n");
			sb.append("<meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n");
			sb.append("\t<body>\n");

			sb.append("\t\t\t<form method='post' action=''><br/>\n");
			sb.append("\t\t<h3 style='margin:0;padding:0'>输入密码：</h3>\n");
			sb.append("<input type='password' name='pwd' value=''/>");
			sb.append("<input type='submit' name='confirm' value='确定'>");
			sb.append("\t\t\t</form>\n");
			sb.append("\t</body>\n");
			sb.append("</html>\n");
			return new Response(sb.toString());

		} else {
			if (parms.containsKey("delete")) {
				File f = new File(G.sRootPath, uri);
				if (f.isDirectory()) {

					String path = entity != null ? entity.getRecordingFilePath() : null;
					if ((path != null) && new File(path).getParent().equals(f.getPath())) {
						// 当前文件夹不能删
					} else {
						deleteDir(f);
					}
				} else {
					f.delete();
				}
				String parentUri = uri.subSequence(0, uri.lastIndexOf("/")).toString();
				if (parentUri.equals("")) {
					parentUri = "/?list_files=1";
				}
				return new Response(
						String.format(
								"<html><meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n<meta http-equiv='refresh' content='1;url=%s' />\n<body>%s%s.</body></html>",
								parentUri, f.getName(), f.exists() ? "未能删除" : "已删除"));
			} else if (parms.containsKey("download_all")) {
				uri = uri.substring(0, uri.lastIndexOf(".zip"));
				File f = new File(G.sRootPath, uri);
				if (f.isDirectory()) {
					try {
						zipDir(f.getPath(), null);
						final String zip = uri + ".zip";
						Response r = serveFile(zip, header, G.sRootPath, true);
						r.callback = new Runnable() {

							@Override
							public void run() {
								new File(G.sRootPath, zip).delete();
							}
						};
						return r;
					} catch (IOException e) {
						e.printStackTrace();
						String parentUri = uri.subSequence(0, uri.lastIndexOf("/")).toString();
						if (parentUri.equals("")) {
							parentUri = "/?list_files=1";
						}
						return new Response(
								String.format(
										"<html><meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n<meta http-equiv='refresh' content='3;url=%s' />\n<body>%s(%s).</body></html>",
										parentUri, "下载失败", e.getMessage()));
					}
				} else {
					return serveFile(uri, header, G.sRootPath, true);
				}
			} else if (parms.containsKey("download_and_delete")) {
				Response r = serveFile(uri, header, G.sRootPath, true);
				final String furi = uri;
				r.callback = new Runnable() {

					@Override
					public void run() {
						File file = new File(G.sRootPath, furi);
						file.delete();
					}
				};
				return r;
			} else {
				return serveFile(uri, header, G.sRootPath, true);
			}
		}
	}

	private void addFiles2Node(File rootFile, Node root, Document doc) {
		File[] fils = rootFile.listFiles(new FileFilter() {

			@Override
			public boolean accept(File pathname) {
				if (pathname.isDirectory() || pathname.getPath().endsWith(".aac")) {
					return true;
				}
				return false;
			}
		});
		for (int i = 0; i < fils.length; i++) {
			File f = fils[i];
			if (f.isDirectory()) {
				Element subRoot = doc.createElement("File");
				subRoot.setAttribute("Name", f.getName());
				addFiles2Node(f, subRoot, doc);
				root.appendChild(subRoot);
			} else {
				Element fnode = doc.createElement("File");
				fnode.setAttribute("Name", f.getName());
				root.appendChild(fnode);
			}
		}
	}

	/**
	 * 
	 * 
	 * @param file
	 * @return
	 */
	private String file2Msg(File file) {
		String length = "";
		String name = file.getName();
		if (file.isDirectory()) {
			length = "/";
		} else {
			long len = file.length();
			if (len < 1024)
				length += len + " bytes";
			else if (len < 1024 * 1024)
				length += len / 1024 + "." + (len % 1024 / 10 % 100) + " KB";
			else
				length += len / (1024 * 1024) + "." + len % (1024 * 1024) / 10 % 100 + " MB";
			length = String.format("&nbsp(%s)", length);
		}
		String result = String
				.format("<a href='%s?open_or_download=1'>%s</a>", name, name + length);
		if (file.isDirectory()) {
			result += String
					.format("&nbsp&nbsp&nbsp<a href='%s.zip?download_all=1'>打包下载</a>", name);
		}
		result += String.format("&nbsp&nbsp&nbsp<a href='%s?delete=1'>删除</a><br>\n", name);
		return result;
	}

	/**
	 * 如果是"/"，那么一定要加上list_files参数，才会列出文件 Serves file from homeDir and its'
	 * subdirectories (only). Uses only URI, ignores all headers and HTTP
	 * parameters.
	 */
	public Response serveFile(String uri, Map<String, String> header, String root,
			boolean allowDirectoryListing) {
		Response res = null;
		File homeDir = new File(root);
		// Make sure we won't die of an exception later
		if (!homeDir.isDirectory())
			res = new Response(Status.INTERNAL_ERROR, MIME_PLAINTEXT,
					"INTERNAL ERRROR: serveFile(): given homeDir is not a directory.");

		if (res == null) {
			// Remove URL arguments
			uri = uri.trim().replace(File.separatorChar, '/');
			if (uri.indexOf('?') >= 0)
				uri = uri.substring(0, uri.indexOf('?'));

			// Prohibit getting out of current directory
			if (uri.startsWith("..") || uri.endsWith("..") || uri.indexOf("../") >= 0)
				res = new Response(Status.FORBIDDEN, MIME_PLAINTEXT,
						"FORBIDDEN: Won't serve ../ for security reasons.");
		}

		File f = new File(homeDir, uri);
		if (res == null && !f.exists())
			res = new Response(Status.NOT_FOUND, MIME_PLAINTEXT, "Error 404, file not found.");

		// List the directory, if necessary
		if (res == null && f.isDirectory()) {
			// Browsers get confused without '/' after the
			// directory, send a redirect.
			if (!uri.endsWith("/")) {
				uri += "/";
				res = new Response(
						Status.REDIRECT,
						MIME_HTML,
						"<html>\n<meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n<body>Redirected: <a href=\""
								+ uri + "\">" + uri + "</a></body></html>");
				res.addHeader("Location", uri);
			}

			if (res == null) {
				// First try index.html and index.htm
				if (new File(f, "index.html").exists())
					f = new File(homeDir, uri + "/index.html");
				else if (new File(f, "index.htm").exists())
					f = new File(homeDir, uri + "/index.htm");
				// No index file, list the directory if it is readable
				else if (allowDirectoryListing && f.canRead()) {
					String[] files = f.list();
					String msg = "<html>\n<meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n<body><h1>Directory "
							+ uri + "</h1><br/>\n";
					// msg += "<form  action='' method='get'>\n";
					// msg += "<fieldset>\n<legend>文件信息</legend>";
					if (uri.length() > 1) {
						String u = uri.substring(0, uri.length() - 1);
						int slash = u.lastIndexOf('/');
						if (slash >= 0 && slash < u.length()) {
							String parentUri = uri.substring(0, slash + 1);
							if (parentUri.equals("/")) {
								parentUri += "?list_files=1";
							}
							msg += "<b><a href=\"" + parentUri + "\">..</a></b><br/>\n";
						}
					}

					if (files != null) {
						MyMPUEntity entity = G.sEntity;
						for (int i = 0; i < files.length; ++i) {
							File curFile = new File(f, files[i]);
							if (entity != null
									&& curFile.getName().equals(G.getRecordingFileName())) {
								continue;
							}
							msg += file2Msg(curFile);
						}
					}
					// msg += "<input type='text' name='fname' /><br/\n>";
					// msg += "</fieldset>";
					// msg +=
					// "<input type='submit' id='download' value='下载全部'><br/>\n";
					// msg += "</form>";
					msg += "</body></html>";
					res = new Response(Status.OK, MIME_HTML, msg);
				} else {
					res = new Response(Status.FORBIDDEN, MIME_PLAINTEXT,
							"FORBIDDEN: No directory listing.");
				}
			}
		}

		try {
			if (res == null) {
				// Get MIME type from file name extension, if possible
				String mime = null;
				int dot = f.getCanonicalPath().lastIndexOf('.');
				if (dot >= 0)
					mime = (String) theMimeTypes.get(f.getCanonicalPath().substring(dot + 1)
							.toLowerCase());
				if (mime == null)
					mime = MIME_DEFAULT_BINARY;

				// Calculate etag
				String etag = Integer.toHexString((f.getAbsolutePath() + f.lastModified() + "" + f
						.length()).hashCode());

				// Support (simple) skipping:
				long startFrom = 0;
				long endAt = -1;
				String range = header.get("range");
				if (range != null) {
					if (range.startsWith("bytes=")) {
						range = range.substring("bytes=".length());
						int minus = range.indexOf('-');
						try {
							if (minus > 0) {
								startFrom = Long.parseLong(range.substring(0, minus));
								endAt = Long.parseLong(range.substring(minus + 1));
							}
						} catch (NumberFormatException nfe) {
						}
					}
				}

				// Change return code and add Content-Range header when skipping
				// is requested
				long fileLen = f.length();
				if (range != null && startFrom >= 0) {
					if (startFrom >= fileLen) {
						res = new Response(Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "");
						res.addHeader("Content-Range", "bytes 0-0/" + fileLen);
						res.addHeader("ETag", etag);
					} else {
						if (endAt < 0)
							endAt = fileLen - 1;
						long newLen = endAt - startFrom + 1;
						if (newLen < 0)
							newLen = 0;

						final long dataLen = newLen;
						FileInputStream fis = new FileInputStream(f) {
							public int available() throws IOException {
								return (int) dataLen;
							}
						};
						fis.skip(startFrom);

						res = new Response(Status.PARTIAL_CONTENT, mime, fis);
						res.addHeader("Content-Length", "" + dataLen);
						res.addHeader("Content-Range", "bytes " + startFrom + "-" + endAt + "/"
								+ fileLen);
						res.addHeader("filename", f.getName());
						res.addHeader("ETag", etag);
					}
				} else {
					if (etag.equals(header.get("if-none-match")))
						res = new Response(Status.NOT_MODIFIED, mime, "");
					else {
						res = new Response(Status.OK, mime, new FileInputStream(f));
						res.addHeader("Content-Length", "" + fileLen);
						res.addHeader("filename", f.getName());
						res.addHeader("ETag", etag);
					}
				}
			}
		} catch (IOException ioe) {
			res = new Response(Status.FORBIDDEN, MIME_PLAINTEXT, "FORBIDDEN: Reading file failed.");
		}

		res.addHeader("Accept-Ranges", "bytes"); // Announce that the file
		// server accepts partial content requestes
		return res;
	}

	private static void deleteDir(File f) {
		File[] children = f.listFiles();
		for (File file : children) {
			if (file.isDirectory()) {
				deleteDir(file);
			} else {
				file.delete();
			}
		}
		f.delete();
	}

	private static void zipDir(String dir, ZipOutputStream out) throws IOException {
		boolean is_i_create = out == null;
		if (is_i_create) {
			String dstPath = String.format("%s.zip", dir);
			FileOutputStream dest = new FileOutputStream(dstPath);
			out = new ZipOutputStream(new BufferedOutputStream(dest));
		}
		int BUFFER = 1024;
		File f = new File(dir);
		File files[] = f.listFiles();

		for (int i = 0; i < files.length; i++) {
			File file = files[i];
			if (file.isDirectory()) {
				ZipEntry entry = new ZipEntry(file.getName());
				out.putNextEntry(entry);
				zipDir(file.getPath(), out);
				continue;
			}
			byte data[] = new byte[BUFFER];
			ZipEntry entry = new ZipEntry(file.getName());
			out.putNextEntry(entry);
			FileInputStream fi = new FileInputStream(file);
			BufferedInputStream origin = new BufferedInputStream(fi, BUFFER);
			int count;
			while ((count = origin.read(data, 0, BUFFER)) != -1) {
				out.write(data, 0, count);
			}
			origin.close();
		}
		if (is_i_create) {
			out.close();
		}
	}

	public static long storageAvailable() {
		String state = Environment.getExternalStorageState();
		try {
			if (Environment.MEDIA_MOUNTED.equals(state)) {
				StatFs sf = new StatFs(G.sRootPath);
				long blockSize = sf.getBlockSize();
				long availCount = sf.getAvailableBlocks();
				return availCount * blockSize;
			}
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
		return -1;
	}

	public static float getBaterryPecent(Context context) {
		Intent batteryInfoIntent = context.getApplicationContext().registerReceiver(null,
				new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

		int level = batteryInfoIntent.getIntExtra("level", 50);
		int scale = batteryInfoIntent.getIntExtra("scale", 100);
		return level * 1f / scale;
	}

}