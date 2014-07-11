package com.ampu;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.drawable.AnimationDrawable;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.OAudioRunnable;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;
import c7.IResType;

import com.crearo.mpu.sdk.AudioRunnable;
import com.crearo.mpu.sdk.CameraThread;
import com.crearo.mpu.sdk.MPUHandler;
import com.crearo.mpu.sdk.MPUHandler.RecordCallback;
import com.crearo.mpu.sdk.MPUHandler.RendCallback;
import com.crearo.mpu.sdk.client.VideoParam;

public class WorkActivity extends Activity implements OnClickListener, RecordCallback,
		RendCallback, SurfaceHolder.Callback {

	private static final int DELAY_MILLIS = 10 * 1000;
	private static final String tag = "WorkActivity";
	private static final int SET_CAMERA_PARAM = 1002;
	private View mStatusBar;
	private SurfaceView mRecorder;
	private Runnable mStatusBarGoneRunnable;
	private MPUHandler mMpuHandler;
	/**
	 * true表示handle状态在start与stop之间
	 */
	private boolean mIsHandleStart = false;
	// protected ScaleAnimation mStatusBarGoneAnim;

	private View mFocus, mSwitchCamera, mRecord, mSnapshot, mPressTalk;
	// private ScaleAnimation mStatusBarShowAnim;
	private View mViewCalling;
	protected BroadcastReceiver mHandsetPluginReceiver, mEndCallReceiver;
	/**
	 * 表示已经按了一次退出了，现在是退出模式。
	 */
	private boolean mQuit;
	/**
	 * 提示重新点击退出的toast
	 */
	private Toast mQuitTipToast = null;
	/**
	 * 复位{@link #mQuit 退出模式}的runnable
	 */
	private Runnable mResetQuitFlagRunnable = new Runnable() {

		@Override
		public void run() {
			mQuit = false;
			mQuitTipToast.cancel();
		}
	};
	/**
	 * 当前摄像头的参数
	 */
	private VideoParam mVideoParam;
	/**
	 * 当前的摄像头ID
	 */
	private int mCameraId;

	@SuppressLint("ShowToast")
	@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		final int[] size = new int[] { 0, 0 };
		final boolean result = CameraThread.getCurrentCameraPreviewSize(this, size);
		if (!result) {
			finish();
			return;
		}
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		mVideoParam = new VideoParam();
		mCameraId = preferences.getInt("cameraId", 0);
		mVideoParam.putParam(VideoParam.KEY_INT_CAMERA_ID, mCameraId);
		mVideoParam.putParam(VideoParam.KEY_INT_FRAME_ROTATE_DEGREE, -1);
		if (preferences.getBoolean(SetActivity.KEY_PORTRAIT_VIDEO, false)) {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
			mVideoParam.putParam(VideoParam.KEY_INT_DISPLAY_ROTATE_DEGREE, 90);
			if (MPUApplication.VERSION && mCameraId != 0) {
				mVideoParam.putParam(VideoParam.KEY_INT_DISPLAY_ROTATE_DEGREE, 270);
				mVideoParam.putParam(VideoParam.KEY_INT_FRAME_ROTATE_DEGREE, 90);
			}
		}
		mVideoParam.putParam(VideoParam.KEY_INT_PREVIEW_WIDTH, size[0]);
		mVideoParam.putParam(VideoParam.KEY_INT_PREVIEW_HEIGHT, size[1]);
		// mCameraId = preferences.getInt("cameraId", 0);
		mVideoParam.putParam(VideoParam.KEY_INT_CAMERA_ID, mCameraId);
		mVideoParam.putParam(VideoParam.KEY_INT_FRAME_RATE,
				preferences.getInt(VideoParam.KEY_INT_FRAME_RATE, 20));
		mVideoParam.putParam(VideoParam.KEY_INT_BIT_RATE,
				preferences.getInt(VideoParam.KEY_INT_BIT_RATE, 150));
		mVideoParam.putParam(VideoParam.KEY_INT_VIDEO_QUALITY,
				preferences.getInt(VideoParam.KEY_INT_VIDEO_QUALITY, 5));
		boolean compatible = false;
		if (MPUApplication.VERSION) {
			compatible = !isS4();
		}
		mVideoParam.putParam(VideoParam.KEY_BOOLEAN_ENCODE_COMPATIBILITY,
				preferences.getBoolean(VideoParam.KEY_BOOLEAN_ENCODE_COMPATIBILITY, compatible));

		// if (Build.MODEL.equals("Nexus 5")) {
		// mVideoParam.putParam(VideoParam.KEY_BOOLEAN_EXTERNAL_CAMERA, true);
		// mVideoParam.putParam(VideoParam.KEY_INT_CAMERA_ID, 3);
		// }

		mMpuHandler = ((MPUApplication) getApplication()).getHandler();
		mMpuHandler.setRecordCallback(this);
		mMpuHandler.setRendCallback(this);
		setContentView(R.layout.work_activity);

		mStatusBar = findViewById(R.id.statusBar);

		mStatusBarGoneRunnable = new Runnable() {

			@Override
			public void run() {
				mStatusBar.setVisibility(View.GONE);
			}
		};
		mRecorder = (SurfaceView) findViewById(R.id.recorder);
		mRecorder.setClickable(true);
		mRecorder.setOnClickListener(this);
		SurfaceHolder holder = mRecorder.getHolder();
		holder.addCallback(this);

		mRecord = findViewById(R.id.ib_record);
		mSnapshot = findViewById(R.id.ib_snapshot);
		mSwitchCamera = findViewById(R.id.ib_switch);
		mFocus = findViewById(R.id.ib_focus);
		mPressTalk = findViewById(R.id.ib_press_talk);

		mRecord.setOnClickListener(this);
		mSnapshot.setOnClickListener(this);
		if (isCameraSwitchEnable())
			mSwitchCamera.setOnClickListener(this);
		else {
			ViewGroup parent = (ViewGroup) mSwitchCamera.getParent();
			if (parent != null) {
				parent.removeView(mSwitchCamera);
			}
		}
		mFocus.setOnClickListener(this);

		ViewGroup root = (ViewGroup) findViewById(R.id.id_work);
		mViewCalling = LayoutInflater.from(this).inflate(R.layout.view_calling, root, false);
		RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		params.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
		mViewCalling.setLayoutParams(params);
		mPressTalk.setVisibility(View.GONE);
		View.OnTouchListener onTouchListener = new View.OnTouchListener() {

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getAction() == MotionEvent.ACTION_DOWN) {
					// say。。
					// G.sEntity.enableAudio(1);
					startOrStopTalkingView(true);
					mStatusBar.removeCallbacks(mStatusBarGoneRunnable);
					ImageButton btn = (ImageButton) v;
					btn.setImageResource(R.drawable.press_talk_pressed);
					enableAudio(1);
				} else if (event.getAction() == MotionEvent.ACTION_UP
						|| event.getAction() == MotionEvent.ACTION_OUTSIDE
						|| event.getAction() == MotionEvent.ACTION_CANCEL) {
					// listen
					// G.sEntity.enableAudio(2);
					enableAudio(2);
					ImageButton btn = (ImageButton) v;
					btn.setImageResource(R.drawable.press_talk);
					startOrStopTalkingView(false);
					mStatusBar.postDelayed(mStatusBarGoneRunnable, DELAY_MILLIS);
				}
				return true;
			}

		};
		mPressTalk.setOnTouchListener(onTouchListener);

		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_HEADSET_PLUG);
		mHandsetPluginReceiver = new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				int pluggedStatu = intent.getIntExtra("state", 0);
				onHandsetPluggedStatusChanged(pluggedStatu);
			}
		};
		registerReceiver(mHandsetPluginReceiver, filter);

		mQuitTipToast = Toast.makeText(this, R.string.press_again_to_exit, Toast.LENGTH_LONG);
	}

	@Override
	public void onClick(View view) {
		switch (view.getId()) {
		case R.id.ib_record: {
			if (mMpuHandler.startOrStopRecord()) {
				view.setBackgroundResource(R.drawable.record_down);
			} else {
				view.setBackgroundResource(R.drawable.record);
			}
		}
			break;
		case R.id.recorder:
			mMpuHandler.focusCamera();
			showStatusBar();
			break;
		case R.id.ib_snapshot:
			if (MPUApplication.VERSION) {
				final Parameters p = mMpuHandler.getParameters();

				List<String> li = p.getSupportedSceneModes();
				if (li == null) {
					Toast.makeText(this, "不支持模式切换", Toast.LENGTH_SHORT).show();
					return;
				}
				ImageButton btn = (ImageButton) view;
				String mode = p.getSceneMode();
				if (Camera.Parameters.SCENE_MODE_NIGHT.equals(mode)) {
					btn.setImageResource(R.drawable.snap);
					p.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);
				} else {
					btn.setImageResource(R.drawable.snap_down);
					p.setSceneMode(Camera.Parameters.SCENE_MODE_NIGHT);
				}
				mMpuHandler.setParameters(p);
				return;
			}
			doTakePicture();

			break;
		case R.id.ib_focus: {
			mMpuHandler.focusCamera();
		}
			break;
		case R.id.ib_switch: {
			// 在这里肯定可以switch了，也就是说已经保证了摄像头数量大于1了。在这里看作为2.
			switchCamera();
		}
		default:
			break;
		}
	}

	private void doTakePicture() {
		final File directory = new File(MPUApplication.PATH_STORAGE_SNAPSHOT);
		if (!directory.exists()) {
			Toast.makeText(this, R.string.pleaseCheckSDCard, Toast.LENGTH_SHORT).show();
			return;
		}

		// entity.takeShot(mTakeShotCb);
		mMpuHandler.takePicture(new PictureCallback() {

			@Override
			public void onPictureTaken(final byte[] data, final Camera camera) {
				try {
					final SimpleDateFormat sdf = new SimpleDateFormat("yy-MM-dd HH.mm.ss",
							Locale.US);
					final String path = String.format("%s/%s.jpg",
							MPUApplication.PATH_STORAGE_SNAPSHOT, sdf.format(new Date()));
					final File f = new File(path);
					final FileOutputStream fos = new FileOutputStream(f);
					fos.write(data);
					fos.close();
				} catch (final FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (final IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		showStatusBar();
		boolean autoEndcall = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
				"key_auto_endcall", false);
		if (autoEndcall) {
			mEndCallReceiver = new PhoneReceiver();
			IntentFilter filter = new IntentFilter();
			filter.addAction("android.intent.action.PHONE_STATE");
			filter.addAction("android.intent.action.NEW_OUTGOING_CALL");
			registerReceiver(mEndCallReceiver, filter);
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		Log.d(tag, "surface changed");
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		Log.d(tag, "surfaceCreated");
		if (!mIsHandleStart) {
			mMpuHandler.start(mRecorder, mVideoParam);
			mIsHandleStart = true;
		} else {// 后台转到前台了
			mMpuHandler.reset();
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		Log.d(tag, "surfaceDestroyed");
		if (isFinishing()) {
			mMpuHandler.close();
			mIsHandleStart = false;
		}
	}

	@Override
	protected void onPause() {
		if (mEndCallReceiver != null) {
			unregisterReceiver(mEndCallReceiver);
			mEndCallReceiver = null;
		}
		super.onPause();
		if (isFinishing()) {
			PreferenceManager.getDefaultSharedPreferences(this).edit()
					.putInt("cameraId", mCameraId).commit();
		}
	}

	@Override
	protected void onDestroy() {
		unregisterReceiver(mHandsetPluginReceiver);
		mHandsetPluginReceiver = null;
		super.onDestroy();
	}

	@Override
	public void onBackPressed() {
		if (mQuit) {
			mMpuHandler.removeCallbacks(mResetQuitFlagRunnable);
			mResetQuitFlagRunnable.run();
			super.onBackPressed();
		} else {
			mQuit = true;
			mQuitTipToast.show();
			mMpuHandler.postDelayed(mResetQuitFlagRunnable, 1500);
		}
	}

	private void switchCamera() {
		int newId = 1 - mCameraId;
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		if (MPUApplication.VERSION && preferences.getBoolean(SetActivity.KEY_PORTRAIT_VIDEO, false)) {
			mVideoParam.putParam(VideoParam.KEY_INT_FRAME_ROTATE_DEGREE, newId != 0 ? 90 : -1);
			mVideoParam.putParam(VideoParam.KEY_INT_DISPLAY_ROTATE_DEGREE, newId != 0 ? 270 : 90);
			mMpuHandler.switchCamera(newId, mVideoParam);
		} else {
			mMpuHandler.switchCamera(newId, null);
		}
		mCameraId = newId;
	}

	@Override
	public void onRecordStatusFetched(final int statusCode) {
		final View view = findViewById(R.id.ib_record);
		view.post(new Runnable() {

			@Override
			public void run() {
				int resource = R.drawable.record;
				switch (statusCode) {
				case STT_RECORD_BEGIN:
					resource = R.drawable.record_down;
					break;
				case STT_RECORD_END:
					resource = R.drawable.record;
					break;
				default:
					break;
				}
				view.setBackgroundResource(resource);
			}
		});
	}

	@Override
	public void onRendStatusFetched(final IResType type, final byte status) {
		final boolean rending = MPUHandler.isRending(status);
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				ImageView view = null;
				int resId = 0;
				switch (type) {
				case IV: {
					view = (ImageView) mStatusBar.findViewById(R.id.iv_rending);
					resId = rending ? R.drawable.iv_ : R.drawable.iv;
				}
					break;
				case IA: {
					view = (ImageView) mStatusBar.findViewById(R.id.ia_rending);
					resId = rending ? R.drawable.ia_ : R.drawable.ia;

					if (IResType.OA.mIsAlive && rending) {
						checkTalk();
					}
					if (!rending) {
						mPressTalk.setVisibility(View.GONE);
						mFocus.setVisibility(View.VISIBLE);
					}
				}
					break;
				case OA: {
					view = (ImageView) mStatusBar.findViewById(R.id.oa_rending);
					resId = rending ? R.drawable.oa_ : R.drawable.oa;
					byte oaType = MPUHandler.getOAType(status);
					switch (oaType) {
					case OA_TYPE_TALK:
						onRendStatusFetched(IResType.IA, rending ? STT_REND_BEGIN : STT_REND_END);
						break;
					default:
						break;
					}

					if (rending && IResType.IA.mIsAlive) {
						checkTalk();
					}
					if (!rending) {
						findViewById(R.id.ib_press_talk).setVisibility(View.GONE);
					}
				}
					break;
				case GPS: {
					view = (ImageView) mStatusBar.findViewById(R.id.gps_rending);
					resId = rending ? R.drawable.gps_ : R.drawable.gps;
				}
					break;
				default:
					break;
				}
				if (view != null) {
					view.setImageResource(resId);
					if (rending) {
						showStatusBar();
					}
				}
			}

			private void checkTalk() {
				boolean pressTalkEnable = PreferenceManager.getDefaultSharedPreferences(
						WorkActivity.this).getBoolean("key_press_talk", true);
				AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
				if (pressTalkEnable && !am.isBluetoothA2dpOn() && !am.isWiredHeadsetOn()) {
					//
					mPressTalk.setVisibility(View.VISIBLE);
					mFocus.setVisibility(View.GONE);
					enableAudio(2);
				} else {
					enableAudio(-1);
					mPressTalk.setVisibility(View.GONE);
					mFocus.setVisibility(View.VISIBLE);
				}
			}

		});
	}

	private void showStatusBar() {
		mStatusBar.removeCallbacks(mStatusBarGoneRunnable);
		mStatusBar.postDelayed(mStatusBarGoneRunnable, DELAY_MILLIS);
		mStatusBar.setVisibility(View.VISIBLE);
	}

	/**
	 * 使能音频
	 * 
	 * @param flag
	 *            0表示不使能；1表示使能输入音频（即使能发送音频）；2表示使能输出音频（即使能B端的音频）；其它表示两者都使能
	 */
	public void enableAudio(int flag) {
		OAudioRunnable oa = OAudioRunnable.singleton();
		AudioRunnable ia = AudioRunnable.singleton();
		switch (flag) {
		case 0:
			oa.pause();
			ia.pause();
			break;
		case 2:
			oa.resume();
			ia.pause();
			break;
		case 1:
			oa.pause();
			ia.resume();
			break;
		default:
			oa.resume();
			ia.resume();
			break;
		}
	}

	/**
	 * 
	 * 
	 * @param state
	 *            0 for unplugged, 1 for plugged.
	 */
	protected void onHandsetPluggedStatusChanged(int state) {
		if (IResType.IA.mIsAlive && IResType.OA.mIsAlive) {
			if (state == 1) {
				enableAudio(-1);
				mPressTalk.setVisibility(View.GONE);
				startOrStopTalkingView(false);
			} else {
				enableAudio(2);
				mPressTalk.setVisibility(View.VISIBLE);
				showStatusBar();
			}
		}
	}

	/**
	 * 开始或者停止“正在说话”控件
	 * 
	 * @param start
	 */
	private void startOrStopTalkingView(boolean start) {
		TextView calling = (TextView) mViewCalling.findViewById(R.id.tv_calling);
		AnimationDrawable ad = (AnimationDrawable) calling.getCompoundDrawables()[1];
		if (start) {
			ViewGroup root = (ViewGroup) findViewById(R.id.id_work);
			root.addView(mViewCalling);
			ad.start();
		} else {
			ad.stop();
			ViewGroup root = (ViewGroup) findViewById(R.id.id_work);
			root.removeView(mViewCalling);
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (MPUApplication.VERSION) {
			if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
				event.startTracking();
				return true;
			}
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyLongPress(int keyCode, KeyEvent event) {
		if (MPUApplication.VERSION) {
			if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
				//
				int visible = findViewById(R.id.id_dark).getVisibility() == View.VISIBLE ? View.GONE
						: View.VISIBLE;
				findViewById(R.id.id_dark).setVisibility(visible);
				return true;
			}
		}
		return super.onKeyLongPress(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (MPUApplication.VERSION) {
			if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
				if (!(event.isTracking() && !event.isCanceled())) {
					return true;
				}
			}
			if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
				doTakePicture();
				Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
				vib.vibrate(new long[] { 0, 100 }, -1);
				return true;
			}
		}
		return super.onKeyUp(keyCode, event);
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		if (id == SET_CAMERA_PARAM) {
			final Parameters p = mMpuHandler.getParameters();
			List<String> li = p.getSupportedSceneModes();
			final String[] items = new String[li.size()];
			for (int i = 0; i < items.length; i++) {
				items[i] = li.get(i);
			}
			return new AlertDialog.Builder(this).setItems(items,
					new DialogInterface.OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							String mode = items[which];
							p.setSceneMode(mode);
							mMpuHandler.setParameters(p);
						}

					}).create();
		}
		return super.onCreateDialog(id);
	}

	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	private static final boolean isCameraSwitchEnable() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
			int cameraCount = Camera.getNumberOfCameras();
			return cameraCount > 1;
		}
		return false;
	}

	private static boolean isS4() {
		return Build.MODEL.equals("SCH-I959") || Build.MODEL.equals("GT-I9500")
				|| Build.MODEL.equals("SCH-I9502") || Build.MODEL.equals("SCH-I9508")
				|| Build.MODEL.equals("SCH-I939D")|| Build.MODEL.equals("SM-N9002");
	}
}
