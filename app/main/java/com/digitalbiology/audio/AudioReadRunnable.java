package com.digitalbiology.audio;

//import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
//import android.net.ConnectivityManager;
//import android.net.NetworkInfo;
import androidx.core.content.ContextCompat;
//import android.telephony.SmsManager;
//import androidx.core.content.FileProvider;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.PopupWindow;

import com.digitalbiology.audio.metadata.MetaDataParser;
import com.digitalbiology.audio.views.FreqTickView;
import com.digitalbiology.audio.views.PowerView;
import com.digitalbiology.audio.views.InfoOverlayView;
import com.digitalbiology.audio.views.SpectrogramView;
import com.digitalbiology.audio.views.TimeTickView;
import com.digitalbiology.audio.views.WaveformView;

import java.io.File;
import java.util.Date;

// ---------------------------------------------
// audio thread, reads and analyzes audio data from an input buffer
// ---------------------------------------------
class AudioReadRunnable implements Runnable {

    private final MainActivity      mActivity;
    private PopupWindow             mRecordPopup;
    public static InfoOverlayView    sInfoOverlayView = null;

    public AudioReadRunnable(MainActivity activity) {
        mActivity = activity;
        mRecordPopup = null;
    }

    private final Runnable initViews = new Runnable() {
        @Override
        public void run() {

            final SpectrogramView spectrogramView = mActivity.getSpectrogramView();
            final WaveformView waveformView = mActivity.getWaveformView();
            final TimeTickView timeTickView = mActivity.getTimeTickView();
            final HorizontalScrollView  scrollView = mActivity.getScrollView();

            mActivity.getSeekScrollView().setVisibility(View.INVISIBLE);

            int width = scrollView.getWidth();
            ViewGroup.LayoutParams lp = timeTickView.getLayoutParams();
            lp.width = width;
            timeTickView.setLayoutParams(lp);

//	           	width = Math.max((int) ((float) mSpectrogramView.getFFTHeight() * mZoomFactor), width);
            lp = waveformView.getLayoutParams();
            lp.width = width;
            waveformView.setLayoutParams(lp);

            lp = spectrogramView.getLayoutParams();
            lp.width = width;
            spectrogramView.setLayoutParams(lp);

            lp = timeTickView.getLayoutParams();
            lp.width = width;
            timeTickView.setLayoutParams(lp);

            scrollView.setScrollX(0);

            LayoutInflater layoutInflater = (LayoutInflater) mActivity.getBaseContext().getSystemService(MainActivity.LAYOUT_INFLATER_SERVICE);
            View popupView = layoutInflater.inflate(R.layout.record, null);
            mRecordPopup = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

            DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
            int xpos = (int) (20f * metrics.density);
            int ypos = (int) (100f * metrics.density);

            // Ordering of showAtLocation here is important!!!!
            mRecordPopup.showAtLocation(scrollView, Gravity.RIGHT | Gravity.BOTTOM, xpos, ypos);

//				mRecordPopup.setBackgroundDrawable(getResources().getDrawable(R.drawable.translucent_background));
            mRecordPopup.setBackgroundDrawable(ContextCompat.getDrawable(mActivity, R.drawable.translucent_background));
            mRecordPopup.setOutsideTouchable(true);
            mRecordPopup.setFocusable(false);
            sInfoOverlayView = (InfoOverlayView) popupView.findViewById(R.id.record_time);
        }
    };

    @Override
    public void run() {

        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);    // Set the thread priority

        final SpectrogramView       spectrogramView = mActivity.getSpectrogramView();
        final WaveformView          waveformView = mActivity.getWaveformView();
        final FreqTickView          freqTickView = mActivity.getFreqTickView();
        final TimeTickView          timeTickView = mActivity.getTimeTickView();
        final HorizontalScrollView  scrollView = mActivity.getScrollView();
        final View                  playView = mActivity.getPlayView();

        SharedPreferences   preferences = mActivity.getPreferences();
        Microphone          microphone = MainActivity.getMicrophone();

        boolean triggerOnly = preferences.getBoolean("triggerOnly", false);

        int fftSize = Integer.parseInt(preferences.getString("fft", "1024"));

        float maxFreq = (float) microphone.getSampleRate() / 2;
        if (freqTickView.getMaxFreq() != maxFreq) {

            freqTickView.setMaxFreq(maxFreq);
            freqTickView.setOffset(0);
            freqTickView.setZoomLevel(1.0f);

            spectrogramView.setYOffset(0);
            spectrogramView.setScaleY(1.0f);
        }

        float  secsPerSample = (float) fftSize / (float) (MainActivity.WINDOW_FACTOR * microphone.getSampleRate());
        mActivity.setSecondsPerSample(secsPerSample);
        timeTickView.setSecondsPerSample(secsPerSample);

        // Need to calculate the zoom factor here!
        int windowWidth = scrollView.getWidth();
        float zoomX = secsPerSample * (float) windowWidth / mActivity.getZoomTime();
        mActivity.setZoomX(zoomX);
        int maxTimespan = (int) ((float) windowWidth / zoomX + 0.5f);
        mActivity.setRecordingLength(secsPerSample * (float) maxTimespan);

        int     recordTime = 0;
        int     currentTime = 0;
        int     prevTime = 0;
        float   timeCounter = 0.0f;

        mActivity.openWriteCacheFile(fftSize);

        spectrogramView.setFFTDimension(maxTimespan, fftSize,  microphone.getSampleRate() / 2, secsPerSample);
        waveformView.setNumSamples(maxTimespan);
//        waveformView.setListenhead(0);
        waveformView.setPlayhead(0);

        mActivity.runOnUiThread(initViews);

        timeTickView.postInvalidate();
        waveformView.postInvalidate();
        spectrogramView.postInvalidate();
        freqTickView.postInvalidate();

        short[] samplingBuffer = new short[fftSize];

        long[] params = new long[MainActivity.PARAM_COUNT];
        params[MainActivity.MAX_BUFFER_PARAM] = fftSize;
        params[MainActivity.WINDOW_FACTOR_PARAM] = MainActivity.WINDOW_FACTOR;
        float freqBin = (float) microphone.getSampleRate() / ((float) fftSize * 1000.0f);
        params[MainActivity.MIN_FREQ_TRIGGER_PARAM] = (int) ((float) preferences.getInt("trigfreq_min", 15) / freqBin);
        params[MainActivity.MAX_FREQ_TRIGGER_PARAM] = (int) ((float) preferences.getInt("trigfreq_max", 200) / freqBin);
        params[MainActivity.MIN_DB_TRIGGER_PARAM] = Short.parseShort(preferences.getString("decibels", "-5"));

        final int delayCount = MainActivity.WINDOW_FACTOR * (int) (Float.parseFloat(preferences.getString("post", "0.5")) * (float) microphone.getSampleRate() / (float) fftSize + 0.5f);
        int delayCounter = 0;

        int maxDataSamples = Integer.parseInt(preferences.getString("maxlength", "0")) * microphone.getSampleRate();
        if (maxDataSamples == 0) maxDataSamples = MainActivity.MAX_SOUND_SAMPLES;
        int maxLength = MainActivity.WINDOW_FACTOR * (int) ((float) maxDataSamples / (float) fftSize);

        int advanceCount = (int) (Float.parseFloat(preferences.getString("pre", "0.01")) * (float) microphone.getSampleRate() + 0.5f);

        int sampleCount = 0;

        float binFrequency = (float) microphone.getSampleRate() / (float) fftSize;
        int minTuningFrequency = preferences.getInt("hetfreq_min", 15) * 1000;
        int minHeterodyneBin = (int) ((float) minTuningFrequency / binFrequency);
        params[MainActivity.MIN_TUNE_BIN_PARAM] = minHeterodyneBin;
        params[MainActivity.MAX_TUNE_BIN_PARAM] = (int) ((float) (preferences.getInt("hetfreq_max", 200) * 1000) / binFrequency);
        params[MainActivity.TUNE_BIN_PARAM] = -1;
        if (freqTickView.getTuneFrequency() > maxFreq) {
            freqTickView.setTuneFrequency((int) maxFreq);
        }
        else if (freqTickView.getTuneFrequency() < minTuningFrequency) {
            freqTickView.setTuneFrequency(minTuningFrequency);
        }

        int capture_buffer_size = Integer.parseInt(preferences.getString("capture", "10")) * microphone.getSampleRate();

//        boolean send_sms = preferences.getBoolean("sms", false) && mActivity.hasSMSPermission();
//        boolean send_email = preferences.getBoolean("email", false);
//        if (send_email) {
//            // Make sure we are connected before we try and send emails...
//            ConnectivityManager cm = (ConnectivityManager) mActivity.getSystemService(Context.CONNECTIVITY_SERVICE);
//            NetworkInfo netInfo = cm.getActiveNetworkInfo();
//            send_email = (netInfo != null && netInfo.isConnectedOrConnecting());
//        }
//        NotificationToken.getNotifications().clear();

        MetaDataParser parser = mActivity.createMetaDataParser();

        int triggered;
        mActivity.setReading(true);
        boolean displaySample;

        boolean continuous = mActivity.getPreferences().getBoolean("cont_rec", false);

        while (mActivity.isReading()) {
            params[MainActivity.ACTUAL_BUFFER_PARAM] = MainActivity.ReadFrameFromAudioBuffer(0, samplingBuffer, fftSize, MainActivity.WINDOW_FACTOR);
            if (params[MainActivity.ACTUAL_BUFFER_PARAM] == fftSize) {
                params[MainActivity.ROW_PARAM] = sampleCount;
                params[MainActivity.IGNORE_PARAM] = ((delayCounter == 0) && (mActivity.isTriggerSet() && triggerOnly)) ? 1 : 0;
                params[MainActivity.STATE_PARAM] = mActivity.isRecording() ? MainActivity.RECORD_STATE : MainActivity.LISTEN_STATE;
                triggered = MainActivity.analyzeSpectrum(samplingBuffer, params);
                synchronized (mActivity.lock) {
                    int advance = 0;
                    if (mActivity.isTriggerSet()) {
                        if (triggered == 1) {
                            delayCounter = delayCount;        // reset
                            if (!mActivity.isRecording()) {
                                mActivity.setRecording(true);
                                advance = advanceCount;
                            }
                        } else {
                            if (delayCounter > 0) {
                                delayCounter--;
                            }
                            if (mActivity.isRecording() && (delayCounter == 0)) {
                                mActivity.setRecording(false);
                                int samplesRecorded = MainActivity.StopRecord();
                                File recordFile = FileAccessManager.getActiveRecording();
                                MainActivity.AppendMetadata(
                                        recordFile.getAbsolutePath(),
                                        parser.getNamespace(),
//                                        parser.create(
//                                            new Date(recordFile.lastModified()),
//                                            (float) samplesRecorded / (float) microphone.getSampleRate(),
//                                            microphone,
//                                            mActivity.getLocationData(),
//                                            mActivity.getSensorData(),
//                                            null)
//                                            (send_sms || send_email) ? new NotificationToken() : null)
                                        parser.create(
                                                new Date(recordFile.lastModified()),
                                                (float) samplesRecorded / (float) microphone.getSampleRate(),
                                                microphone,
                                                mActivity.getLocationData(),
                                                mActivity.getSensorData())
                                );
                                recordTime = 0;
                                mActivity.createWayPointFile();

                                Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                intent.setData(FileAccessManager.getFileUri(mActivity, recordFile));
//                                intent.setData(Uri.fromFile(recordFile));
                                mActivity.sendBroadcast(intent);

//                                if (send_sms) new Thread(new SendSMSRunnable()).start();
//                                if (send_email) new Thread(new SendEmailRunnable()).start();

                                FileAccessManager.setActiveRecording(null);
                                playView.postOnAnimation(new Runnable() {
                                    @Override
                                    public void run() {
                                        mActivity.setPlayToRecordIcon();
                                        sInfoOverlayView.makeDirty(-1.0f);
                                    }
                                });
                            }
                        }
                    }

                    if (mActivity.isRecording()) {
                        if (FileAccessManager.getActiveRecording() == null) {
                            if (mActivity.getListenCapture()) {
                                advance = capture_buffer_size;
                                mActivity.setListenCapture(false);
                            }
                            Date date = new Date();
                            if (advance > 0) {
                                int fill = GetAudioBufferFillSize();
                                if (fill < advance) advance = fill;
                                if ((maxDataSamples > 0) && (advance > maxDataSamples)) advance = maxDataSamples;
                                date.setTime(date.getTime() - (1000 * advance / microphone.getSampleRate()));
                            }
                            FileAccessManager.createActiveRecording(FileAccessManager.uniqueFilename(date));
                            MainActivity.StartRecord(FileAccessManager.getActiveRecording().getAbsolutePath(), microphone.getSampleRate(), advance);
                            recordTime = mActivity.isTriggerSet() ? (advanceCount * MainActivity.WINDOW_FACTOR / fftSize) : 0;
                            if (maxDataSamples > 0)
                                maxLength = MainActivity.WINDOW_FACTOR * (int) ((float) (maxDataSamples - advance) / (float) fftSize) + 1;  // Need to be greater than zero to cause auto termination
                            else
                                maxLength = 0;
                        } else {
                            recordTime++;
                            if (maxLength > 0) {
                                if (--maxLength == 0) {

                                    if (continuous) mActivity.setRecording(false);

                                    int samplesRecorded = MainActivity.StopRecord();

                                    File recordFile = FileAccessManager.getActiveRecording();
                                    MainActivity.AppendMetadata(
                                            recordFile.getAbsolutePath(),
                                            parser.getNamespace(),
                                            parser.create(new Date(recordFile.lastModified()),
                                                    (float) samplesRecorded / (float) microphone.getSampleRate(),
                                                    microphone,
                                                    mActivity.getLocationData(),
                                                    mActivity.getSensorData()));
//                                    parser.create(new Date(recordFile.lastModified()),
//                                                    (float) samplesRecorded / (float) microphone.getSampleRate(),
//                                                    microphone,
//                                                    mActivity.getLocationData(),
//                                                    mActivity.getSensorData(),
//                                                    null));
                                    recordTime = 0;
                                    mActivity.createWayPointFile();

                                    Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                    intent.setData(FileAccessManager.getFileUri(mActivity, recordFile));
//                                    intent.setData(Uri.fromFile(recordFile));
                                    mActivity.sendBroadcast(intent);

                                    FileAccessManager.setActiveRecording(null);
                                    if (continuous) {
                                        mActivity.setRecording(true);
                                        playView.postOnAnimation(new Runnable() {
                                            @Override
                                            public void run() {
                                                mActivity.setPlayToRecordIcon();
                                                sInfoOverlayView.makeDirty(-1.0f);
                                            }
                                        });
                                    }
                                }
                            }
                        }
                    }
                }

                if ((MainActivity.getHeadsetMode() == MainActivity.PLAY_MODE_HETERODYNE) && freqTickView.isAutoTune()) {
                    if (params[MainActivity.TUNE_BIN_PARAM] > minHeterodyneBin) {
                        freqTickView.setTuneFrequency((int) (binFrequency * (params[MainActivity.TUNE_BIN_PARAM] + 0.5f)));
                    }
                }

                displaySample = !mActivity.isTriggerSet() || !triggerOnly || (delayCounter > 0);
                if (prevTime != currentTime) {

                    if (currentTime < prevTime) {
                        sampleCount = 0;
                        timeCounter = 0.0f;
                        waveformView.reset();
                        MainActivity.resetCache();
                        spectrogramView.postInvalidateOnAnimation();
                        waveformView.postInvalidateOnAnimation();
                        spectrogramView.resetUpdateStep();
                        waveformView.resetUpdateStep();
                        waveformView.setPlayhead(0);
                        prevTime = 0;
                    } else if (displaySample) {
                        spectrogramView.makeDirty(currentTime);
                        waveformView.makeDirty(currentTime, (int) ((float) currentTime / mActivity.getZoomX()));
                        prevTime = currentTime;
                    }

                    if (displaySample) {
                        if (mActivity.isPowerPopupVisible()) {
                            PowerView powerView = mActivity.getPowerView();
                            powerView.updateDataFromCacheBuffer(false);
                            powerView.postInvalidateOnAnimation();
                        }
                    }

                    if (mActivity.isRecording() && (sInfoOverlayView != null)) {
                        sInfoOverlayView.makeDirty((float) recordTime * secsPerSample);
                    }
                }
                if (displaySample) {
                    timeCounter += mActivity.getZoomX();
                    currentTime = ((int) timeCounter) % windowWidth;
                    sampleCount = (sampleCount + 1) % maxTimespan;
                }
            }
        }
        MainActivity.closeWriteCache();

//        if (send_email && (NotificationToken.getNotifications().size() > 0)) {
//            // Broadcast any remaining triggers...
//            NotificationToken.setLastEmailTime(Long.MAX_VALUE);
//            new Thread(new SendEmailRunnable()).start();
//        }

        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mRecordPopup != null) {
                    mRecordPopup.dismiss();
                    mRecordPopup = null;
                    sInfoOverlayView = null;
                }
            }
        });
        UsbRecordRunnable.mAudioReadThread = null;
    }

//    private class SendSMSRunnable implements Runnable {
//
//        @Override
//        public void run() {
//            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
//            String phoneNumber = mActivity.getPreferences().getString("sms_recp", "").replaceAll("[^0-9]+", "");
//            if (!phoneNumber.isEmpty()) {
//                NotificationToken token = NotificationToken.getNotifications().getLast();
//                if (token != null) {
//                    try {
//                        SmsManager sms = SmsManager.getDefault();
//                        String msg = mActivity.getString(R.string.triggered) + " " + token.timestamp.toString();
//                        if (token.latitude != null) {
//                            msg += " @ " + token.latitude + " " + token.longitude;
//                        }
//                        sms.sendTextMessage(phoneNumber, null, msg, null, null);
//                    } catch (Exception e) {
////                    e.printStackTrace();
//                    }
//                }
//            }
//        }
//    }
//
//    private class SendEmailRunnable implements Runnable {
//
//        @Override
//        public void run() {
//            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
//            if (NotificationToken.getLastEmailTime() > System.currentTimeMillis()){
//                String emailAddr = mActivity.getPreferences().getString("email_recp", "");
//                if (!emailAddr.isEmpty()) {
//
//                    Mail m = new Mail("batrecorderapp@gmail.com", "Efuscus47N122W");
//                    String[] toArr = {emailAddr};
//                    m.setTo(toArr);
//                    m.setFrom("batrecorderapp@gmail.com");
//                    String[] replyArr = {mActivity.getPreferences().getString("account", "")};
//                    m.setReply(replyArr);
//                    m.setSubject(mActivity.getString(R.string.app_name));
//                    String body = "";
//                    for (NotificationToken token : NotificationToken.getNotifications()) {
//                        body += mActivity.getString(R.string.triggered) + " " + token.timestamp.toString();
//                        if (token.latitude != null) {
//                            body += " @ " + token.latitude + " " + token.longitude;
//                        }
//                        body += System.getProperty("line.separator");
//                    }
//                    m.setBody(body);
//
//                    try {
//                        m.send();
//                        NotificationToken.getNotifications().clear();
//                        NotificationToken.setLastEmailTime(System.currentTimeMillis() + NotificationToken.MIN_EMAIL_DELAY);
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
//                }
//            }
//        }
//    }

    private static native int GetAudioBufferFillSize();
}

