package com.digitalbiology.audio;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
//import androidx.core.content.FileProvider;
import android.widget.Toast;

import com.digitalbiology.audio.metadata.GUANOMetaDataParser;
import com.digitalbiology.audio.metadata.MetaData;
import com.digitalbiology.audio.metadata.MetaDataParser;
import com.digitalbiology.audio.metadata.WAMDMetaDataParser;
import com.digitalbiology.audio.metadata.XMPMetaDataParser;

import java.io.File;
import java.util.ArrayList;

// ---------------------------------------------
// Exports audio version of current file using current playback mode
// ---------------------------------------------
class ExportWAVTask extends AsyncTask<File, Integer, Boolean> {

    private ProgressDialog  mProgressDialog;
    private File            mAudioFile;
    private final MainActivity    mActivity;
    private final short           mPlayMode;

    public ExportWAVTask(MainActivity activity, short playMode) {

        mActivity = activity;
        mPlayMode = playMode;
    }

    @Override
    protected void onPostExecute(Boolean result) {
        super.onPostExecute(result);

        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
        if (result) {
            Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setData(FileAccessManager.getFileUri(mActivity, mAudioFile));
//            intent.setData(Uri.fromFile(mAudioFile));
            mActivity.sendBroadcast(intent);

            Toast.makeText(mActivity, R.string.export_file_succeed, Toast.LENGTH_SHORT).show();
        } else {
            // File could not be opened
            new AlertDialog.Builder(mActivity, R.style.CustomDialog)
                    .setMessage(R.string.export_file_fail)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .show();
        }
    }

    @Override
    protected void onPreExecute() {
        mProgressDialog = ProgressDialog.show(mActivity, null, null);
        mProgressDialog.setContentView(R.layout.spinner);
    }

    @Override
    protected Boolean doInBackground(File... parameters) {

        mAudioFile = parameters[0];

        long[] audioParams = new long[4];
        MainActivity.readWAVHeader(FileAccessManager.getActiveRecording().getAbsolutePath(), audioParams);
        final int recordingRate = (int) audioParams[1];
        final short channelNo = (short) audioParams[0];
        final short whichChannel = mActivity.getStereoChannel();

        ArrayList<Integer> sampleRates = mActivity.getValidSampleRates();
        int playbackRate;
        float tunerFreq = 0.0f;

        String recordPath = FileAccessManager.getActiveRecording().getAbsolutePath();
        String exportPath = mAudioFile.getAbsolutePath();

        if (mPlayMode == MainActivity.PLAY_MODE_TIME_EXPANSION) {
            // Time expansion mode
            playbackRate = AudioPlayRunnable.findBestPlaybackRate(sampleRates, recordingRate / Integer.parseInt(mActivity.getPreferences().getString("expansion", "20")));
            exportTimeExpandedWAV(recordPath, exportPath, playbackRate);
        }
        else {
            byte[] inverseBuffer = new byte[AudioPlayRunnable.INVERSE_FFT_SIZE * 2];     // really shorts
            long maxData = audioParams[3];
            int data2Read = (int) Math.min(maxData, AudioPlayRunnable.INVERSE_FFT_SIZE);
            int bytesRead;
            int readOffset = 0;

            tunerFreq = (float) recordingRate / (float) mActivity.getFreqTickView().getTuneFrequency();
            float modAdj = Float.parseFloat(mActivity.getPreferences().getString("hetadj", "1.0"));

            float compression;

            if (mPlayMode == MainActivity.PLAY_MODE_FREQUENCY_CUTOFF) {
                playbackRate = AudioPlayRunnable.findBestCutoffPlaybackRate(sampleRates, recordingRate);
                compression = (float) recordingRate / (float) playbackRate;
                if (compression < 1.0f) compression = 1.0f;
            } else {
                playbackRate = AudioPlayRunnable.findBestPlaybackRate(sampleRates, recordingRate / Integer.parseInt(mActivity.getPreferences().getString("expansion", "20")));
                compression = (float) recordingRate / (float) playbackRate;
                if (compression < 1.0f) compression = 1.0f;
            }


            exportWAVHeader(recordPath, exportPath, playbackRate);
            while ((bytesRead = AudioPlayRunnable.filterFrequencyFile(
                    mPlayMode,
                    compression,
                    tunerFreq,
                    modAdj,
                    data2Read,
                    readOffset,
                    channelNo,
                    whichChannel,
                    recordPath, inverseBuffer)) > 0) {

                exportWAVData(exportPath, inverseBuffer, (int) ((float) bytesRead / compression));
                readOffset += bytesRead / 2;       // number of shorts
                data2Read = (int) Math.min(maxData - readOffset, AudioPlayRunnable.INVERSE_FFT_SIZE);
            }
            exportWAVFinalize(exportPath);
        }

        // Metadata massaging
        File recordFile = new File(recordPath);
        MetaData metadata = MetaDataParser.getMetadata(recordFile);
        if (metadata == null) {
            metadata = new MetaData();
            metadata.namespace = mActivity.createMetaDataParser().getNamespace();
        }
        metadata.length = (float) audioParams[3] / (float) recordingRate;       // Needs to be length at original recording rate

        String filename = FileAccessManager.getActiveRecording().getName();
        if (mPlayMode == MainActivity.PLAY_MODE_TIME_EXPANSION) {
            metadata.timeExpansion = Integer.parseInt(mActivity.getPreferences().getString("expansion", "20"));
            metadata.derivedFrom = mActivity.getPreferences().getString("expansion", "20") + "x expansion of " + filename;
        }
        else if (mPlayMode == MainActivity.PLAY_MODE_FREQUENCY_CUTOFF)
            metadata.derivedFrom = "low pass filter (" + (playbackRate / 2) + " Hz) of "+filename;
        else if (mPlayMode == MainActivity.PLAY_MODE_FREQUENCY_DIVISION)
            metadata.derivedFrom = mActivity.getPreferences().getString("expansion", "20")+"x frequency division of "+filename;
        else
            metadata.derivedFrom = "heterodyne tuning (" + tunerFreq + " Hz) of "+filename;

        if (metadata.namespace == MetaData.XMP_NAMESPACE)
            new XMPMetaDataParser().update(recordFile, metadata);
        else if (metadata.namespace == MetaData.WAMD_NAMESPACE)
            new WAMDMetaDataParser().update(recordFile, metadata);
        else
            new GUANOMetaDataParser().update(recordFile, metadata);

        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setData(FileAccessManager.getFileUri(mActivity, recordFile));
//        intent.setData(Uri.fromFile(recordFile));
        mActivity.sendBroadcast(intent);

        return true;
    }

    private static native void exportTimeExpandedWAV(String inpath, String outpath, int sampleRate);
    private static native void exportWAVHeader(String recordPath, String exportPath, int sampleRate);
    private static native void exportWAVData(String exportPath, byte[] buffer, int count);
    private static native void exportWAVFinalize(String exportPath);
}

