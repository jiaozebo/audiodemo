package com.xtw.msrd;

import java.io.IOException;

import com.crearo.puserver.PUServerThread;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class WifiAndPuServerService extends Service {

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		try {
			new ConfigServer(this, G.sRootPath).start();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		PUServerThread thread = new PUServerThread(this, G.sPUInfo, 8866);

		G g = (G) getApplication();
		MyMPUEntity e = g.mEntity;
		if (e != null) {
			thread.setCallbackHandler(e);
//			e.startOrRestart();
		}
		thread.start();

		if (g.checkParam(true)) {
			startService(new Intent(this, MsrdService.class));
		}
		return super.onStartCommand(intent, flags, startId);
	}

}
