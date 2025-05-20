package com.digitalbiology.usb;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.os.Build;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class UsbConfigurationParser {

    // =============== GENERAL =============================

    private static final int INTERFACE_DESCRIPTOR = 0x04;
    private static final int ENDPOINT_DESCRIPTOR = 0x05;
    private static final int CS_DEVICE = 0x21;
    private static final int CS_INTERFACE = 0x24;
    private static final int CS_ENDPOINT = 0x25;

    private static final int DESC_SIZE_CONFIG = 9;

    //    private static final int DESC_OFFSET = 18;
    private static final int DESC_OFFSET = 0;

    private static final int STD_USB_REQUEST_GET_DESCRIPTOR = 0x06;
    private static final int LIBUSB_DT_STRING = 0x03;
    //
//    //Value: Descriptor Type (High) and Index (Low)
//    // Configuration Descriptor = 0x2
//    // Index = 0x0 (First configuration)
    private static final int REQ_VALUE = 0x200;
    private static final int REQ_INDEX = 0x00;
//    private static final int BUFFER_LENGTH = 255;
    private static final int BUFFER_LENGTH = 2000;

    // =============== AUDIO =============================
    /******* Audio Class-Specific AC Interface Descriptor Subtypes**********/
    private final static int AC_DESCRIPTOR_UNDEFINED = 0x00;
    private final static int HEADER = 0x01;
    private final static int INPUT_TERMINAL = 0x02;
    private final static int OUTPUT_TERMINAL = 0x03;
    private final static int MIXER_UNIT = 0x04;
    private final static int SELECTOR_UNIT = 0x05;
    private final static int FEATURE_UNIT = 0x06;
    private final static int PROCESSING_UNIT = 0x07;
    private final static int EXTENSION_UNIT = 0x08;

    /******* Audio Class-Specific AS Interface Descriptor Subtypes**********/
    private final static int AS_DESCRIPTOR_UNDEFINED = 0x00;
    private final static int AS_GENERAL = 0x01;
    private final static int FORMAT_TYPE = 0x02;
    private final static int FORMAT_SPECIFIC = 0x03;

    private final static int AUDIOCONTROL = 0x01;
    private final static int AUDIOSTREAMING = 0x02;
    private final static int MIDISTREAMING = 0x03;

//    private static final short VENDOR_PETTERSSON = 10365;
//
//    private static final short PRODUCT_M500_384 = 592;
//
//    private static final short VENDOR_DODOTRONIC = 2153;
//
//    private static final short PRODUCT_ULTRAMIC_192 = 776;
//    private static final short PRODUCT_ULTRAMIC_200 = 769;
//    private static final short PRODUCT_ULTRAMIC_250 = 770;
//    private static final short PRODUCT_ULTRAMIC_200_R4 = 773;
//    private static final short PRODUCT_ULTRAMIC_250_R4 = 774;
//    private static final short PRODUCT_ULTRAMIC_384 = 777;

    public static UsbDescriptor parse(UsbDeviceConnection connection, UsbDevice device) {

        UsbDescriptor descriptor = new UsbDescriptor();
        byte[] buffer = new byte[BUFFER_LENGTH];
        int rdo;

        byte[] rawDescs = connection.getRawDescriptors();
        if (UsbLogger.sDebugWriter != null) {
            try {
                UsbLogger.writeToLog("==============================");
                UsbLogger.writeToLog("Raw descriptors:");
                UsbLogger.sDebugWriter.write(bytesToHex(rawDescs));
                UsbLogger.sDebugWriter.newLine();
            }
            catch (IOException e) {
            }
        }
        descriptor.fileDescriptor = connection.getFileDescriptor();
        descriptor.deviceName = device.getDeviceName();
        UsbLogger.writeToLog("fileDescriptor = " + descriptor.fileDescriptor);
        UsbLogger.writeToLog("deviceName = " + descriptor.deviceName);

        descriptor.vendorId = device.getVendorId();
        descriptor.productId = device.getProductId();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            descriptor.manufacturer = device.getManufacturerName();
            descriptor.product = device.getProductName();
        }
        else {
            int idxMan = rawDescs[14];
            int idxPrd = rawDescs[15];
            try {
                int langid = 0;
                rdo = connection.controlTransfer(UsbConstants.USB_DIR_IN, STD_USB_REQUEST_GET_DESCRIPTOR,
                        (LIBUSB_DT_STRING << 8), REQ_INDEX, buffer, BUFFER_LENGTH, 0);
                if (rdo >= 0) {
                    langid = buffer[2] | (buffer[3] << 8);
                    UsbLogger.writeToLog("language id = " + String.format("0x%04X", langid));
                } else
                    UsbLogger.writeToLog("controlTransfer for langid failed ERR=" + rdo);

                rdo = connection.controlTransfer(UsbConstants.USB_DIR_IN, STD_USB_REQUEST_GET_DESCRIPTOR,
                        (LIBUSB_DT_STRING << 8) | idxMan, langid, buffer, BUFFER_LENGTH, 0);
                if (rdo >= 0) {
                    descriptor.manufacturer = new String(buffer, 2, rdo - 2, "UTF-16LE");
                    UsbLogger.writeToLog("manufacturer = " + descriptor.manufacturer);
                } else
                    UsbLogger.writeToLog("controlTransfer for manufacturer failed ERR=" + rdo);

                rdo = connection.controlTransfer(UsbConstants.USB_DIR_IN, STD_USB_REQUEST_GET_DESCRIPTOR,
                        (LIBUSB_DT_STRING << 8) | idxPrd, langid, buffer, BUFFER_LENGTH, 0);
                if (rdo >= 0) {
                    descriptor.product = new String(buffer, 2, rdo - 2, "UTF-16LE");
                    UsbLogger.writeToLog("product = " + descriptor.product);
                } else
                    UsbLogger.writeToLog("controlTransfer for product failed ERR=" + rdo);

            } catch (UnsupportedEncodingException e) {
                UsbLogger.writeToLog("UsbDescriptor: ERR=" + e.getMessage());
            }
        }
        UsbLogger.writeToLog("manufacturer = " + descriptor.manufacturer);
        UsbLogger.writeToLog("product = " + descriptor.product);

        rdo = connection.controlTransfer(UsbConstants.USB_DIR_IN, STD_USB_REQUEST_GET_DESCRIPTOR,
                REQ_VALUE, REQ_INDEX, buffer, BUFFER_LENGTH, 0);
        if (rdo >= 0) {
            if (UsbLogger.sDebugWriter != null) {
                printConfigDescriptor(buffer, UsbLogger.sDebugWriter);
            }
            parseConfigDescriptor(descriptor, buffer);
        }
        return descriptor;
    }

    private static void parseConfigDescriptor(UsbDescriptor descriptor, byte[] buffer) {

        UsbInterface interfaceDesc = null;
        //Parse configuration descriptor header
        int totalLength = (buffer[DESC_OFFSET+3] & 0xFF) << 8;
        totalLength += (buffer[DESC_OFFSET+2] & 0xFF);
        totalLength += DESC_OFFSET;
        //Interface count
//        int numInterfaces = (buffer[5] & 0xFF);
//        //Configuration attributes
//        int attributes = (buffer[7] & 0xFF);
//        //Power is given in 2mA increments
//        int maxPower = (buffer[8] & 0xFF) * 2;

        //The rest of the descriptor is interfaces and endpoints
        int index = DESC_OFFSET+DESC_SIZE_CONFIG;
        int intfClass = 0;
        int subIntfClass = 0;
        while (index < totalLength) {
            //Read length and type
            int len = (buffer[index] & 0xFF);
            int type = (buffer[index+1] & 0xFF);
            int sub;
            switch (type) {
                case INTERFACE_DESCRIPTOR: //Interface Descriptor
                    int intfNumber = (buffer[index+2] & 0xFF);
                    int bAlternateSetting = (buffer[index+3] & 0xFF);
//                    int numEndpoints = (buffer[index+4] & 0xFF);
                    intfClass = (buffer[index+5] & 0xFF);
                    subIntfClass = (buffer[index+6] & 0xFF);

                    if (intfClass == UsbConstants.USB_CLASS_AUDIO)
                        interfaceDesc = new UsbAudioInterface();
                    else
                        interfaceDesc = new UsbInterface();

                    descriptor.interfaces.add(interfaceDesc);
                    interfaceDesc.intfClass = intfClass;
                    interfaceDesc.intfId = intfNumber;
                    interfaceDesc.altSetting = bAlternateSetting;

                    break;

                case ENDPOINT_DESCRIPTOR: //Endpoint Descriptor
                    int endpointAddr = ((buffer[index+2] & 0xFF));
                    //Number is lower 4 bits
//                    int endpointNum = (endpointAddr & 0x0F);
                    //Direction is high bit
                    int direction = (endpointAddr & 0x80);

                    int endpointAttrs = (buffer[index+3] & 0xFF);
                    //Type is the lower two bits
                    int endpointType = (endpointAttrs & 0x03);

                    if ((direction == UsbConstants.USB_DIR_IN) && (descriptor != null)) {
                        interfaceDesc.endpointType = endpointType;
                        interfaceDesc.endPointAddr = endpointAddr;
                        interfaceDesc.maxPacketSize = ((buffer[index+5] & 0xFF) << 8) + (buffer[index+4] & 0xFF);
                    }
                    break;

                case CS_INTERFACE:
                    if (intfClass == UsbConstants.USB_CLASS_AUDIO) {
                        sub = (buffer[index + 2] & 0xFF);
                        if (subIntfClass == AUDIOSTREAMING) {
                            UsbAudioInterface audioIntf = (UsbAudioInterface) interfaceDesc;
                            if (sub == AS_GENERAL) {
                                int wFormatTag = (buffer[DESC_OFFSET + 6] & 0xFF) << 8;
                                wFormatTag += (buffer[DESC_OFFSET + 5] & 0xFF);
                                audioIntf.formatTag = wFormatTag;
                            } else if (sub == FORMAT_TYPE) {
                                //                            int bFormatType = (buffer[index + 3] & 0xFF);
                                int bNrChannels = (buffer[index + 4] & 0xFF);
                                //                            int bSubFrameSize = (buffer[index + 5] & 0xFF);
                                int bBitResolution = (buffer[index + 6] & 0xFF);
                                int bSamFreqType = (buffer[index + 7] & 0xFF);

                                audioIntf.numChannels = bNrChannels;
                                audioIntf.bitResolution = bBitResolution;

                                int jj = index + 8;
                                if (bSamFreqType == 0) {
                                    // Continuous
                                    int tLowerSamFreq = (buffer[jj + 2] & 0xFF) << 16;
                                    tLowerSamFreq += (buffer[jj + 1] & 0xFF) << 8;
                                    tLowerSamFreq += (buffer[jj] & 0xFF);
                                    jj += 3;
                                    int tUpperSamFreq = (buffer[jj + 2] & 0xFF) << 16;
                                    tUpperSamFreq += (buffer[jj + 1] & 0xFF) << 8;
                                    tUpperSamFreq += (buffer[jj] & 0xFF);

                                    audioIntf.continuousFreq = true;
                                    audioIntf.samplingFrequencies = new int[2];
                                    audioIntf.samplingFrequencies[0] = tLowerSamFreq;
                                    audioIntf.samplingFrequencies[1] = tUpperSamFreq;
                                } else {
                                    // Discrete
                                    audioIntf.continuousFreq = false;
                                    audioIntf.samplingFrequencies = new int[bSamFreqType];
                                    for (int ii = 0; ii < bSamFreqType; ii++, jj += 3) {
                                        int tSamFreq = (buffer[jj + 2] & 0xFF) << 16;
                                        tSamFreq += (buffer[jj + 1] & 0xFF) << 8;
                                        tSamFreq += (buffer[jj] & 0xFF);
                                        audioIntf.samplingFrequencies[ii] = tSamFreq;
                                    }
                                }
                            }
                        }
                    }
                    break;

                case CS_ENDPOINT:
                    interfaceDesc = null;
                    break;

                default:
                    break;
            }
            //Advance to next descriptor
            index += len;
        }
    }

    private static void printConfigDescriptor(byte[] buffer, BufferedWriter writer) {

        StringBuilder sb = new StringBuilder();
        sb.append("Configuration Descriptor:\n");
        //Parse configuration descriptor header
        int totalLength = (buffer[DESC_OFFSET+3] & 0xFF) << 8;
        totalLength += (buffer[DESC_OFFSET+2] & 0xFF);
        sb.append("Length: ");
        sb.append(totalLength);
        sb.append(" bytes\n");
        totalLength += DESC_OFFSET;
        //Interface count
        int numInterfaces = (buffer[DESC_OFFSET+5] & 0xFF);
        //Configuration attributes
        int attributes = (buffer[DESC_OFFSET+7] & 0xFF);
        //Power is given in 2mA increments
        int maxPower = (buffer[DESC_OFFSET+8] & 0xFF) * 2;

        sb.append(numInterfaces);
        sb.append(" Interfaces\n");
        sb.append(String.format("Attributes:%s%s%s\n",
                (attributes & 0x80) == 0x80 ? " BusPowered" : "",
                (attributes & 0x40) == 0x40 ? " SelfPowered" : "",
                (attributes & 0x20) == 0x20 ? " RemoteWakeup" : ""));
        sb.append("Max Power: ");
        sb.append(maxPower);
        sb.append("mA\n");

        //The rest of the descriptor is interfaces and endpoints
        int index = DESC_OFFSET+DESC_SIZE_CONFIG;
        int intfClass = 0;
        int subIntfClass = 0;
        while (index < totalLength) {
            //Read length and type
            int len = (buffer[index] & 0xFF);
            int type = (buffer[index+1] & 0xFF);
            int sub;
            String subString;
            switch (type) {
                case INTERFACE_DESCRIPTOR: //Interface Descriptor
                    int intfNumber = (buffer[index+2] & 0xFF);
                    int bAlternateSetting = (buffer[index+3] & 0xFF);
                    int numEndpoints = (buffer[index+4] & 0xFF);
                    intfClass = (buffer[index+5] & 0xFF);
                    subIntfClass = (buffer[index+6] & 0xFF);

                    sb.append(String.format("\nbDescriptorType=USB_INTERFACE_DESCRIPTOR bLength=%d bInterfaceNumber=%d bInterfaceClass=%s bInterfaceSubclass=%s bAlternateSetting=%d bNumEndpoints=%d\n",
                            len, intfNumber, nameForClass(intfClass), nameForSubClass(subIntfClass), bAlternateSetting, numEndpoints));
                    break;

                case ENDPOINT_DESCRIPTOR: //Endpoint Descriptor
                    int endpointAddr = ((buffer[index+2] & 0xFF));
                    //Number is lower 4 bits
//                    int endpointNum = (endpointAddr & 0x0F);
                    //Direction is high bit
                    int direction = (endpointAddr & 0x80);

                    int endpointAttrs = (buffer[index+3] & 0xFF);
                    //Type is the lower two bits
                    int endpointType = (endpointAttrs & 0x03);

                    sb.append(String.format("bDescriptorType=USB_ENDPOINT_DESCRIPTOR bLength=%d bEndpointAddress=%d type=%s direction=%s\n",
                            len,
                            endpointAddr,
                            nameForEndpointType(endpointType),
                            nameForDirection(direction)));
                    break;

                case CS_INTERFACE:
                    if (intfClass == UsbConstants.USB_CLASS_AUDIO) {
                        sub = (buffer[index + 2] & 0xFF);
                        if (subIntfClass == AUDIOCONTROL)
                            subString = nameForACSubtype(sub);
                        else if (subIntfClass == AUDIOSTREAMING)
                            subString = nameForASSubtype(sub);
                        else
                            subString = Integer.toString(subIntfClass);
                        sb.append(String.format("bDescriptorType=CS_INTERFACE bLength=%d bDescriptorSubtype=%s", len, subString));
                        if (subIntfClass == AUDIOCONTROL) {
                            if (sub == OUTPUT_TERMINAL) {
                                int wTerminalType = (buffer[DESC_OFFSET + 5] & 0xFF) << 8;
                                wTerminalType += (buffer[DESC_OFFSET + 4] & 0xFF);
                                int bAssocTerminal = (buffer[index + 6] & 0xFF);
                                sb.append(String.format(" bTerminalID=%d wTerminalType=%#08x bAssocTerminal=%d", (buffer[index + 3] & 0xFF), wTerminalType, bAssocTerminal));
                            } else if (sub == INPUT_TERMINAL) {
                                int wTerminalType = (buffer[DESC_OFFSET + 5] & 0xFF) << 8;
                                wTerminalType += (buffer[DESC_OFFSET + 4] & 0xFF);
                                int bAssocTerminal = (buffer[index + 6] & 0xFF);
                                int bNrChannels = (buffer[index + 7] & 0xFF);
                                sb.append(String.format(" bTerminalID=%d wTerminalType=%#08x bAssocTerminal=%d bNrChannels=%d",
                                        (buffer[index + 3] & 0xFF), wTerminalType, bAssocTerminal, bNrChannels));
                            } else if (sub == HEADER) {
                                int bcdADC = (buffer[DESC_OFFSET + 4] & 0xFF) << 8;
                                bcdADC += (buffer[DESC_OFFSET + 3] & 0xFF);
                                sb.append(String.format(" bcdADC=%#08x", bcdADC));
                            } else if (sub == FEATURE_UNIT) {
                                int bUnitID = (buffer[index + 3] & 0xFF);
                                int bSourceID = (buffer[index + 4] & 0xFF);
                                int bControlSize = (buffer[index + 5] & 0xFF);
                                int iFeature = (buffer[index + len - 1] & 0xFF);
                                sb.append(String.format(" bUnitID=%d bSourceID=%d bControlSize=%d iFeature=%d", bUnitID, bSourceID, bControlSize, iFeature));
                            }
                        } else if (subIntfClass == AUDIOSTREAMING) {
                            if (sub == AS_GENERAL) {
                                int wFormatTag = (buffer[DESC_OFFSET + 6] & 0xFF) << 8;
                                wFormatTag += (buffer[DESC_OFFSET + 5] & 0xFF);
                                int bTerminalLink = (buffer[index + 3] & 0xFF);
                                int bDelay = (buffer[index + 4] & 0xFF);
                                sb.append(String.format(" bTerminalLink=%d bDelay=%d wFormatTag=%d", bTerminalLink, bDelay, wFormatTag));
                            } else if (sub == FORMAT_TYPE) {
                                int bFormatType = (buffer[index + 3] & 0xFF);
                                int bNrChannels = (buffer[index + 4] & 0xFF);
                                int bSubFrameSize = (buffer[index + 5] & 0xFF);
                                int bBitResolution = (buffer[index + 6] & 0xFF);
                                int bSamFreqType = (buffer[index + 7] & 0xFF);

                                sb.append(String.format(" bFormatType=%d bNrChannels=%d bSubFrameSize=%d bBitResolution=%d bSamFreqType=%d",
                                        bFormatType, bNrChannels, bSubFrameSize, bBitResolution, bSamFreqType));
                                int jj = index + 8;
                                if (bSamFreqType == 0) {
                                    // Continuous
                                    int tLowerSamFreq = (buffer[jj + 2] & 0xFF) << 16;
                                    tLowerSamFreq += (buffer[jj + 1] & 0xFF) << 8;
                                    tLowerSamFreq += (buffer[jj] & 0xFF);
                                    jj += 3;
                                    int tUpperSamFreq = (buffer[jj + 2] & 0xFF) << 16;
                                    tUpperSamFreq += (buffer[jj + 1] & 0xFF) << 8;
                                    tUpperSamFreq += (buffer[jj] & 0xFF);

                                    sb.append(String.format(" tLowerSamFreq=%d tUpperSamFreq=%d", tLowerSamFreq, tUpperSamFreq));
                                } else {
                                    // Discrete
                                    for (int ii = 0; ii < bSamFreqType; ii++, jj += 3) {

                                        int tSamFreq = (buffer[jj + 2] & 0xFF) << 16;
                                        tSamFreq += (buffer[jj + 1] & 0xFF) << 8;
                                        tSamFreq += (buffer[jj] & 0xFF);
                                        sb.append(String.format(" tSamFreq[%d]=%d", ii, tSamFreq));
                                    }
                                }
                            }
                        }
                        sb.append("\n");
                    }
                    break;

                case CS_ENDPOINT:		// 37
                    sb.append(String.format("bDescriptorType=CS_ENDPOINT bLength=%d", len));
                    sub = (buffer[index+2] & 0xFF);
                    if (intfClass == UsbConstants.USB_CLASS_AUDIO) {
                        if (subIntfClass == AUDIOSTREAMING && sub == AS_GENERAL) {
                            subString = nameForASSubtype(sub);
                            int bmAttributes = (buffer[index + 3] & 0xFF);
                            sb.append(String.format(" bDescriptorSubtype=%s bmAttributes=%d", subString, bmAttributes));
                        }
                        sb.append("\n");
                    }
                    break;

                case CS_DEVICE:		// 33
                    sb.append(String.format("bDescriptorType=CS_DEVICE bLength=%d\n", len));
                    break;

                default:
                    sb.append(String.format("bDescriptorType=%d bLength=%d\n", type, len));
                    break;
            }
            //Advance to next descriptor
            index += len;
        }

        try {
            UsbLogger.writeToLog("==============================");
            writer.write(sb.toString());
            UsbLogger.writeToLog("==============================");
        }
        catch (Exception e) {
        }
    }

    /* Helper Methods to Provide Readable Names for USB Constants */

    private static String nameForACSubtype(int type) {
        switch (type) {
            case AC_DESCRIPTOR_UNDEFINED:
                return "AC_DESCRIPTOR_UNDEFINED";
            case HEADER:
                return "HEADER";
            case INPUT_TERMINAL:
                return "INPUT_TERMINAL";
            case OUTPUT_TERMINAL:
                return "OUTPUT_TERMINAL";
            case MIXER_UNIT:
                return "MIXER_UNIT";
            case SELECTOR_UNIT:
                return "SELECTOR_UNIT";
            case FEATURE_UNIT:
                return "FEATURE_UNIT";
            case PROCESSING_UNIT:
                return "PROCESSING_UNIT";
            case EXTENSION_UNIT:
                return "EXTENSION_UNIT";
            default:
                return "Unknown";
        }
    }

    private static String nameForASSubtype(int type) {
        switch (type) {
            case AS_DESCRIPTOR_UNDEFINED:
                return "AS_DESCRIPTOR_UNDEFINED";
            case AS_GENERAL:
                return "AS_GENERAL";
            case FORMAT_TYPE:
                return "FORMAT_TYPE";
            case FORMAT_SPECIFIC:
                return "FORMAT_SPECIFIC";
            default:
                return "Unknown";
        }
    }

    private static String nameForClass(int classType) {
        switch (classType) {
            case UsbConstants.USB_CLASS_APP_SPEC:
                return String.format("Application Specific 0x%02x", classType);
            case UsbConstants.USB_CLASS_AUDIO:
                return "Audio";
            case UsbConstants.USB_CLASS_CDC_DATA:
                return "CDC Control";
            case UsbConstants.USB_CLASS_COMM:
                return "Communications";
            case UsbConstants.USB_CLASS_CONTENT_SEC:
                return "Content Security";
            case UsbConstants.USB_CLASS_CSCID:
                return "Content Smart Card";
            case UsbConstants.USB_CLASS_HID:
                return "Human Interface Device";
            case UsbConstants.USB_CLASS_HUB:
                return "Hub";
            case UsbConstants.USB_CLASS_MASS_STORAGE:
                return "Mass Storage";
            case UsbConstants.USB_CLASS_MISC:
                return "Wireless Miscellaneous";
            case UsbConstants.USB_CLASS_PER_INTERFACE:
                return "(Defined Per Interface)";
            case UsbConstants.USB_CLASS_PHYSICA:
                return "Physical";
            case UsbConstants.USB_CLASS_PRINTER:
                return "Printer";
            case UsbConstants.USB_CLASS_STILL_IMAGE:
                return "Still Image";
            case UsbConstants.USB_CLASS_VENDOR_SPEC:
                return String.format("Vendor Specific 0x%02x", classType);
            case UsbConstants.USB_CLASS_VIDEO:
                return "Video";
            case UsbConstants.USB_CLASS_WIRELESS_CONTROLLER:
                return "Wireless Controller";
            default:
                return String.format("0x%02x", classType);
        }
    }

    private static String nameForSubClass(int classType) {
        switch (classType) {
             case AUDIOCONTROL:
                return "AUDIOCONTROL";
            case AUDIOSTREAMING:
                return "AUDIOSTREAMING";
            case MIDISTREAMING:
                return "MIDISTREAMING";
             default:
                return String.format("0x%02x", classType);
        }
    }

    private static String nameForEndpointType(int type) {
        switch (type) {
            case UsbConstants.USB_ENDPOINT_XFER_BULK:
                return "Bulk";
            case UsbConstants.USB_ENDPOINT_XFER_CONTROL:
                return "Control";
            case UsbConstants.USB_ENDPOINT_XFER_INT:
                return "Interrupt";
            case UsbConstants.USB_ENDPOINT_XFER_ISOC:
                return "Isochronous";
            default:
                return "Unknown Type";
        }
    }

    private static String nameForDirection(int direction) {
        switch (direction) {
            case UsbConstants.USB_DIR_IN:
                return "IN";
            case UsbConstants.USB_DIR_OUT:
                return "OUT";
            default:
                return "Unknown Direction";
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for ( int j = 0; j < bytes.length; j++ ) sb.append(String.format("%02X ", bytes[j]));
        return sb.toString();
    }

//    private static native String GetUSBManufacturerName(int VID);
//    private static native String GetUSBProductName(int VID, int PID);

    public static native  void EnableLogging(boolean enable);
    public static native String GetLogMessage();
}
