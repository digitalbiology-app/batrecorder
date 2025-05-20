package com.digitalbiology.usb;

import java.util.ArrayList;

public class UsbDescriptor {

    public int      vendorId;
    public int      productId;
    public int      fileDescriptor;
    public String   deviceName;
    public String   manufacturer;
    public String   product;
    public ArrayList<UsbInterface> interfaces;

    public UsbDescriptor() {
        vendorId = 0;
        productId = 0;
        fileDescriptor = -1;
        deviceName = null;
        manufacturer = "";
        product = "";
        interfaces = new ArrayList<>();
    }
}
