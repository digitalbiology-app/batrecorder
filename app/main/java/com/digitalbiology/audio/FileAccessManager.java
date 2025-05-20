package com.digitalbiology.audio;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
//import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FileAccessManager {

    static private String sStorageDirectoryPath = null;
    static private File sInternalStorageDirectory = null;
    static private File sExternalStorageDirectory = null;
    static private volatile File sActiveRecording = null;
    static private volatile File sActiveDirectory = null;

    private final static SimpleDateFormat sFileNameFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
    private final static SimpleDateFormat sDirNameFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    static private boolean hasExternalSDCard() {
        try {
            String state = Environment.getExternalStorageState();
            if(Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state))
                return true;
        }
        catch (Throwable e) {
        }
        return false;
    }

    static public void init(Context context, Boolean useExternalStorage) {

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q)
            sInternalStorageDirectory = new File(Environment.getExternalStorageDirectory(), "BatRecorder");
        else
            sInternalStorageDirectory = new File(context.getExternalFilesDir(null), "BatRecorder");
        if (!sInternalStorageDirectory.exists()) {
            Boolean success = sInternalStorageDirectory.mkdir();
            if (!success) {
                Log.e(MainActivity.TAG, "Unable to create " + sInternalStorageDirectory.getAbsolutePath());
            }
        }
        sStorageDirectoryPath = sInternalStorageDirectory.getAbsolutePath() + File.separator;

        sExternalStorageDirectory = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            if (FileAccessManager.hasExternalSDCard()) {
                File[] dirs = ContextCompat.getExternalFilesDirs(context, null);
                File dir = null;
                if (dirs.length > 1) {
                    dir = dirs[1];
                    sExternalStorageDirectory = new File(dir, "BatRecorder");
                    if (!sExternalStorageDirectory.exists()) {
                        Boolean success = sExternalStorageDirectory.mkdir();
                        if (!success) {
                            Log.e(MainActivity.TAG, "Unable to create " + sExternalStorageDirectory.getAbsolutePath());
                        }
                    }
                    if (sExternalStorageDirectory.exists()) {
                        if (useExternalStorage) {
                            sStorageDirectoryPath = sExternalStorageDirectory.getAbsolutePath() + File.separator;
                        }
                    } else {
                        sExternalStorageDirectory = null;
                    }
                }
            }
        }
    }

    static public void setActiveRecording(File recording) {
        sActiveRecording = recording;
    }

    static public File getActiveRecording() { return sActiveRecording; }

    static public File getInternalStorageDirectory() {
        return sInternalStorageDirectory;
    }

    static public File getExternalStorageDirectory() {
        return sExternalStorageDirectory;
    }

    static public String getStorageDirectoryPath() {
        return sStorageDirectoryPath;
    }

    static public File getStorageDirectory(Boolean useExternalStorage) {
        // NOTE - can't use this in listen/record thread as it takes too long
        if (useExternalStorage && (sExternalStorageDirectory != null)) {
            sStorageDirectoryPath = sExternalStorageDirectory.getAbsolutePath() + File.separator;
            return sExternalStorageDirectory;
        }
        sStorageDirectoryPath = sInternalStorageDirectory.getAbsolutePath() + File.separator;
        return sInternalStorageDirectory;
    }

    static public String uniqueFilename(Date date) {
        return sFileNameFormat.format(date) + ".wav";
    }

    static public void setActiveDirectory(File directory) {
        sActiveDirectory = directory;
    }

    static public File getActiveDirectory(boolean create) {
        if (create && (sActiveDirectory == null)) {
            sActiveDirectory = new File(getStorageDirectoryPath(), sDirNameFormat.format(new Date()));
            if (!sActiveDirectory.exists()) {
                sActiveDirectory.mkdir();
            }
        }
        return sActiveDirectory;
    }

    static public void createActiveRecording(String name) {
        sActiveRecording = new File(FileAccessManager.getActiveDirectory(true), name);
    }

    static public Uri getFileUri(Context context, File file) {
        return FileProvider.getUriForFile(context, "com.digitalbiology.audio.fileprovider", file);
    }
}
