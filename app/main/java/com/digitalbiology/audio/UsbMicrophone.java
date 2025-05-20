package com.digitalbiology.audio;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
//import android.os.Build;
import android.hardware.usb.UsbInterface;
import android.util.SparseArray;
import android.widget.ArrayAdapter;

import com.digitalbiology.usb.UsbDescriptor;
import com.digitalbiology.usb.UsbAudioInterface;
import com.digitalbiology.usb.UsbConfigurationParser;

import java.util.ArrayList;

public class UsbMicrophone extends Microphone {

	private static final short VENDOR_BAT = 5532;
	private static final short VENDOR_CMEDIA = 5242;

	private static final short VENDOR_PETTERSSON = 10365;
	private static final short VENDOR_DODOTRONIC = 1155;
	private static final short VENDOR_DODOTRONIC_2 = 2153;

	private static final short VENDOR_AUDIOMOTH = 5840;

	private static final int PRODUCT_USB_HIGH_SPEED = 57429;

	private static final short PRODUCT_PETTERSSON_M500 = 326;

	// 	BAT Sample Rate:    	250Ksps (miniMIC/AR125);  300Ksps (AR150); 375Ksps (AR180)
	private static final short PRODUCT_MINI_MIC = 258;

	private final UsbDescriptor mDescriptor;
	private final UsbAudioInterface mInterface;
	private final SparseArray<UsbAudioInterface> mSampleRates;
	private int mSampleRate;
	private boolean mBulkTransfer = false;

	public static boolean isSupported(UsbDevice device) {
		if (device.getVendorId() == VENDOR_BAT) return true;
		if (device.getVendorId() == VENDOR_CMEDIA && device.getProductId() == PRODUCT_USB_HIGH_SPEED) {	// Class 239  (Miscellaneous)
			int intfCount = device.getInterfaceCount();
			for (int ii = 0; ii < intfCount; ii++) {
				if (device.getInterface(ii).getInterfaceClass() == UsbConstants.USB_CLASS_AUDIO) return true;
			}
			return false;
		}

		if ((device.getVendorId() == VENDOR_PETTERSSON) && (device.getProductId() == PRODUCT_PETTERSSON_M500)) // M500
			return false;

		if (device.getDeviceClass() == UsbConstants.USB_CLASS_PER_INTERFACE) {
			int intfCount = device.getInterfaceCount();
			for (int ii = 0; ii < intfCount; ii++) {
				UsbInterface inf = device.getInterface(ii);
				if ((inf.getInterfaceClass() == UsbConstants.USB_CLASS_AUDIO) && (inf.getEndpointCount() > 0))
					return true;
			}
			return false;
		}
		return false;
	}

	public static UsbMicrophone createMicrophone(final Activity activity, UsbDeviceConnection connection, UsbDevice device) {

		//Parse received data into a description
		UsbDescriptor descriptor = UsbConfigurationParser.parse(connection, device);

		if (descriptor.interfaces.size() == 0) return null;

		if ((MainActivity.getBuildVariation() == MainActivity.VAR_PETTERSSON) && device.getVendorId() != VENDOR_PETTERSSON)  return null;
		if ((MainActivity.getBuildVariation() == MainActivity.VAR_DODOTRONIC) && !(device.getVendorId() == VENDOR_DODOTRONIC || device.getVendorId() == VENDOR_DODOTRONIC_2))  return null;

		if (device.getVendorId() == VENDOR_BAT) {
			if (device.getProductId() == PRODUCT_MINI_MIC) {
				if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
					UsbMicrophone microphone = null;
					String productName = device.getProductName();
					if (productName != null) {
						if (productName.startsWith("AR125")) {
							microphone = new UsbMicrophone(device, 250000);
							microphone.setDescription("BAT miniMIC AR125");
						}
						else if (productName.startsWith("AR150")) {
							microphone = new UsbMicrophone(device, 300000);
							microphone.setDescription("BAT miniMIC AR150");
						}
						else if (productName.startsWith("AR180")) {
							microphone = new UsbMicrophone(device, 375000);
							microphone.setDescription("BAT miniMIC AR180");
						}
					}
					return microphone;
				}
				else {
					UsbAudioInterface usbInterface = UsbAudioInterface.getValidInterface(descriptor);
					if (usbInterface == null) return null;
					final UsbMicrophone microphone = new UsbMicrophone(device, descriptor, usbInterface);
					AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.CustomDialog);
					builder.setTitle(R.string.mini_mic)
							.setCancelable(false);

					final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(
							activity,
							R.layout.mini_mic_dialog);
					arrayAdapter.add("AR125");
					arrayAdapter.add("AR150");
					arrayAdapter.add("AR180");

					builder.setAdapter(arrayAdapter,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								String productName = arrayAdapter.getItem(which);
								microphone.setDescription("BAT miniMIC " + productName);
								if (productName.startsWith("AR125"))
									microphone.setSampleRate(250000);
								else if (productName.startsWith("AR150"))
									microphone.setSampleRate(300000);
								else if (productName.startsWith("AR180"))
									microphone.setSampleRate(375000);
								dialog.dismiss();
							}
						});
					final Dialog dialog = builder.create();
					dialog.show();
					return microphone;
				}
			}
			return null;
		}
		if (device.getVendorId() == VENDOR_PETTERSSON && device.getProductId() == PRODUCT_PETTERSSON_M500) {
			UsbMicrophone microphone = new UsbMicrophone(device, 500000);	// M500
			microphone.setDescription("Pettersson M500");
			return microphone;
		}
		UsbAudioInterface usbInterface = UsbAudioInterface.getValidInterface(descriptor);
		if (usbInterface == null) return null;
		return new UsbMicrophone(device, descriptor, usbInterface);
	}

	public UsbMicrophone(UsbDevice device, UsbDescriptor descriptor, UsbAudioInterface usbInterface) {

		mDescriptor = descriptor;
		mInterface = usbInterface;
		mSampleRates = UsbAudioInterface.buildSampleRateMap(descriptor);

		mSampleRate = 0;
		for (int ii = 0; ii < mSampleRates.size(); ii++) {
			if (mSampleRates.keyAt(ii) > mSampleRate) mSampleRate = mSampleRates.keyAt(ii);
		}
		initBuffers();
	}

	public UsbMicrophone(UsbDevice device, int sampleRate) {

		mBulkTransfer = true;

		mInterface = new UsbAudioInterface();
		mInterface.altSetting = 0;
		mInterface.endPointAddr = device.getInterface(0).getEndpoint(0).getAddress();
		mInterface.maxPacketSize = device.getInterface(0).getEndpoint(0).getMaxPacketSize();
		mInterface.numChannels = 1;
		mInterface.continuousFreq = false;
		mInterface.samplingFrequencies = new int[1];
		mInterface.samplingFrequencies[0] = sampleRate;

		mSampleRates = new SparseArray<>();
		mSampleRates.put(sampleRate, mInterface);
		mSampleRate = sampleRate;

		mDescriptor = new UsbDescriptor();
		mDescriptor.interfaces = new ArrayList<>();
		mDescriptor.interfaces.add(mInterface);
		mDescriptor.vendorId = device.getVendorId();
		mDescriptor.productId = device.getProductId();
		mDescriptor.deviceName = device.getDeviceName();
		initBuffers();
	}

	public int getVendorId() {
		return mDescriptor.vendorId;
	}

	public int getProductId() {
		return mDescriptor.productId;
	}

	public int getType() {
		return USB_MICROPHONE;
	}

	public int getMaxPacketSize() {
		return mSampleRates.get(mSampleRate).maxPacketSize;
	}

	public int getBitResolution() { return mInterface.bitResolution; }

	public int getNumChannels() { return mInterface.numChannels; }

	public int getSampleRate() {
		return mSampleRate;
	}

	public void setSampleRate(int sampleRate) {
		// Only changes if the requested sample rate is valid
		if ((mSampleRate != sampleRate) && (mSampleRates.indexOfKey(sampleRate) >= 0)) {
			mSampleRate = sampleRate;
		}
	}

	public String getManufacturerName() { return mDescriptor.manufacturer;	}

	public String getProductName() {
		return mDescriptor.product;
	}

	public void setDescription(String description) {
		mDescriptor.product = description;
	}

	public int[] getSampleRates() {
		int[] rates = new int[mSampleRates.size()];
		int i = 0;
		for (int ii = 0; ii < mSampleRates.size(); ii++) {
			rates[i++] = mSampleRates.keyAt(ii);
		}
		java.util.Arrays.sort(rates);
		return rates;
	}

	public int getMaxMaxPacketSize() {
		int max = getMaxPacketSize();
		for (int ii = 0; ii < mSampleRates.size(); ii++) {
			UsbAudioInterface desc = mSampleRates.valueAt(ii);
			if (desc.maxPacketSize > max) max = desc.maxPacketSize;
		}
		return max;
	}

	public int getMaxSampleRate() {
		int max = getSampleRate();
		for (int ii = 0; ii < mSampleRates.size(); ii++) {
			UsbAudioInterface desc = mSampleRates.valueAt(ii);
			if (desc.samplingFrequencies[desc.samplingFrequencies.length-1] > max) max = desc.samplingFrequencies[desc.samplingFrequencies.length-1];
		}
		return max;
	}

	public int init() {
//		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N)
			return InitUSB2(mDescriptor.fileDescriptor, mDescriptor.deviceName);
//		else
//			return InitUSB(mDescriptor.vendorId, mDescriptor.productId);
	}

	public void cleanup() {
		CleanupUSB();
	}

	public void enterLoop() {
		UsbAudioInterface inftDesc = mSampleRates.get(mSampleRate);
		if (mBulkTransfer)
			EnterBulkUSBLoop(inftDesc.intfId, inftDesc.altSetting, inftDesc.endPointAddr, inftDesc.maxPacketSize);
		else
			EnterUSBLoop(inftDesc.intfId,
					inftDesc.altSetting,
					inftDesc.endPointAddr,
					(mSampleRates.size() > 1) ? mSampleRate : 0,		// If more than one sample rate, need to specify the one we want - otherwise pass 0
					inftDesc.maxPacketSize,
					inftDesc.bitResolution,
					inftDesc.numChannels,
					!mBulkTransfer);
	}

	public void exitLoop(boolean detached) {
		ExitUSBLoop(detached);
	}

	public void initBuffers() {
		super.initBuffers();
		AllocateTransferBuffers(getMaxPacketSize(), !mBulkTransfer);
	}

	private native int InitUSB(int VID, int PID);
	private native int InitUSB2(int fd, String devicePath);

//	private static native String GetUSBProductName(int VID, int PID);

	private native void CleanupUSB();

	private native void EnterUSBLoop(int interfaceID, int altSetting, int endpointAddress, int sampleRate, int packetSize, int dataSize, int channels, boolean isochronous);

	private native void ExitUSBLoop(boolean detached);

	private native void EnterBulkUSBLoop(int interfaceID, int altSetting, int endpointAddress, int packetSize);

	private native int AllocateTransferBuffers(int packetSize, boolean isochronous);

}
