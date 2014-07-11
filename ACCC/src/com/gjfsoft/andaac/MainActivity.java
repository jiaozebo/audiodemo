package com.gjfsoft.andaac;

public class MainActivity {

	public native long NativeEncodeOpen(int aac_type, int samplerate, int channels, int bit_rate);

	public native int NativeEncodeFrame(long handle, byte[] inbuf, int inlen, byte[] outbuf,
			int outlen);

	public native int NativeEncodeClose(long handle);

	public native long NativeDecodeOpen(int aac_type, int samplerate, int channels);

	public native int NativeDecodeFrame(long handle, byte[] inbuf, int inlen, byte[] outbuf,
			int outlen);

	public native int NativeDecodeClose(long handle);

	static {
		System.loadLibrary("andaac");
	}
}
