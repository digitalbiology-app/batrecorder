package com.digitalbiology.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.widget.Toast;

// ---------------------------------------------
// USB audio thread, gets and encodes audio data
// ---------------------------------------------
class UsbRecordRunnable implements Runnable {

    private final MainActivity    mActivity;
    public static volatile Thread mAudioReadThread = null;

    public UsbRecordRunnable(MainActivity activity) {
        mActivity = activity;
    }

    @Override
    public void run() {

        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);    // Set the thread priority

        mActivity.setListening(true);
        ResetAudioBuffers();

        if (BuildConfig.DEBUG && (mAudioReadThread != null)) throw new RuntimeException();
        mAudioReadThread = new Thread(new AudioReadRunnable(mActivity));
        mAudioReadThread.start();

        Microphone microphone = MainActivity.getMicrophone();
        if (mActivity.IsUSBInitialized()) {
            ((UsbMicrophone) microphone).enterLoop();
        } else {
            AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    microphone.getSampleRate(),
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    ((DeviceMicrophone) microphone).getBufferSize());
            audioRecord.startRecording();

            int bufferReadResult;

            short[] audioData = ((DeviceMicrophone) microphone).getAudioData();
            while (mActivity.isListening()) {
                bufferReadResult = audioRecord.read(audioData, 0, audioData.length);
                if (bufferReadResult > 0) {
                    WriteDataToAudioBuffer(audioData, bufferReadResult);
                }
            }
            audioRecord.stop();
            audioRecord.release();
        }
        mActivity.setReading(false);
        while (mAudioReadThread != null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        mActivity.mUSBListenThread = null;
        if (mActivity.isListening()) {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mActivity.stopListening();
                }
            });
        }
        int overwrites = GetOverwriteCount();
        if (overwrites > 0) {
            final String message = Integer.toString(overwrites / 2) + ' ' + mActivity.getString(R.string.dropped);
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(mActivity, message, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private native void ResetAudioBuffers();
    private native void WriteDataToAudioBuffer(short[] data, int dataLength);
    private native int GetOverwriteCount();
}
