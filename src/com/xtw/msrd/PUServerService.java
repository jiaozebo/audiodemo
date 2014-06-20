package com.xtw.msrd;

import java.util.Iterator;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.Process;
import android.preference.PreferenceManager;

import com.crearo.config.Wifi;
import com.crearo.mpu.sdk.client.PUInfo;
import com.crearo.puserver.PUServerThread;

/**
 * pu server and watch dog
 * 
 */
public class PUServerService extends Service {
	private PUServerThread mPUThread;
	private Thread mWatchThread;

	public PUServerService() {
	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO: Return the communication channel to the service.
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);
		if (mPUThread == null) {
			PUInfo info = new PUInfo();
			info.puid = G.sPUInfo.puid;
			info.name = "audio";
			info.cameraName = "camera";
			info.mMicName = "camera";
			info.mSpeakerName = null;
			info.mGPSName = null;
			mPUThread = new PUServerThread(this, info, 8866);
			mPUThread.start();
			mPUThread.setCallbackHandler(G.sEntity);
		}
		if (mWatchThread == null) {
			mWatchThread = new Thread("WATCHER") {

				@Override
				public void run() {
					String prev = null;
					android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
					while (mWatchThread != null) {
						try {
							Thread.sleep(10000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
						final WifiInfo connectionInfo = wifiManager.getConnectionInfo();
						if (connectionInfo == null) {// unavailable
							continue;
						}
						boolean equalDefault = false;

						String ssid = connectionInfo.getSSID();
						if (connectionInfo.getNetworkId() != -1 && ssid != null) {
							if (ssid.startsWith("\"")) {
								ssid = ssid.substring(1);
							}
							if (ssid.endsWith("\"")) {
								ssid = ssid.substring(0, ssid.length() - 1);
							}
							if (G.DEFAULT_SSID.equals(ssid)) {
								equalDefault = true;
							}
						}
						if (equalDefault) { // same as default
							continue;
						}

						Iterable<ScanResult> iterable = Wifi
								.getConfiguredNetworks(PUServerService.this);
						if (iterable == null) {
							continue;
						}
						boolean foundDefault = false;
						Iterator<ScanResult> it = iterable.iterator();
						while (it.hasNext()) {
							ScanResult cfg = (ScanResult) it.next();
							String sSID = cfg.SSID;
							if (sSID.startsWith("\"")) {
								sSID = sSID.substring(1);
							}
							if (sSID.endsWith("\"")) {
								sSID = sSID.substring(0, sSID.length() - 1);
							}

							if (sSID.equals(G.DEFAULT_SSID)) {
								prev = connectionInfo.getSSID();
								PreferenceManager
										.getDefaultSharedPreferences(PUServerService.this)
										.edit()
										.putString(WifiStateReceiver.KEY_DEFAULT_SSID,
												G.DEFAULT_SSID)
										.putString(WifiStateReceiver.KEY_DEFAULT_SSID_PWD,
												G.DEFAULT_SSID_PWD).commit();
								Wifi.connectWifi(PUServerService.this, G.DEFAULT_SSID,
										G.DEFAULT_SSID_PWD);
								foundDefault = true;
								break;
							}
						}

						if (!foundDefault && connectionInfo.getNetworkId() == -1) { // set
							if (prev != null) { // if no available , try old
								Wifi.connectWifi(PUServerService.this, prev, G.DEFAULT_SSID_PWD);
							} else {
								// we can't do noting,wait for default
							}
						}
					}
				}

			};
			mWatchThread.start();
		}
		return START_REDELIVER_INTENT;
	}

	@Override
	public void onDestroy() {
		if (mPUThread != null) {
			mPUThread.quit();
			mPUThread = null;
		}
		Thread thread = mWatchThread;
		if (thread != null) {
			mWatchThread = null;
			thread.interrupt();
			try {
				thread.join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		super.onDestroy();
	}

}
