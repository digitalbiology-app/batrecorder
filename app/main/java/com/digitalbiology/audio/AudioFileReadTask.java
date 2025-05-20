package com.digitalbiology.audio;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.digitalbiology.audio.metadata.LocationData;
import com.digitalbiology.audio.metadata.MetaData;
import com.digitalbiology.audio.metadata.MetaDataParser;
import com.digitalbiology.audio.utils.TimeAxisFormat;
import com.digitalbiology.audio.views.FreqTickView;
import com.digitalbiology.audio.views.PowerView;
import com.digitalbiology.audio.views.SpectrogramView;
import com.digitalbiology.audio.views.TimeTickView;
import com.digitalbiology.audio.views.WaveformView;

import java.io.File;

// ---------------------------------------------
// Loads audio data from a file
// ---------------------------------------------
class AudioFileReadTask extends AsyncTask<File, Integer, Boolean> {

    private ProgressDialog  mProgressDialog;
    private File            mAudioFile;
    private final boolean         mShowProgess;
    private final MainActivity    mActivity;
    private float           mSecsPerSample;

    private volatile boolean mCanceled = false;

    public AudioFileReadTask(MainActivity activity, boolean showProgress) {
        mActivity = activity;
        mShowProgess = showProgress;
        MainActivity.sTunings = null;
     }

    @Override
    protected void onPostExecute(Boolean result) {
        super.onPostExecute(result);

        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }

        if (result) {

            final SpectrogramView spectrogramView = mActivity.getSpectrogramView();
            final WaveformView waveformView = mActivity.getWaveformView();
            final TimeTickView timeTickView = mActivity.getTimeTickView();
            final FreqTickView freqTickView = mActivity.getFreqTickView();
            final HorizontalScrollView  scrollView = mActivity.getScrollView();

            int width = Math.max((int) ((float) spectrogramView.getFFTHeight() * mActivity.getZoomX()), scrollView.getWidth());

            ViewGroup.LayoutParams lp = timeTickView.getLayoutParams();
            lp.width = width;
            timeTickView.setLayoutParams(lp);

            lp = spectrogramView.getLayoutParams();
            lp.width = width;
            spectrogramView.setLayoutParams(lp);

            lp = waveformView.getLayoutParams();
            lp.width = width;
            waveformView.setLayoutParams(lp);

            freqTickView.invalidate();
            timeTickView.invalidate();
            waveformView.invalidate();
            spectrogramView.invalidate();

             MetaData metadata = MetaDataParser.getMetadata(mAudioFile);
            if (metadata != null) {
                if (metadata.timestamp != null) {
                    String dateText = metadata.timestamp.toString();

                    ((TextView) mActivity.findViewById(R.id.sampleDate)).setText(dateText);
                    mActivity.findViewById(R.id.sampleDate).setVisibility(View.VISIBLE);
                } else {
                    ((TextView) mActivity.findViewById(R.id.sampleDate)).setText("");
                    mActivity.findViewById(R.id.sampleDate).setVisibility(View.GONE);
                }

                if (metadata.captureDevice != null) {
                    ((TextView) mActivity.findViewById(R.id.sampleDevice)).setText(metadata.captureDevice);
                    mActivity.findViewById(R.id.sampleDevice).setVisibility(View.VISIBLE);
                } else {
                    ((TextView) mActivity.findViewById(R.id.sampleDevice)).setText("");
                    mActivity.findViewById(R.id.sampleDevice).setVisibility(View.GONE);
                }

                if (metadata.species != null)
                    ((TextView) mActivity.findViewById(R.id.sampleSpecies)).setText(metadata.species);
                else
                    ((TextView) mActivity.findViewById(R.id.sampleSpecies)).setText("");

                if (metadata.latitude != LocationData.INVALID_GPS_COORD && metadata.longitude != LocationData.INVALID_GPS_COORD) {
                    ((TextView) mActivity.findViewById(R.id.sampleLat)).setText(MetaDataParser.latitude2String(metadata.latitude));
                    ((TextView) mActivity.findViewById(R.id.sampleLon)).setText(MetaDataParser.longitude2String(metadata.longitude));
                    mActivity.findViewById(R.id.latlon_layout).setVisibility(View.VISIBLE);
                } else {
                    ((TextView) mActivity.findViewById(R.id.sampleLat)).setText("");
                    ((TextView) mActivity.findViewById(R.id.sampleLon)).setText("");
                    mActivity.findViewById(R.id.latlon_layout).setVisibility(View.GONE);
                }
            }
            else {
                ((TextView) mActivity.findViewById(R.id.sampleDate)).setText("");
                ((TextView) mActivity.findViewById(R.id.sampleDevice)).setText("");
                ((TextView) mActivity.findViewById(R.id.sampleSpecies)).setText("");
                ((TextView) mActivity.findViewById(R.id.sampleLat)).setText("");
                ((TextView) mActivity.findViewById(R.id.sampleLon)).setText("");
                mActivity.findViewById(R.id.sampleDate).setVisibility(View.GONE);
                mActivity.findViewById(R.id.sampleDevice).setVisibility(View.GONE);
                mActivity.findViewById(R.id.latlon_layout).setVisibility(View.GONE);
            }

            long[] params = new long[4];
            MainActivity.readWAVHeader(mAudioFile.getAbsolutePath(), params);
            ((TextView) mActivity.findViewById(R.id.sampleFile)).setText(mAudioFile.getName());
            TimeAxisFormat form = new TimeAxisFormat();
            String text = form.format((float) params[3] / (float) (params[1] * params[0])) + " @ " + (params[1] / 1000) + " kHz";
            if (params[0] > 1) text += " [channel " + mActivity.getStereoChannel()+ "]";
            ((TextView) mActivity.findViewById(R.id.sampleRate)).setText(text);

            mActivity.getInfoOverlayView().setVisibility(View.VISIBLE);
            mActivity.getDetailOverlayView().setVisibility(View.INVISIBLE);
            mActivity.getPlaybackOverlayView().setVisibility(View.VISIBLE);

            mActivity.setActiveSampleRate((int) params[1]);

            SeekBar scroller = mActivity.getSeekScrollView();
            scroller.setProgress(0);
            scroller.setMax((int) spectrogramView.getFFTHeight());
            scroller.setVisibility(View.VISIBLE);

        } else if (!mCanceled) {
            // File could not be opened
            new AlertDialog.Builder(mActivity, R.style.CustomDialog)
                    .setMessage(R.string.open_file_fail)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .show();
        }
        else
            mActivity.handleRecordingDiscarded();
    }

    @Override
    protected void onPreExecute() {

        mActivity.getPlaybackOverlayView().setVisibility(View.INVISIBLE);

         if (mShowProgess) {
            mProgressDialog = new ProgressDialog(mActivity, R.style.CustomDialog);
            mProgressDialog.setMessage(mActivity.getString(R.string.load_file) + " " + FileAccessManager.getActiveRecording().getName() + "\u2026");
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setCancelable(false);
            mProgressDialog.setIndeterminate(!mShowProgess);
            mProgressDialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            if (mShowProgess) {
                mProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, mActivity.getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mCanceled = true;
                        dialog.dismiss();
                    }
                });
            }
            mProgressDialog.show();
        } else {
            mProgressDialog = ProgressDialog.show(mActivity, null, null);
            mProgressDialog.setContentView(R.layout.spinner);
        }
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        if (values[0] == -1) {
            mProgressDialog.setMessage(mActivity.getString(R.string.analyzing));
        }
        else
            mProgressDialog.setProgress((int)((float) values[0] * mSecsPerSample));
    }

    @Override
    protected Boolean doInBackground(File... parameters) {

        mAudioFile = FileAccessManager.getActiveRecording();
        if (mAudioFile == null) return false;
        long [] audioParams = new long[4];
        int headerSize = MainActivity.readWAVHeader(mAudioFile.getAbsolutePath(), audioParams);
        final short channelNo = (short) audioParams[0];
        if ((headerSize == 0) || ((channelNo < 1) || (channelNo > 2)) || (audioParams[2] != 16) || (audioParams[3] == 0)) return false;

        final short whichChannel = mActivity.getStereoChannel();

//	        Log.i(TAG, "NoChannels="+audioParams[0]+
//	        		" SampleRate="+audioParams[1]+
//	        		" BitsPerSample="+audioParams[2]+
//	        		" NumSamples="+audioParams[3]+
//	        		" Seconds="+(float) audioParams[3] / (float)(audioParams[1])
//	        	);

        SharedPreferences   preferences = mActivity.getPreferences();

        final long maxDataCount = Math.min(MainActivity.MAX_SOUND_SAMPLES, audioParams[3] / channelNo);
        final int fftSize = Integer.parseInt(preferences.getString("fft", "1024"));
        short[] samplingBuffer = new short[fftSize];

        mSecsPerSample = (float) fftSize / (float) (MainActivity.WINDOW_FACTOR * audioParams[1]);
        mActivity.setSecondsPerSample(mSecsPerSample);
        final long totalTimespan = (maxDataCount * MainActivity.WINDOW_FACTOR) / fftSize;
        final int maxTimespan = (totalTimespan > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) totalTimespan;
        mActivity.setRecordingLength((float) maxTimespan * mSecsPerSample);
        if (mShowProgess) mProgressDialog.setMax((int) ((float) maxTimespan * mSecsPerSample));

        long[] params = new long[MainActivity.PARAM_COUNT];
        params[MainActivity.MAX_BUFFER_PARAM] = fftSize;
        params[MainActivity.WINDOW_FACTOR_PARAM] = MainActivity.WINDOW_FACTOR;
        params[MainActivity.MIN_FREQ_TRIGGER_PARAM] = 0;
        params[MainActivity.MAX_FREQ_TRIGGER_PARAM] = fftSize;
        params[MainActivity.MIN_DB_TRIGGER_PARAM] = -1000;
        params[MainActivity.STATE_PARAM] = MainActivity.LISTEN_STATE;

        float binFrequency = (float) audioParams[1] / (float) fftSize;
        int minTuningFrequency = preferences.getInt("hetfreq_min", 15) * 1000;
        int minHeterodyneBin = (int) ((float) minTuningFrequency / binFrequency);
        params[MainActivity.MIN_TUNE_BIN_PARAM] = minHeterodyneBin;
        params[MainActivity.MAX_TUNE_BIN_PARAM] = (int) ((float) (preferences.getInt("hetfreq_max", 200) * 1000) / binFrequency);

        params[MainActivity.TUNE_BIN_PARAM] = -1;

        int currentTime = 0;
        int fileOffset = headerSize;
        int dataRead;
        long availSpace = fftSize;
        int windowSize = fftSize / MainActivity.WINDOW_FACTOR;

        SpectrogramView       spectrogramView = mActivity.getSpectrogramView();
        WaveformView          waveformView = mActivity.getWaveformView();
        TimeTickView          timeTickView = mActivity.getTimeTickView();
        FreqTickView          freqTickView = mActivity.getFreqTickView();
        final HorizontalScrollView  scrollView = mActivity.getScrollView();

        waveformView.setNumSamples(maxTimespan);
        spectrogramView.setFFTDimension(maxTimespan, fftSize, (int) audioParams[1] / 2, mSecsPerSample);

        MainActivity.sTunings = new int[maxTimespan];
        int tuning_idx = 0;

        mActivity.openWriteCacheFile(fftSize);

        params[MainActivity.ACTUAL_BUFFER_PARAM] = 0;
        while (!mCanceled && ((dataRead = MainActivity.readWAVData(mAudioFile.getAbsolutePath(), samplingBuffer, (int) params[MainActivity.ACTUAL_BUFFER_PARAM],
                (int) availSpace, fileOffset, channelNo, whichChannel)) > 0)) {
            params[MainActivity.ACTUAL_BUFFER_PARAM] += dataRead;
            if (params[MainActivity.ACTUAL_BUFFER_PARAM] < fftSize) {
                // EOF
                params[MainActivity.ROW_PARAM] = currentTime;
                MainActivity.analyzeSpectrum(samplingBuffer, params);
                MainActivity.sTunings[tuning_idx++] = (int) params[MainActivity.TUNE_BIN_PARAM];
                break;
            }
            if (params[MainActivity.ACTUAL_BUFFER_PARAM] == fftSize) {
                params[MainActivity.ROW_PARAM] = currentTime;
                MainActivity.analyzeSpectrum(samplingBuffer, params);
                MainActivity.sTunings[tuning_idx++] = (int) params[MainActivity.TUNE_BIN_PARAM];
                params[MainActivity.ACTUAL_BUFFER_PARAM] = 0;
                fileOffset += windowSize;
                if ((fileOffset + fftSize) > (maxDataCount + headerSize)) {
                    availSpace = (maxDataCount + headerSize) - fileOffset;
                    if (availSpace <= 0) break;
                }
                else
                    availSpace = fftSize;
                publishProgress(currentTime);
                currentTime++;
            } else {
                availSpace -= dataRead;
            }
        }
        MainActivity.closeWriteCache();

        if (mCanceled) return false;

//        publishProgress(-1);
//        spectrogramView.setFeatures(FeatureExtractor.extractFeatures(fftSize / 2));

        // We don't update drawing till after the data is loaded!
        timeTickView.setSecondsPerSample(mSecsPerSample);

        mActivity.setZoomX(mSecsPerSample * (float) scrollView.getWidth() / mActivity.getZoomTime());

        float maxFreq = (float) (audioParams[1] / 2);
        if (freqTickView.getMaxFreq() != maxFreq) {

            freqTickView.setMaxFreq(maxFreq);
            freqTickView.setOffset(0);
            freqTickView.setZoomLevel(1.0f);
            freqTickView.postInvalidate();

            spectrogramView.setYOffset(0);
            spectrogramView.setScaleY(1.0f);
        }

        if (mActivity.isPowerPopupVisible()) {
            PowerView powerView = mActivity.getPowerView();
            PowerView.openReadCache();
            powerView.updateDataFromCacheFile(0, true);
            PowerView.closeReadCache();
            powerView.postInvalidate();
        }

        int min_bin = (int) params[MainActivity.MIN_TUNE_BIN_PARAM] + (int) ((float) params[MainActivity.MIN_TUNE_BIN_PARAM] * 0.1f);
        for (int ii = 0; ii < MainActivity.sTunings.length; ii++) {
            if (MainActivity.sTunings[ii] > min_bin) {
                int tune = MainActivity.sTunings[ii];
                for (int jj = ii-1; jj >= 0; jj--) MainActivity.sTunings[jj] = tune;
                break;
            }
        }
         if (freqTickView.isAutoTune()) {
            int minHeterodyneFreq = preferences.getInt("hetfreq_min", 15) * 1000;
            int maxHeterodyneFreq = preferences.getInt("hetfreq_max", 200) * 1000;
            int tuningFrequency = (int) (binFrequency * (MainActivity.sTunings[0] + 0.5f));
            if (tuningFrequency < minHeterodyneFreq)
                tuningFrequency = minHeterodyneFreq;
            else if (tuningFrequency > maxHeterodyneFreq)
                tuningFrequency = maxHeterodyneFreq;
            else
                freqTickView.setTuneFrequency(tuningFrequency);
        }

        return true;
    }
}


