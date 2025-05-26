package com.digitalbiology.usb;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class UsbLogger {
    public static BufferedWriter sDebugWriter;

    public static void createLog(android.content.Context context) {
        try {
            java.io.File logFile = new java.io.File(context.getExternalFilesDir(null), "usb_log.txt");
            sDebugWriter = new BufferedWriter(new FileWriter(logFile, true));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void writeToLog(String message) {
        try {
            if (sDebugWriter != null) {
                sDebugWriter.write(message);
                sDebugWriter.newLine();
                sDebugWriter.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void closeLog() {
        try {
            if (sDebugWriter != null) {
                sDebugWriter.close();
                sDebugWriter = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
