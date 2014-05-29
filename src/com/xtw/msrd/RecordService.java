package com.xtw.msrd;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

public class RecordService extends Service {
	public static final String KEY_START = "key_start_record";

	public RecordService() {
	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO: Return the communication channel to the service.
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);
		G.log("RecordService onStartCommand startrecord!");
		MyMPUEntity entity = G.sEntity;
		if (entity != null) {
			entity.setLocalRecord(true);
		}
		return START_REDELIVER_INTENT;
	}

	@Override
	public void onDestroy() {
		G.log("RecordService onStartCommand startrecord!");
		MyMPUEntity entity = G.sEntity;
		if (entity != null) {
			entity.setLocalRecord(false);
		}
		super.onDestroy();
	}

	public static void stop(Context context) {
		Intent intent = new Intent(context, RecordService.class);
		context.stopService(intent);
	}
	
	public static void start(Context context) {
		Intent intent = new Intent(context, RecordService.class);
		context.startService(intent);
	}

}
