package com.digitalbiology.usb;

import android.hardware.usb.UsbConstants;
import android.util.SparseArray;

public class UsbAudioInterface extends UsbInterface {

    private final static int WAVE_FORMAT_PCM = 1;
    private final static int WILDLIFE_ACOUSTICS = 10534;
    private final static int DODOTRONICS = 1155;

    private final static int DOREMIC_R1 = 22357;       // product id

    public int formatTag;
    public int bitResolution;
    public int numChannels;
    public int[] samplingFrequencies;
    public boolean continuousFreq;

    public static SparseArray<UsbAudioInterface> buildSampleRateMap(UsbDescriptor desc) {
        SparseArray<UsbAudioInterface> map = new SparseArray<>();
        for (UsbInterface inf : desc.interfaces) {
            if (inf instanceof UsbAudioInterface) {
                UsbAudioInterface ainf = (UsbAudioInterface) inf;
                if (UsbAudioInterface.isValidInterface(desc,  ainf)) {
                    for (int ii = 0; ii < ainf.samplingFrequencies.length; ii++) {
                        map.put(ainf.samplingFrequencies[ii], ainf);
                    }
                }
            }
        }
        return map;
    }

    private static boolean isValidInterface(UsbDescriptor desc,  UsbAudioInterface ainf) {
        if ((ainf.endpointType == UsbConstants.USB_ENDPOINT_XFER_ISOC) && (ainf.intfClass == UsbConstants.USB_CLASS_AUDIO)) {
            if  (ainf.bitResolution == 16) {
                if (ainf.formatTag == WAVE_FORMAT_PCM) {
                    return true;
                } else if ((desc.vendorId == WILDLIFE_ACOUSTICS) && (ainf.formatTag == 1025)) {
                    return true;
                }
            } else if ((ainf.bitResolution == 24) && (desc.vendorId == DODOTRONICS) && (desc.productId == DOREMIC_R1)) {
                return true;
            }
        }
        return false;
    }

    public static UsbAudioInterface getValidInterface(UsbDescriptor desc) {
        for (UsbInterface inf : desc.interfaces) {
            if (inf instanceof UsbAudioInterface) {
                if (UsbAudioInterface.isValidInterface(desc,  (UsbAudioInterface) inf)) {
                    return (UsbAudioInterface) inf;
                }
            }
        }
        return null;
    }
}
