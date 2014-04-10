package com.xtw.msrd;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import junit.framework.Assert;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.widget.Toast;
import c7.CRChannel;

import com.crearo.config.StorageOptions;
import com.crearo.config.Wifi;
import com.crearo.mpu.sdk.Common;
import com.crearo.mpu.sdk.MPUHandler;
import com.crearo.mpu.sdk.MPUHandler.NCCAllback;
import com.crearo.mpu.sdk.client.PUInfo;
import com.crearo.puserver.PUServerThread;

public class G extends Application implements OnSharedPreferenceChangeListener {
	private static final String DEFAULT_PORT = "8958";
	private static final String DEFAULT_ADDRESS = "58.211.11.100";

	public enum LoginStatus {
		STT_PRELOGIN, STT_LOGINING, STT_LOGINED;
	}

	/**
	 * true l,false f
	 */
	public static final boolean USE_APN = false;
	private volatile static LoginStatus mLoginStatus = LoginStatus.STT_PRELOGIN;
	private final List<Runnable> mLoginStatusChanedCallbacks = new ArrayList<Runnable>();

	public static final String KEY_SERVER_ADDRESS = "KEY_SERVER_ADDRESS";
	public static final String KEY_SERVER_PORT = "KEY_SERVER_PORT";
	public static final String KEY_SERVER_FIXADDR = "key_fixAddr";

	public static final String KEY_AUTO_ANSWER_ENABLE = "KEY_AUTO_ANSWER_ENABLE";

	public static final String KEY_AUTO_ANSWER_WHITE_LIST = "KEY_AUTO_ANSWER_WHITE_LIST";

	public static final String KEY_DIALING_TRIGER_MODE = "KEY_DIALING_TRIGER_MODE";
	public static final String KEY_DIALING_USE_3G_CARD = "KEY_DIALING_USE_3G_CARD";

	public static String mAddres;
	public static String mPort;
	public static boolean mFixAddr, mPreviewVideo;

	/**
	 * 0表示手动上线，1表示主动上线
	 */
	public static int sTriggerMode = 0;

	// public static final Executor sExecutor =
	// Executors.newFixedThreadPool(10);
	public static final Handler sUIHandler = new Handler();

	static MyMPUEntity mEntity = null;

	private static final String TAG = "G";

	public static final CharSequence KEY_SERVER_MORE = "KEY_SERVER_MORE";
	private static final String KEY_SERVER_PREVIEW_VIDEO = "key_preivew_video";
	public static final String KEY_HIGH_QUALITY = "key_high_quality";
	public static final String KEY_WHITE_LIST = "key_white_list";
	public static final String KEY_AUDIO_FREQ = "key_audio_freq";

	public static final String DEFAULT_SSID = USE_APN ? "123456" : "LiYinConfigure-WiFi007";
	public static final String DEFAULT_SSID_PWD = USE_APN ? "12344321" : "admin123";
	public static PUInfo sPUInfo;
	static {
		sPUInfo = new PUInfo();
	}
	private NCCAllback mChannelCallback;
	private Runnable mLoginLoopTask;
	/**
	 * 正在登录的线程，不等于null表示需要循环登录
	 */
	private Thread mLoginThread;
	public static String sRootPath;
	private static PUServerThread mServer;
	private static ConfigServer sConfigServer;

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Application#onCreate()
	 */
	@Override
	public void onCreate() {
		super.onCreate();
		initRoot();
		if (sRootPath == null) {
			Toast.makeText(this, "初始化存储路径失败！", Toast.LENGTH_LONG).show();
			return;
		}

		Thread.UncaughtExceptionHandler uncaughtExceptionHandler = new Thread.UncaughtExceptionHandler() {

			@Override
			public void uncaughtException(Thread thread, Throwable ex) {
				ex.printStackTrace();
				try {
					FileOutputStream os = new FileOutputStream(new File(String.format("%s/%s",
							sRootPath, "audiolog.txt")), true);
					SimpleDateFormat sdf = new SimpleDateFormat("yy-MM-dd HH.mm.ss");
					String dateStr = sdf.format(new Date());
					os.write(dateStr.getBytes());
					PrintStream err = new PrintStream(os);
					ex.printStackTrace(err);
					err.close();

					os.close();
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				System.exit(-1);
			}
		};
		Thread.setDefaultUncaughtExceptionHandler(uncaughtExceptionHandler);

		mEntity = new MyMPUEntity(this);
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
		sPUInfo.name = pref.getString(MPUHandler.KEY_PUNAME.toString(), android.os.Build.MODEL);
		sPUInfo.puid = pref.getString("key_puid", null);
		if (sPUInfo.puid == null) {
			sPUInfo.puid = Common.getPuid(this);
			pref.edit().putString("key_puid", sPUInfo.puid).commit();
		}
		sPUInfo.cameraName = pref.getString(MPUHandler.KEY_CAMNAME.toString(),
				android.os.Build.MODEL);
		sPUInfo.mMicName = pref
				.getString(MPUHandler.KEY_IA_NAME.toString(), android.os.Build.MODEL);
		sPUInfo.mSpeakerName = null;
		sPUInfo.mGPSName = null;// 暂时不支持GPS，在这里设置为null

		/**
		 * 断线重连
		 */
		mChannelCallback = mEntity.new NCCAllback() {

			@Override
			public void onErrorFetched(CRChannel arg0, int arg1) {
				Intent service = new Intent(G.this, MsrdService.class);
				service.putExtra(MsrdService.KEY_LOGOUT, true);
				startService(service);
				service = new Intent(G.this, MsrdService.class);
				startService(service);
			}
		};
		/**
		 * 断线重连Task
		 */
		mLoginLoopTask = new Runnable() {

			@Override
			public void run() {
				if (networkAvailable(G.this)) {
					mChannelCallback.onErrorFetched(null, 0);
				}
			}
		};
		File root = new File(sRootPath);
		root.mkdirs();
		try {
			InputStream is = getAssets().open("ic_launcher.png");
			FileOutputStream fos = new FileOutputStream(root + "/ic.ico");
			byte[] buffer = new byte[1024];
			int size = 0;
			while ((size = is.read(buffer)) != -1) {
				fos.write(buffer, 0, size);
			}
			is.close();
			fos.close();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		final SharedPreferences prf = PreferenceManager.getDefaultSharedPreferences(this);
		mAddres = prf.getString(KEY_SERVER_ADDRESS, DEFAULT_ADDRESS);
		mPort = prf.getString(KEY_SERVER_PORT, DEFAULT_PORT);
		mFixAddr = prf.getBoolean(KEY_SERVER_FIXADDR, true);
		mPreviewVideo = prf.getBoolean(KEY_SERVER_PREVIEW_VIDEO, false);
		prf.registerOnSharedPreferenceChangeListener(this);

		startService(new Intent(this, WifiAndPuServerService.class));
	}

	public static void initRoot() {
		StorageOptions.determineStorageOptions();
		String[] paths = StorageOptions.paths;
		if (paths == null || paths.length == 0) {
			return;
		}
		// 使用版本号作为设备的名称
		sRootPath = String.format("%s/%s", paths[0], "audio");
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences prf, String key) {
		if (key.equals(KEY_SERVER_ADDRESS) || key.equals(KEY_SERVER_PORT)) {
			mAddres = prf.getString(KEY_SERVER_ADDRESS, null);
			mPort = prf.getString(KEY_SERVER_PORT, DEFAULT_PORT);
			if (TextUtils.isEmpty(mAddres)) {
				logoutAndEndLoop();
			} else {

				Intent service = new Intent(this, MsrdService.class);
				service.putExtra(MsrdService.KEY_LOGOUT, true);
				startService(service);
				service = new Intent(this, MsrdService.class);
				startService(service);
			}
		} else if (key.equals(KEY_SERVER_FIXADDR)) {
			mFixAddr = prf.getBoolean(KEY_SERVER_FIXADDR, false);
			Intent service = new Intent(this, MsrdService.class);
			service.putExtra(MsrdService.KEY_LOGOUT, true);
			startService(service);
			startService(new Intent(this, MsrdService.class));
		} else if (key.equals(KEY_SERVER_PREVIEW_VIDEO)) {
			mPreviewVideo = prf.getBoolean(key, false);
		}
	}

	public boolean checkParam(boolean toast) {
		if (TextUtils.isEmpty(G.mAddres)) {
			if (toast) {
				Toast.makeText(getApplicationContext(), "平台地址不合法", Toast.LENGTH_SHORT).show();
			}
			return false;
		}
		if (TextUtils.isEmpty(G.mPort)) {
			if (toast) {
				Toast.makeText(getApplicationContext(), "平台端口不合法", Toast.LENGTH_SHORT).show();
			}
			return false;
		}
		return true;
	}

	public static LoginStatus getLoginStatus() {
		return mLoginStatus;
	}

	private void setLoginStatus(final LoginStatus newStatus) {
		if (newStatus == mLoginStatus) {
			return;
		}
		mLoginStatus = newStatus;
		Runnable runnable = new Runnable() {

			@Override
			public void run() {
				Iterator<Runnable> it = mLoginStatusChanedCallbacks.iterator();
				while (it.hasNext()) {
					Runnable run = (Runnable) it.next();
					run.run();
				}
			}
		};
		if (Looper.getMainLooper().getThread() == Thread.currentThread()) {
			runnable.run();
		} else {
			sUIHandler.post(runnable);
		}
	}

	public void login() {

		mLoginThread = Thread.currentThread();
		Assert.assertEquals(LoginStatus.STT_PRELOGIN, mLoginStatus);
		Assert.assertTrue(Looper.getMainLooper().getThread() != mLoginThread);

		mEntity.setChannelCallback(mChannelCallback);
		setLoginStatus(LoginStatus.STT_LOGINING);
		long loginBegin = System.currentTimeMillis();
		int r = mEntity.login(G.mAddres, Integer.parseInt(G.mPort), G.mFixAddr, "", sPUInfo);
		if (r != 0) {
			long timeSpend = System.currentTimeMillis() - loginBegin;
			if (timeSpend < 1000) {
				try {
					Thread.sleep(1000 - timeSpend);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			setLoginStatus(LoginStatus.STT_PRELOGIN);
			if (mLoginThread != null) {
				mEntity.removeCallbacks(mLoginLoopTask);
				mEntity.postDelayed(mLoginLoopTask, 3000);
			}
		} else {
			setLoginStatus(LoginStatus.STT_LOGINED);
		}

	}

	public void logout() {
		mEntity.logout();
		mEntity.removeCallbacks(mLoginLoopTask);
		setLoginStatus(LoginStatus.STT_PRELOGIN);
	}

	public void logoutAndEndLoop() {
		mLoginThread = null;
		mEntity.removeCallbacks(mLoginLoopTask);
		Intent service = new Intent(this, MsrdService.class);
		service.putExtra(MsrdService.KEY_LOGOUT, true);
		startService(service);
	}

	public void registerLoginStatusChangedCallback(Runnable callback) {
		Assert.assertTrue(Looper.getMainLooper().getThread() == Thread.currentThread());
		mLoginStatusChanedCallbacks.add(callback);
	}

	public void unRegisterLoginStatusChangedCallback(Runnable callback) {
		Assert.assertTrue(Looper.getMainLooper().getThread() == Thread.currentThread());
		mLoginStatusChanedCallbacks.remove(callback);
	}

	public static boolean networkAvailable(Context c) {
		ConnectivityManager connMgr = (ConnectivityManager) c
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
		return (networkInfo != null && networkInfo.isConnected());
	}

	public static void stopCameraPreview() {

	}

	/**
	 * 获取可用空间百分比
	 */
	public static int getAvailableStore() {
		int result = 0;
		try {
			// 取得sdcard文件路径
			StatFs statFs = new StatFs(Environment.getExternalStorageDirectory().getAbsolutePath());
			// 获取BLOCK数量
			float totalBlocks = statFs.getBlockCount();
			// 可使用的Block的数量
			float availaBlock = statFs.getAvailableBlocks();
			float s = availaBlock / totalBlocks;
			s *= 100;
			result = (int) s;
		} catch (Exception e) {
			// TODO: handle exception
		}
		return result;
	}

	public static void startServer(Context context) {
		if (mServer == null) {
			PUInfo info = new PUInfo();
			info.puid = sPUInfo.puid;
			info.name = "audio";
			info.cameraName = "camera";
			info.mMicName = "camera";
			info.mSpeakerName = null;
			info.mGPSName = null;

			PUServerThread p = new PUServerThread(context, info, 8866);
			p.start();
			p.setCallbackHandler(mEntity);
			mServer = p;
		}
		if (sConfigServer == null) {
			try {
				sConfigServer = new ConfigServer(context, G.sRootPath);
				sConfigServer.start();
			} catch (IOException e) {
				sConfigServer = null;
				e.printStackTrace();
			}
		}

	}

	public static void stopServer() {
		if (mServer != null) {
			mServer.setCallbackHandler(null);
			mServer.quit();
			mServer = null;
		}
		if (sConfigServer != null) {
			sConfigServer.stop();
			sConfigServer = null;
		}
	}
}
