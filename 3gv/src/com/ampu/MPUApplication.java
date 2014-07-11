package com.ampu;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.Thread.UncaughtExceptionHandler;

import util.CommonMethod;
import android.app.Application;
import android.os.Environment;
import android.os.Message;

import com.crearo.config.StorageOptions;
import com.crearo.mpu.sdk.AudioRunnable;
import com.crearo.mpu.sdk.CameraThread;
import com.crearo.mpu.sdk.GPSHandler;
import com.crearo.mpu.sdk.client.MPUEntity;
import com.crearo.puserver.PUDataChannel;

/**
 * @author John
 * @date 2011-4-26
 */
public class MPUApplication extends Application {
	private static final CharSequence ERROR_LINE = "\n\n\n";
	private static MPUEntity sHandler = null;
	/**
	 * 是否为定制版本
	 */
	protected static final boolean sOEMVersion = false;
	/**
	 * 测试
	 */
	public static final boolean VERSION = true;
	public static final String PRODUCE_ID = "00005";

	public MPUEntity getHandler() {
		return sHandler;
	}

	public static String PATH_AUTHORIZATION = Environment.getExternalStorageDirectory().getPath()
			+ "/3GV/Storage";

	public static String PATH_LOG = Environment.getExternalStorageDirectory().getPath()
			+ "/3GV/Storage";
	public static String PATH_STORAGE = Environment.getExternalStorageDirectory().getPath()
			+ "/3GV/Storage";
	public static String PATH_STORAGE_SNAPSHOT = PATH_STORAGE + "/Snapshot";
	public static String PATH_STORAGE_RECORD = PATH_STORAGE + "/Record";

	@Override
	public void onCreate() {
		super.onCreate();
		if (VERSION) {
			StorageOptions.determineStorageOptions();
			if (StorageOptions.paths != null && StorageOptions.paths.length != 0) {
				PATH_AUTHORIZATION = StorageOptions.paths[0] + "/3GV/Storage";
				PATH_LOG = StorageOptions.paths[0] + "/3GV/Storage";
				PATH_STORAGE = StorageOptions.paths[0] + "/3GV/Storage";
				PATH_STORAGE_SNAPSHOT = PATH_STORAGE + "/Snapshot";
				PATH_STORAGE_RECORD = PATH_STORAGE + "/Record";
			}
		}
		File file = new File(PATH_AUTHORIZATION);
		file.mkdirs();
		file = new File(PATH_LOG);
		file.mkdirs();
		file = new File(PATH_STORAGE_SNAPSHOT);
		file.mkdirs();
		file = new File(PATH_STORAGE_RECORD);
		file.mkdirs();
		sHandler = new MPUEntity(this) {

			@Override
			public void handleMessage(Message msg) {
				super.handleMessage(msg);
				if (msg.what == 0x3600 || msg.what == 0x3601) {// puserverthread
					int resType = msg.arg1;
					PUDataChannel pdc = (PUDataChannel) msg.obj;

					if (resType == 0) {// iv
						CameraThread cameraThread = mCameraThread;
						if (cameraThread != null)
							if (msg.what == 0x3600) {
								cameraThread.addPUDataChannel(pdc);
							} else {
								cameraThread.removePUDataChannel(pdc);
							}
					} else if (resType == 1) {// ia
						AudioRunnable ar = AudioRunnable.singleton();
						if (msg.what == 0x3600) {
							ar.addPUDataChannel(pdc);
							if (!ar.isActive()) {
								ar.start();
							}
						} else {
							ar.removePUDataChannel(pdc);
						}
					} else if (resType == 3) {
						GPSHandler gpsHandler = mGpsHandler;
						if (gpsHandler == null) {
							gpsHandler = new GPSHandler(MPUApplication.this, null);
							mGpsHandler = gpsHandler;
						}
						if (msg.what == 0x3600) {
							gpsHandler.addPUDataChannel(pdc);
						} else {
							gpsHandler.removePUDataChannel(pdc);
						}
					}
				}
			}

		};
		sHandler.setRecordDirectory(PATH_STORAGE_RECORD);
		Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {

			@Override
			public void uncaughtException(Thread arg0, Throwable e) {
				e.printStackTrace();
				int nRet = CommonMethod.createDirectorys(PATH_LOG);
				if (nRet == 0) {
					File file = new File(PATH_LOG, "/log.txt");
					if (CommonMethod.createFile(file)) {
						PrintStream err;
						OutputStream os;
						try {
							java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat(
									"yy-MM-dd HH.mm.ss");
							String time = formatter.format(new java.util.Date());
							os = new FileOutputStream(file, true);
							err = new PrintStream(os);
							err.append(ERROR_LINE);
							err.append(time);
							err.append(ERROR_LINE);
							e.printStackTrace(err);
							os.close();
							err.close();
						} catch (FileNotFoundException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						} catch (IOException e2) {
							// TODO Auto-generated catch block
							e2.printStackTrace();
						}
					}
				}

				android.os.Process.killProcess(android.os.Process.myPid());
				System.exit(10);
			}
		});
		MPUSplasher.globalInit(this);
	}

}
