package com.xtw.msrd;

import android.app.IntentService;
import android.content.Intent;

import com.xtw.msrd.G.LoginStatus;

public class MsrdService extends IntentService {

	public MsrdService() {
		super("MsrdService");
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		G g = (G) getApplication();
		if (g.getLoginStatus() == LoginStatus.STT_PRELOGIN)
			g.login();
	}

}
