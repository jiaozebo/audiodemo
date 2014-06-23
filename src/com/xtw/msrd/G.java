package com.xtw.msrd;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import util.CommonMethod;
import android.annotation.TargetApi;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.crearo.config.StorageOptions;
import com.crearo.mpu.sdk.Common;
import com.crearo.mpu.sdk.MPUHandler;
import com.crearo.mpu.sdk.client.PUInfo;

public class G extends Application implements OnSharedPreferenceChangeListener {
	private static final String DEFAULT_PORT = "8958";
	private static final String DEFAULT_ADDRESS = "58.211.11.100";

	public static final int STT_PRELOGIN = -1;
	public static final int STT_LOGINING = 0;
	public static final int STT_LOGINED = 1;
	/**
	 * true l,false f
	 */
	public static boolean USE_APN = false;
	private volatile static int mLoginStatus = STT_PRELOGIN;

	public static final String KEY_SERVER_ADDRESS = "KEY_SERVER_ADDRESS";
	public static final String KEY_SERVER_PORT = "KEY_SERVER_PORT";
	public static final String KEY_SERVER_FIXADDR = "key_fixAddr";

	public static final String KEY_AUTO_ANSWER_ENABLE = "KEY_AUTO_ANSWER_ENABLE";

	public static final String KEY_AUTO_ANSWER_WHITE_LIST = "KEY_AUTO_ANSWER_WHITE_LIST";

	public static final String KEY_DIALING_TRIGER_MODE = "KEY_DIALING_TRIGER_MODE";
	public static final String KEY_DIALING_USE_3G_CARD = "KEY_DIALING_USE_3G_CARD";

	public static String mAddres;
	public static int mPort;
	public static boolean mFixAddr, mPreviewVideo;

	/**
	 * 0表示手动上线，1表示主动上线
	 */
	public static int sTriggerMode = 0;

	// public static final Executor sExecutor =
	// Executors.newFixedThreadPool(10);
	public static final Handler sUIHandler = new Handler();

	static MyMPUEntity sEntity = null;

	private static final String TAG = "G";

	public static final CharSequence KEY_SERVER_MORE = "KEY_SERVER_MORE";
	private static final String KEY_SERVER_PREVIEW_VIDEO = "key_preivew_video";
	public static final String KEY_HIGH_QUALITY = "key_high_quality";
	public static final String KEY_WHITE_LIST = "key_white_list";
	public static final String KEY_AUDIO_FREQ = "key_audio_freq";

	// public static final String DEFAULT_SSID = USE_APN ? "123456" :
	// "LiYinConfigure-WiFi007";
	// public static final String DEFAULT_SSID_PWD = USE_APN ? "12344321" :
	// "admin123";

	public static final String DEFAULT_SSID = "123_zxcvbnm";
	public static final String DEFAULT_SSID_PWD = "12344321";
	public static PUInfo sPUInfo;
	static {
		sPUInfo = new PUInfo();
	}
	/**
	 * 正在登录的线程，不等于null表示需要循环登录
	 */
	public volatile static String sRootPath;

	/**
	 * 该值表示是否在mount了之后启动录像
	 */
	public static boolean sIsAbortToRecordAfterMounted = true;
	public static String sVersionCode = null;
	public static String sCurrentRecordFilePath;

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Application#onCreate()
	 */
	@Override
	public void onCreate() {
		super.onCreate();
		try {
			sVersionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
		} catch (NameNotFoundException e1) {
			sVersionCode = "0";
			e1.printStackTrace();
		}
		USE_APN = sVersionCode.contains(".apn");
		Thread.UncaughtExceptionHandler uncaughtExceptionHandler = new Thread.UncaughtExceptionHandler() {

			@Override
			public void uncaughtException(Thread thread, Throwable ex) {
				ex.printStackTrace();
				try {
					FileOutputStream os = new FileOutputStream(new File(String.format("%s/%s.log",
							sRootPath, sVersionCode)), true);
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

		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
		sPUInfo.name = pref.getString(MPUHandler.KEY_PUNAME.toString(), "丽音模块");
		sPUInfo.puid = pref.getString("key_puid", null);
		if (sPUInfo.puid == null) {
			sPUInfo.puid = Common.getPuid(this);
			pref.edit().putString("key_puid", sPUInfo.puid).commit();
		}
		sPUInfo.cameraName = pref.getString(MPUHandler.KEY_CAMNAME.toString(), "camera");
		sPUInfo.mMicName = pref.getString(MPUHandler.KEY_IA_NAME.toString(), "audio");
		sPUInfo.mSpeakerName = null;
		sPUInfo.mGPSName = null;// 暂时不支持GPS，在这里设置为null

		final SharedPreferences prf = PreferenceManager.getDefaultSharedPreferences(this);
		mAddres = prf.getString(KEY_SERVER_ADDRESS, DEFAULT_ADDRESS);
		try {
			mPort = -1;
			mPort = Integer.parseInt(prf.getString(KEY_SERVER_PORT, DEFAULT_PORT));
		} catch (Exception e) {
			e.printStackTrace();
		}
		mFixAddr = prf.getBoolean(KEY_SERVER_FIXADDR, true);
		mPreviewVideo = prf.getBoolean(KEY_SERVER_PREVIEW_VIDEO, false);
		prf.registerOnSharedPreferenceChangeListener(this);

		sEntity = new MyMPUEntity(this);

		// wifi 配置
		ConfigServer.start(this, null, 8080);

		// 直连
		Intent i = new Intent(this, PUServerService.class);
		this.startService(i);

		initRoot();
		if (!TextUtils.isEmpty(sRootPath)) {
			log("start record by G!");
			RecordService.start(this);
		}
	}

	public static void initRoot() {
		// log(StorageOptions.MNT_SDCARD);
		// StorageOptions.readMountsFile();
		// String storager1 = "/storage/sdcard1";
		// boolean contain = false;
		// for (String mount : StorageOptions.mMounts) {
		// log("MOUNT1: " + mount);
		// if (mount.equals(storager1)) {
		// contain = true;
		// }
		// }
		// StorageOptions.readVoldFile();
		// for (String vold : StorageOptions.mVold) {
		// log("mVold: " + vold);
		// }
		// StorageOptions.compareMountsWithVold();
		// for (String mount : StorageOptions.mMounts) {
		// log("MOUNT2: " + mount);
		// }
		// if (!contain) {
		// StorageOptions.mMounts.add(storager1);
		// }
		// File root = new File(storager1);
		// log("storage exists " + root.exists());
		// log("storage isDirectory " + root.isDirectory());
		// log("storage canWrite " + root.canWrite());
		// log(storager1 + "/11111", "ceshi是否写入成功");
		// StorageOptions.testAndCleanMountsList();
		// for (String mount : StorageOptions.mMounts) {
		// log("MOUNT3: " + mount);
		// }
		// StorageOptions.setProperties();

		StorageOptions.determineStorageOptions();
		String[] paths = StorageOptions.paths;
		if (paths == null || paths.length == 0) {
			log("no path found !!!");
			return;
		}
		for (String path : paths) {
			log("PATH: " + path);
		}
		// 使用版本号作为设备的名称
		sRootPath = String.format("%s/%s", paths[0], "audio");
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences prf, String key) {
		if (key.equals(KEY_SERVER_ADDRESS) || key.equals(KEY_SERVER_PORT)) {
			mAddres = prf.getString(KEY_SERVER_ADDRESS, null);
			try {
				mPort = -1;
				mPort = Integer.parseInt(prf.getString(KEY_SERVER_PORT, DEFAULT_PORT));
			} catch (Exception e) {
				e.printStackTrace();
			}
			NCIntentService.stopNC(this);
			if (checkParam(false)) {
				NCIntentService.startNC(this, mAddres, mPort);
			}
		} else if (key.equals(KEY_SERVER_FIXADDR)) {
			mFixAddr = prf.getBoolean(KEY_SERVER_FIXADDR, true);
			NCIntentService.stopNC(this);
			if (checkParam(false)) {
				NCIntentService.startNC(this, mAddres, mPort);
			}
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
		if (G.mPort == -1) {
			if (toast) {
				Toast.makeText(getApplicationContext(), "平台端口不合法", Toast.LENGTH_SHORT).show();
			}
			return false;
		}
		return true;
	}

	public static int getLoginStatus() {
		return mLoginStatus;
	}

	public static void setLoginStatus(final int newStatus) {
		mLoginStatus = newStatus;
	}

	public void login() throws InterruptedException {
		if (sEntity == null) {
			return;
		}
		long loginBegin = System.currentTimeMillis();
		int r = sEntity.loginBlock(G.mAddres, G.mPort, G.mFixAddr, "", sPUInfo);
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
		} else {
		}

	}

	public void logout() {
		sEntity.logout();
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

	public static void log(String message) {
		log(null, message);
	}

	public static synchronized void log(String path, String message) {
		Log.e(TAG, message);
		if (path == null) {
			if (TextUtils.isEmpty(G.sRootPath)) {
				return;
			}

			path = new File(String.format("%s/%s.log", G.sRootPath, sVersionCode)).getPath();
		}
		CommonMethod.save2File(message, path, true);
	}

	@TargetApi(Build.VERSION_CODES.KITKAT)
	public static String getStorageState() {
		String state = null;
		if (VERSION.SDK_INT >= VERSION_CODES.KITKAT) {
			state = Environment.getStorageState(new File(G.sRootPath));
		} else {
			state = Environment.getExternalStorageState();
		}
		return state;
	}

	public static String getRecordingFileName() {
		if (sCurrentRecordFilePath == null) {
			return null;
		}
		return new File(sCurrentRecordFilePath).getName();
	}
}
