package com.digitalbiology.audio;

public abstract class Microphone {

	public static final int DEVICE_MICROPHONE = 1;
	public static final int USB_MICROPHONE = 2;

	private static final int MAX_RECORD_PRE_BUFFER_SECS = 10;

	abstract public int getMaxPacketSize();

	abstract public int getSampleRate();

	abstract protected int getMaxMaxPacketSize();

	abstract protected int getMaxSampleRate();

	abstract public void setSampleRate(int sampleRate);

	abstract public String getManufacturerName();

	abstract public String getProductName();

	abstract public int getType();

	abstract public int[] getSampleRates();

	abstract public int getBitResolution();

	abstract public int getNumChannels();

	protected void initBuffers() {
		int maxrate = MAX_RECORD_PRE_BUFFER_SECS * getMaxSampleRate();
		int packetSize = getMaxMaxPacketSize();
		int bitResolution = getBitResolution();
		int numChannels = getNumChannels();
		AllocateAudioBuffers(maxrate, packetSize, bitResolution, numChannels);
//		AllocateAudioBuffers(MAX_RECORD_PRE_BUFFER_SECS * getMaxSampleRate(), getMaxMaxPacketSize(), getBitResolution(), getNumChannels());
	}

	private native static void AllocateAudioBuffers(int sampleBufferSize, int packetSize, int bitSize, int numChannels);

//	private native void CleanupAudioBuffers();
}
