package com.ampu;

import com.crearo.mpu.sdk.Common;

import junit.framework.Assert;
import android.annotation.SuppressLint;
import android.hardware.Camera;

public class VersionAdapter {
	@SuppressLint("NewApi")
	public static int getCameraCount() {
		Assert.assertTrue(Common.getSdkVersion() > 8);
		return Camera.getNumberOfCameras();
	}

	@SuppressLint("NewApi")
	public static Camera openCamera(int id) {
		Assert.assertTrue(Common.getSdkVersion() > 8);
		return Camera.open(id);
	}
}
