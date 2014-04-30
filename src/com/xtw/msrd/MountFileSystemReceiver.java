package com.xtw.msrd;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.widget.Toast;

public class MountFileSystemReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		G.log("MountFileSystemReceiver begin" + intent.getAction() + ", uri : " + intent.getData());
		Intent service = new Intent(context, WifiAndPuServerService.class);
		if (intent.getAction().equals(Intent.ACTION_MEDIA_MOUNTED)) {
			G.initRoot();
			if (TextUtils.isEmpty(G.sRootPath)) {
				G.log("init root error!");
				Toast.makeText(context, "初始化存储路径失败！", Toast.LENGTH_LONG).show();
				return;
			}
			service.putExtra(WifiAndPuServerService.KEY_ACTION_START_AUDIO, true);
			context.startService(service);
		} else {
			service.putExtra(WifiAndPuServerService.KEY_ACTION_START_AUDIO, false);
			context.startService(service);
		}
		G.log("MountFileSystemReceiver end" + intent.getAction() + ", uri : " + intent.getData());
	}
}
