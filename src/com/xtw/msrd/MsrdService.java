package com.xtw.msrd;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;

import com.xtw.msrd.G.LoginStatus;

public class MsrdService extends Service {

	public static final String KEY_LOGOUT = "logout";

	private final class ServiceHandler extends Handler {
		public ServiceHandler(Looper looper) {
			super(looper);
		}
	}

	private Looper mServiceLooper;
	private ServiceHandler mServiceHandler;
	private Runnable mLogoutRunnable = new Runnable() {

		@Override
		public void run() {
			if (G.getLoginStatus() != LoginStatus.STT_PRELOGIN) {
				G g = (G) getApplication();
				g.logout();
			}
		}
	};

	private Runnable mLoginRunnable = new Runnable() {

		@Override
		public void run() {
			if (G.getLoginStatus() == LoginStatus.STT_PRELOGIN
					&& G.networkAvailable(MsrdService.this)) {
				G g = (G) getApplication();
				g.login();
			}
		}
	};

	public void onCreate() {
		// TODO: It would be nice to have an option to hold a partial wakelock
		// during processing, and to have a static startService(Context, Intent)
		// method that would launch the service & hand off a wakelock.

		super.onCreate();
		HandlerThread thread = new HandlerThread("IntentService");
		thread.start();

		mServiceLooper = thread.getLooper();
		mServiceHandler = new ServiceHandler(mServiceLooper);
	}

	@Override
	public void onStart(Intent intent, int startId) {
		boolean logout = intent.getBooleanExtra(KEY_LOGOUT, false);
		if (logout) {
			mServiceHandler.removeCallbacks(mLogoutRunnable);
			mServiceHandler.post(mLogoutRunnable);
		} else {
			mServiceHandler.removeCallbacks(mLoginRunnable);
			mServiceHandler.post(mLoginRunnable);
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}

}
