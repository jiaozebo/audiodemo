package com.ampu;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.android.internal.telephony.ITelephony;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

public class PhoneReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		// TODO Auto-generated method stub
		System.out.println("action" + intent.getAction());
		if (intent.getAction().equals(Intent.ACTION_NEW_OUTGOING_CALL)) {
			// 如果是去电（拨出）
			System.out.println("拨出");
		} else {
			// 查了下android文档，貌似没有专门用于接收来电的action,所以，非去电即来电
			System.out.println("来电");
			TelephonyManager tm = (TelephonyManager) context
					.getSystemService(Service.TELEPHONY_SERVICE);
			tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE);
			
			endCall(context);
			// 设置一个监听器
		}
	}

	private void endCall(Context context) {
		TelephonyManager tManager = (TelephonyManager) context
				.getSystemService(Context.TELEPHONY_SERVICE);
		// 初始化iTelephony
		Class<TelephonyManager> c = TelephonyManager.class;
		Method getITelephonyMethod = null;
		try {
			getITelephonyMethod = c.getDeclaredMethod("getITelephony", (Class[]) null);
			getITelephonyMethod.setAccessible(true);
			ITelephony iTelephony = (ITelephony) getITelephonyMethod.invoke(tManager,
					(Object[]) null);
			iTelephony.endCall();
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	PhoneStateListener listener = new PhoneStateListener() {

		@Override
		public void onCallStateChanged(int state, String incomingNumber) {
			// TODO Auto-generated method stub
			// state 当前状态 incomingNumber,貌似没有去电的API
			super.onCallStateChanged(state, incomingNumber);
			switch (state) {
			case TelephonyManager.CALL_STATE_IDLE:
				System.out.println("挂断");
				break;
			case TelephonyManager.CALL_STATE_OFFHOOK:
				System.out.println("接听");
				break;
			case TelephonyManager.CALL_STATE_RINGING:
				System.out.println("响铃:来电号码" + incomingNumber);
				// 输出来电号码
				break;
			}
		}

	};
}