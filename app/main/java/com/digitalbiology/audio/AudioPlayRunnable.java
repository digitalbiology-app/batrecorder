package com.digitalbiology.audio;

import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.widget.HorizontalScrollView;

import com.digitalbiology.audio.views.FreqTickView;
import com.digitalbiology.audio.views.PowerView;
import com.digitalbiology.audio.views.WaveformView;

import java.util.ArrayList;
import java.util.Arrays;

class AudioPlayRunnable implements Runnable {

    private final float[]       mTimeCompression = new float[MainActivity.PLAY_MODE_COUNT];
    private final int[]         mPlaybackRate = new int[MainActivity.PLAY_MODE_COUNT];
    private int                mReadOffset;
    private int                 mWindowSize;

    private final MainActivity mActivity;

    public static final int INVERSE_FFT_SIZE = 4096;
    private static final byte[] inverseBuffer = new byte[INVERSE_FFT_SIZE * 2];     // really shorts

    private static final int SCROLL_SPACER = 100;

    public AudioPlayRunnable(MainActivity activity) {
        mActivity = activity;
    }

    @Override
    public void run() {

        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);    // Set the thread priority

        final WaveformView waveformView = mActivity.getWaveformView();
        final FreqTickView freqTickView = mActivity.getFreqTickView();
        final HorizontalScrollView  scrollView = mActivity.getScrollView();

        long maxData = 0;
        int recordingRate;
        short channelNo = mActivity.getStereoChannel();
        short whichChannel = 1;
        if (mActivity.isAudioWhileListening()) {
            recordingRate = MainActivity.getMicrophone().getSampleRate();
        } else {
            long[] audioParams = new long[4];
            if (BuildConfig.DEBUG && (FileAccessManager.getActiveRecording() == null)) throw new RuntimeException();
            MainActivity.readWAVHeader(FileAccessManager.getActiveRecording().getAbsolutePath(), audioParams);
            recordingRate = (int) audioParams[1];
            maxData = audioParams[3];
            channelNo = (short) audioParams[0];
        }

        SharedPreferences   preferences = mActivity.getPreferences();

        ArrayList<Integer> sampleRates = mActivity.getValidSampleRates();
        int playbackRate = findBestPlaybackRate(sampleRates, recordingRate / Integer.parseInt(preferences.getString("expansion", "20")));
        float compression = (float) recordingRate / (float) playbackRate;
        if (compression < 1.0f) compression = 1.0f;
        mTimeCompression[MainActivity.PLAY_MODE_FREQUENCY_DIVISION]      = compression;
        mTimeCompression[MainActivity.PLAY_MODE_HETERODYNE]              = compression;
        mTimeCompression[MainActivity.PLAY_MODE_TIME_EXPANSION]          = 1.0f;

        mPlaybackRate[MainActivity.PLAY_MODE_FREQUENCY_DIVISION]         = playbackRate;
        mPlaybackRate[MainActivity.PLAY_MODE_HETERODYNE]                 = playbackRate;
        mPlaybackRate[MainActivity.PLAY_MODE_TIME_EXPANSION]             = playbackRate;

        playbackRate = findBestCutoffPlaybackRate(sampleRates, recordingRate);
        compression = (float) recordingRate / (float) playbackRate;
        if (compression < 1.0f) compression = 1.0f;
        mTimeCompression[MainActivity.PLAY_MODE_FREQUENCY_CUTOFF]      = compression;
        mPlaybackRate[MainActivity.PLAY_MODE_FREQUENCY_CUTOFF]         = playbackRate;

        int fftSizeScreen = Integer.parseInt(preferences.getString("fft", "1024"));
//        final int fftRatio = INVERSE_FFT_SIZE / fftSizeScreen;

        short playMode = mActivity.isAudioWhileListening() ? MainActivity.getHeadsetMode() : MainActivity.getPlayMode();
        // Get minimum buffer size for the highest rate possible in case the playback mode changes...
        int minBufferSize = AudioTrack.getMinBufferSize(
                mPlaybackRate[MainActivity.PLAY_MODE_FREQUENCY_CUTOFF],
//                (channelNo == 1) ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        AudioTrack audioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                mPlaybackRate[playMode],
 //               (channelNo == 1) ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize, AudioTrack.MODE_STREAM);

        int state = audioTrack.getState();
        if (state == AudioTrack.STATE_INITIALIZED) {

            if (!mActivity.isAudioWhileListening()) {

                mWindowSize = fftSizeScreen / MainActivity.WINDOW_FACTOR;
                audioTrack.setPositionNotificationPeriod(mWindowSize);
                audioTrack.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {

                    @Override
                    public void onMarkerReached(AudioTrack arg0) {
                    }

                    @Override
                    public void onPeriodicNotification(AudioTrack arg0) {

                        int playHead = mReadOffset / mWindowSize;
                        if (waveformView.getPlayhead() != playHead) {

                            if (mActivity.getPlayEnd() > 0 && playHead > mActivity.getPlayEnd()) playHead = mActivity.getPlayEnd();
                            waveformView.setPlayhead(playHead);
                            waveformView.postInvalidateOnAnimation();

                            int playhead = (int) ((float) waveformView.getPlayhead() * mActivity.getZoomX());
                            int scrollLeft = scrollView.getScrollX();
                            if (playhead < scrollLeft) {
                                // off to the left
                                scrollView.scrollTo(Math.max(playhead, 0), 0);
                            } else if ((playhead + SCROLL_SPACER) > (scrollLeft + scrollView.getWidth())) {
                                // off to the right
                                int maxLeftOffset = waveformView.getWidth() - scrollView.getWidth();
                                if ((playhead + SCROLL_SPACER - scrollView.getWidth()) > maxLeftOffset)
                                    scrollView.scrollTo(waveformView.getWidth() - scrollView.getWidth(), 0);
                                else
                                    scrollView.scrollTo(playhead + SCROLL_SPACER - scrollView.getWidth(), 0);
                            }
                        }
                    }
                });
            }

            audioTrack.play();

            float modAdj = Float.parseFloat(preferences.getString("hetadj", "1.0"));

            int bytesRead;
            int tuningFreq;
            if (mActivity.isPlaying()) {

                int playStart = mActivity.getPlayStart();
                int playEnd = mActivity.getPlayEnd();
                int start = waveformView.getPlayhead();
                if (start > playStart && ((playEnd == -1) || (start < playEnd)))
                    playStart = start;
                else
                    waveformView.setPlayhead(playStart);

                if (playEnd > playStart) maxData = Math.min(maxData, playEnd * mWindowSize);
                mReadOffset = playStart * mWindowSize;

                String recordPath = FileAccessManager.getActiveRecording().getAbsolutePath();
                int data2Read = (int) Math.min(maxData - mReadOffset, INVERSE_FFT_SIZE);

                float binFrequency = (float) recordingRate / (float) fftSizeScreen;
                int minHeterodyneFreq = preferences.getInt("hetfreq_min", 15) * 1000;
                int maxHeterodyneFreq = preferences.getInt("hetfreq_max", 200) * 1000;

                PowerView.openReadCache();

                while (mActivity.isPlaying() && (data2Read > 0)) {

                    if ((playMode == MainActivity.PLAY_MODE_HETERODYNE) && freqTickView.isAutoTune()) {
                        tuningFreq = (int) (binFrequency * ((MainActivity.sTunings[(int) (mReadOffset / fftSizeScreen)] + 0.5f)));
                        if ((tuningFreq >= minHeterodyneFreq) && (tuningFreq <= maxHeterodyneFreq)) {
                            freqTickView.setTuneFrequency(tuningFreq);
                            freqTickView.postInvalidateOnAnimation();
                        }
                        else
                            tuningFreq = freqTickView.getTuneFrequency();
                    }
                    else
                        tuningFreq = freqTickView.getTuneFrequency();

                    bytesRead = filterFrequencyFile(
                        playMode,
                        mTimeCompression[playMode],
                        (float) recordingRate / (float) tuningFreq,
                        modAdj,
                        INVERSE_FFT_SIZE,
                        mReadOffset,
                        channelNo,
                        whichChannel,
                        recordPath,
                        inverseBuffer);

                    if (bytesRead == 0) break;

                    audioTrack.write(inverseBuffer, 0, (int) ((float) bytesRead / mTimeCompression[playMode]));

                    if (mActivity.isPowerPopupVisible()) {
                        PowerView powerView = mActivity.getPowerView();
                        powerView.updateDataFromCacheFile(mReadOffset * 2, false);
                        powerView.postInvalidateOnAnimation();
                    }

                    mReadOffset += INVERSE_FFT_SIZE;       // number of shorts
                    data2Read = (int) Math.min(maxData - mReadOffset, INVERSE_FFT_SIZE);
                    if (data2Read <= 0) {

                        if (inverseBuffer.length >= minBufferSize) {
                            Arrays.fill(inverseBuffer, (byte) 0);
                            audioTrack.write(inverseBuffer, 0, minBufferSize);
                        }

                         if (mActivity.isLooping()) {

                             audioTrack.pause();
                             audioTrack.flush();

                             audioTrack.setPositionNotificationPeriod(mWindowSize);
                             audioTrack.play();

                             // If we started somewhere in the middle, but it wasn't a clip, then reset to beginning...
                            if (mActivity.getPlayEnd() < 0) {
                                playStart = 0;
                            }
                             mReadOffset = playStart * mWindowSize;
                            data2Read = (int) Math.min(maxData - mReadOffset, INVERSE_FFT_SIZE);
                             waveformView.resetPlayhead();
                            waveformView.setPlayhead(playStart);
                             waveformView.setSelRange(playStart, playEnd);
                        }
                        else {
                             if (mActivity.getPlayEnd() < 0) {
                                 audioTrack.pause();
                                 audioTrack.flush();
                             }

                             audioTrack.play();
                         }
                    }

                    if (playMode != MainActivity.getPlayMode()) {
                        playMode = MainActivity.getPlayMode();
                        audioTrack.setPlaybackRate(mPlaybackRate[playMode]);
                    }
                }
                PowerView.closeReadCache();

                // Reset head to beginning
                waveformView.resetPlayhead();
                if (playEnd != -1) {
                    mReadOffset = playStart * mWindowSize;
                    waveformView.setPlayhead(playStart);
                }
                else {
                    mReadOffset = 0;
                    waveformView.setPlayhead(0);
                }
                waveformView.postInvalidateOnAnimation();

                // Scroll to show head if we need to...
                int playhead = (int) ((float) waveformView.getPlayhead() * mActivity.getZoomX());
                int scrollLeft = scrollView.getScrollX();
                if (playhead < scrollLeft) {
                    // off to the left
                    scrollView.scrollTo(Math.max(playhead, 0), 0);
                } else if ((playhead + SCROLL_SPACER) > (scrollLeft + scrollView.getWidth())) {
                    // off to the right
                    int maxLeftOffset = waveformView.getWidth() - scrollView.getWidth();
                    if ((playhead + SCROLL_SPACER - scrollView.getWidth()) > maxLeftOffset)
                        scrollView.scrollTo(waveformView.getWidth() - scrollView.getWidth(), 0);
                    else
                        scrollView.scrollTo(playhead + SCROLL_SPACER - scrollView.getWidth(), 0);
                }

            } else if (mActivity.isAudioWhileListening()) {
                mReadOffset = 0;
                int shortsRead;
                short[] samplingBuffer = new short[INVERSE_FFT_SIZE];
                SyncListenHead();
                while (mActivity.isAudioWhileListening()) {
                    shortsRead = MainActivity.ReadFrameFromAudioBuffer(1, samplingBuffer, INVERSE_FFT_SIZE, 1);
                    if (shortsRead == INVERSE_FFT_SIZE) {
                        tuningFreq = freqTickView.getTuneFrequency();
                        bytesRead = filterFrequencyBuffer(
                                MainActivity.getHeadsetMode(),
                                mTimeCompression[playMode],
                                (float) recordingRate / (float) tuningFreq,
                                 modAdj,
                                INVERSE_FFT_SIZE,
                                samplingBuffer,
                                inverseBuffer);
                        audioTrack.write(inverseBuffer, 0, (int) ((float) bytesRead / mTimeCompression[playMode]));
                        mReadOffset += bytesRead / 2;
                    }
                    if (playMode != MainActivity.getHeadsetMode()) {
                        playMode = MainActivity.getHeadsetMode();
                        audioTrack.setPlaybackRate(mPlaybackRate[playMode]);
                    }
                }
            }
            if (inverseBuffer.length >= minBufferSize) {
                Arrays.fill(inverseBuffer, (byte) 0);
                audioTrack.write(inverseBuffer, 0, minBufferSize);
            }
            audioTrack.stop();
        }
        audioTrack.release();

        mActivity.clearAudioPlayThread();

        if (!mActivity.isAudioWhileListening()) {
            mActivity.setPlaying(false);
//            waveformView.postDelayed(new Runnable() {
//                public void run() {
//                    waveformView.resetPlayhead();
//                    waveformView.postInvalidateOnAnimation();
//                }
//            }, 100);
            mActivity.findViewById(R.id.play).post(new Runnable() {
                public void run() {
                    mActivity.doStop();
                }
            });
        }
    }

    public static int findBestPlaybackRate(ArrayList<Integer> sampleRates, int targetRate) {
        int playbackRate = sampleRates.get(0);
        int delta = Math.abs(playbackRate - targetRate);
        for (int ii = 1; ii < sampleRates.size(); ii++) {
            if (Math.abs(sampleRates.get(ii) - targetRate) < delta) {
                playbackRate = sampleRates.get(ii);
                delta = Math.abs(playbackRate - targetRate);
            }
        }
        return playbackRate;
    }

    public static int findBestCutoffPlaybackRate(ArrayList<Integer> sampleRates, int recordingRate) {
        int ii = sampleRates.size()-1;
        while ((ii >= 0) && (sampleRates.get(ii) > recordingRate)) {
            ii--;
        }
        return sampleRates.get(ii);
    }

    public static native int filterFrequencyFile(short mode, float factor, float modulator, float modAdj, int fftLen, int offset, short channels, short whichChannel, String path, byte[] out);
    private static native int filterFrequencyBuffer(short mode, float factor, float modulator, float modAdj, int len, short[] data, byte[] out);
    private static native void SyncListenHead();
}

