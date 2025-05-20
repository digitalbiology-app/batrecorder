package com.digitalbiology.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import androidx.annotation.NonNull;

import java.util.ArrayList;

public class DeviceMicrophone extends Microphone {

	private int 			mSampleRateIndex;
	private final String 	mManufacturerName;
	private final String 	mProductName;
    private int 			mBufferSize;
    private short[] 		mAudioData;
	private final int[]			mSampleRates;
    
	public DeviceMicrophone(@NonNull ArrayList<Integer> samplingFrequencies, String manufacturer, String product) {
		mManufacturerName = manufacturer;
		mProductName = product;
//		mSampleRates = new int[samplingFrequencies.size()];
//		for (int i = 0; i < mSampleRates.length; i++) {
//			mSampleRates[i] = samplingFrequencies.get(i).intValue();
//		}
//		mSampleRateIndex = mSampleRates.length - 1;
		mSampleRates = new int[1];
		mSampleRates[0] = samplingFrequencies.get(samplingFrequencies.size()-1);
		mSampleRateIndex = 0;
		setSampleRate(mSampleRates[mSampleRateIndex]);

		initBuffers();
	}

	public int[] getValidSampleRates() { return mSampleRates; }

	public int getType() {
		return DEVICE_MICROPHONE;
	}
	
	public short[] getAudioData() {
		return mAudioData;
	}
	
	public int getBufferSize() {
		return mBufferSize;
	}
	
	public int getMaxPacketSize() {
		return 512;
	}
	
	public int getSampleRate() {
		return mSampleRates[mSampleRateIndex];
	}

	public String getManufacturerName() {
		return mManufacturerName;
	}

	public String getProductName() {
		return mProductName;
	}

	public int getBitResolution() { return 16; }

	public int getNumChannels() { return 1; }

	public int[] getSampleRates() { return mSampleRates; }

	public void setSampleRate(int sampleRate) {
		// Only changes if the requested sample rate is valid
		if (sampleRate != mSampleRates[mSampleRateIndex]) {
			for (int ii = 0; ii < mSampleRates.length; ii++) {
				if (mSampleRates[ii] == sampleRate) {
					mSampleRateIndex = ii;
					break;
				}
			}
		}
	}

	public int getMaxMaxPacketSize() {
		return getMaxPacketSize();
	}

	public int getMaxSampleRate() {
		return getSampleRate();
	}

	public void initBuffers() {
		super.initBuffers();
		mBufferSize = AudioRecord.getMinBufferSize(mSampleRates[mSampleRateIndex], AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
		mAudioData = new short[mBufferSize];
	}
}
