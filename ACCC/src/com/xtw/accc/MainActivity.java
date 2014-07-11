package com.xtw.accc;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.w3c.dom.Node;

import util.XMLParser;
import android.app.Activity;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;
import c7.DC7;
import c7.DCAssist;
import c7.Frame;
import c7.LoginInfo;
import client.CRClient;
import client.ClientAssist;
import client.ClientNode;

import common.E;

public class MainActivity extends Activity {

	static String tag = "MainActivity";
	private static final int REQUEST_LOGIN = 0x1000;
	private Thread mAudioProducer;

	protected static int SIZE = 4096;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		if (AcccApp.sClient == null) {
			Intent intent = new Intent(this, LoginActivity.class);
			startActivityForResult(intent, REQUEST_LOGIN);
		} else {
			openAudio();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQUEST_LOGIN) {
			if (resultCode != RESULT_OK) {
				finish();
			} else {
				openAudio();
			}
		}
	}

	private void openAudio() {
		Thread thread = new Thread("AudioProducer") {
			Thread mAudioConsumer = null;
			/**
			 * 音频缓冲区，存储帧头开始
			 */
			protected BlockingQueue<Frame> mAudioBuffers, mAudioCache;

			@Override
			public void run() {
				CRClient client = AcccApp.sClient;
				if (client == null) {
					return;
				}
				XMLParser parser = new XMLParser();
				parser.setEncoder(E.UTF8);
				Node Msg = parser.addTag2(E.Msg, E.MsgName, E.CUCommonMsgReq, E.DomainRoad, "");
				Node Cmd = parser.add_tag_parent(Msg, E.Cmd, E.Type, E.CTL, E.Prio,
						client.getPriority(), E.EPID, "system");
				Node DstRes = parser.add_tag_parent(Cmd, E.DstRes, E.Type, ClientNode.IA, E.Idx,
						"", E.OptID, E.CTL_COMMONRES_StartStream_PullMode);
				parser.add_tag_parent(DstRes, E.Param, E.StreamType, E.REALTIME);

				Node[] response = new Node[] { null };
				int result = ClientAssist.requestWithCommonResponse(parser, (byte) E.PU,
						AcccApp.sPeerUnit.puid(), client, response);
				if (result == 0) {
					Node param = response[0];
					String addr = client.getAddress();
					int port = Integer.parseInt(XMLParser.getAttrVal(param, E.Port, "0"));
					String token = XMLParser.getAttrVal(param, E.Token, null);
					if (port == 0 || token == null) {
						return;
					}
					DC7 dc = new DC7();
					LoginInfo info = new LoginInfo();
					info.addr = addr;
					info.port = port;
					info.token = token;
					Log.i(tag, String.format("IP:%s PORT:%d TOKEN:%s", addr, port, token));
					dc.create(info);
					try {
						result = dc.create(3000);
						Log.w(tag, "dc create ret:" + result);
						if (result == 0) {
							initConsumer();
							mAudioConsumer.start();
							while (mAudioProducer != null) {
								Frame f = mAudioCache.poll();
								if (f == null) {
									f = new Frame(1024);
								} else {
									f.clear();
								}
								do {
									result = dc.recvRawFrame(SystemClock.uptimeMillis(), f);
									if (result == 1) {
										Thread.sleep(10);
									}
								} while (mAudioProducer != null && result == 1);
								if (result == 0) {
									mAudioBuffers.put(f);
								} else {
									Log.w(tag, "dc recvRawFrame ret:" + result);
									break;
								}
							}
						}
					} catch (IOException e) {
						e.printStackTrace();
						result = 0x7008;
					} catch (InterruptedException e) {
						e.printStackTrace();
					} finally {
						Log.w(tag, "dc about to close! by hand ? " + (mAudioProducer == null));
						dc.close();
						Thread t = mAudioConsumer;
						mAudioConsumer = null;
						t.interrupt();
						try {
							t.join();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}

				}

				if (mAudioProducer != null) { // 非关闭退出，出错退出
					final int fResult = result;
					runOnUiThread(new Runnable() {

						@Override
						public void run() {
							Toast.makeText(MainActivity.this, String.format("播放声音失败(%d)", fResult),
									Toast.LENGTH_LONG).show();
							mAudioProducer = null;
						}
					});
				}
			}

			private void initConsumer() {
				mAudioCache = new ArrayBlockingQueue<Frame>(10);
				mAudioBuffers = new ArrayBlockingQueue<Frame>(10);
				Thread thread = new Thread("AudioConsumer") {

					@Override
					public void run() {
						Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);

						int Fr = 24000;
						int CC = AudioFormat.CHANNEL_IN_STEREO;
						int BitNum = AudioFormat.ENCODING_PCM_16BIT;
						int audioBuffer = AudioTrack.getMinBufferSize(Fr, CC, BitNum);

						Log.d(tag, String.valueOf(audioBuffer));
						int size = SIZE;
						if (size < audioBuffer) {
							size = audioBuffer * 2;
						}

						AudioTrack at = new AudioTrack(AudioManager.STREAM_MUSIC, Fr, CC, BitNum,
								size, AudioTrack.MODE_STREAM);
						com.gjfsoft.andaac.MainActivity decoder = new com.gjfsoft.andaac.MainActivity();
						long handle = 0;
						try {
							handle = decoder.NativeDecodeOpen(2, Fr, 2);
							byte[] mIn = new byte[2 * 1024];
							byte[] mOut = new byte[4096];
							at.play();
							while (mAudioConsumer != null) {
								Frame f = mAudioBuffers.take();

								f.offset += DCAssist.STORAGE_HEAD_LENGTH;
								f.length -= DCAssist.STORAGE_HEAD_LENGTH;

								f.offset += 16; // audio header
								f.length -= 16; //

								f.offset += 8; // acc header
								f.length -= 8; //
								if (mIn.length < f.length) {
									mIn = new byte[f.length];
								}
								System.arraycopy(f.data, f.offset, mIn, 0, f.length);
								// public native int NativeDecodeFrame(long
								// handle, byte[] inbuf, int inlen, byte[]
								// outbuf,
								// int outlen);
								int nRet = decoder.NativeDecodeFrame(handle, mIn, f.length, mOut,
										4096);
								if (nRet == 0) {
									nRet = at.write(mOut, 0, 4096);
									if (nRet == AudioTrack.ERROR_BAD_VALUE
											|| nRet == AudioTrack.ERROR_INVALID_OPERATION) {
										// 直接退出线程即可。
										break;
									}
								} else {
									Log.e(tag, "decoder decode error! code:" + nRet);
								}
								mAudioCache.offer(f);

							}
						} catch (InterruptedException e) {
							e.printStackTrace();
						} finally {
							decoder.NativeDecodeClose(handle);
							at.release();
						}
					}
				};
				mAudioConsumer = thread;
			}
		};
		mAudioProducer = thread;
		thread.start();
	}

	protected void doStopAudio() {
		Thread t = mAudioProducer;
		if (t != null) {
			mAudioProducer = null;
			t.interrupt();
			try {
				t.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	protected void onDestroy() {
		doStopAudio();
		super.onDestroy();
	}

}
