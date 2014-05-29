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
		if (intent.getAction().equals(Intent.ACTION_MEDIA_MOUNTED)) {
			G.initRoot();
			if (TextUtils.isEmpty(G.sRootPath)) {
				G.log("init root error!");
				Toast.makeText(context, "初始化存储路径失败！", Toast.LENGTH_LONG).show();
				return;
			}
			G.log("start record by Mounter!");
			RecordService.start(context);
		} else {
			RecordService.stop(context);
			G.sRootPath = null;
			G.log("stop record by Mounter!");
		}
		G.log("MountFileSystemReceiver end" + intent.getAction() + ", uri : " + intent.getData());
	}
}
