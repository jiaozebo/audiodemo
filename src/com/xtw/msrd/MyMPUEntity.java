package com.xtw.msrd;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import junit.framework.Assert;
import nochump.util.zip.EncryptZipEntry;
import nochump.util.zip.EncryptZipOutput;
import util.CommonMethod;
import util.DES;
import util.MD5;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;
import android.os.Message;
import android.os.Process;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import c7.CRChannel;
import c7.DC7;
import c7.DCAssist;
import c7.Frame;
import c7.IResType;
import c7.LoginInfo;
import c7.NC7;
import c7.PUParam;

import com.crearo.mpu.sdk.CameraThread;
import com.crearo.mpu.sdk.GPSHandler;
import com.crearo.mpu.sdk.client.ErrorCode;
import com.crearo.mpu.sdk.client.MPUEntity;
import com.crearo.mpu.sdk.client.PUInfo;
import com.crearo.puserver.PUDataChannel;
import com.gjfsoft.andaac.MainActivity;

public class MyMPUEntity extends MPUEntity {

	/**
	 * 临时版：1分钟，正式版：15分钟
	 */
	private static final int MINUTES_PER_FILE = G.sVersionCode.contains(".temp") ? 1 : 15;
	protected static int SIZE = 4096;
	protected static final String TAG = "AUDIO";
	private Thread mIAThread;
	private DC7 mIADc;
	private DC7 mIVDc;
	private boolean mResetFile = false;
	private static Calendar sBaseCalendar;
	static {
		sBaseCalendar = Calendar.getInstance();
		sBaseCalendar.clear();
		sBaseCalendar.set(2014, Calendar.JUNE, 0);
	}

	public void resetFile() {
		mResetFile = true;
	}

	public MyMPUEntity(Context context) {
		super(context);
		String path = G.sRootPath;
		if (TextUtils.isEmpty(path)) {
			return;
		}
		File f = new File(path);
		f.mkdirs();
	}

	protected List<PUDataChannel> mPDc = new ArrayList<PUDataChannel>();
	private String mCurrentRecordFilePath;

	public NC7 getNC() {
		return sNc;
	}

	public int loginBlock(String addr, int port, boolean fixAddress, String password, PUInfo info)
			throws InterruptedException {
		LoginInfo li = new LoginInfo();
		li.addr = addr;
		li.port = port;
		li.isFixAddr = fixAddress;
		li.password = password;
		li.param = new PUParam();
		li.param.ProducerID = "00005";
		li.param.PUID = info.puid;
		li.param.DevID = info.puid.substring(3);
		li.param.HardwareVer = info.hardWareVer;
		li.param.SoftwareVer = info.softWareVer;
		li.param.puname = info.name;
		li.param.pudesc = info.name;
		li.param.mCamName = info.cameraName;
		li.param.mMicName = info.mMicName;
		li.param.mSpeakerName = info.mSpeakerName;
		li.param.mGPSName = info.mGPSName;

		sNc.setCallback(this);
		for (IResType type : IResType.values()) {
			type.mIsAlive = false;
		}
		li.binPswHash = MD5.encrypt(li.password.getBytes());
		int rst = sNc.create(li, 5000);
		if (rst != 0) {
			rst += ErrorCode.NC_OFFSET;
		} else {
			sNc.sendRpt(li.param);
			mDes = DES.getNativeInstance(sNc.getCryptKey());
		}
		return rst;

	}

	public void addPUDataChannel(PUDataChannel pdc) {
		synchronized (mPDc) {
			this.mPDc.add(pdc);
		}
	}

	public void removePUDataChannel(PUDataChannel pdc) {
		synchronized (mPDc) {
			mPDc.remove(pdc);
		}
	}

	@Override
	public void handleMessage(Message msg) {
		super.handleMessage(msg);
		if (msg.what == 0x3600 || msg.what == 0x3601) {// puserverthread
			int resType = msg.arg1;
			PUDataChannel pdc = (PUDataChannel) msg.obj;

			if (resType == 0) {// iv
				CameraThread cameraThread = mCameraThread;
				if (cameraThread != null)
					if (msg.what == 0x3600) {
						cameraThread.addPUDataChannel(pdc);
					} else {
						cameraThread.removePUDataChannel(pdc);
					}
			} else if (resType == 1) {// ia
				if (msg.what == 0x3600) {
					addPUDataChannel(pdc);
					checkThread();
				} else {
					removePUDataChannel(pdc);
				}
			} else if (resType == 3) {
				GPSHandler gpsHandler = mGpsHandler;
				if (gpsHandler == null) {
					gpsHandler = new GPSHandler(mContext, null);
					mGpsHandler = gpsHandler;
				}
				if (msg.what == 0x3600) {
					gpsHandler.addPUDataChannel(pdc);
				} else {
					gpsHandler.removePUDataChannel(pdc);
				}
			}
		}
	}

	/**
	 * 1秒钟读fre*4个字节（双通道，short）；1分钟读fre * 240个字节；5分钟读1200 * fre个字节。
	 * 一帧SIZE个字节，5分钟读1200*fre/SIZE帧
	 */
	private void startOrRestart() {
		if (isAudioStarted()) {
			stopAudio();
		}
		Thread t = new Thread("AUDIO") {
			int mIdx = 0;

			public void run() {
				Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
				G.log("AUDIO IN");
				AudioRecord ar = null;
				try {
					SharedPreferences preferences = PreferenceManager
							.getDefaultSharedPreferences(mContext);
					int Fr = preferences.getInt(G.KEY_AUDIO_FREQ, 44100);
					if (Fr != 24000 && Fr != 8000 && Fr != 16000 && Fr != 44100) {// fix
																					// fr
						Fr = 24000;
					}
					final int FRAME_NUMBER_PER_FILE = MINUTES_PER_FILE * 240 * Fr / SIZE;

					int bitRate = preferences.getBoolean(G.KEY_HIGH_QUALITY, true) ? 64000 : 32000;
					final int audioSource = AudioSource.DEFAULT;
					int CC = AudioFormat.CHANNEL_IN_STEREO;
					int BitNum = AudioFormat.ENCODING_PCM_16BIT;
					int audioBuffer = AudioRecord.getMinBufferSize(Fr, CC, BitNum);
					Log.d(TAG, String.valueOf(audioBuffer));
					int size = SIZE;
					if (size < audioBuffer) {
						size = audioBuffer * 2;
					}
					do {
						try {
							ar = new AudioRecord(audioSource, Fr, CC, BitNum, size * 4);
							ar.startRecording();
							break;
						} catch (Exception e) {
							G.log("AudioRecord error !!!, i will retry after 3000ms, msg : "
									+ e.getMessage());
							e.printStackTrace();
							if (ar != null) {
								ar.release();
							}
							Thread.sleep(3000);
						}
					} while (mIAThread != null);

					MainActivity mAac = new MainActivity();
					long mEncHandle = mAac.NativeEncodeOpen(2, Fr, 2, bitRate);
					byte[] readBuf = new byte[SIZE];
					byte[] outBuf = new byte[SIZE];
					int read = 0;
					int loopCount = 0;
					while (mIAThread != null) {
						int cread = ar.read(readBuf, read, readBuf.length - read);
						if (cread < 0)
							break;
						read += cread;
						if (read < readBuf.length) {
							continue;
						}
						read = 0;

						int ret = mAac.NativeEncodeFrame(mEncHandle, readBuf, readBuf.length / 2,
								outBuf, SIZE);
						if (ret <= 0) {
							continue;
						}
						// save begin
						if (isLocalRecord()) {
							if ((mIdx++ == FRAME_NUMBER_PER_FILE || mResetFile)) {
								mIdx = 0;
								stopRecord();
								if (mResetFile) {
									mResetFile = false;
									G.log("manual switch file!!!");
								} else {
									G.log("timeout switch file!!!");
								}
								startNewFile();
							}
							recordFrame(outBuf, ret);
						}
						// save end

						final DC7 dc = mIADc;
						if (!shouldContinue()) {
							break;
						}
						// send
						boolean empty = mPDc.isEmpty();
						if (dc != null || !empty) {
							Frame frame = new Frame();
							frame.timeStamp = System.currentTimeMillis();
							/**
							 * BlockAlign 2 每个算法帧的包含的字节数 804 0x0324 Channels 1
							 * 通道个数,一般是1或2 1 0x01 BitsPerSample 1
							 * PCM格式时的采样精度，一般是8bit或16bit 16 0x10 SamplesPerSec 2
							 * PCM格式时的采样率除以100。例如：8K采样填80 320 0x0140 AlgFrmNum 2
							 * 后面算法帧的个数，这个值应该保持不变 01 0x01 ProducerID 2 厂商ID 01
							 * 0x01 PCMLen 2 一个算法帧解码后的PCM数据长度 2048 0x0800
							 * Reserved 4 保留 //AudioData AlgFrmNum* BlockAlign
							 * AlgFrmNum个算法帧，每个算法帧的长度为BlockAlign。客户端解码时，
							 * 直接将这段数据分成BlockAlign的算法帧
							 * ，然后分别送入ProducerID对应的解码库进行解码。 AlgID 1 算法类型的ID 0x0a
							 * Rsv 3 保留
							 */
							ByteBuffer buffer = ByteBuffer.allocate(16 + 4 + 4 + ret);
							buffer.order(ByteOrder.LITTLE_ENDIAN);
							buffer.putShort((short) (0x0324));
							buffer.put((byte) 1);
							buffer.put((byte) 0x10);
							buffer.putShort((short) (Fr / 100));
							buffer.putShort((short) 0);
							buffer.putShort((short) 1);
							buffer.putShort((short) SIZE);
							buffer.putInt(0);

							buffer.putInt(ret + 4); // 4 length
							buffer.put((byte) 0x0a);
							buffer.put((byte) 0);
							buffer.putShort((short) 0);
							buffer.put(outBuf, 0, ret);

							frame.data = buffer.array();
							frame.offset = 0;
							frame.length = frame.data.length;
							frame.keyFrmFlg = 1;
							frame.type = Frame.FRAME_TYPE_AUDIO;
							frame.mFrameIdx = loopCount++;
							ByteBuffer bf = null;
							if (dc != null) {
								try {
									bf = DCAssist.pumpFrame2DC(frame, dc, true).buffer;
								} catch (InterruptedException e) {
									e.printStackTrace();
									continue;
								}
							} else {
								bf = DCAssist.buildFrame(frame);
							}
							if (bf != null) {
								int lim = bf.limit();
								int pos = bf.position();
								synchronized (mPDc) {
									Iterator<PUDataChannel> it = mPDc.iterator();
									while (mIAThread != null && it.hasNext()) {
										PUDataChannel channel = (PUDataChannel) it.next();
										channel.pumpFrame(bf);
										bf.limit(lim);
										bf.position(pos);
									}
								}
							}
						}
					}
					mAac.NativeEncodeClose(mEncHandle);
				} catch (Exception e) {
					e.printStackTrace();
					if (isLocalRecord()) {
						G.log("audio exception : " + e.getMessage());
						stopRecord();
					}
				} finally {
					if (ar != null) {
						ar.release();
					}
					G.log("AUDIO OUT");
				}
			}

			@Override
			public synchronized void start() {
				super.start();
			}

		};
		Assert.assertNull(mIAThread);
		mIAThread = t;
		t.start();
	}

	protected static void save2FileEncrypt(byte[] outBuf, int offset, int length, String filePath,
			boolean append) throws IOException {
		EncryptZipOutput out = new EncryptZipOutput(new FileOutputStream(filePath, append), "123");

		out.putNextEntry(new EncryptZipEntry(new File(filePath).getName()));
		out.write(outBuf, offset, length);
		out.flush();
		out.closeEntry();
		out.close();
	}

	@Override
	protected void handleStartWork(DC7 dc) {
		LoginInfo info = dc.getLoginInfo();
		switch (info.resType) {
		case IV:
			if (G.mPreviewVideo) {
				// VideoRunnable runnable = VideoRunnable.singleton();
				// if (runnable != null) {
				// runnable.setVideoDC(dc);
				// } else {
				// dc.close();
				// return;
				// }
			} else {
				mIVDc = dc;
			}
			break;
		default:
			super.handleStartWork(dc);
		}
	}

	@Override
	public void handleChannelError(CRChannel channel, int errorCode) {
		if (channel == sNc) {
			super.handleChannelError(channel, errorCode);
			return;
		}
		LoginInfo info = channel.getLoginInfo();
		info.resType.mIsAlive = false;
		switch (info.resType) {
		case IA: {
			// closeIAThread();
			final DC7 dc = mIADc;
			if (dc != null) {
				dc.close();
				mIADc = null;
				checkThread();
			}
			byte status = RendCallback.STT_REND_END;
			if (mRendCallback != null) {
				mRendCallback.onRendStatusFetched(info.resType, status);
			}
		}
		default:
			super.handleChannelError(channel, errorCode);
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.crearo.mpu.sdk.client.MPUEntity#logout()
	 */
	@Override
	public void logout() {
		super.logout();
		// closeIAThread();
		DC7 dc = mIVDc;
		mIVDc = null;
		if (dc != null) {
			dc.close();
		}
		dc = mIADc;
		mIADc = null;
		if (dc != null) {
			dc.close();
		}
	}

	public void stopAudio() {

		Thread t = mIAThread;
		if (t != null) {
			mIAThread = null;
			try {
				t.interrupt();
				t.join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		G.log("stopAudio !");
		stopRecord();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.crearo.mpu.sdk.MPUHandler#startIAWithDC(c7.DC7)
	 */
	@Override
	public void startIAWithDC(final DC7 dc) {
		// if (mIAThread != null) {
		// dc.close();
		// closeIAThread();
		// return;
		// }
		boolean needCheck = mIADc == null;
		mIADc = dc;
		if (needCheck) {
			checkThread();
		}
	}

	/**
	 * 
	 * @param data
	 * @param offset
	 * @param length
	 * @param topValue
	 *            设置的最高值，如果数组里有大于或者等于该值的，直接返回该值
	 * @param bigEndian
	 * @return
	 */
	public static short getMax(byte[] data, int offset, int length, short topValue,
			boolean bigEndian) {
		short max = 0;
		// ByteBuffer bf = ByteBuffer.wrap(data, offset, length);
		// ShortBuffer sf = bf.asShortBuffer();
		// for (int i = 0; i < sf.limit(); i++) {
		// short value = sf.get(i);
		// if (value > max) {
		// max = value;
		// }
		// }
		for (int i = offset; i < length;) {
			short h = data[i + 1];
			short l = data[i];
			short value = (short) (h * 256 + l);
			if (value >= topValue) {
				return topValue;
			}
			if (value > max) {
				max = value;
			}
			i += 2;
		}
		return max;
	}

	public boolean isLocalRecord() {
		return (mCurrentRecordFilePath != null);
	}

	/**
	 * 调用该函数，如果必要的话，会启动音频
	 * 
	 * @param record
	 */
	public void setLocalRecord(boolean record) {
		boolean needCheck = (isLocalRecord() != record);
		if (needCheck) {
			G.log("LocalRecord change: " + record);
			if (record) {
				startNewFile();
			} else {
				stopRecord();
			}
			checkThread();
		} else {
			G.log("LocalRecord unchange: " + record);
		}
	}

	private void startNewFile() {
		String filePath = createWavPath();
		if (filePath == null) {
			G.log("LocalRecord createZipPath returu null!!! ");
			return;
		}
		G.sCurrentRecordFilePath = mCurrentRecordFilePath = filePath;
	};

	private void recordFrame(byte[] outBuf, int ret) {

		if (!TextUtils.isEmpty(mCurrentRecordFilePath)) {
			CommonMethod.save2fileNoLength(outBuf, 0, ret, mCurrentRecordFilePath, true);
		}
	}

	private void stopRecord() {
		if (mCurrentRecordFilePath == null) {
			G.log("stop record, but path is already null!");
			return;
		}
		EncryptIntentService.startActionFoo(mContext, mCurrentRecordFilePath, null);
		mCurrentRecordFilePath = null;
	}

	/**
	 * 判断是否满足继续采集的条件
	 * 
	 * @return
	 */
	public boolean shouldContinue() {
		return mIADc != null || isLocalRecord() || !mPDc.isEmpty();
	}

	public synchronized void checkThread() {
		if (!shouldContinue()) {
			stopAudio();
		} else if (!isAudioStarted()) {
			startOrRestart();
		}
	}

	public String getRecordingFilePath() {
		return mCurrentRecordFilePath;
	}

	private String createWavPath() {
		// String rootPath = String.format("%s/%s", Environment
		// .getExternalStorageDirectory().getPath(), "audio");
		// if (G.sRootPath.equals(rootPath)) {
		// return null;
		// }
		SimpleDateFormat sdf = new SimpleDateFormat("yy-MM-dd", Locale.CHINA);
		Date date = new Date();
		String filePath = null;
		if (date.after(sBaseCalendar.getTime())) { // 视作时间正确
			String dirPath = sdf.format(date);
			File dirFile = new File(G.sRootPath, dirPath);
			dirFile.mkdirs();
			sdf = new SimpleDateFormat("HH.mm.ss", Locale.CHINA);
			filePath = String.format("%s/%s.wav", dirFile.getPath(), sdf.format(date));
		} else { // 视作时间不正确
			String dirPath = String.valueOf(1);
			File dirFile = new File(G.sRootPath, dirPath);
			dirFile.mkdirs();
			String[] paths = dirFile.list(new FilenameFilter() {

				@Override
				public boolean accept(File dir, String filename) {
					return Pattern.matches("\\d+\\.wav", filename);
				}
			});
			if (paths == null) {
				return null;
			}
			int max = 0;
			for (String path : paths) {
				int p = Integer.parseInt(path.substring(0, path.indexOf(".zip")));
				if (max < p) {
					max = p;
				}
			}
			filePath = String.format("%s/%d.wav", dirFile.getPath(), ++max);
		}
		return filePath;
	}

	public boolean isAudioStarted() {
		Thread mIAThread2 = mIAThread;
		return mIAThread2 != null;
	}
}
