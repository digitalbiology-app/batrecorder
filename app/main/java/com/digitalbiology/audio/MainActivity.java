package com.digitalbiology.audio;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings.Secure;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.multidex.BuildConfig;

import android.text.Editable;
import android.text.Html;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.digitalbiology.SpeciesDataModel;
import com.digitalbiology.audio.FileChooser.FileSelectedListener;
import com.digitalbiology.audio.metadata.GUANOMetaDataParser;
import com.digitalbiology.audio.metadata.LocationData;
import com.digitalbiology.audio.metadata.MetaData;
import com.digitalbiology.audio.metadata.MetaDataParser;
import com.digitalbiology.audio.metadata.SensorData;
import com.digitalbiology.audio.metadata.WAMDMetaDataParser;
import com.digitalbiology.audio.metadata.XMPMetaDataParser;
import com.digitalbiology.audio.utils.TimeAxisFormat;
import com.digitalbiology.audio.views.PaletteView;
import com.digitalbiology.audio.views.SpeciesListAdapter;
import com.digitalbiology.usb.UsbConfigurationParser;
import com.digitalbiology.audio.views.FreqTickView;
import com.digitalbiology.audio.views.PowerFreqTickView;
import com.digitalbiology.audio.views.PowerView;
import com.digitalbiology.audio.views.SpectrogramView;
import com.digitalbiology.audio.views.TimeTickView;
import com.digitalbiology.audio.views.TouchHorizontalScrollView;
import com.digitalbiology.audio.views.WaveformView;
import com.digitalbiology.usb.UsbLogger;
import com.google.android.gms.maps.MapsInitializer;

//import com.google.android.licensing.LicenseChecker;
//import com.google.android.licensing.LicenseCheckerCallback;
//import com.google.android.licensing.Policy;

//import com.squareup.sqip.CheckoutActivity;
//import sqip.CardEntry;
//import sqip.CheckoutActivity;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
//import java.util.Locale;

public class MainActivity extends Activity implements SensorEventListener {

    public static final boolean DEBUG_MODE = false;

    public static final short VAR_UNIVERSAL = 0;
    public static final short VAR_PETTERSSON = 1;
    public static final short VAR_DODOTRONIC = 2;

    private static final short sBuildVariation = VAR_UNIVERSAL;

    public static final String TAG = "BatRecorder";

    // Licensing =================================================================================

    private static final String sCacheFileName = ".bat.tmp";        // make it invisible

    private static final int BATRECORDER_REQUIRED_PERMISSIONS = 100;

    public static final int MAX_SOUND_SAMPLES = 1036800000;      // 45 minutes @ 384 KHz - enough to cover 2 byte ints

    public static final int OPEN_REQUEST_CODE = 41;

    private static String version_n;

//    private LicenseChecker mChecker;
//    private LicenseCheckerCallback mLicenseCheckerCallback;
//    private int mLicenseCode = Policy.NOT_LICENSED;
//    private boolean mDidCheckLicense = false;

    private volatile int mActiveSampleRate = 0;
    private short mStereoChannel = 1;

    private boolean mRecordEnabled = false;

    static private Microphone sMicrophone = null;

    private final ArrayList<Integer> mValidSampleRates = new ArrayList<>();

    public volatile Thread mUSBListenThread;
    private volatile Thread mAudioPlayThread;

    private volatile boolean listening = false;
    private volatile boolean recording = false;
    private volatile boolean reading = false;
    private volatile boolean playing = false;
    private volatile boolean trigger_set = false;
    private volatile boolean capture_listen = false;
    private volatile boolean loop = false;
    private volatile boolean mHeadphones = false;
    private volatile boolean mAudioWhileListening = false;

    private static boolean sNightMode = false;

    public final Object lock = new Object();

//	public static float MAX_ZOOM = 6.0f;
//	public static float MIN_ZOOM = 0.1f;

    private static final float MIN_ZOOM_TIME = 0.1f;
    private static final float MAX_ZOOM_TIME = 30.0f;

    private static final float MIN_ZOOM_Y = 1.0f;
    private static final float MAX_ZOOM_Y = 8.0f;

    private float mZoomTime;
    private float mZoomFactorX;
    private float mZoomFactorY;

    private float mRecordingLength = 0.0f;
    private float mSecsPerSample = 0.0f;

    private static final short DATA_MODE_RECORD = 0;
    public static final short DATA_MODE_PLAY = 1;

    public static final short LISTEN_STATE = 1;
    public static final short RECORD_STATE = 2;

    private static short sDataMode = DATA_MODE_RECORD;

    public static final short PLAY_MODE_FREQUENCY_DIVISION = 0;
    public static final short PLAY_MODE_HETERODYNE         = 1;
    public static final short PLAY_MODE_FREQUENCY_CUTOFF   = 2;
    public static final short PLAY_MODE_TIME_EXPANSION     = 3;

    public static final short PLAY_MODE_COUNT      = 4;
    private static final short HEADSET_MODE_COUNT   = 3;

    private static short sPlayMode;
    private static short sHeadsetMode;
    public static int[]  sTunings;

    public static final short MAX_BUFFER_PARAM         = 0;
    public static final short ACTUAL_BUFFER_PARAM      = 1;
    public static final short WINDOW_FACTOR_PARAM      = 2;
    public static final short ROW_PARAM                = 3;
    public static final short STATE_PARAM              = 4;
    public static final short MIN_FREQ_TRIGGER_PARAM   = 5;
    public static final short MAX_FREQ_TRIGGER_PARAM   = 6;
    public static final short MIN_DB_TRIGGER_PARAM     = 7;
    public static final short MIN_TUNE_BIN_PARAM       = 8;
    public static final short MAX_TUNE_BIN_PARAM       = 9;
    public static final short TUNE_BIN_PARAM           = 10;
    public static final short IGNORE_PARAM             = 11;
    public static final short PARAM_COUNT              = 12;

    private View mSplashView;
    private TextView mInitStatusView;
    private WaveformView mWaveformView;
    private SpectrogramView mSpectrogramView;
    private FreqTickView mFreqTickView;
    private TimeTickView mTimeTickView;
    private PowerView mPowerOverlay;
    private PowerFreqTickView mPowerTickOverlay;
    private HorizontalScrollView mScrollView;
    private ImageView mPlayView;
    private ImageView mMicView;
    private ImageView mTriggerView;
    private ImageView mHeadsetView;
    private ImageView mPlaybackRateView;
    private ImageView mGainView;
    private TextView mGainText;
    private ImageView mPowerView;
//    private ImageView mArchiveView;
//    private ImageView mSettingsView;
    private View mPowerPopup;
    private SeekBar mScrollSeekBar;
    private TextView mScrollLabel;
    private TextView mFileIndicator;

    private View mPlaybackOverlay;
    private View mMeasureLayout;
    private View mInfoLayout;
    private View mDetailsLayout;
    private View mNavigateLayout;

    private ImageView mLoopView;

    private Drawable mRecordImage;
    private Drawable mRecordStopImage;
    private Drawable mPlayImage;
    private Drawable mStopImage;
    private Drawable mMicImage;
    private Drawable mMicOffImage;
    private Drawable mHeadsetFDImage;
    private Drawable mHeadsetTuneImage;
    private Drawable mHeadsetCutoffImage;
    private Drawable mFrequencyImage;
    private Drawable mHeterodyneImage;
    private Drawable mTimeImage;
    private Drawable mCutoffImage;
    private Drawable mLoopImage;
    private Drawable mLoopOffImage;

    private int mPlayStart;
    private int mPlayEnd;
    private int mPlayAnchor;

    private int mMinTuningFreq;
    private int mMaxTuningFreq;

    private ArrayList<File> mRecordingList;

    private static final String ACTION_USB_PERMISSION = "com.digitalbiology.audio.MainActivity";

    private int mUSBInitialized = 0;
    private BroadcastReceiver mUsbReceiver = null;
    private AlertDialog mDisconnectAlert = null;

    private final static DecimalFormat sGainFormatter = new DecimalFormat("#0.00x");
    // Geotagging =================================================================================
    private LocationManager mLocManager = null;
    private final Object locLock = new Object();
    private Location mLocation = null;
    private File mWayPointFile = null;

    // Sensors =================================================================================
    private SensorManager mSensorManager = null;
    private Sensor mLightSensor = null;
    private Sensor mTemperatureSensor = null;
    private Sensor mPressureSensor = null;
    private Sensor mRelHumiditySensor = null;
    private Sensor mShakeSensor = null;

    private SensorData  mSensorData;

    private float mLastX = -1.0f;
    private float mLastY = -1.0f;
    private float mLastZ = -1.0f;
    private int mShakeCount = 0;
    private long mLastShake;
    private long mLastForce;
    private long mLastTime;

    private static final int FORCE_THRESHOLD = 600;
    private static final int TIME_THRESHOLD = 100;
    private static final long SHAKE_TIMEOUT = 500;
    private static final long SHAKE_DURATION = 1000;
    private static final int SHAKE_COUNT = 2;

    public static final int WINDOW_FACTOR = 4;

    private SharedPreferences mPreferences;

    public static short getDataMode() {
        return sDataMode;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {

        if (BuildConfig.DEBUG) StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .penaltyDeath()
                .build());

        super.onCreate(savedInstanceState);

        PackageInfo pInfo = null;
        try {
            pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }

        PreferenceManager.setDefaultValues(this, R.xml.prefs, false);
        mPreferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

        mMinTuningFreq = mPreferences.getInt("hetfreq_min", 15) * 1000;
        mMaxTuningFreq = mPreferences.getInt("hetfreq_max", 200) * 1000;

        setContentView(R.layout.activity_main);
        if (sBuildVariation == VAR_PETTERSSON) {
            findViewById(R.id.pettersson_logo_view).setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.status)).setText(getString(R.string.mic_init_pettersson));
        }
        else if (sBuildVariation == VAR_DODOTRONIC) {
            findViewById(R.id.dodotronic_logo_view).setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.status)).setText(getString(R.string.mic_init_dodotronic));
        }

        if (pInfo != null) {
            version_n = pInfo.versionName;
            ((TextView) findViewById(R.id.versionView)).setText(getString(R.string.version)+" "+version_n);
        }
        else
            version_n = "";

        if (mPreferences.getBoolean("debuglog", false)) {
            UsbLogger.createLog(this);
        }

        sNightMode = mPreferences.getBoolean("nightMode", false);
//        GISData.extractFiles(this);
//        GISData.readGIS();

        MapsInitializer.initialize(this);

        // We use drawable to reduce latency that occurs with setImageResource
        mRecordImage = ContextCompat.getDrawable(getApplicationContext(), R.drawable.record);
        mRecordStopImage = ContextCompat.getDrawable(getApplicationContext(), R.drawable.record_stop);
        mPlayImage = ContextCompat.getDrawable(getApplicationContext(), R.drawable.play);
        mStopImage = ContextCompat.getDrawable(getApplicationContext(), R.drawable.stop);
        mMicImage = ContextCompat.getDrawable(getApplicationContext(), R.drawable.mic);
        mMicOffImage = ContextCompat.getDrawable(getApplicationContext(), R.drawable.mic_off);
        mHeadsetFDImage = ContextCompat.getDrawable(getApplicationContext(), R.drawable.headset_div);
        mHeadsetTuneImage = ContextCompat.getDrawable(getApplicationContext(), R.drawable.headset_tune);
        mHeadsetCutoffImage = ContextCompat.getDrawable(getApplicationContext(), R.drawable.headset_cutoff);
        mFrequencyImage = ContextCompat.getDrawable(getApplicationContext(), R.drawable.frequency);
        mHeterodyneImage = ContextCompat.getDrawable(getApplicationContext(), R.drawable.tuning);
        mTimeImage = ContextCompat.getDrawable(getApplicationContext(), R.drawable.time);
        mCutoffImage = ContextCompat.getDrawable(getApplicationContext(), R.drawable.cutoff);
//        mTriggerImage = ContextCompat.getDrawable(getApplicationContext(), R.drawable.trigger);
        mLoopImage = ContextCompat.getDrawable(getApplicationContext(), R.drawable.loop);
        mLoopOffImage = ContextCompat.getDrawable(getApplicationContext(), R.drawable.loop_off);

        sPlayMode = (short) mPreferences.getInt("playMode", PLAY_MODE_FREQUENCY_DIVISION);
        sHeadsetMode = (short) mPreferences.getInt("headMode", PLAY_MODE_FREQUENCY_DIVISION);

        mSplashView = findViewById(R.id.initialization_layout);
        mSplashView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });
         mInitStatusView = (TextView) findViewById(R.id.status);

        mWaveformView = (WaveformView) findViewById(R.id.waveform);

        mSpectrogramView = (SpectrogramView) findViewById(R.id.spectrogram);
//        mSpectrogramView.setBackgroundColor(Palette.getColorsARGB()[0]);
//        mSpectrogramView.setBackgroundDrawable(null);

        mFreqTickView = (FreqTickView) findViewById(R.id.freq_tick);
        mFreqTickView.addOnLayoutChangeListener((v, left, top, right, bottom, leftWas, topWas, rightWas, bottomWas) -> {
            ((FreqTickView) v).rebuildBitmap();
        });

        mTimeTickView = (TimeTickView) findViewById(R.id.time_tick);

        createPowerPopup();

        boolean logscale = mPreferences.getBoolean("logscale", false);
        mFreqTickView.setLogScale(logscale);
        mSpectrogramView.setLogScale(logscale);
        mSpectrogramView.showGridLines(mPreferences.getBoolean("gridlines", false));

        mScrollView = (HorizontalScrollView) findViewById(R.id.hsv);

        mScrollLabel = (TextView) findViewById(R.id.scrollLabel);
        mScrollSeekBar = (SeekBar) findViewById(R.id.scrollSeek);
        mScrollSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            private final TimeAxisFormat formatter = new TimeAxisFormat();

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mScrollLabel.setVisibility(View.INVISIBLE);
                mScrollView.scrollTo((int) ((float) seekBar.getProgress() * MainActivity.this.getZoomX()), 0);
                int progress = (int)((float) mScrollView.getScrollX() / mZoomFactorX);
                if (progress != mScrollSeekBar.getProgress()) mScrollSeekBar.setProgress(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mScrollLabel.setVisibility(View.VISIBLE);
                mScrollLabel.setText(formatter.formatFull((float) seekBar.getProgress() * mSecsPerSample));
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mScrollLabel.setText(formatter.formatFull((float) seekBar.getProgress() * mSecsPerSample));
            }
        });

        mScrollView.getViewTreeObserver().addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() {
            @Override
            public void onScrollChanged() {
                mScrollSeekBar.setProgress((int)((float)(mScrollView.getScrollX() / mZoomFactorX)));
            }
        });

        mPlaybackOverlay = findViewById(R.id.playback_overlay);

//        mMeasureLayout = findViewById(R.id.measure);
//        mMeasureLayout.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
////                mSpectrogramView.setShowFeatures(!mSpectrogramView.getShowFeatures());
//                int fftSize = Integer.parseInt(mPreferences.getString("fft", "1024"));
//                mSpectrogramView.setFeatures(FeatureExtractor.extractFeatures(fftSize / 2));
//                mSpectrogramView.invalidate();
//            }
//        });

        mInfoLayout = findViewById(R.id.info);
        mInfoLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mInfoLayout.setVisibility(View.INVISIBLE);
//                if (getNightMode()) {
//                    ((TextView) findViewById(R.id.sampleFile)).setTextColor(Color.RED);
//                    ((TextView) findViewById(R.id.sampleDevice)).setTextColor(Color.RED);
//                    ((TextView) findViewById(R.id.sampleRate)).setTextColor(Color.RED);
//                    ((TextView) findViewById(R.id.sampleDate)).setTextColor(Color.RED);
//                    ((TextView) findViewById(R.id.sampleSpecies)).setTextColor(Color.RED);
//                    ((TextView) findViewById(R.id.sampleLat)).setTextColor(Color.RED);
//                    ((TextView) findViewById(R.id.sampleLon)).setTextColor(Color.RED);
//                    ((ImageView) findViewById(R.id.bat)).getDrawable().setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
//                }
                mDetailsLayout.setVisibility(View.VISIBLE);
            }
        });

        mDetailsLayout = findViewById(R.id.details);
        mDetailsLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDetailsLayout.setVisibility(View.INVISIBLE);
                mInfoLayout.setVisibility(View.VISIBLE);
            }
        });

        findViewById(R.id.species_layout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                final ChiropteraMap map = new ChiropteraMap(MainActivity.this);
                final int region;

//                int namespace = MetaDataParser.getMetadataNamespace(mActiveRecording.getAbsolutePath());

                MetaData metadata = MetaDataParser.getMetadata(FileAccessManager.getActiveRecording());
                if ((metadata != null) && ((metadata.latitude != LocationData.INVALID_GPS_COORD) && (metadata.longitude != LocationData.INVALID_GPS_COORD))) {
                    region = map.calcRegion((float) metadata.latitude, (float) metadata.longitude);
                }
                else {
                    // See if there is some current location coordinates...
                    if (mLocation != null) {
                        region = map.calcRegion((float) mLocation.getLatitude(), (float) mLocation.getLongitude());
                    }
                    else
                        region = ChiropteraMap.GLOBAL_REGION;
                }

                int first_selection = -1;
                ArrayList<SpeciesDataModel> species = map.loadSpeciesList(MainActivity.this, region);
                if (metadata != null && metadata.species != null) {
                    String selected_species[] = metadata.species.split(",");
                    for (int jj = 0; jj < selected_species.length; jj++) {
                        String selected = selected_species[jj].toLowerCase().trim();
                        for (int ii = 0; ii < species.size(); ii++) {
                            if (species.get(ii).name.toLowerCase().equals(selected)) {
                                species.get(ii).selected = true;
                                if (first_selection < 0) first_selection = ii;
                                break;
                            }
                        }
                    }
                }

                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this, R.style.CustomDialog);
                builder.setCancelable(true);

                LayoutInflater inflater = getLayoutInflater();

                View convertView = inflater.inflate(R.layout.species_dialog, null);
                builder.setView(convertView);

                final Dialog dialog = builder.create();
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                dialog.setCanceledOnTouchOutside(true);
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));

                final SpeciesListAdapter arrayAdapter = new SpeciesListAdapter(
                        MainActivity.this,
                        species);
//                final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(
//                        MainActivity.this,
//                        MainActivity.getNightMode() ? R.layout.species_night_list : R.layout.species_list,
//                        species);
                final ListView lv = (ListView) convertView.findViewById(R.id.speciesList);
                lv.setAdapter(arrayAdapter);
//                lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
//                    @Override
//                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
// //                       String speciesName = arrayAdapter.getItem(position);
//                        ((TextView) dialog.findViewById(R.id.selectedSpecies)).setText(arrayAdapter.getItem(position).name);
//                    }
//                });

                final EditText editField = (EditText) convertView.findViewById(R.id.selectedSpecies);
                editField.setSelectAllOnFocus(true);

                ((Button) convertView.findViewById(R.id.clear_species)).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ((TextView) dialog.findViewById(R.id.selectedSpecies)).setText("");
                    }
                });

                if (MainActivity.getNightMode()) ((TextView) convertView.findViewById(R.id.titlebar)).setTextColor(Color.RED);

                ImageView mapView = (ImageView) convertView.findViewById(R.id.place);
                if (region != ChiropteraMap.GLOBAL_REGION) {
                    mapView.setBackground(null);
                    mapView.setImageResource(R.drawable.place);
                }

                mapView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (region > 0) {
                            ArrayList<String> selected_species = new ArrayList<String>();
                            for (int ii = 0; ii < arrayAdapter.getCount(); ii++) {
                                SpeciesDataModel item = arrayAdapter.getItem(ii);
                                if (item.selected) {
                                    selected_species.add(item.name);
                                }
                            }
                            arrayAdapter.clear();
                            ArrayList<SpeciesDataModel> sdata = map.loadSpeciesList(MainActivity.this, (region != map.getRegion()) ? region : ChiropteraMap.GLOBAL_REGION);
                            arrayAdapter.addAll(sdata);
                            int first_selection = -1;
                            for (int jj = 0; jj < sdata.size(); jj++) {
                                for (int ii = 0; ii < selected_species.size(); ii++) {
                                    if (selected_species.get(ii).equals(sdata.get(jj).name)) {
                                        sdata.get(jj).selected = true;
                                        if (first_selection < 0) first_selection = ii;
                                        break;
                                    }
                                }
                            }

                            v.setBackground(null);
                            if (map.getRegion() != ChiropteraMap.GLOBAL_REGION)
                                ((ImageView) v).setImageResource(R.drawable.place);
                            else
                                ((ImageView) v).setImageResource(R.drawable.world);

                            if (first_selection >= 0) {
                                lv.setSelection(first_selection);
                            }
                        }
                    }
                });

                convertView.findViewById(R.id.speciesOk).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        File activeRecording = FileAccessManager.getActiveRecording();

                        MetaData metadata = MetaDataParser.getMetadata(activeRecording);
                        if (metadata == null) {
                            metadata = new MetaData();
                            metadata.namespace = createMetaDataParser().getNamespace();
                        }

                        metadata.species = "";
                        boolean first = true;
                        for (int ii = 0; ii < arrayAdapter.getCount(); ii++) {
                            SpeciesDataModel item = arrayAdapter.getItem(ii);
                            if (item.selected) {
                                if (!first) {
                                    metadata.species += ", ";
                                }
                                metadata.species += item.name;
                                first = false;
                            }
                        }
                        if (metadata.namespace == MetaData.XMP_NAMESPACE)
                            new XMPMetaDataParser().update(activeRecording, metadata);
                        else if (metadata.namespace == MetaData.WAMD_NAMESPACE)
                            new WAMDMetaDataParser().update(activeRecording, metadata);
                        else
                            new GUANOMetaDataParser().update(activeRecording, metadata);
                        ((TextView) findViewById(R.id.sampleSpecies)).setText(metadata.species);

                        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        intent.setData(FileAccessManager.getFileUri(MainActivity.this, activeRecording));
//                        intent.setData(Uri.fromFile(mActiveRecording));
                        sendBroadcast(intent);

                        dialog.dismiss();
                    }
                });

                // Initialize
//                String speciesName = ((TextView) findViewById(R.id.sampleSpecies)).getText().toString();
//                editField.setText(speciesName);
                editField.setText("");
//                int position = arrayAdapter.getPosition(speciesName);
//                int position = arrayAdapter.getPositionForSpecies(speciesName);
//                if (position >= 0) {
//                    arrayAdapter.getItem(position).selected = true;
//                    lv.setSelection(position);
//                }
                if (first_selection >= 0) {
                   lv.setSelection(first_selection);
                }
                editField.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                    }
                    @Override
                    public void afterTextChanged(Editable s) {
                        String species = editField.getText().toString().toLowerCase();
                        int count = arrayAdapter.getCount();
                        for (int ii = 0; ii < count; ii++) {
//                            if (arrayAdapter.getItem(ii).toLowerCase().startsWith(species)) {
                            if (arrayAdapter.getItem(ii).name.toLowerCase().startsWith(species)) {
                                lv.setSelection(ii);
                                break;
                            }
                        }
                    }
                });

                dialog.show();
            }
        });

        mNavigateLayout = findViewById(R.id.navigate_overlay);
        mFileIndicator = findViewById(R.id.which_file);
        findViewById(R.id.nav_prev).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    doStop();
                    int next;
                    for (int ii = 0; ii < mRecordingList.size(); ii++) {
                        if (mRecordingList.get(ii).equals(FileAccessManager.getActiveRecording())) {
                            if (ii == 0)
                                next = mRecordingList.size()-1;
                            else
                                next = ii-1;
                            loadRecording(mRecordingList.get(next));
                            mFileIndicator.setText((next+1)+" / "+mRecordingList.size());
                            break;
                        }
                    }
                }
            });
        findViewById(R.id.nav_next).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    doStop();
                    int next;
                    for (int ii = 0; ii < mRecordingList.size(); ii++) {
                        if (mRecordingList.get(ii).equals(FileAccessManager.getActiveRecording())) {
                            if (ii == mRecordingList.size()-1)
                                next = 0;
                            else
                                next = ii+1;
                            loadRecording(mRecordingList.get(next));
                            mFileIndicator.setText((next+1)+" / "+mRecordingList.size());
                            break;
                        }
                    }
                }
            });
        findViewById(R.id.nav_delete).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    doStop();
                    new AlertDialog.Builder(MainActivity.this, R.style.CustomDialog)
                            .setTitle(MainActivity.this.getString(R.string.delete))
                            .setMessage(MainActivity.this.getString(R.string.delete_file) + " " + FileAccessManager.getActiveRecording().getName() + "?")
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    File activeRecording = FileAccessManager.getActiveRecording();
                                    boolean success = activeRecording.delete();
                                    File deletedRecording = activeRecording;
                                    if (success) {

                                        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                        intent.setData(FileAccessManager.getFileUri(MainActivity.this, deletedRecording));
//                                        intent.setData(Uri.fromFile(deletedRecording));
                                        MainActivity.this.sendBroadcast(intent);

                                        dialog.dismiss();

                                        if (mRecordingList.size() > 1) {
                                            for (int ii = 0; ii < mRecordingList.size(); ii++) {
                                                if (mRecordingList.get(ii).equals(deletedRecording)) {
                                                    if (ii == mRecordingList.size() - 1)
                                                        loadRecording(mRecordingList.get(0));
                                                    else
                                                        loadRecording(mRecordingList.get(ii + 1));
                                                    mRecordingList.remove(deletedRecording);
                                                    break;
                                                }
                                            }
                                        }
                                        else
                                            MainActivity.this.handleRecordingDiscarded();
                                    }
                                }
                            })
                            .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            })
                            .show();
               }
            });

        mLoopView = (ImageView) findViewById(R.id.loop);
        mLoopView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loop = !loop;
                mLoopView.setBackground(null);
                if (loop)
// 					mLoopView.setImageResource(R.drawable.loop);
                    mLoopView.setImageDrawable(mLoopImage);
                else
// 					mLoopView.setImageResource(R.drawable.loop_off);
                    mLoopView.setImageDrawable(mLoopOffImage);
            }
        });

        ImageView settingsView = (ImageView) findViewById(R.id.settings);
        settingsView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listening)
                    stopListening();
                else
                    doStop();
                mPreferences.edit().putString("samplerate", Integer.toString(sMicrophone.getSampleRate())).apply();
                Intent in = new Intent(MainActivity.this, SettingsActivity.class);
//					in.putIntegerArrayListExtra("sampleRates", mValidSampleRates);
                startActivity(in);
            }
        });

        mMicView = (ImageView) findViewById(R.id.mic);
        mMicView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//				if (!listening && (IsUSBInitialized() || mDebugUseMic))
                if (!listening)
                    startListening();
                else
                    stopListening();
            }
        });

        mPlayView = (ImageView) findViewById(R.id.play);
         mPlayView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (sDataMode == DATA_MODE_RECORD) {
                    if (recording)
                        doStop();
                    else
                        doRecord(false);
                } else {
                    if (playing)
                        doStop();
                    else
                        doPlay();
                }
            }
        });
        mPlayView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (sDataMode == DATA_MODE_RECORD) {
                    if (recording)
                        doStop();
                    else {
                        doRecord(true);
                        Toast.makeText(MainActivity.this, R.string.prebuffer, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    if (playing)
                        doStop();
                    else
                        doPlay();
                }
                return true;
            }
        });

        mTriggerView = (ImageView) findViewById(R.id.trigger);
        mTriggerView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                trigger_set = !trigger_set;
                if (trigger_set) {
                    mTriggerView.setBackgroundColor(sNightMode ? Color.RED : Color.WHITE);
                }
                else {
                    if (recording) doStop();
                    mTriggerView.setBackgroundColor(Color.BLACK);
                }
            }
        });
        mTriggerView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                setListenTimer();
                return true;
            }
        });

        mHeadsetView = (ImageView) findViewById(R.id.headset);
        mHeadsetView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (isUltrasonic() || mPreferences.getBoolean("ultramodes", false)) {
                    sHeadsetMode = (short) ((short) (sHeadsetMode + 1) % HEADSET_MODE_COUNT);
                    updateHeadsetMode();
                    mPreferences.edit().putInt("headMode", sHeadsetMode).apply();
                }
                else
                    Toast.makeText(MainActivity.this, R.string.not_ultrasonic, Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        mHeadsetView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mHeadphones = !mHeadphones;
                if (mHeadphones) {
                    mAudioWhileListening = true;
                    if (listening && (mAudioPlayThread == null)) {
                        mAudioPlayThread = new Thread(new AudioPlayRunnable(MainActivity.this));
                        mAudioPlayThread.start();
                    }
                    mHeadsetView.setBackgroundColor(sNightMode ? Color.RED : Color.WHITE);
                } else {
                    mAudioWhileListening = false;
                    mHeadsetView.setBackgroundColor(Color.BLACK);
                }
            }
        });

        mPlaybackRateView = (ImageView) findViewById(R.id.playback);
        mPlaybackRateView.setOnClickListener(new View.OnClickListener() {
                                                 @Override
                                                 public void onClick(View v) {
                                                     if (isUltrasonic() || mPreferences.getBoolean("ultramodes", false)) {
                                                         sPlayMode = (short) ((short) (sPlayMode + 1) % PLAY_MODE_COUNT);
                                                         updatePlayMode();
                                                         mPreferences.edit().putInt("playMode", sPlayMode).apply();
                                                     }
                                                     else
                                                         Toast.makeText(MainActivity.this, R.string.not_ultrasonic, Toast.LENGTH_SHORT).show();
                                                 }
                                             }
        );
        mPlaybackRateView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {

                if (sPlayMode != PLAY_MODE_TIME_EXPANSION)
                    return true;
                else if (!isUltrasonic() && !mPreferences.getBoolean("ultramodes", false)) {
                    Toast.makeText(MainActivity.this, R.string.not_ultrasonic, Toast.LENGTH_SHORT).show();
                    return true;
                }

                final EditText nameText = new EditText(MainActivity.this);
                InputFilter filter = new InputFilter() {
                    public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                        if (source.length() < 1) return null;
                        char last = source.charAt(source.length() - 1);
                        String reservedChars = "?:\"*|/\\<> ";
                        if (reservedChars.indexOf(last) > -1)
                            return source.subSequence(0, source.length() - 1);
                        return null;
                    }
                };
                nameText.setFilters(new InputFilter[]{filter});
                File activeRecording = FileAccessManager.getActiveRecording();
                String filename = activeRecording.getName().substring(0, activeRecording.getName().toLowerCase().indexOf(".wav"))+"_";
                if (sPlayMode == PLAY_MODE_TIME_EXPANSION)
                    filename += "TE_"+mPreferences.getString("expansion", "20")+"x";
                else if (sPlayMode == PLAY_MODE_FREQUENCY_CUTOFF)
                    filename += "LP";
                else if (sPlayMode == PLAY_MODE_FREQUENCY_DIVISION)
                    filename += "FD_"+mPreferences.getString("expansion", "20")+"x";
                else
                    filename += "HT";
                nameText.setText(filename);
                nameText.setTextColor(Color.parseColor("#33b5e5"));

                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this, R.style.CustomDialog)
                        .setTitle(getString(R.string.export_file))
                        .setView(nameText)
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.ok, null)
                        .setNegativeButton(android.R.string.cancel, null);

                final AlertDialog alertDialog = alertDialogBuilder.create();
                alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
                    @Override
                    public void onShow(DialogInterface dialogInterface) {
                        Button b = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                        b.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {

                                String filename = nameText.getText().toString();
                                if (!filename.isEmpty()) {
                                    String absolutePath = FileAccessManager.getActiveRecording().getAbsolutePath();
                                    final String filePath = absolutePath.substring(0,absolutePath.lastIndexOf(File.separator));
                                    File exportFile = new File(filePath, filename + ".wav");
                                    if (exportFile.exists())
                                        Toast.makeText(MainActivity.this, R.string.already_exists, Toast.LENGTH_SHORT).show();
                                    else {
                                        alertDialog.dismiss();
                                        new ExportWAVTask(MainActivity.this, sPlayMode).execute(exportFile);

                                    }
                                } else
                                    Toast.makeText(MainActivity.this, R.string.empty_name, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
                alertDialog.show();
                return true;
            }
        });

        if (sDataMode == DATA_MODE_PLAY)
            updatePlayMode();
        else
            updateHeadsetMode();

        mGainView = (ImageView) findViewById(R.id.gain);
        mGainView.setOnClickListener(new View.OnClickListener() {
                                                       @Override
                                                       public void onClick(View v) {
                                                           final float prevGain = getGain();
                                                           LayoutInflater layoutInflater = (LayoutInflater) getBaseContext().getSystemService(LAYOUT_INFLATER_SERVICE);
                                                           View popupView = layoutInflater.inflate(R.layout.popup, null);
                                                           final PopupWindow popupWindow = new PopupWindow(popupView, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                                                           popupWindow.setBackgroundDrawable(ContextCompat.getDrawable(MainActivity.this, R.drawable.translucent_background));
                                                           popupWindow.setOutsideTouchable(true);
                                                           popupWindow.setFocusable(false);

                                                           mGainText = (TextView) popupView.findViewById(R.id.gain_text);
                                                           mGainText.setText(sGainFormatter.format(getGain()));

                                                           SeekBar seekBar = (SeekBar) popupView.findViewById(R.id.gain_bar);
                                                           seekBar.setMax(100);
                                                           seekBar.setProgress((int) (Math.log10(getGain()) * 50.0f) + 50);
                                                           seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

                                                               @Override
                                                               public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                                                                   float value = (float) Math.pow(10.0, (float) (progress - 50) / 50.0f);
                                                                   setGain(value);
                                                                   mGainText.setText(sGainFormatter.format(value));
                                                               }

                                                               @Override
                                                               public void onStartTrackingTouch(SeekBar seekBar) {
                                                               }

                                                               @Override
                                                               public void onStopTrackingTouch(SeekBar seekBar) {
                                                               }
                                                           });

                                                           popupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {

                                                               @Override
                                                               public void onDismiss() {
                                                                   popupWindow.dismiss();
                                                                   mPreferences.edit().putFloat("gain", getGain()).apply();
                                                                   if ((sDataMode == DATA_MODE_PLAY) && (prevGain != getGain()) && (FileAccessManager.getActiveRecording() != null)) {
                                                                       new AudioFileReadTask(MainActivity.this, false).execute(FileAccessManager.getActiveRecording());
                                                                       mWaveformView.invalidate();
                                                                       mSpectrogramView.invalidate();
                                                                   }
                                                                   mGainView.setBackgroundColor(Color.BLACK);
                                                               }
                                                           });
                                                           popupWindow.showAtLocation(mScrollView, Gravity.CENTER, 0, 0);
                                                           mGainView.setBackgroundColor(sNightMode ? Color.RED : Color.WHITE);
                                                       }
                                                   }

        );

        mPowerView = (ImageView) findViewById(R.id.power);
        mPowerView.setOnClickListener(new View.OnClickListener() {

                                                       @Override
                                                       public void onClick(View v) {
                                                           if (mPowerPopup.getVisibility() == View.INVISIBLE) {

                                                               View frame = mPowerPopup.findViewById(R.id.power_frame);
                                                               if (sNightMode)
                                                                   frame.setBackgroundResource(R.drawable.red_power_border);
                                                               else
                                                                   frame.setBackgroundResource(R.drawable.power_border);
                                                               frame.invalidate();

                                                               mPowerTickOverlay.setMaxFreq(mFreqTickView.getMaxFreq());
                                                               mPowerTickOverlay.setLogScale(mFreqTickView.getLogScale());

                                                               mPowerOverlay.setSampleSize(Integer.parseInt(mPreferences.getString("fft", "1024")) / 2);

                                                               if (!playing) {
                                                                   PowerView.openReadCache();
                                                                   mPowerOverlay.updateDataFromCacheFile(mPlayStart * Integer.parseInt(mPreferences.getString("fft", "1024")) * 2 / WINDOW_FACTOR, true);
                                                                   PowerView.closeReadCache();
                                                                   mPowerOverlay.postInvalidate();
                                                               }
                                                               mPowerView.setBackgroundColor(sNightMode ? Color.RED : Color.WHITE);
                                                               mPowerPopup.setVisibility(View.VISIBLE);
                                                           } else {
                                                               mPowerPopup.setVisibility(View.INVISIBLE);
                                                               mPowerView.setBackgroundColor(Color.BLACK);
                                                           }
                                                       }
                                                   }

        );

        ImageView archiveView = (ImageView) findViewById(R.id.archive);
        archiveView.setOnClickListener(new View.OnClickListener()

                                       {
                                           @Override
                                           public void onClick(View v) {
                                               if (listening)
                                                   stopListening();
                                               else
                                                   doStop();

                                               final FileChooser chooser = new FileChooser(MainActivity.this);
                                               chooser.setExtension("wav");
                                               chooser.setFileListener(new FileSelectedListener() {
                                                   @Override
                                                   public void fileSelected(final File file) {
                                                       // Read file
                                                       mRecordingList = chooser.getFileList();
                                                       loadRecording(file);
//                                                       DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
//                                                       ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) mNavigateLayout.getLayoutParams();
//                                                       lp.setMargins(0, (int) ((float) (mWaveformView.getHeight()-mNavigateLayout.getHeight()) / (2.0f * metrics.density)), 20, 0);
//                                                       mNavigateLayout.setLayoutParams(lp);
                                                       mFileIndicator.setText((mRecordingList.indexOf(file)+1)+" / "+mRecordingList.size());
                                                       mNavigateLayout.setVisibility(View.VISIBLE);
                                                   }
                                               }).showDialog();
                                           }
                                       }

        );

        mTimeTickView.getViewTreeObserver().

                addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
                                              @Override
                                              public void onGlobalLayout() {

                                                  LayoutParams lp = mTimeTickView.getLayoutParams();
                                                  lp.width = mScrollView.getWidth();
                                                  mTimeTickView.setLayoutParams(lp);
                                                  mTimeTickView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                                              }
                                          }

                );

//        mWaveformView.getViewTreeObserver().
//
//                addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
//                                              @Override
//                                              public void onGlobalLayout() {
//
//                                                  View view = findViewById(R.id.waveform_control_layout);
//                                                  LayoutParams lp = view.getLayoutParams();
//                                                  lp.height = mWaveformView.getHeight();
//                                                  view.setLayoutParams(lp);
//
//                                                  mWaveformView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
//                                              }
//                                          }
//
//                );

        mScrollView.getViewTreeObserver().

                addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                                              @Override
                                              public void onGlobalLayout() {
                                                   int min_height = (int) ((float) mScrollView.getHeight() * 0.2f);
                                                  ((TouchHorizontalScrollView) mScrollView).setMinWaveHeight(min_height);
                                                  ((TouchHorizontalScrollView) mScrollView).resizeChildViews(mPreferences.getInt("waveheight", min_height));

                                                  mFreqTickView.setPixelOffset(mTimeTickView.getHeight());
//                                                  View view = findViewById(R.id.waveform_control_layout);
//                                                  LayoutParams lp = view.getLayoutParams();
//                                                  lp.height = mWaveformView.getHeight();
//                                                  view.setLayoutParams(lp);

                                                  mScrollView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                                              }
                                          }

                );

        mZoomTime = mPreferences.getFloat("zoomX", 10.0f);
//        mZoomFactorY = mPreferences.getFloat("zoomY", 1.0f);

//        mSpectrogramView.setScaleY(mZoomFactorY);
//        mFreqTickView.setZoomLevel(mZoomFactorY);

        sNightMode = mPreferences.getBoolean("nightMode", false);
        if (sNightMode) setNightMode();

        initLocationServices();
        // Get an instance of the sensor service, and use that to get an instance of
        // a particular sensor.
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        mTemperatureSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
        mPressureSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        mRelHumiditySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY);
        mShakeSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorData = new SensorData();

        // Check to make sure we have the desired permissions before we create any directories!
        if (!hasRequiredPermissions()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ActivityCompat.requestPermissions(this,
                            new String[]{
                                    Manifest.permission.RECORD_AUDIO,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                    Manifest.permission.INTERNET,
                                    Manifest.permission.ACCESS_NETWORK_STATE,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                    Manifest.permission.READ_EXTERNAL_STORAGE
                            },
                            BATRECORDER_REQUIRED_PERMISSIONS);
                return;
            }
            mInitStatusView.post(new StatusRunnable(getString(R.string.permissions_verify_problem)));
            findViewById(R.id.quit).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    CloseApp();
                }
            });
            findViewById(R.id.playback_only).setVisibility(View.GONE);
            findViewById(R.id.init_control_layout).setVisibility(View.VISIBLE);
            return;
        }

//        if (MainActivity.CUSTOM_LICENSE)
//           checkCustomLicense();
        mRecordEnabled = isVendorBranded() || isValidCode() || DEBUG_MODE;
        finishInitialization();

//        else
//        checkGoogleStoreLicense();

//		createTestToneFile(mInternalStorageDirectory.getAbsolutePath() + File.separator + "tone3K.wav", 384000, 3000);
//		createTestToneFile(mInternalStorageDirectory.getAbsolutePath() + File.separator + "tone100K.wav", 250000, 100000);
//		createTestToneFile(mInternalStorageDirectory.getAbsolutePath() + File.separator + "tone10K.wav", 200000, 10000);
//		createTestToneFile(mInternalStorageDirectory.getAbsolutePath() + File.separator + "tone2K.wav", 48000, 2000);
//		createTestToneFile(mInternalStorageDirectory.getAbsolutePath() + File.separator + "tone75K.wav", 384000, 75000);
    }

    private void finishInitialization() {
        FileAccessManager.init(getApplicationContext(), mPreferences.getBoolean("storage", false));
        deleteCacheFile();
        initSystem();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {

        if (!mPreferences.getBoolean("hardware_btns", false)) return super.dispatchKeyEvent(event);

        int action = event.getAction();
        int keyCode = event.getKeyCode();
        switch (keyCode) {

            case KeyEvent.KEYCODE_VOLUME_UP:
                if (action == KeyEvent.ACTION_DOWN) {
                    if (sDataMode == DATA_MODE_RECORD) {
                        if (recording)
                            doStop();
                        else {
                            doRecord(true);
                            Toast.makeText(MainActivity.this, R.string.prebuffer, Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        if (playing)
                            doStop();
                        else
                            doPlay();
                    }
                }
                return true;

            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (action == KeyEvent.ACTION_DOWN) {
                    if (!listening)
                        startListening();
                    else
                        stopListening();
                }
                return true;

            default:
                return super.dispatchKeyEvent(event);
        }
    }

    private void createPowerPopup() {

        mPowerPopup = findViewById(R.id.power_popup);
        mPowerOverlay = (PowerView) mPowerPopup.findViewById(R.id.power_plot);
        ViewGroup.LayoutParams params = mPowerOverlay.getLayoutParams();
        params.width = mPreferences.getInt("pow_width", 512);
        params.height = mPreferences.getInt("pow_height", 256);
        mPowerOverlay.setLayoutParams(params);

        mPowerPopup.setX(mPreferences.getInt("pow_x", 20));
        mPowerPopup.setY(mPreferences.getInt("pow_y", 20));

        mPowerTickOverlay = (PowerFreqTickView) mPowerPopup.findViewById(R.id.power_ticks);
        mPowerOverlay.setFreqTickView(mFreqTickView);

        mPowerOverlay.setOnTouchListener(new View.OnTouchListener() {
            private int mPosX = 0;
            private int mPosY = 0;
            private int dx = 0;
            private int dy = 0;

            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        mPosX = mPreferences.getInt("pow_x", 20);
                        mPosY = mPreferences.getInt("pow_y", 20);
                        dx = mPosX - (int) motionEvent.getRawX();
                        dy = mPosY - (int) motionEvent.getRawY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        mPosX = (int) (motionEvent.getRawX() + dx);
                        mPosY = (int) (motionEvent.getRawY() + dy);
                        mPowerPopup.setX(mPosX);
                        mPowerPopup.setY(mPosY);
                        mPreferences.edit().putInt("pow_x", mPosX).apply();
                        mPreferences.edit().putInt("pow_y", mPosY).apply();
                        break;
                }
                return true;
            }
        });

        View view = mPowerPopup.findViewById(R.id.power_sizer);
//        if (MainActivity.getNightMode()) ((ImageView) view).getDrawable().setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
        view.setOnTouchListener(new View.OnTouchListener() {
            private int mOrgX = 0;
            private int mOrgY = 0;
            private int mOrgWidth;
            private int mOrgHeight;

            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        mOrgX = (int) motionEvent.getRawX();
                        mOrgY = (int) motionEvent.getRawY();
                        mOrgWidth = mPowerOverlay.getMeasuredWidth();
                        mOrgHeight = mPowerOverlay.getMeasuredHeight();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        int offsetX = (int) motionEvent.getRawX() - mOrgX;
                        int offsetY = (int) motionEvent.getRawY() - mOrgY;
                        int newWidth = Math.max(200, mOrgWidth + offsetX);
                        int newHeight = Math.max(100, mOrgHeight + offsetY);

                        ViewGroup.LayoutParams params = mPowerOverlay.getLayoutParams();
                        if ((params.width != newWidth) || (params.height != newHeight)) {
                            params.width = newWidth;
                            params.height = newHeight;
                            mPowerOverlay.setLayoutParams(params);

                            params = mPowerTickOverlay.getLayoutParams();
                            params.width = newWidth;
                            mPowerTickOverlay.setLayoutParams(params);
                            mPowerTickOverlay.rebuildBitmap();

//                            mPowerPopup.setHeight(0);
//                            mPowerPopup.setWidth(0);
                            findViewById(R.id.main_layout).postInvalidateOnAnimation();

                            mPreferences.edit().putInt("pow_width", mPowerOverlay.getWidth()).apply();
                            mPreferences.edit().putInt("pow_height", mPowerOverlay.getHeight()).apply();                                                                              }
                        break;
                }
                return true;
            }
        });
    }

    private void deleteCacheFile() {
        // Delete any existing cache file
      File cacheFile = new File(getFilesDir(), sCacheFileName);
 //       File cacheFile = new File(FileAccessManager.getStorageDirectoryPath(), sCacheFileName);
        if (cacheFile.exists()) cacheFile.delete();
    }

    private void loadRecording(final File file) {

        sDataMode = DATA_MODE_PLAY;
        mTriggerView.setVisibility(View.GONE);
        mHeadsetView.setVisibility(View.GONE);
        mPlaybackRateView.setVisibility(View.VISIBLE);
        FileAccessManager.setActiveRecording(file);
        mPlayStart = 0;
        mPlayEnd = -1;
        mWaveformView.setSelRange(0, -1);
        mWaveformView.setPlayhead(mPlayStart);
        mPlayView.setImageDrawable(mPlayImage);
        updatePlayMode();
        mLoopView.setVisibility(View.VISIBLE);

        long [] audioParams = new long[4];
        MainActivity.readWAVHeader(file.getAbsolutePath(), audioParams);
        final int channelNo = (int) audioParams[0];
        mStereoChannel = 1;
        if (channelNo == 2) {
            final Dialog dialog = new AlertDialog.Builder(this, R.style.CustomAlertDialog)
  //              .setTitle(file.getName())
                .setCancelable(false)
                .create();
            dialog.show();
            dialog.setContentView(R.layout.stereo_dialog);
            String text = "<font color=\"0x33b5e5\"><b><i>"+file.getName()+"</font></i></b> "+getString(R.string.stereo_warning);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                ((TextView) dialog.findViewById(R.id.stereo_msg)).setText(Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY));
            } else {
                ((TextView) dialog.findViewById(R.id.stereo_msg)).setText(Html.fromHtml(text));
            }
            dialog.findViewById(R.id.chan1).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mStereoChannel = 1;
                    dialog.dismiss();
                    new AudioFileReadTask(MainActivity.this, true).execute(file);
                }
            });
            dialog.findViewById(R.id.chan2).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mStereoChannel = 2;
                    dialog.dismiss();
                    new AudioFileReadTask(MainActivity.this, true).execute(file);
                }
            });
        }
        else {
            new AudioFileReadTask(MainActivity.this, true).execute(file);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        if (requestCode == BATRECORDER_REQUIRED_PERMISSIONS && (grantResults.length >= 3)) {
            // If request is cancelled, the result arrays are empty.
            if ((grantResults[0] == PackageManager.PERMISSION_GRANTED)
                && (grantResults[1] == PackageManager.PERMISSION_GRANTED)
                && (grantResults[2] == PackageManager.PERMISSION_GRANTED)
//                && (grantResults[3] == PackageManager.PERMISSION_GRANTED)
//                && (grantResults[4] == PackageManager.PERMISSION_GRANTED)
//                && (grantResults[5] == PackageManager.PERMISSION_GRANTED)
                    ) {
//                if (MainActivity.CUSTOM_LICENSE)
//                    checkCustomLicense();
                    mRecordEnabled = isVendorBranded() || isValidCode();
                    finishInitialization();
//                else
//                    checkGoogleStoreLicense();
            } else {
                mInitStatusView.post(new StatusRunnable(getString(R.string.permissions_verify_problem)));
                findViewById(R.id.quit).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        CloseApp();
                    }
                });
                findViewById(R.id.playback_only).setVisibility(View.GONE);
                findViewById(R.id.init_control_layout).setVisibility(View.VISIBLE);
            }
        }
    }

    private static boolean isVendorBranded() {
        return (sBuildVariation != VAR_UNIVERSAL);
    }

    public static short getBuildVariation() {
        return sBuildVariation;
    }

    public static boolean getNightMode() {
        return sNightMode;
    }

     private void setNightMode() {

 //        View decor = getWindow().getDecorView();
         if (sNightMode) {
 //           decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

//             if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
//                 getWindow().setStatusBarColor(Color.RED);
//             }
/*
                mRecordImage.setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
                mRecordStopImage.setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
                mPlayImage.setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
                mStopImage.setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
                mMicImage.setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
                mMicOffImage.setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
                mHeadsetFDImage.setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
                mHeadsetTuneImage.setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
                mHeadsetCutoffImage.setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
                mFrequencyImage.setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
                mHeterodyneImage.setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
                mTimeImage.setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
                mCutoffImage.setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
                mLoopImage.setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
                mLoopOffImage.setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);

                mPlayView.getDrawable().setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
                mMicView.getDrawable().setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
                mTriggerView.getDrawable().setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
                mHeadsetView.getDrawable().setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
                mPlaybackRateView.getDrawable().setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
                mLoopView.getDrawable().setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
                mGainView.getDrawable().setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
                mPowerView.getDrawable().setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
                mArchiveView.getDrawable().setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
                mSettingsView.getDrawable().setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
                ((ImageView) mInfoLayout).getDrawable().setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
*/
                mScrollLabel.setTextColor(Color.RED);
                mScrollSeekBar.getProgressDrawable().setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
                mScrollSeekBar.getThumb().setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);

             if (mDetailsLayout.getVisibility() == View.VISIBLE) {
/*                 ((TextView) findViewById(R.id.sampleFile)).setTextColor(Color.RED);
                 ((TextView) findViewById(R.id.sampleDevice)).setTextColor(Color.RED);
                 ((TextView) findViewById(R.id.sampleRate)).setTextColor(Color.RED);
                 ((TextView) findViewById(R.id.sampleDate)).setTextColor(Color.RED);
                 ((TextView) findViewById(R.id.sampleSpecies)).setTextColor(Color.RED);
                 ((TextView) findViewById(R.id.sampleLat)).setTextColor(Color.RED);
                 ((TextView) findViewById(R.id.sampleLon)).setTextColor(Color.RED);
                 ((ImageView) findViewById(R.id.bat)).getDrawable().setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
*/                 mDetailsLayout.invalidate();
             }
             Palette.setType(Palette.PALETTE_RED);
         }
            else {
 //          decor.setSystemUiVisibility(0);

//             if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
//                 getWindow().setStatusBarColor(Color.WHITE);
//             }
/*
                mRecordImage.clearColorFilter();
                mRecordStopImage.clearColorFilter();
                mPlayImage.clearColorFilter();
                mStopImage.clearColorFilter();
                mMicImage.clearColorFilter();
                mMicOffImage.clearColorFilter();
                mHeadsetFDImage.clearColorFilter();
                mHeadsetTuneImage.clearColorFilter();
                mHeadsetCutoffImage.clearColorFilter();
                mFrequencyImage.clearColorFilter();
                mHeterodyneImage.clearColorFilter();
                mLoopImage.clearColorFilter();
                mLoopOffImage.clearColorFilter();
                mTimeImage.clearColorFilter();
                mCutoffImage.clearColorFilter();

                mPlayView.getDrawable().clearColorFilter();
                mMicView.getDrawable().clearColorFilter();
                mTriggerView.getDrawable().clearColorFilter();
                mHeadsetView.getDrawable().clearColorFilter();
                mPlaybackRateView.getDrawable().clearColorFilter();
                mLoopView.getDrawable().clearColorFilter();
                mGainView.getDrawable().clearColorFilter();
                mPowerView.getDrawable().clearColorFilter();
                mArchiveView.getDrawable().clearColorFilter();
                mSettingsView.getDrawable().clearColorFilter();
               ((ImageView) mInfoLayout).getDrawable().clearColorFilter();
*/
                mScrollLabel.setTextColor(Color.WHITE);
                mScrollSeekBar.getProgressDrawable().clearColorFilter();
                mScrollSeekBar.getThumb().clearColorFilter();

                 if (mDetailsLayout.getVisibility() == View.VISIBLE) {
/*                     ((TextView) findViewById(R.id.sampleFile)).setTextColor(Color.WHITE);
                     ((TextView) findViewById(R.id.sampleDevice)).setTextColor(Color.WHITE);
                     ((TextView) findViewById(R.id.sampleRate)).setTextColor(Color.WHITE);
                     ((TextView) findViewById(R.id.sampleDate)).setTextColor(Color.WHITE);
                     ((TextView) findViewById(R.id.sampleSpecies)).setTextColor(Color.WHITE);
                     ((TextView) findViewById(R.id.sampleLat)).setTextColor(Color.WHITE);
                     ((TextView) findViewById(R.id.sampleLon)).setTextColor(Color.WHITE);
                     ((ImageView) findViewById(R.id.bat)).getDrawable().clearColorFilter();
 */                     mDetailsLayout.invalidate();
                 }
               Palette.setType(mPreferences.getInt("palette", 0));
            }

         View frame = mPowerPopup.findViewById(R.id.power_frame);
         frame.setBackgroundResource(sNightMode ? R.drawable.red_power_border : R.drawable.power_border);
         frame.invalidate();

         /*
         if (mHeadphones) mHeadsetView.setBackgroundColor(sNightMode ? Color.RED : Color.WHITE);
         if (trigger_set) mTriggerView.setBackgroundColor(sNightMode ? Color.RED : Color.WHITE);
         if (getPowerView() != null) mPowerView.setBackgroundColor(sNightMode ? Color.RED : Color.WHITE);

         mPlayView.invalidate();
            mMicView.invalidate();
            mTriggerView.invalidate();
            mHeadsetView.invalidate();
            mPlaybackRateView.invalidate();
            mLoopView.invalidate();
            mGainView.invalidate();
            mPowerView.invalidate();
            mArchiveView.invalidate();
*/
            findViewById(R.id.palette_ramp).invalidate();

            mSpectrogramView.setBackgroundColor(Palette.getLinearColors()[0]);
            mSpectrogramView.invalidate();

            mFreqTickView.rebuildBitmap();
            mFreqTickView.invalidate();

            mTimeTickView.invalidate();

            mWaveformView.invalidate();

//            View power = getPowerView();
//            if (power != null) {
//                View frame = mPowerPopup.getContentView().findViewById(R.id.power_frame);
//                if (sNightMode) {
//                    frame.setBackgroundResource(R.drawable.red_power_border);
//                    ((ImageView) frame.findViewById(R.id.power_sizer)).getDrawable().setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
//                }
//                else {
//                    frame.setBackgroundResource(R.drawable.power_border);
//                    ((ImageView) frame.findViewById(R.id.power_sizer)).getDrawable().clearColorFilter();
//                }
//                frame.invalidate();
//                mPowerTickOverlay.rebuildBitmap();
//            }
    }

    private void updatePlayMode() {
        if (sPlayMode == PLAY_MODE_HETERODYNE) {
            mFreqTickView.showTuner(true);
            mPlaybackRateView.setImageDrawable(mHeterodyneImage);
        } else {
            mFreqTickView.showTuner(false);
            if (sPlayMode == PLAY_MODE_FREQUENCY_DIVISION) {
                mPlaybackRateView.setImageDrawable(mFrequencyImage);
            } else if (sPlayMode == PLAY_MODE_TIME_EXPANSION) {
                mPlaybackRateView.setImageDrawable(mTimeImage);
            } else {
                mPlaybackRateView.setImageDrawable(mCutoffImage);
            }
        }
    }

    private void updateHeadsetMode() {
        if (sHeadsetMode == PLAY_MODE_HETERODYNE) {
            mFreqTickView.showTuner(true);
            mHeadsetView.setImageDrawable(mHeadsetTuneImage);
        } else {
            mFreqTickView.showTuner(false);
            if (sHeadsetMode == PLAY_MODE_FREQUENCY_DIVISION) {
                mHeadsetView.setImageDrawable(mHeadsetFDImage);
            } else if (sHeadsetMode == PLAY_MODE_FREQUENCY_CUTOFF) {
                mHeadsetView.setImageDrawable(mHeadsetCutoffImage);
            }
        }
    }

    public MetaDataParser createMetaDataParser() {
        MetaDataParser parser;
        String mdFormat = getPreferences().getString("metadata", "GUANO");
        if (mdFormat.equals("GUANO"))
            parser = new GUANOMetaDataParser();
        else if (mdFormat.equals("WAMD"))
            parser = new WAMDMetaDataParser();
        else
            parser = new XMPMetaDataParser();
        return parser;
    }

    @SuppressWarnings({"MissingPermission"})
    private void initLocationServices() {
        if (mPreferences.getBoolean("geo", true) && hasLocationPermission()) {
            if (mLocManager == null) {
                mLocManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                int updateFreq = Integer.parseInt(mPreferences.getString("geofreq", "120"));
                float updateDistance = Float.parseFloat(mPreferences.getString("geodist", "10"));
                if (mLocManager.getAllProviders().contains(LocationManager.GPS_PROVIDER)) {
                    mLocManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, updateFreq * 1000L, updateDistance, locationListener);
                    synchronized (locLock) {
                        if (mLocation == null) mLocation = mLocManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    }
                }
                if (mLocManager.getAllProviders().contains(LocationManager.NETWORK_PROVIDER)) {
                    mLocManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, updateFreq * 1000L, updateDistance, locationListener);
                    synchronized (locLock) {
                        if (mLocation == null) mLocation = mLocManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    }
                }
            }
        } else if (mLocManager != null) {
            mLocManager.removeUpdates(locationListener);
            mLocManager = null;
            synchronized (locLock) {
                mLocation = null;
            }
        }
    }

    public boolean hasRequiredPermissions() {
        return (
            ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
//            ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
//            ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        );
    }

    public boolean hasLocationPermission() {
        return (Build.VERSION.SDK_INT < 23 ||
                (ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED));
    }

//    public boolean hasSMSPermission() {
//        return (Build.VERSION.SDK_INT < 23 || ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED);
//    }

     private void doRecord(boolean capture) {

        if (mRecordEnabled) {
            trigger_set = false;
            mTriggerView.setBackgroundColor(Color.BLACK);
            if (!recording) {
                if (!listening) startListening();
                //            if (mActiveRecording != null) Log.d(TAG, "ActiveRecording not null!");
                capture_listen = capture;
                recording = true;
                mPlayView.setImageDrawable(mRecordStopImage);
            }
        }
        else {
            checkCustomLicense();
        }
     }

    private void doPlay() {
        if (sDataMode == DATA_MODE_PLAY) {
            if (!playing) {
                doStop();
                if (sPlayMode == PLAY_MODE_HETERODYNE) {
                    mFreqTickView.showTuner(true);
                    mPlaybackRateView.setImageDrawable(mHeterodyneImage);
                } else {
                    mFreqTickView.showTuner(false);
                    if (sPlayMode == PLAY_MODE_FREQUENCY_DIVISION)
                        mPlaybackRateView.setImageDrawable(mFrequencyImage);
                    else if (sPlayMode == PLAY_MODE_TIME_EXPANSION)
                        mPlaybackRateView.setImageDrawable(mTimeImage);
                    else
                        mPlaybackRateView.setImageDrawable(mCutoffImage);
                }

                if (FileAccessManager.getActiveRecording() != null) {
                     {
                        final Runnable audioPlayer = new Runnable() {
                            @Override
                            public void run() {
                                while (mAudioPlayThread != null) {
                                    try {
                                        Thread.sleep(100);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }
                                playing = true;
                                mAudioPlayThread = new Thread(new AudioPlayRunnable(MainActivity.this));
                                mAudioPlayThread.start();
                            }
                        };
                         new Thread(audioPlayer).start();
                         mPlayView.setImageDrawable(mStopImage);
                    }
                 }
            }
        }
    }

    public void createWayPointFile() {
        String dirPath =  FileAccessManager.getActiveRecording().getParentFile().getAbsolutePath();
        if ((mWayPointFile == null) || !dirPath.equals(mWayPointFile.getParentFile().getAbsolutePath())) {
            synchronized (locLock) {
                closeWaypoint();
                mWayPointFile = new File(dirPath, "wp.bin");
                openWaypoint(mWayPointFile.getAbsolutePath());
                if (mLocation != null) {
                    writeWaypoint(mLocation.getTime(),
                            mLocation.getLatitude(),
                            mLocation.getLongitude(),
                            (mLocation.hasAltitude()) ? mLocation.getAltitude() : LocationData.INVALID_ELEVATION);
//                    try {
//                        DataOutputStream out = new DataOutputStream(new FileOutputStream(mWayPointFile, true));
//                        out.writeLong(mLocation.getTime());
//                        out.writeDouble(mLocation.getLatitude());
//                        out.writeDouble(mLocation.getLongitude());
//                        if (mLocation.hasAltitude())
//                            out.writeDouble(mLocation.getAltitude());
//                        else
//                            out.writeDouble((double) LocationData.INVALID_ELEVATION);
//                        out.close();
//                    } catch (Exception e) {
//                    }
                }
            }
        }
    }

     public void doStop() {

        if (sDataMode == DATA_MODE_RECORD) {
//            if (mAudioWhileListening) mAudioWhileListening = false;
            File activeRecording = FileAccessManager.getActiveRecording();
            if (recording && (activeRecording != null)) {
                synchronized (lock) {

                    recording = false;
                    int samplesRecorded = StopRecord();

                    MetaDataParser parser = createMetaDataParser();
                    AppendMetadata(
                            activeRecording.getAbsolutePath(),
                            parser.getNamespace(),
//                            parser.create(new Date(mActiveRecording.lastModified()),
//                                    (float) samplesRecorded / (float) sMicrophone.getSampleRate(),
//                                    sMicrophone,
//                                    getLocationData(),
//                                    getSensorData(),
//                                    null)
                            parser.create(new Date(activeRecording.lastModified()),
                                    (float) samplesRecorded / (float) sMicrophone.getSampleRate(),
                                    sMicrophone,
                                    getLocationData(),
                                    getSensorData())
                    );
                    createWayPointFile();

                    Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    intent.setData(FileAccessManager.getFileUri(MainActivity.this, activeRecording));
//                    intent.setData(Uri.fromFile(mActiveRecording));
                    sendBroadcast(intent);

                    FileAccessManager.setActiveRecording(null);
                }
                if (AudioReadRunnable.sInfoOverlayView != null) AudioReadRunnable.sInfoOverlayView.makeDirty(-1);
//	    		mPlayView.setImageResource(R.drawable.record);
                mPlayView.setImageDrawable(mRecordImage);
            }
        } else {
            playing = false;
//    		mPlayView.setImageResource(R.drawable.play); // SLOW
            mPlayView.setImageDrawable(mPlayImage);
        }
    }

    private void setRecordMode() {
        setPlayStart(0);
        sDataMode = DATA_MODE_RECORD;
        mPlayView.setImageDrawable(mRecordImage);
        mPlaybackRateView.setVisibility(View.GONE);
        mLoopView.setVisibility(View.GONE);
        mTriggerView.setVisibility(View.VISIBLE);
        mHeadsetView.setVisibility(View.VISIBLE);
        FileAccessManager.setActiveRecording(null);
        mNavigateLayout.setVisibility(View.INVISIBLE);
        mPlaybackOverlay.setVisibility(View.INVISIBLE);
    }

    private void startListening() {

        if (BuildConfig.DEBUG && listening) throw new RuntimeException();

        doStop();
        setRecordMode();
        mMicView.setImageDrawable(mMicImage);
        updateHeadsetMode();
        listening = true;

        if (mHeadphones && (mAudioPlayThread == null)) {
            mAudioWhileListening = true;
            mAudioPlayThread = new Thread(new AudioPlayRunnable(this));
            mAudioPlayThread.start();
        }

        if (BuildConfig.DEBUG && (mUSBListenThread != null)) throw new RuntimeException();
        mUSBListenThread = new Thread(new UsbRecordRunnable(this));
        mUSBListenThread.start();
    }

    public void clearAudioPlayThread() {
        mAudioPlayThread = null;
    }

    static public String getVersion() {
        return version_n;
    }

    static public short getHeadsetMode() {
        return sHeadsetMode;
    }

    static public short getPlayMode() {
        return sPlayMode;
    }

    public float getZoomX() {
        return mZoomFactorX;
    }
    public float getZoomY() {
        return mZoomFactorY;
    }

    public float getZoomTime() { return mZoomTime; }

    public void setZoomX(float zoom) {
        mZoomFactorX = zoom;
        mSpectrogramView.setZoomX(mZoomFactorX);
        mWaveformView.setZoomX(mZoomFactorX);
        mTimeTickView.setZoomLevel(mZoomFactorX);
    }

    public short getStereoChannel() {
        return mStereoChannel;
    }

    public void setActiveSampleRate(int rate) {
        mActiveSampleRate = rate;
        if (!isUltrasonic() && (sPlayMode != PLAY_MODE_FREQUENCY_CUTOFF)) {
            sPlayMode = PLAY_MODE_FREQUENCY_CUTOFF;
            updatePlayMode();
        }
    }

     public void setListenCapture(boolean capture) {
        capture_listen = capture;
    }

    public boolean getListenCapture() {
        return capture_listen;
    }

    public boolean isLooping() {
        return loop;
    }

    public boolean isAudioWhileListening() {
        return mAudioWhileListening;
    }

    public void setRecording(boolean record) {
        recording = record;
    }

    public boolean isRecording() {
        return recording;
    }

    public void setReading(boolean read) {
        reading = read;
    }

    public boolean isReading() {
        return reading;
    }

    public void setListening(boolean listen) {
        listening = listen;
    }

    public boolean isListening() {
        return listening;
    }

    public boolean isTriggerSet() {
        return trigger_set;
    }

    public boolean isPlaying() {
        return playing;
    }

    public void setPlaying(boolean play) {
        playing = play;
    }

    public void setRecordingLength(float length) {
        mRecordingLength = length;
    }

    public float getSecondsPerSample() { return mSecsPerSample; }
    public void setSecondsPerSample(float s) {
        mSecsPerSample = s;
    }

    public SpectrogramView getSpectrogramView() {
        return mSpectrogramView;
    }

    public WaveformView getWaveformView() {
        return mWaveformView;
    }

    public boolean isPowerPopupVisible() {
        return (mPowerPopup.getVisibility() == View.VISIBLE);
    }

    public PowerView getPowerView() {
        return mPowerOverlay;
    }

    public PowerFreqTickView getPowerFreqTickView() {
        return mPowerTickOverlay;
    }

    public TimeTickView getTimeTickView() {
        return mTimeTickView;
    }

    public FreqTickView getFreqTickView() {
        return mFreqTickView;
    }

    public HorizontalScrollView getScrollView() {
        return mScrollView;
    }

    public View getPlaybackOverlayView() { return mPlaybackOverlay; }

    public View getInfoOverlayView() { return mInfoLayout; }

    public View getDetailOverlayView() { return mDetailsLayout; }

    public View getPlayView() { return mPlayView; }

    public SeekBar getSeekScrollView() { return mScrollSeekBar; }

    public void setPlayToRecordIcon() {
        mPlayView.setImageDrawable(mRecordImage);
    }
    public void stopListening() {

        if (mAudioWhileListening) mAudioWhileListening = false;
        doStop();
        listening = false;
        mMicView.setImageDrawable(mMicOffImage);
        if (IsUSBInitialized()) ((UsbMicrophone) sMicrophone).exitLoop(false);
    }

    public SharedPreferences getPreferences() {
        return mPreferences;
    }

    private void initSystem() {

        checkValidSampleRates();
        initInverseFFT(AudioPlayRunnable.INVERSE_FFT_SIZE);
        initFFT(Integer.parseInt(mPreferences.getString("fft", "1024")), mPreferences.getFloat("gain", 1.0f));

        mUsbReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                    // Cleans up and closes communication with the device
                    UsbLogger.writeToLog("USB device detached.");

                    if ((sMicrophone != null) && (sMicrophone.getType() == Microphone.USB_MICROPHONE)) {
                        DisconnectUSB();
                        if (mSplashView.getVisibility() == View.INVISIBLE) showDisconnectAlert();
                    }
                } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                    UsbLogger.writeToLog("USB device attached.");
                    if ((sMicrophone == null) || (sMicrophone.getType() != Microphone.USB_MICROPHONE)) {
                        if (mDisconnectAlert != null) mDisconnectAlert.dismiss();
                        // HACK
                        int count = 0;
                        while (!checkUSBPermissions()) {
                            try {
                                Thread.sleep(1000);
                                if (++count > 5) break;
                            } catch (InterruptedException e) {
                                //handle
                            }
                        }
//                        checkUSBPermissions();
                    }
                } else if (ACTION_USB_PERMISSION.equals(action)) {
                    UsbLogger.writeToLog("USB device permission request.");
                    UsbDevice device;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // TIRAMISU onwards
                        device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class);
                    } else {
                        device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    }
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            checkUSBPermissions();
                        } else {
                            UsbLogger.writeToLog("Permission denied for USB device.");
                        }
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(ACTION_USB_PERMISSION);
        registerReceiver(mUsbReceiver, filter, RECEIVER_NOT_EXPORTED);            // TODO do I need to unregister / re-register on Pause / Resume?

        // HACK!
        int count = 0;
        while (!checkUSBPermissions()) {
            try {
                Thread.sleep(1000);
                if (++count > 5) break;
            } catch (InterruptedException e) {
                //handle
            }
        }
    }

    private void showDisconnectAlert() {
        mDisconnectAlert = new AlertDialog.Builder(this, R.style.CustomDialog)
                .setMessage(R.string.usb_detach)
                .setPositiveButton(R.string.playback_only, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        mDisconnectAlert = null;
                        sMicrophone = createMicrophone();
                        if (!isUltrasonic()) {
                            sHeadsetMode = PLAY_MODE_FREQUENCY_CUTOFF;
                            updateHeadsetMode();
                        }
                    }
                })
                .setNegativeButton(R.string.quit, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        CloseApp();
                    }
                })
                .show();
    }

    static public Microphone getMicrophone() {
        return sMicrophone;
    }

    public boolean IsUSBInitialized() {
        return ((mUSBInitialized == 1) && (sMicrophone != null) && (sMicrophone.getType() == Microphone.USB_MICROPHONE));
    }

    private boolean checkUSBPermissions() {

        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        for (UsbDevice device : usbManager.getDeviceList().values()) {
            String usbInfo = "checkUSBPermissions: id="+device.getDeviceId()+ " vendor="+device.getVendorId()+" class="+device.getDeviceClass();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) usbInfo += " name = "+device.getManufacturerName();
            UsbLogger.writeToLog(usbInfo);
            if (UsbMicrophone.isSupported(device)) {
                UsbLogger.writeToLog("Device supported id="+device.getDeviceId());
                UsbDeviceConnection connection = usbManager.openDevice(device);
                if (connection == null) {
                    UsbLogger.writeToLog("No USB connection found for id="+device.getDeviceId());
                    PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
                    usbManager.requestPermission(device, permissionIntent);
                    return false;
                } else {
                    UsbLogger.writeToLog("USB connection found for id="+device.getDeviceId());
                    ConnectUSB(connection, device);
                    if (IsUSBInitialized()) {
                        UsbLogger.writeToLog("USB device successfully initialized");
                        final Handler handler = new Handler();
                        final Runnable splashDelayHandler = new Runnable() {
                            @Override
                            public void run() {
                                mSplashView.setVisibility(View.INVISIBLE);
                                adjustSystemToolBars();
                                mPlayView.setImageDrawable(mRecordImage);
                                if (sMicrophone.getSampleRate() > 0 && !sMicrophone.getProductName().isEmpty())
                                    Toast.makeText(MainActivity.this, sMicrophone.getProductName(), Toast.LENGTH_SHORT).show();
                            }
                        };
                        handler.postDelayed(splashDelayHandler, 4000);

//                        mSplashView.setVisibility(View.INVISIBLE);
//                        adjustSystemToolBars();
//                        mPlayView.setImageDrawable(mRecordImage);
//                        if (sMicrophone.getSampleRate() > 0 && !sMicrophone.getProductName().isEmpty()) Toast.makeText(MainActivity.this, sMicrophone.getProductName(), Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    else
                        UsbLogger.writeToLog("USB device initialization failed.");
                    break;
                }
            }
            else {
                UsbLogger.writeToLog("USB device unsupported id="+device.getDeviceId());
            }
        }
        if (mSplashView.getVisibility() == View.INVISIBLE)
            showDisconnectAlert();
        else {
            mInitStatusView.setText(R.string.no_mic);
            findViewById(R.id.quit).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    CloseApp();
                }
            });
            findViewById(R.id.playback_only).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sMicrophone = createMicrophone();
                    if (!isUltrasonic()) {
                        sHeadsetMode = PLAY_MODE_FREQUENCY_CUTOFF;
                        updateHeadsetMode();
                    }
                    mSplashView.setVisibility(View.INVISIBLE);
                    adjustSystemToolBars();
                }
            });

            findViewById(R.id.init_control_layout).setVisibility(View.VISIBLE);
        }
        return true;
    }

    private Microphone createMicrophone() {
        return new DeviceMicrophone(mValidSampleRates, Build.MANUFACTURER, Build.MODEL);    // Always record at highest available rate
    }

    private Microphone createMicrophone(UsbDeviceConnection connection, UsbDevice device) {
        UsbMicrophone microphone = UsbMicrophone.createMicrophone(this, connection, device);
        if (microphone != null) {
            String prefTag = String.format("%08X%08X", microphone.getVendorId(), microphone.getProductId());
            int desired_sampleRate = mPreferences.getInt(prefTag, 0);
            if (desired_sampleRate > 0) microphone.setSampleRate(desired_sampleRate);
        }
        else
            UsbLogger.writeToLog("createMicrophone returned null");
        return microphone;
    }

    private void ConnectUSB(UsbDeviceConnection connection, UsbDevice device) {

        synchronized (this) {
            sMicrophone = createMicrophone(connection, device);
            if (sMicrophone != null) {
                mUSBInitialized = ((UsbMicrophone) sMicrophone).init();
                if (mUSBInitialized == 0) {
                    UsbLogger.writeToLog("USB microphone initialization failed.");
                    UsbLogger.writeToLog(UsbConfigurationParser.GetLogMessage());
                }
                if (!isUltrasonic()) {
                    sHeadsetMode = PLAY_MODE_FREQUENCY_CUTOFF;
                    updateHeadsetMode();
                }
            }
            else
                Toast.makeText(MainActivity.this, R.string.unsupported, Toast.LENGTH_SHORT).show();
        }
    }

    private void DisconnectUSB() {

        synchronized (this) {
            if (IsUSBInitialized()) {
                ((UsbMicrophone) sMicrophone).exitLoop(true);
                mUSBInitialized = 0;
                stopListening();
                ((UsbMicrophone) sMicrophone).cleanup();
//                CleanupAudioBuffers();
                sMicrophone = createMicrophone();
                if (!isUltrasonic()) {
                    sHeadsetMode = PLAY_MODE_FREQUENCY_CUTOFF;
                    updateHeadsetMode();
                }
            }
        }
    }

    @Override
    public void onBackPressed() {

        new AlertDialog.Builder(MainActivity.this, R.style.CustomDialog)
                .setMessage(R.string.close_app)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        CloseApp();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void cleanup() {

        if (mUsbReceiver != null) unregisterReceiver(mUsbReceiver);
        UsbLogger.closeLog();

        cleanupTimer();

        DisconnectUSB();
        cleanupFFT();
        cleanupInverseFFT();

        deleteCacheFile();

//        if (mChecker != null) mChecker.onDestroy();
    }

    private void CloseApp() {
        this.finish();
        cleanup();
        System.exit(0);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cleanup();
    }

    public LocationData getLocationData() {
        LocationData data = null;
        synchronized (locLock) {
            if (mLocation != null) {
                data = new LocationData();
                data.timestamp = mLocation.getTime();
                data.latitude = mLocation.getLatitude();
                data.longitude = mLocation.getLongitude();
                data.elevation = (mLocation.hasAltitude()) ? mLocation.getAltitude() : LocationData.INVALID_ELEVATION;
            }
        }
        return data;
    }

    public SensorData getSensorData() {
        return mSensorData;
    }

    private final LocationListener locationListener = new LocationListener() {

        public void onLocationChanged(Location location) {
            synchronized (locLock) {
                mLocation = location;
                if (mWayPointFile != null) {
                    writeWaypoint(location.getTime(),
                                location.getLatitude(),
                                location.getLongitude(),
                                (location.hasAltitude()) ? location.getAltitude() : LocationData.INVALID_ELEVATION);
//                    try {
//                        DataOutputStream out = new DataOutputStream(new FileOutputStream(mWayPointFile, true));
//                        out.writeLong(location.getTime());
//                        out.writeDouble(location.getLatitude());
//                        out.writeDouble(location.getLongitude());
//                        if (location.hasAltitude())
//                            out.writeDouble(location.getAltitude());
//                        else
//                            out.writeDouble((double) LocationData.INVALID_ELEVATION);
//                        out.close();
//                    } catch (Exception e) {
//                    }
                }
            }
        }

        public void onProviderDisabled(String provider) {
            synchronized (locLock) {
                mLocation = null;
            }
        }

        public void onProviderEnabled(String provider) {
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
        }
    };

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) adjustSystemToolBars();
    }

    private void adjustSystemToolBars() {
        if (mPreferences.getBoolean("statusbar", false) && (mSplashView.getVisibility() == View.INVISIBLE)) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().getDecorView().setSystemUiVisibility(0);
        }
        else {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                            | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    private void adjustPowerPopupPosition(float[] powerRatios) {

        View mainView = findViewById(R.id.main_layout);
        int width = mainView.getWidth();
        int height = mainView.getHeight();

        int posX = (int) (powerRatios[0] * (float) width);
        int posY = (int) (powerRatios[1] * (float) height);
        int powWidth = (int) (powerRatios[2] * (float) width);
        int powHeight = (int) (powerRatios[3] * (float) height);

        ViewGroup.LayoutParams params = mPowerOverlay.getLayoutParams();
        params.width = powWidth;
        params.height = powHeight;
        mPowerOverlay.setLayoutParams(params);

        params = mPowerTickOverlay.getLayoutParams();
        params.width = powWidth;
        mPowerTickOverlay.setLayoutParams(params);
        mPowerTickOverlay.rebuildBitmap();

        mPowerPopup.setX(posX);
        mPowerPopup.setY(posY);

        mPreferences.edit().putInt("pow_x", posX).apply();
        mPreferences.edit().putInt("pow_y", posY).apply();
        mPreferences.edit().putInt("pow_width", powWidth).apply();
        mPreferences.edit().putInt("pow_height", powHeight).apply();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {

        super.onConfigurationChanged(newConfig);

        View mainView = findViewById(R.id.main_layout);
        int displayWidth = mainView.getWidth();
        int displayHeight = mainView.getHeight();

        final float[] powerRatios = new float[4];
        powerRatios[0] = (float) mPreferences.getInt("pow_x", 20) / (float) displayWidth;
        powerRatios[1] = (float) mPreferences.getInt("pow_y", 20) / (float) displayHeight;
        powerRatios[2] = (float) mPreferences.getInt("pow_width", 512) / (float) displayWidth;
        powerRatios[3] = (float) mPreferences.getInt("pow_height", 256) / (float) displayHeight;

        final View view = findViewById(R.id.hsv);
        final float viewRatio = (float) mWaveformView.getHeight() / (float) (view.getHeight() - mTimeTickView.getHeight());

        ViewTreeObserver observer = view.getViewTreeObserver();
        observer.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {

                int width = view.getWidth();
                int height = view.getHeight() - mTimeTickView.getHeight();

                int waveHeight = (int) (viewRatio * (float) height);
                int specHeight = height - waveHeight;
                mPreferences.edit().putInt("waveheight", waveHeight).apply();

                ViewGroup.LayoutParams lp;
                lp = mWaveformView.getLayoutParams();
                lp.width = width;
                lp.height = waveHeight;
                mWaveformView.setLayoutParams(lp);

                lp = mSpectrogramView.getLayoutParams();
                lp.width = width;
                lp.height = specHeight;
                mSpectrogramView.setLayoutParams(lp);

                lp = mTimeTickView.getLayoutParams();
                lp.width = width;
                mTimeTickView.setLayoutParams(lp);

                View cntrlView = findViewById(R.id.waveform_control_layout);
                lp = cntrlView.getLayoutParams();
                lp.height = waveHeight;
                cntrlView.setLayoutParams(lp);

                lp = mFreqTickView.getLayoutParams();
                lp.height = specHeight;
                mFreqTickView.setLayoutParams(lp);
                mFreqTickView.rebuildBitmap();

                view.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                adjustPowerPopupPosition(powerRatios);
            }
        });
    }

    @Override
    public void onResume() {

        super.onResume();

//        adjustSystemToolBars();

        if (mPreferences.getBoolean("nightMode", false) != sNightMode) {
            sNightMode = mPreferences.getBoolean("nightMode", false);
            setNightMode();
        }

        if (!isUltrasonic() && !mPreferences.getBoolean("ultramodes", false)) {
            if (sDataMode == DATA_MODE_PLAY) {
                sPlayMode = PLAY_MODE_FREQUENCY_CUTOFF;
                updatePlayMode();
            }
            else {
                sHeadsetMode = PLAY_MODE_FREQUENCY_CUTOFF;
                updateHeadsetMode();
            }
        }

        boolean logscale = mPreferences.getBoolean("logscale", false);
        if (logscale != mFreqTickView.getLogScale()) {
            mFreqTickView.setLogScale(logscale);
            mSpectrogramView.setLogScale(logscale);
            mFreqTickView.invalidate();
            mSpectrogramView.invalidate();
        }
        boolean gridlines = mPreferences.getBoolean("gridlines", false);
        if (gridlines != mSpectrogramView.showingGridLines()) {
            mSpectrogramView.showGridLines(gridlines);
            mSpectrogramView.invalidate();
        }

        File activeRecording = FileAccessManager.getActiveRecording();
        int fftSize = Integer.parseInt(mPreferences.getString("fft", "1024"));
        if (initFFT(fftSize, mPreferences.getFloat("gain", 1.0f)) != 0) {
            if (sDataMode == DATA_MODE_PLAY) {
                if (activeRecording != null) new AudioFileReadTask(this, false).execute(activeRecording);
            } else {
                mWaveformView.reset();
                resetCache();
            }
            if (isPowerPopupVisible()) {
                PowerView powerView = getPowerView();
                powerView.setSampleSize(fftSize / 2);
                powerView.invalidate();
            }
            mWaveformView.invalidate();
            mSpectrogramView.invalidate();
        }

        int minTuningFrequency = mPreferences.getInt("hetfreq_min", 15) * 1000;
        int maxTuningFrequency = mPreferences.getInt("hetfreq_max", 200) * 1000;
        if (mFreqTickView.isAutoTune()) {
            if (mFreqTickView.getTuneFrequency() < minTuningFrequency)
                mFreqTickView.setTuneFrequency(minTuningFrequency);
            else if (mFreqTickView.getTuneFrequency() > maxTuningFrequency)
                mFreqTickView.setTuneFrequency(maxTuningFrequency);
            else if ((sDataMode == DATA_MODE_PLAY) && (activeRecording != null)) {
                // Need to reload and analyze if new thresholds outside previous range
                if ((minTuningFrequency != mMinTuningFreq) || (maxTuningFrequency != mMaxTuningFreq)) {
                    new AudioFileReadTask(this, false).execute(activeRecording);
                }
            }
        }
        mMinTuningFreq = minTuningFrequency;
        mMaxTuningFreq = maxTuningFrequency;

        initLocationServices();
        if (mSensorManager != null) {
            if (mLightSensor != null) mSensorManager.registerListener(this, mLightSensor, SensorManager.SENSOR_DELAY_UI);
            if (mTemperatureSensor != null) mSensorManager.registerListener(this, mTemperatureSensor, SensorManager.SENSOR_DELAY_UI);
            if (mPressureSensor != null) mSensorManager.registerListener(this, mPressureSensor, SensorManager.SENSOR_DELAY_UI);
            if (mRelHumiditySensor != null) mSensorManager.registerListener(this, mRelHumiditySensor, SensorManager.SENSOR_DELAY_UI);
            if (mShakeSensor != null) mSensorManager.registerListener(this, mShakeSensor, SensorManager.SENSOR_DELAY_UI);
        }

//        if ((mSplashView.getVisibility() == View.INVISIBLE) && mPreferences.getBoolean("statusbar", true))
//            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
//        else
//            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    @Override
    protected void onPause() {
        // Be sure to unregister the sensor when the activity pauses.
        super.onPause();
        if (mSensorManager != null) mSensorManager.unregisterListener(this);
    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something here if sensor accuracy changes.
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {
        if (event.sensor == mLightSensor) {
            mSensorData.illuminance = event.values[0];
//            Log.d(TAG, "illuminance = " + mSensorData.illuminance);
        }
        else if (event.sensor == mTemperatureSensor) {
            mSensorData.temperature = event.values[0];
//           Log.d(TAG, "temperature = " + mSensorData.temperature);
        }
        else if (event.sensor == mPressureSensor) {
            mSensorData.pressure = event.values[0];
//            Log.d(TAG, "pressure = " + mSensorData.pressure);
        }
        else if (event.sensor == mRelHumiditySensor) {
            mSensorData.humidity = event.values[0];
//            Log.d(TAG, "rel humidity = " + mSensorData.humidity);
        }
        else if ((event.sensor == mShakeSensor) && (mPreferences.getBoolean("shakerec", false))) {
            long now = System.currentTimeMillis();
            if ((now - mLastForce) > SHAKE_TIMEOUT) {
                mShakeCount = 0;
            }
            if ((now - mLastTime) > TIME_THRESHOLD) {
                long diff = now - mLastTime;
                float speed = Math.abs(event.values[0] + event.values[1] + event.values[2] - mLastX - mLastY - mLastZ) / diff * 10000;
                if (speed > FORCE_THRESHOLD) {
                    if ((++mShakeCount >= SHAKE_COUNT) && (now - mLastShake > SHAKE_DURATION)) {
                        mLastShake = now;
                        mShakeCount = 0;
                        Log.d(TAG, "shake: " + speed);
                        if (sDataMode == DATA_MODE_RECORD) {
                            if (recording) {
                                stopListening();
                            } else {
                                doRecord(true);
                            }
                        }
                    }
                    mLastForce = now;
                }
                mLastTime = now;
                mLastX = event.values[0];
                mLastY = event.values[1];
                mLastZ = event.values[2];
            }
        }
    }

    public void handleRecordingDiscarded() {

        if (FileAccessManager.getActiveRecording() != null) {

            setRecordMode();

            mMicView.setImageDrawable(mMicOffImage);

            int width = mScrollView.getWidth();
            ViewGroup.LayoutParams lp = mTimeTickView.getLayoutParams();
            lp.width = width;
            mTimeTickView.setLayoutParams(lp);

//	           	width = Math.max((int) ((float) mSpectrogramView.getFFTHeight() * mZoomFactor), width);
            lp = mWaveformView.getLayoutParams();
            lp.width = width;
            mWaveformView.setLayoutParams(lp);

            lp = mSpectrogramView.getLayoutParams();
            lp.width = width;
            mSpectrogramView.setLayoutParams(lp);

            lp = mTimeTickView.getLayoutParams();
            lp.width = width;
            mTimeTickView.setLayoutParams(lp);

            mScrollView.setScrollX(0);

            int fftSize = Integer.parseInt(mPreferences.getString("fft", "1024"));

            float maxFreq = (float) (sMicrophone.getSampleRate() / 2);
            if (mFreqTickView.getMaxFreq() != maxFreq) {

                mFreqTickView.setMaxFreq(maxFreq);
                mFreqTickView.setOffset(0);
                mFreqTickView.setZoomLevel(1.0f);

                mSpectrogramView.setYOffset(0);
                mSpectrogramView.setScaleY(1.0f);
            }

            float secsPerSample = (float) fftSize / (float) (MainActivity.WINDOW_FACTOR * sMicrophone.getSampleRate());
            mTimeTickView.setSecondsPerSample(secsPerSample);
            int windowWidth = mScrollView.getWidth();
            float zoomX = secsPerSample * (float) windowWidth / getZoomTime();
            setZoomX(zoomX);
            int maxTimespan = (int) ((float) windowWidth / zoomX + 0.5f);
            setRecordingLength(secsPerSample * (float) maxTimespan);
            mSpectrogramView.setFFTDimension(maxTimespan, fftSize, sMicrophone.getSampleRate() / 2, secsPerSample);
            mWaveformView.setNumSamples(maxTimespan);

            mWaveformView.reset();
            resetCache();

            mWaveformView.invalidate();
            mSpectrogramView.invalidate();
            mFreqTickView.invalidate();
            mTimeTickView.invalidate();
        }
    }

    public boolean reopenRecording() {
        if ((sDataMode == DATA_MODE_PLAY) && (FileAccessManager.getActiveRecording() != null)) {
            new AudioFileReadTask(this, false).execute(FileAccessManager.getActiveRecording());
            return true;
        }
        return false;
    }

    private Boolean isValidCode() {
        return true;
    }

    private static String insertPeriodSpaces(String text)
    {
        StringBuilder builder = new StringBuilder(
                text.length() + (text.length()/4)+1);

        int index = 0;
        String prefix = "";
        while (index < text.length())
        {
            // Don't put the insert in the very first iteration.
            // This is easier than appending it *after* each substring
            builder.append(prefix);
            prefix = " ";
            builder.append(text.substring(index,
                    Math.min(index + 4, text.length())));
            index += 4;
        }
        return builder.toString();
    }

    private void checkCustomLicense() {

//        if (isValidCode()) {
//            finishInitialization();
//            return;
//        }

        // If we reach here, the license is either invalid or doesn't exist. So ask user to input valid license code...
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this, R.style.CustomDialog);
        builder.setCancelable(true);

        LayoutInflater inflater = getLayoutInflater();

        View convertView = inflater.inflate(R.layout.custom_code_dialog, null);
        builder.setView(convertView);

        final Dialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));

//        String deviceID = MainActivity.insertPeriodSpaces(Secure.getString(getContentResolver(), Secure.ANDROID_ID).toUpperCase());
//        ((TextView) convertView.findViewById(R.id.android_id)).setText(deviceID);
        final EditText editField = (EditText) convertView.findViewById(R.id.code);
        editField.setText("");

        editField.addTextChangedListener(new TextWatcher() {

            public void afterTextChanged(Editable s) {
                Button okButton = (Button) dialog.findViewById(R.id.codeRequestLicense);
                if (editField.getText().toString().isEmpty())
                    okButton.setText(getResources().getString(R.string.requestLicense));
                else
                    okButton.setText(getResources().getString(android.R.string.ok));
            }

            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start,
                                      int before, int count) {
            }
        });

        convertView.findViewById(R.id.codeCancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        convertView.findViewById(R.id.codeRequestLicense).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
//                    CardEntry.startCardEntryActivity(CheckoutActivity.this, true, DEFAULT_CARD_ENTRY_REQUEST_CODE);

                    String code = editField.getText().toString();
                    if (code.isEmpty()) {
                        String deviceID = MainActivity.insertPeriodSpaces(Secure.getString(getContentResolver(), Secure.ANDROID_ID).toUpperCase());
//                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://buy.stripe.com/aEUg1Yc1ubLI848bII"));
//                    startActivity(browserIntent);

                        Intent mailIntent = new Intent(Intent.ACTION_SENDTO);
                        mailIntent.setData(Uri.parse("mailto:")); // only email apps should handle this
                        String[] addresses = {"batrecorder@digitalbiology.com"};
                        mailIntent.putExtra(Intent.EXTRA_EMAIL, addresses);
                        mailIntent.putExtra(Intent.EXTRA_SUBJECT, "Bat Recorder License");
                        mailIntent.putExtra(Intent.EXTRA_TEXT, getResources().getString(R.string.license_msg1) + deviceID + getResources().getString(R.string.license_msg2));
                        startActivity(mailIntent);
                    }
                    else {
                        mPreferences.edit().putString("ccode", code).apply();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
//                if (isValidCode()) {
                    dialog.dismiss();
//                }
            }
        });

        dialog.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    public void openWriteCacheFile(int fftSize) {
        openWriteCache(getFilesDir() + File.separator + sCacheFileName, fftSize / 2, mPreferences.getString("refresh", "0").equals("0"));
 //       openWriteCache(FileAccessManager.getStorageDirectoryPath() + sCacheFileName, fftSize / 2, mPreferences.getString("refresh", "0").equals("0"));
    }

    public static native int ReadFrameFromAudioBuffer(int bufIndex, short[] data, int dataLength, int window);

    private static native void createTestToneFile(String path, int sampleRate, int frequency);

    public static native void resetCache();

    private static native void openWriteCache(String path, int len, boolean refresh);

    public static native void closeWriteCache();

//    private native float GetUSBSampleRate(int interfaceID, int altSetting, int endpointAddress, int packetSize);

    private static native int HasNEON();

    private static native int initFFT(int fftLen, float gain);

    private static native void cleanupFFT();

    private static native void initInverseFFT(int fftLen);

    private static native int cleanupInverseFFT();

    private static native void setGain(float gain);

    private static native float getGain();

    public static native void StartRecord(String path, int sampleRate, int advance);

    public static native int StopRecord();

    public static native void AppendMetadata(String path, int namespace, byte[] metadata);

    public static native short analyzeSpectrum(short[] data, long[] params);

    public static native int readWAVHeader(String path, long[] params);

    public static native int readWAVData(String path, short[] data, int dataOffset, int dataLength, int fileOffset, short channels, short whichChannel);

    private static native void copyWAVSnippet(String inFile, String outFile, int from, int len, short channel);

    private static native void openWaypoint(String outFile);
    private static native void writeWaypoint(long timestamp, double lat, double lon, double ele);
    private static native void closeWaypoint();

    static {
        System.loadLibrary("usb");
        System.loadLibrary("file");
        if (HasNEON() == 1)
            System.loadLibrary("analysisNEON");
        else
            System.loadLibrary("analysis");
    }

    @SuppressLint("MissingPermission")
    private static boolean validSampleRate(int sample_rate) {
        AudioRecord recorder = null;
        try {
            int bufferSize = AudioRecord.getMinBufferSize(sample_rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sample_rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        } catch (IllegalArgumentException e) {
            return false; // cannot sample at this rate
        } finally {
            if (recorder != null) recorder.release(); // release resources to prevent a memory leak
        }
        return true; // if nothing has been returned yet, then we must be able to sample at this rate!
    }

    private boolean isUltrasonic() {
        if (mValidSampleRates.size() == 0) return (mActiveSampleRate > 48000);
        if (sDataMode == DATA_MODE_RECORD) {
            return ((sMicrophone != null) && (sMicrophone.getSampleRate() > mValidSampleRates.get(mValidSampleRates.size()-1)));
        }
        else {
            return (mActiveSampleRate > mValidSampleRates.get(mValidSampleRates.size()-1));
         }
     }

    private void checkValidSampleRates() {

        final int sample_rates[] = new int[]{8000, 11025, 16000, 22050, 32000, 37800, 44056, 44100, 48000};
//        final int sample_rates[] = new int[]{48000, 44100, 44056, 37800, 32000, 22050, 16000, 11025, 8000};
        for (int sample_rate : sample_rates) {
            if (validSampleRate(sample_rate)) {
                mValidSampleRates.add(sample_rate);
            }
        }
    }

    public ArrayList<Integer> getValidSampleRates() {
        return mValidSampleRates;
    }

    public int getPlayStart() {
        return mPlayStart;
    }

    public int getPlayEnd() {
        return mPlayEnd;
    }

    public int getZoomedPlayStart() {
        return (int) ((float) mPlayStart * mZoomFactorX);
    }

    public int getZoomedPlayEnd() {
        return (int) ((float) mPlayEnd * mZoomFactorX);
    }

    public void setPlayStart(int start) {
        mPlayStart = (int) ((float) start / mZoomFactorX);
        mPlayStart = Math.max(0, Math.min(mPlayStart, mSpectrogramView.getFFTHeight()));
        mPlayEnd = -1;
        mPlayAnchor = mPlayStart;
        mWaveformView.setSelRange(mPlayStart, mPlayStart);
        mWaveformView.setPlayhead(mPlayStart);
//        mWaveformView.setListenhead(mPlayStart);
        mWaveformView.invalidate();
        if (isPowerPopupVisible()) {
            PowerView powerView = getPowerView();
            PowerView.openReadCache();
            powerView.updateDataFromCacheFile(mPlayStart * Integer.parseInt(mPreferences.getString("fft", "1024")) * 2 / WINDOW_FACTOR, true);
            PowerView.closeReadCache();
            powerView.postInvalidate();
        }
    }

    public void setPlayEnd(int end) {
        int val = (int) ((float) end / mZoomFactorX);
        val = Math.max(0, Math.min(val, mSpectrogramView.getFFTHeight()));
        if (val >= mPlayAnchor) {
            mPlayStart = mPlayAnchor;
            mPlayEnd = val;
        } else {
            mPlayStart = val;
            mPlayEnd = mPlayAnchor;
        }
        mWaveformView.setSelRange(mPlayStart, mPlayEnd);
        mWaveformView.invalidate();
    }

    public void snipRecording() {

        if (FileAccessManager.getActiveRecording() == null) return;

        final EditText nameText = new EditText(this);
        InputFilter filter = new InputFilter() {
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                if (source.length() < 1) return null;
                char last = source.charAt(source.length() - 1);
                String reservedChars = "?:\"*|/\\<> ";
                if (reservedChars.indexOf(last) > -1)
                    return source.subSequence(0, source.length() - 1);
                return null;
            }
        };
        nameText.setFilters(new InputFilter[]{filter});
        // Increment name
        File activeRecording = FileAccessManager.getActiveRecording();
        String clipName = activeRecording.getName().substring(0, activeRecording.getName().toLowerCase().indexOf(".wav"));
        String absolutePath = activeRecording.getAbsolutePath();
        final String filePath = absolutePath.substring(0,absolutePath.lastIndexOf(File.separator));
        File file;
        int increment = -1;
        do {
            increment++;
            file = new File(filePath, clipName + "_" + increment + ".wav");
        } while (file.exists());

        nameText.setText(file.getName().substring(0, file.getName().toLowerCase().indexOf(".wav")));
        nameText.setTextColor(Color.parseColor("#33b5e5"));

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this, R.style.CustomDialog)
                .setTitle(getString(R.string.export))
                .setView(nameText)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null);

        final AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                Button b = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

                        String filename = nameText.getText().toString();
                        if (!filename.isEmpty()) {
                            File snippetFile = new File(filePath, filename + ".wav");
                            if (!snippetFile.exists()) {

                                int fftSize = Integer.parseInt(mPreferences.getString("fft", "1024"));
                                int start = mPlayStart * fftSize / WINDOW_FACTOR;
                                int end = mPlayEnd * fftSize / WINDOW_FACTOR;
                                copyWAVSnippet(FileAccessManager.getActiveRecording().getAbsolutePath(), snippetFile.getAbsolutePath(), start, (int) (end - start), mStereoChannel);

                                MetaData metadata = MetaDataParser.getMetadata(snippetFile);
                                if (metadata.timestamp != null) {
                                    long[] params = new long[4];
                                    MainActivity.readWAVHeader(snippetFile.getAbsolutePath(), params);
                                    metadata.length = (float) (1000L * (end - start)) / (float) params[1];
                                    long millsecs = metadata.timestamp.getTime() + (1000L * start) / params[1];
                                    metadata.timestamp = new Date(millsecs);
                                    if (metadata.namespace == MetaData.XMP_NAMESPACE)
                                        new XMPMetaDataParser().update(snippetFile, metadata);
                                    else if (metadata.namespace == MetaData.WAMD_NAMESPACE)
                                        new WAMDMetaDataParser().update(snippetFile, metadata);
                                    else
                                        new GUANOMetaDataParser().update(snippetFile, metadata);
                                }
                                Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                intent.setData(FileAccessManager.getFileUri(MainActivity.this, snippetFile));
//                                intent.setData(Uri.fromFile(snippetFile));
                                sendBroadcast(intent);

                                alertDialog.dismiss();
                                Toast.makeText(MainActivity.this, R.string.saved, Toast.LENGTH_SHORT).show();
                            } else {
                                // File already exists
                                Toast.makeText(MainActivity.this, R.string.already_exists, Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(MainActivity.this, R.string.empty_name, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
        alertDialog.show();
    }

     public void onVerticalScroll(float distance) {

        mSpectrogramView.offsetYBy(distance);
        mFreqTickView.offsetBy(distance);

        mSpectrogramView.invalidate();
        mFreqTickView.invalidate();
    }

    public void onVerticalScale(float scaleFactor, float focusY) {

//        if (listening) return;

        mZoomFactorY *= scaleFactor;
        mZoomFactorY = Math.max(MIN_ZOOM_Y, Math.min(mZoomFactorY, MAX_ZOOM_Y));

        mSpectrogramView.setScaleY(mZoomFactorY);
        mFreqTickView.setZoomLevel(mZoomFactorY);

        mSpectrogramView.invalidate();
        mFreqTickView.invalidate();

//        mPreferences.edit().putFloat("zoomY", mZoomFactorY).apply();
    }

    public void onHorizontalScale(float scaleFactor, float focusX) {

        if (listening) return;

        float oldScrollX = ((float) mScrollView.getScrollX() + focusX) / mZoomFactorX;

//		mZoomFactorX *= scaleFactor;
        // Don't let the object get too small or too large.
//        mZoomFactorX = Math.max(MIN_ZOOM, Math.min(mZoomFactorX, MAX_ZOOM));
        mZoomTime /= scaleFactor;
        mZoomTime = Math.max(MIN_ZOOM_TIME, Math.min(mZoomTime, MAX_ZOOM_TIME));
        mZoomFactorX = mTimeTickView.getSecondsPerSample() * (float) mScrollView.getWidth() / mZoomTime;

//		mZoomFactorX = mZoomTime / DEFAULT_ZOOM_TIME;
//		mZoomFactorX = Math.max(MIN_ZOOM, Math.min(mZoomFactorX, MAX_ZOOM));

        float newScrollX = oldScrollX * mZoomFactorX - focusX;

        int width = mScrollView.getWidth();
        if ((sDataMode == DATA_MODE_PLAY) && (mSpectrogramView.getFFTHeight() > 0)) {
            width = (int) ((float) width * mRecordingLength / mZoomTime);
            if (width < mScrollView.getWidth()) width = mScrollView.getWidth();
        }

        LayoutParams lp = mSpectrogramView.getLayoutParams();
        lp.width = width;
        mSpectrogramView.setLayoutParams(lp);
        mSpectrogramView.setZoomX(mZoomFactorX);

        lp = mWaveformView.getLayoutParams();
        lp.width = width;
        mWaveformView.setLayoutParams(lp);
        mWaveformView.setZoomX(mZoomFactorX);

        mTimeTickView.setZoomLevel(mZoomFactorX);
        lp = mTimeTickView.getLayoutParams();
        lp.width = width;
        mTimeTickView.setLayoutParams(lp);

        if (sDataMode == DATA_MODE_PLAY) {
            mScrollView.scrollTo((int) newScrollX, 0);
            mScrollSeekBar.setProgress((int) (newScrollX / mZoomFactorX));
        }
        else {
            mScrollSeekBar.setProgress(0);
            mScrollView.scrollTo(0, 0);
        }

        mSpectrogramView.invalidate();
        mWaveformView.invalidate();
        mTimeTickView.invalidate();

        mPreferences.edit().putFloat("zoomX", mZoomTime).apply();
    }

    private final Handler mTimerHandler = new Handler();

    private void setListenTimer() {

        mTriggerView.setBackgroundColor(sNightMode ? Color.RED : Color.WHITE);

        LayoutInflater layoutInflater = (LayoutInflater) getBaseContext().getSystemService(LAYOUT_INFLATER_SERVICE);
        View popupView = layoutInflater.inflate(R.layout.popup_timer, null);
        final PopupWindow popupWindow = new PopupWindow(popupView, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
//            popupWindow.setBackgroundDrawable(getResources().getDrawable(R.drawable.translucent_background));
        popupWindow.setOutsideTouchable(false);
        popupWindow.setFocusable(false);
        popupView.findViewById(R.id.alarm_quit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                cleanupTimer();
                stopListening();

                popupWindow.dismiss();
                mTriggerView.setBackgroundColor(Color.BLACK);
            }
        });
        popupWindow.showAtLocation(mScrollView, Gravity.CENTER, 0, 0);
        timerOn();
    }

    protected void onNewIntent(Intent incomingIntent) {
        // We were woken up by the alarm manager, but were already running
        if (incomingIntent.getBooleanExtra("record_wakeup", false)) {
//            Log.d(TAG, "timer on");
            timerOn();
        }
    }

    private void cleanupTimer() {

        mTimerHandler.removeCallbacksAndMessages(null);

        Intent intent = new Intent(MainActivity.this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(MainActivity.this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        AlarmManager mgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        mgr.cancel(pi);
        pi.cancel();
    }

     private void timerOn() {

        long recordTime = 60000L * (long) mPreferences.getInt("timer_on", 2);
         mTimerHandler.postDelayed(new Runnable() {
             public void run() {
                 timerOff();
            }}, recordTime);

        if (!listening) startListening();
        trigger_set = true;
    }

    private void timerOff() {

        stopListening();

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("record_wakeup", true);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager mgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        mgr.cancel(pi);    // Cancel any previously-scheduled wakeups

        long sleepTime = SystemClock.elapsedRealtime() + 60000L * (long) mPreferences.getInt("timer_off", 10);
        mgr.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, sleepTime, pi);
    }

//    private void doLicenseCheck() {
//        mChecker.checkAccess(mLicenseCheckerCallback);
//    }

//    private class MyLicenseCheckerCallback implements LicenseCheckerCallback {
//
//        @Override
//        public void allow(int reason) {
//
//            if (isFinishing()) return;    // Don't update UI if Activity is finishing.
//            //You can do other things here, like saving the licensed status to a
//            //SharedPreference so the app only has to check the license once.
//            mLicenseCode = reason;
//            mDidCheckLicense = true;
//        }
//
//        @Override
//        public void dontAllow(int reason) {
//
//            if (isFinishing()) return;    // Don't update UI if Activity is finishing.
//            UsbLogger.writeToLog("License denial: err=" + reason);
//
//            mLicenseCode = reason;
//            mDidCheckLicense = true;
//        }
//
//        @Override
//        public void applicationError(int reason) {
//
//            if (isFinishing()) return;    // Don't update UI if Activity is finishing.
//            UsbLogger.writeToLog("License denial: err=" + reason);
//
//            mLicenseCode = reason;
//            mDidCheckLicense = true;
//        }
//    }

    private class StatusRunnable implements Runnable {
        private final String msg;

        public StatusRunnable(String txt) {
            this.msg = txt;
        }

        @Override
        public void run() {
            mInitStatusView.setText(msg);
        }
    }
}