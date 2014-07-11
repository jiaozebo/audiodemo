package com.xtw.accc;

import client.CRClient;
import client.PeerUnit;
import android.app.Application;

public class AcccApp extends Application {

	public static CRClient sClient;
	public static PeerUnit sPeerUnit;

	@Override
	public void onCreate() {
		super.onCreate();
	}

}
