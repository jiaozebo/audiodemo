package com.ampu;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import util.MD5;
import util.MyLog;
import android.R.bool;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore.Video;
import android.telephony.TelephonyManager;
import android.util.ACommonMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;
import c7.CRChannel;
import c7.LoginInfo;
import c7.PUParam;

import com.crearo.mpu.sdk.Common;
import com.crearo.mpu.sdk.MPUHandler;
import com.crearo.mpu.sdk.client.PUInfo;
import com.crearo.mpu.sdk.client.VideoParam;
import com.crearo.puserver.PUServerThread;

public class OperationItems extends Activity {
	private static final int REQUEST_CODE_WORK_ACTIVITY = 100;

	public static final Integer[] images = MPUApplication.VERSION ? new Integer[] {
			R.drawable.serve, R.drawable.storager, R.drawable.setting } : new Integer[] {
			R.drawable.serve, R.drawable.storager, R.drawable.monitor, R.drawable.setting,
			R.drawable.help, R.drawable.about };

	public static final int[] TEXTS = MPUApplication.VERSION ? new int[] { R.string.startService,
			R.string.storageManagement, R.string.systemSetting } : new int[] {
			R.string.startService, R.string.storageManagement, R.string.mobileSureillance,
			R.string.systemSetting, R.string.help, R.string.about };

	protected static final String AUTO_LOGIN = "auto_login";

	protected static final String tag = "OperationItems";

	private static PUServerThread sServer;

	private SimpleAdapter simpleAdapter;
	private GridView gridView;

	private static final int ERROR_SERVER_INVALID = -1;
	private static final int ERROR_PUID_INVALID = -2;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.gridview);

		// ���GridView
		gridView = (GridView) findViewById(R.id.GridView);

		simpleAdapter = new SimpleAdapter(this, fillMap(), R.layout.griditem, new String[] {
				"imageView", "imageTitle", "index" }, new int[] { R.id.imageView, R.id.imageTitle });
		gridView.setAdapter(simpleAdapter);

		GridView.OnItemClickListener gridviewListener = new GridView.OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> adapter, View arg1, int position, long arg3) {
				if (!checkAccredit(OperationItems.this)) {
					Toast.makeText(OperationItems.this, R.string._authorization, Toast.LENGTH_SHORT)
							.show();
					return;
				}
				GridView gv = (GridView) adapter;
				@SuppressWarnings("unchecked")
				HashMap<String, Integer> itemData = (HashMap<String, Integer>) gv
						.getItemAtPosition(position);
				int index = itemData.get("index");

				final Intent intent = new Intent();
				if (index == R.string.startService) {
					final MPUHandler mpu = ((MPUApplication) getApplication()).getHandler();
					// final NC7 nc = ((MPUApplication)
					// getApplication()).getNc7();
					final LoginInfo loginInfo = new LoginInfo();
					final int result = initLoginInfo(loginInfo);
					if (result == 0) {
						ProgressDialog dlg = new ProgressDialog(OperationItems.this);
						dlg.setMessage(getString(R.string.logining));
						dlg.setIcon(R.drawable.icon);
						final WeakReference<ProgressDialog> weakDlg = new WeakReference<ProgressDialog>(
								dlg);
						final Thread loginThread = new Thread() {
							@Override
							public void run() {
								super.run();
								final int nRet = mpu.login(loginInfo);
								if (weakDlg.get() == null) {
									return;
								}
								weakDlg.get().dismiss();
								runOnUiThread(new Runnable() {
									public void run() {
										if (nRet == 0 && weakDlg.get() != null) {
											mpu.setChannelCallback(mpu.new NCCAllback() {

												@Override
												public void onErrorFetched(CRChannel arg0, int arg1) {
													finishActivity(REQUEST_CODE_WORK_ACTIVITY);
													Toast.makeText(OperationItems.this,
															R.string.connection_reset,
															Toast.LENGTH_SHORT).show();
												}
											});
											intent.setClass(OperationItems.this, WorkActivity.class);
											startActivityForResult(intent,
													REQUEST_CODE_WORK_ACTIVITY);
										} else {
											Toast.makeText(
													OperationItems.this,
													DisplayFilter.translate(OperationItems.this,
															nRet), Toast.LENGTH_LONG).show();
											mpu.close();
										}
									}
								});
							}
						};
						dlg.setOnCancelListener(new DialogInterface.OnCancelListener() {

							@Override
							public void onCancel(DialogInterface dialog) {
								weakDlg.clear();
								Log.d(tag, "before interrupt");
								loginThread.interrupt();
								Log.d(tag, "before close");
								mpu.close();
								try {
									loginThread.join();
								} catch (InterruptedException e) {
									loginThread.interrupt();
									e.printStackTrace();
								}
							}
						});
						loginThread.start();
						dlg.show();
					} else if (result == ERROR_SERVER_INVALID) {
						new AlertDialog.Builder(OperationItems.this)
								.setMessage(R.string.addr_unreachable)
								.setPositiveButton(R.string.set,
										new DialogInterface.OnClickListener() {

											@Override
											public void onClick(DialogInterface dialog, int which) {
												Intent intent = new Intent();
												intent.setClass(OperationItems.this,
														SetActivity.class);
												intent.putExtra(SetActivity.KEY_PREFERENCE,
														SetActivity.KEY_SERVER);
												startActivity(intent);
											}
										}).setNegativeButton(R.string.cancel, null).show();
					} else if (result == ERROR_PUID_INVALID) {
						Toast.makeText(OperationItems.this, R.string.puid_invalid,
								Toast.LENGTH_LONG).show();
					}
				} else if (index == R.string.storageManagement) {
					File file = new File(MPUApplication.PATH_STORAGE);
					if (file.exists()) {
						intent.setClass(OperationItems.this, StorageFilesActivity.class);
						startActivity(intent);
					} else {
						Toast.makeText(OperationItems.this, R.string.pleaseCheckSDCard,
								Toast.LENGTH_SHORT).show();
					}
				} else if (index == R.string.mobileSureillance) {
					intent.setClassName("com.crearo.mcu", "com.crearo.mcu.Login");
					Log.d(tag, intent.toString());
					try {
						startActivity(intent);
						overridePendingTransition(R.anim.slide_left, R.anim.slide_left);
					} catch (Exception e) {
						e.printStackTrace();
						Toast.makeText(OperationItems.this, R.string.noMCU, Toast.LENGTH_SHORT)
								.show();
					}
				} else if (index == R.string.systemSetting) {
					intent.setClass(OperationItems.this, SetActivity.class);
					startActivity(intent);
					overridePendingTransition(R.anim.zoom_enter, R.anim.zoom_exit);
				} else if (index == R.string.help) {
				} else if (index == R.string.about) {
					AlertDialog.Builder builder = new AlertDialog.Builder(OperationItems.this);
					LayoutInflater inflater = LayoutInflater.from(OperationItems.this);
					View view = inflater.inflate(
							MPUApplication.sOEMVersion ? R.layout.about_common_version
									: R.layout.about, null);
					TextView ver = (TextView) view.findViewById(R.id.tv_version);
					ver.setText("V" + ACommonMethod.getVersion(OperationItems.this));
					builder.setIcon(R.drawable.icon)
							.setTitle(getResources().getString(R.string.app_name)).setView(view)
							.setPositiveButton(R.string.ok, null).show();
				}
			}

		};
		gridView.setOnItemClickListener(gridviewListener);
	}

	private int initLoginInfo(LoginInfo info) {
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
		final String addr = sp.getString(SetActivity.KEY_ADDRESS.toString(), null);
		final String strPort = sp.getString(SetActivity.KEY_PORT.toString(), null);
		int port = -1;
		try {
			port = Integer.parseInt(strPort);
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (addr == null || port == -1) {
			return ERROR_SERVER_INVALID;
		}
		final String pwd = sp.getString(SetActivity.KEY_PASSWORD.toString(), "");
		String puid = sp.getString(SetActivity.KEY_PUID.toString(), "");
		if (puid.length() != 18) {
			return ERROR_PUID_INVALID;
		}
		info.addr = addr;
		info.port = port;
		info.password = pwd;
		info.binPswHash = MD5.encrypt(info.password.getBytes());
		info.dvcId = ACommonMethod.getDeviceId(OperationItems.this);
		info.param = new PUParam();
		info.param.ProducerID = MPUApplication.PRODUCE_ID;
		info.param.mCurrentFrameRate = sp.getInt(VideoParam.KEY_INT_FRAME_RATE, 20);
		info.param.mCurrentBitRate = sp.getInt(VideoParam.KEY_INT_BIT_RATE, 150);
		info.isFixAddr = sp.getBoolean(SetActivity.KEY_FIXADDR.toString(), true);
		Common.initParam(info.param, this);
		return 0;
	}

	public List<Map<String, Object>> fillMap() {
		List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
		for (int i = 0, j = TEXTS.length; i < j; i++) {
			Map<String, Object> map = new HashMap<String, Object>();
			map.put("imageView", images[i]);
			map.put("imageTitle", getString(TEXTS[i]));
			map.put("index", TEXTS[i]);
			list.add(map);
		}
		return list;
	}

	public static boolean checkAccredit(Context context) {
		boolean justReturn = true;
		if (justReturn) {
			return true;
		}
		File dir = new File(MPUApplication.PATH_AUTHORIZATION);
		boolean bRet = false;
		boolean bRet1 = false;
		if (!dir.exists()) {
			bRet = false;
			dir.mkdirs();
		} else {
			bRet = true;
		}
		File flog = new java.io.File(dir.getPath(), "log.txt");
		if (!flog.exists()) {
			try {
				flog.createNewFile();
			} catch (IOException e) {
				return false;
			}
		}
		MyLog log = new MyLog(MPUApplication.PATH_AUTHORIZATION + "/log.txt");

		log.info("version : " + Common.getVersion(context));
		log.info(bRet ? "dir already exist." : "dir not exist.");

		byte[] token = null;
		token = getToken(context);
		if (token == null) {
			log.info("token is null");
			log.close();
			return false;
		}
		log.info("token got");
		File accredit = new java.io.File(dir.getPath(), "CreditToken.txt");
		if (!accredit.exists()) {
			// 如果没有授权文件,就产生一个"Token.txt"文件
			log.info("no CreditToken");
			bRet1 = false;
			File src = new java.io.File(dir.getPath(), "Token.txt");
			if (!src.exists()) {
				log.info("no Token");
				try {
					src.createNewFile();
					log.info("Token created");
				} catch (IOException e) {
					log.info("create token IOException : " + e.getMessage());
				}
			}
			try {
				FileOutputStream fos = new FileOutputStream(src);
				log.info("write Token");
				fos.write(token);
				fos.close();
				log.info("token wrote");
			} catch (FileNotFoundException e) {
				log.info("write FileNotFoundException " + e.getMessage());
				bRet1 = false;
			} catch (IOException e) {
				log.info("write IOException " + e.getMessage());
				bRet1 = false;
			}
		} else {
			log.info("come to compare");
			// 如果授权文件存在,就验证授权码.
			FileInputStream fis = null;
			try {
				final byte[] cipher = MPUApplication.VERSION ? "godsaveem".getBytes() : "godsaveme"
						.getBytes();

				byte[] src = new byte[token.length + cipher.length];
				System.arraycopy(token, 0, src, 0, 16);
				System.arraycopy(cipher, 0, src, 16, cipher.length);
				byte[] des = MD5.encrypt(src);
				fis = new FileInputStream(accredit);
				byte[] acc = new byte[16];
				log.info("read accredit");
				fis.read(acc);
				log.info("accredit read");
				bRet1 = java.util.Arrays.equals(acc, des);
			} catch (FileNotFoundException e) {
				log.info("compare FileNotFoundException " + e.getMessage());
			} catch (IOException e) {
				log.info("compare IOException " + e.getMessage());
			} finally {
				if (fis != null) {
					try {
						fis.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
		log.close();
		boolean success = bRet && bRet1;
		return success;
	}

	private static byte[] getToken(Context contex) {
		// 获取手机串号
		TelephonyManager tm = (TelephonyManager) contex.getSystemService(Context.TELEPHONY_SERVICE);
		String did = tm.getDeviceId();
		if (did == null) {
			did = "000000000000000";
		}
		did += "mygod";
		return MD5.encrypt(did.getBytes());
	}
}