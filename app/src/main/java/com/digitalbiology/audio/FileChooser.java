package com.digitalbiology.audio;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.os.Environment;
import androidx.annotation.NonNull;
//import androidx.core.content.FileProvider;
import android.text.InputFilter;
import android.text.Spanned;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager.LayoutParams;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.digitalbiology.audio.metadata.GUANOMetaDataParser;
import com.digitalbiology.audio.metadata.LocationData;
import com.digitalbiology.audio.metadata.MetaData;
import com.digitalbiology.audio.metadata.MetaDataParser;
import com.digitalbiology.audio.metadata.WAMDMetaDataParser;
import com.digitalbiology.audio.metadata.XMPMetaDataParser;
import com.digitalbiology.audio.utils.TimeAxisFormat;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
//import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

public class FileChooser implements OnMapReadyCallback {

    private static final DecimalFormat sFileSizeFormatter;
    private static final SimpleDateFormat sOutDateFormatter;
//    private static final DecimalFormat sTimeFormatter;
    private static final TimeAxisFormat sTimeFormatter;


    private final MainActivity activity;
    private final Dialog dialog;
    static private File sCurrentPath = null;
    static private WavFileDescriptor sLastFileOpened = null;
    private int selectedPos = -1;
    private int lastTouched = -1;
    private ArrayList<WavFileDescriptor> fileList;
    private ArrayList<WavFileDescriptor> transectList;

    private final ImageView mListMode;
    private final ImageView mUseSDCard;
    private final ListView mListView;
    private final MapView mMapView;

    private final ImageView mExportCSV;
    private final ImageView mExportGPX;

    private GoogleMap mMap;
    private Marker mSelectedMarker;

    private ImageView mSortOrderView;

    private final ImageView mFilenameView;
    private final ImageView mCreateDateView;
    private final ImageView mRecLengthView;
    private final ImageView mDataLengthView;
    private final ImageView mPlaceView;
    private final ImageView mDeviceView;
    private final ImageView mSpeciesView;
    private final View mSortControls;

    private static final short UNSORTED = 0;
    private static final short SORT_FILENAME = 1;
    private static final short SORT_DATE = 2;
    private static final short SORT_RECORD_LENGTH = 3;
    private static final short SORT_DATA_LENGTH = 4;
    private static final short SORT_LOCATION = 5;
    private static final short SORT_DEVICE = 6;
    private static final short SORT_SPECIES = 7;

    private static final short SORT_ORDER_DOWN = 1;
    private static final short SORT_ORDER_UP = 2;

    private short mSortKey;
    private short mSortOrder;

    private boolean useSDCard = false;

    private static final DecimalFormat sCoordFormatter = new DecimalFormat("#0.0000000", new DecimalFormatSymbols(Locale.ENGLISH));
    private static final DecimalFormat sAltFormatter = new DecimalFormat("#0.0", new DecimalFormatSymbols(Locale.ENGLISH));

    // filter on file extension
    private static String sExtension = null;

    public void setExtension(String ext) {
        sExtension = (ext == null) ? null : ext.toLowerCase();
    }

    public static File getCurrentPath() {
        return sCurrentPath;
    }

    // file selection event handling
    public interface FileSelectedListener {
        void fileSelected(File file);
    }

    public FileChooser setFileListener(FileSelectedListener fileListener) {
        this.fileListener = fileListener;
        return this;
    }

    public ArrayList<File> getFileList() {
        ArrayList<File> files = new ArrayList<>();
        for (WavFileDescriptor desc : fileList) {
            if (!desc.isDirectory) {
                files.add(new File(sCurrentPath, desc.fileName));
            }
        }
        return files;
    }

    private FileSelectedListener fileListener;

    public FileChooser(final MainActivity activity) {

        this.activity = activity;

        final GestureDetector gestureDetector = new GestureDetector(activity, new GestureListener());

        View view = activity.getLayoutInflater().inflate(R.layout.file_dialog, null);

        mListView = (ListView) view.findViewById(R.id.file_list);
        mListMode = (ImageView) view.findViewById(R.id.list_mode);

        mUseSDCard = (ImageView) view.findViewById(R.id.sd_card);
        if (FileAccessManager.getExternalStorageDirectory() != null) {
            mUseSDCard.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!useSDCard && activity.getPreferences().getBoolean("sdcard_alert", true)) {
                        showSDCardAlert();
                        return;
                    }
                    useSDCard = !useSDCard;
                    if (useSDCard) {
//                        mUseSDCard.setBackgroundResource(activity.getNightMode() ? R.drawable.night_icon_border : R.drawable.icon_border);
                         mUseSDCard.setBackgroundResource(R.drawable.icon_border);
                    }
                    else
                        mUseSDCard.setBackgroundResource(0);
                    activity.getPreferences().edit().putBoolean("storage", useSDCard).apply();
                    sCurrentPath = FileAccessManager.getStorageDirectory(useSDCard);
                    refresh();
                }
            });
            mUseSDCard.setVisibility(View.VISIBLE);
//            if (activity.getPreferences().getBoolean("storage", false)) mUseSDCard.setBackgroundResource(activity.getNightMode() ? R.drawable.night_icon_border : R.drawable.icon_border);
            if (activity.getPreferences().getBoolean("storage", false)) mUseSDCard.setBackgroundResource(R.drawable.icon_border);
        }

        mSortControls = view.findViewById(R.id.sort_controls);

        mMapView = (MapView) view.findViewById(R.id.map);
        if (activity.hasLocationPermission()) {
            mMapView.onCreate(null);
            mMapView.getMapAsync(this);
            mMapView.onResume(); //without this, map showed but was empty
        }
        else {
            mListMode.setVisibility(View.INVISIBLE);
        }

        mExportCSV = (ImageView) view.findViewById(R.id.export_csv);
        //set the ontouch listener
        mExportCSV.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN: {
                        mExportCSV.getDrawable().setColorFilter(0xff028ec1, PorterDuff.Mode.SRC_ATOP);
                        mExportCSV.invalidate();
                        break;
                    }
                    case MotionEvent.ACTION_UP: {
                        File csvFile = new File(Environment.getExternalStorageDirectory(), "BatRecorder_" + sCurrentPath.getName() + ".csv");
                        Toast.makeText(activity, activity.getString(R.string.exporting) + " " + csvFile.getName(), Toast.LENGTH_LONG).show();
                        try {
                            FileWriter writer = new FileWriter(csvFile, false);
                            writer.append("Recording File,Timestamp,Recording Length (secs),Sample Rate,Recording Device,Latitude,Longitude,Elevation,Species\n");
                            for (WavFileDescriptor desc : fileList) {
                                if (!desc.isDirectory) {
                                    writer.append(desc.fileName + "," + desc.createDate.toString() + "," + desc.recordingLength + "," + desc.samplingRate + ",");
                                    if (desc.device != null)
                                        writer.append(desc.device + ",");
                                    else
                                        writer.append(",");
                                    if ((desc.latitude != LocationData.INVALID_GPS_COORD) && (desc.longitude != LocationData.INVALID_GPS_COORD))
                                        writer.append(desc.latitude + "," + desc.longitude + ",");
                                    else
                                        writer.append(",,");
                                    if (desc.elevation != LocationData.INVALID_ELEVATION)
                                        writer.append(desc.elevation + ",");
                                    else
                                        writer.append(",");
                                    if (desc.species != null) writer.append(desc.species);
                                    writer.append("\n");
                                }
                            }
                            writer.close();

                            Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            intent.setData(FileAccessManager.getFileUri(FileChooser.this.activity, csvFile));
//                            intent.setData(Uri.fromFile(csvFile));
                            activity.sendBroadcast(intent);
                        } catch (Exception e) {
                        }
                    }
                    case MotionEvent.ACTION_CANCEL: {
                        mExportCSV.getDrawable().clearColorFilter();
                        mExportCSV.invalidate();
                        break;
                    }
                }

                return true;
            }
        });
        mExportGPX = (ImageView) view.findViewById(R.id.export_gpx);
        //set the ontouch listener
        mExportGPX.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN: {
                        mExportGPX.getDrawable().setColorFilter(0xff028ec1, PorterDuff.Mode.SRC_ATOP);
                        mExportGPX.invalidate();
                        break;
                    }
                    case MotionEvent.ACTION_UP: {
                        File gpxFile = new File(Environment.getExternalStorageDirectory(), "BatRecorder_" + sCurrentPath.getName() + ".gpx");
                        Toast.makeText(activity, activity.getString(R.string.exporting) + " " + gpxFile.getName(), Toast.LENGTH_LONG).show();
                        try {
                            FileWriter writer = new FileWriter(gpxFile, false);
                            writer.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                            writer.append("<gpx version=\"1.0\" creator=\"BatRecorder "+MainActivity.getVersion()+"\"\n");
                            writer.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
                            writer.append("xmlns=\"http://www.topografix.com/GPX/1/0\"\n");
                            writer.append("xsi:schemaLocation=\"http://www.topografix.com/GPX/1/0 http://www.topografix.com/GPX/1/0/gpx.xsd\">\n");

                            writer.append("<trk>\n");
                            writer.append("<name>" + sCurrentPath.getName() + " Track</name>\n");
                            writer.append("<trkseg>\n");
                            for (WavFileDescriptor desc : transectList) {
                                if ((!desc.isDirectory) && (desc.fileName == null)) {
                                    if ((desc.latitude != LocationData.INVALID_GPS_COORD) && (desc.longitude != LocationData.INVALID_GPS_COORD)) {
                                        writer.append("<trkpt lat=\""+sCoordFormatter.format(desc.latitude) + "\" lon=\"" + sCoordFormatter.format(desc.longitude) + "\">");
                                        if (desc.elevation != LocationData.INVALID_ELEVATION) writer.append("<ele>" + sAltFormatter.format(desc.elevation) + "</ele>");
                                        writer.append("<time>" + GUANOMetaDataParser.sDateFormat.format(desc.createDate) + "</time>");
                                        writer.append("</trkpt>\n");
                                    }
                                }
                            }
                            writer.append("</trkseg>\n");
                            writer.append("</trk>\n");

                            writer.append("<rte>\n");
                            writer.append("<name>" + sCurrentPath.getName() + " Recordings</name>\n");

                            String text;
                            for (WavFileDescriptor desc : transectList) {
                                if ((!desc.isDirectory) && (desc.fileName != null)) {
                                    if ((desc.latitude != LocationData.INVALID_GPS_COORD) && (desc.longitude != LocationData.INVALID_GPS_COORD)) {
                                        writer.append("<rtept lat=\""+sCoordFormatter.format(desc.latitude) + "\" lon=\"" + sCoordFormatter.format(desc.longitude) + "\">");
                                        if (desc.elevation != LocationData.INVALID_ELEVATION) writer.append("<ele>" + sAltFormatter.format(desc.elevation) + "</ele>");
                                        writer.append("<time>" + GUANOMetaDataParser.sDateFormat.format(desc.createDate) + "</time>");
                                        writer.append("<name>" + desc.fileName + "</name>");
                                        text = Float.toString(desc.recordingLength) + "s";
                                        if (desc.species != null) text += "  " + desc.species;
                                        writer.append("<desc>" + text + "</desc>");
                                        writer.append("</rtept>\n");
                                    }
                                }
                            }
                            writer.append("</rte>\n");
                            writer.append("</gpx>\n");
                            writer.close();

                            Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            intent.setData(FileAccessManager.getFileUri(FileChooser.this.activity, gpxFile));
//                            intent.setData(Uri.fromFile(gpxFile));
                            activity.sendBroadcast(intent);

                        } catch (Exception e) {
                        }
                    }
                    case MotionEvent.ACTION_CANCEL: {
                        mExportGPX.getDrawable().clearColorFilter();
                        mExportGPX.invalidate();
                        break;
                    }
                }

                return true;
            }
        });

        dialog = new Dialog(activity, R.style.CustomFileDialog);
        dialog.setCancelable(false);
//        dialog.setContentView(R.layout.file_dialog);
        dialog.setContentView(view);
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {

            @Override
            public void onDismiss(DialogInterface dlog) {
                dlog.dismiss();
            }
        });
        dialog.setOnKeyListener(new Dialog.OnKeyListener() {

            private boolean mVolUpisDown = false;
            private boolean mVolDownisDown = false;

            @Override
            public boolean onKey(DialogInterface arg0, int keyCode, KeyEvent event) {
                if ((keyCode == KeyEvent.KEYCODE_BACK) && (event.getAction() == KeyEvent.ACTION_UP)) {
                    if (mMapView.getVisibility() == View.VISIBLE) {
                        mListView.setVisibility(View.VISIBLE);
                        mMapView.setVisibility(View.INVISIBLE);
//                        mListMode.setBackgroundResource(R.drawable.map);
                        mListMode.setImageResource(R.drawable.map);
                        mSortControls.setVisibility(View.VISIBLE);
                        mSortOrderView.setVisibility(View.VISIBLE);
                        if (FileAccessManager.getExternalStorageDirectory() != null) {
                            mUseSDCard.setVisibility(View.VISIBLE);
                        }
                    } else {
                        if (sCurrentPath.equals(FileAccessManager.getStorageDirectory(useSDCard)))
                            dialog.dismiss();
                        else {
                            sCurrentPath = FileAccessManager.getStorageDirectory(useSDCard);
                            refresh();
                        }
                    }
                }

                boolean up = (keyCode == KeyEvent.KEYCODE_VOLUME_UP);
                boolean down = (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN);

                if (!up && !down) return true;

                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (up) mVolUpisDown = true;
                    if (down) mVolDownisDown = true;
                }
                else if (event.getAction() == KeyEvent.ACTION_UP) {
                    if (up) mVolUpisDown = false;
                    if (down) mVolDownisDown = false;
                    return true;
                }

//                if (mVolUpisDown && mVolDownisDown) {
//                    if (selectedPos >= 0) {
//                        WavFileDescriptor fileChosen = (WavFileDescriptor) mListView.getItemAtPosition(selectedPos);
//                        if (fileChosen == null) {
//                            Log.d("FileChooser", "null file descriptor");
//                            return true;
//                        }
//                        if (fileChosen.isDirectory) {
//                            sCurrentPath = new File(sCurrentPath, fileChosen.fileName);
//                            refresh();
//                        }
//                        else {
//                            File chosenFile = getChosenFile(fileChosen);
//                            if (!chosenFile.isDirectory()) {
//                                if (fileListener != null) {
//                                    if (fileChosen.dataLength > MainActivity.MAX_SOUND_SAMPLES) {
//                                        String msg = activity.getResources().getString(R.string.file_too_long) + " " + sTimeFormatter.formatFull((float) MainActivity.MAX_SOUND_SAMPLES / (float) fileChosen.samplingRate);
//                                        Toast.makeText(activity,  msg, Toast.LENGTH_LONG).show();
//                                    }
//                                    fileListener.fileSelected(chosenFile);
//                                }
//                                dialog.dismiss();
//                            }
//                        }
//                    }
//                    return true;
//                }

                if (selectedPos >= 0) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        ((WavFileDescriptor) mListView.getItemAtPosition(selectedPos)).exposeCtrl = false;
                        if (mVolUpisDown) {
                            if (selectedPos > 0)
                                selectedPos--;
                            else
                                selectedPos = fileList.size() - 1;
                        }
                        else {
                            if (selectedPos < fileList.size() - 1)
                                selectedPos++;
                            else
                                selectedPos = 0;
                        }
                    }
                }
                else
                    selectedPos = 0;

                ((WavFileDescriptor) mListView.getItemAtPosition(selectedPos)).pressed = true;
                if ((lastTouched != selectedPos) && (lastTouched >= 0)) ((WavFileDescriptor) mListView.getItemAtPosition(lastTouched)).pressed = false;
                mListView.invalidateViews();
                lastTouched = selectedPos;

                return true;
            }
        });

        mSortOrderView = (ImageView) dialog.findViewById(R.id.sort_order);
        mSortOrderView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSortOrder == SORT_ORDER_DOWN) {
                    mSortOrderView.setBackground(null);;
                    mSortOrder = SORT_ORDER_UP;
//                    mSortOrderView.setBackgroundResource(R.drawable.up);
                    mSortOrderView.setImageResource(R.drawable.up);
                } else {
                    mSortOrder = SORT_ORDER_DOWN;
//                    mSortOrderView.setBackgroundResource(R.drawable.down);
                    mSortOrderView.setImageResource(R.drawable.down);
                }
                short sortKey = mSortKey;
                mSortKey = UNSORTED;
                sortDescriptors(sortKey);
                activity.getPreferences().edit().putInt("sort_order", mSortOrder).apply();
            }
        });

//        if (activity.getNightMode()) {
//            mSortOrderView.setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
//            mListMode.setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
//        }

        mFilenameView = (ImageView) dialog.findViewById(R.id.file_name);
        mFilenameView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sortDescriptors(SORT_FILENAME);
            }
        });

        mCreateDateView = (ImageView) dialog.findViewById(R.id.calendar);
        mCreateDateView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sortDescriptors(SORT_DATE);
            }
        });

        mRecLengthView = (ImageView) dialog.findViewById(R.id.recording_length);
        mRecLengthView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sortDescriptors(SORT_RECORD_LENGTH);
            }
        });

        mDataLengthView = (ImageView) dialog.findViewById(R.id.data_length);
        mDataLengthView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sortDescriptors(SORT_DATA_LENGTH);
            }
        });

        mPlaceView = (ImageView) dialog.findViewById(R.id.place);
        mPlaceView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sortDescriptors(SORT_LOCATION);
            }
        });

        mDeviceView = (ImageView) dialog.findViewById(R.id.device);
        mDeviceView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sortDescriptors(SORT_DEVICE);
            }
        });

        mSpeciesView = (ImageView) dialog.findViewById(R.id.bat);
        mSpeciesView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sortDescriptors(SORT_SPECIES);
            }
        });

        mListView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return gestureDetector.onTouchEvent(event);
            }
        });

        if (sCurrentPath == null) sCurrentPath = FileAccessManager.getStorageDirectory(useSDCard);

        int status = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity);
        if (status != ConnectionResult.SUCCESS) {
            if (status == ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED)
                Toast.makeText(activity, R.string.googleplay_update, Toast.LENGTH_LONG).show();
            else
                Toast.makeText(activity, R.string.googleplay_missing, Toast.LENGTH_LONG).show();
        }
        dialog.getWindow().setLayout(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    }

    @Override
    public void onMapReady(GoogleMap map) {
        if (activity.hasLocationPermission()) {
            mMap = map;
            if (mMap != null) {
                mListMode.setVisibility(View.VISIBLE);
                mListMode.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mMapView.getVisibility() == View.INVISIBLE) {
                            mMapView.setVisibility(View.VISIBLE);
                            mListView.setVisibility(View.INVISIBLE);
//                            mListMode.setImageResource(android.R.color.transparent);
//                            mListMode.setBackgroundResource(R.drawable.file_list);
                            mListMode.setImageResource(R.drawable.file_list);
                            mSortControls.setVisibility(View.INVISIBLE);
                            mSortOrderView.setVisibility(View.INVISIBLE);
                            mUseSDCard.setVisibility(View.GONE);
                        } else {
                            rebuildMap();
                            mListView.setVisibility(View.VISIBLE);
                            mMapView.setVisibility(View.INVISIBLE);
//                            mListMode.setImageResource(android.R.color.transparent);
//                            mListMode.setBackgroundResource(R.drawable.map);
                            mListMode.setImageResource(R.drawable.map);
                            mSortControls.setVisibility(View.VISIBLE);
                            mSortOrderView.setVisibility(View.VISIBLE);
                            if (FileAccessManager.getExternalStorageDirectory() != null)
                                mUseSDCard.setVisibility(View.VISIBLE);
                        }
                    }
                });

                mMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
                    @Override
                    @SuppressWarnings({"MissingPermission"})
                    public void onMapLoaded() {
                        mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                        mMap.setTrafficEnabled(false);
//                        mMap.setMyLocationEnabled(true);
                        mMap.getUiSettings().setZoomControlsEnabled(true);
                        mMap.getUiSettings().setMapToolbarEnabled(false);
//                        try {
//                            LatLngBounds bounds = mBuilder.build();
//                            int padding = 100; // offset from edges of the map in pixels
//                            CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, padding);
//                            mMap.moveCamera(cu);
//                        } catch (IllegalStateException e) {
//                        }

                        mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {

                            @Override
                            public View getInfoContents(Marker arg0) {
                                return null;
                            }

                            @Override
                            public View getInfoWindow(Marker marker) {

                                View v = activity.getLayoutInflater().inflate(R.layout.info_window_layout, null);

                                TextView title = (TextView) v.findViewById(R.id.filename);
                                title.setText(marker.getTitle());

                                String[] lines = marker.getSnippet().split("\n");
                                TextView snippet = (TextView) v.findViewById(R.id.timestamp);
                                snippet.setText(lines[0]);

                                if (lines.length > 1) {
                                    snippet = (TextView) v.findViewById(R.id.reclen);
                                    snippet.setText(lines[1] + " s");
                                }
                                if (lines.length > 2) {
                                    snippet = (TextView) v.findViewById(R.id.species);
                                    snippet.setText(lines[2]);
                                }

                                return v;
                            }
                        });
                    }
                });

                mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
                    @Override
                    public boolean onMarkerClick(Marker marker) {
                        if (marker.isDraggable() && (mSelectedMarker != null) && (mSelectedMarker != marker)) {
                            mSelectedMarker.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.bat_marker));
                        }
                        mSelectedMarker = marker;
                        mSelectedMarker.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.bat_marker_selected));
                        return false;
                    }
                });

                mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
                    @Override
                    public void onMapClick(LatLng latLng) {
                        if (mSelectedMarker != null) {
                            mSelectedMarker.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.bat_marker));
                        }
                        mSelectedMarker = null;
                    }
                });

                // Simulate long click
                mMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {

                    @Override
                    public void onMarkerDragStart(Marker marker) {

                        WavFileDescriptor fileChosen = null;
                        for (WavFileDescriptor desc : fileList) {
                            if (desc.fileName.equals(marker.getTitle())) {
                                fileChosen = desc;
                                break;
                            }
                        }
                        if (fileChosen == null) {
                            Log.d("FileChooser", "null WAV file descriptor");
                            return;
                        }
                        File chosenFile = getChosenFile(fileChosen);
                        if (!chosenFile.isDirectory()) {
                            if (fileListener != null) {
                                sLastFileOpened = fileChosen;
                                fileListener.fileSelected(chosenFile);
                            }
                            dialog.dismiss();
                        }
                    }

                    @Override
                    public void onMarkerDragEnd(Marker marker) {
                    }

                    @Override
                    public void onMarkerDrag(Marker marker) {
                    }
                });
            }
        }
    }

    public void showDialog() {

        refresh();
        dialog.show();

        if (sLastFileOpened != null) {
            int index = 0;
            for (WavFileDescriptor desc : fileList) {
                if (desc.equals(sLastFileOpened)) {
                    selectedPos = index;
                    lastTouched = selectedPos;
                    ((WavFileDescriptor) mListView.getItemAtPosition(selectedPos)).pressed = true;
                    mListView.smoothScrollToPosition(selectedPos);
                    break;
                }
                index++;
            }
        }
    }

    private void rebuildMap() {

        if (mMap == null) return;

        mMap.clear();
        mSelectedMarker = null;

        if (transectList == null) return;

        Marker marker;
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (WavFileDescriptor desc : transectList) {
            if (desc.fileName != null) {
                String txt = desc.createDate.toString() + "\n" + desc.recordingLength;
                if (desc.species != null) txt += "\n" + desc.species;
                marker = mMap.addMarker(new MarkerOptions()
                        .position(new LatLng(desc.latitude, desc.longitude))
                        .title(desc.fileName)
                        .snippet(txt)
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.bat_marker)));
                marker.setDraggable(true);
                builder.include(marker.getPosition());
            }
        }
        if (transectList.size() > 1) {
            LatLng prev = new LatLng(transectList.get(0).latitude, transectList.get(0).longitude);
            for (int ii = 1; ii < transectList.size(); ii++) {
                LatLng next = new LatLng(transectList.get(ii).latitude, transectList.get(ii).longitude);
                mMap.addPolyline(new PolylineOptions()
                        .add(prev, next)
                        .width(5)
                        .color(0x88ff0000));
                prev = next;
            }
        }
        try {
            LatLngBounds bounds = builder.build();
            int padding = 100; // offset from edges of the map in pixels
            CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, padding);
            mMap.moveCamera(cu);
        } catch (IllegalStateException e) {
        }
    }

    /**
     * Sort, filter and display the files for the given path.
     */
    private void refresh() {

        lastTouched = -1;
        selectedPos = -1;
        if (sCurrentPath.exists()) {
            if (mMap != null) {
                mMap.clear();
                mSelectedMarker = null;
            }

            mExportCSV.setClickable(false);
            mExportCSV.setAlpha(0.4f);

            mExportGPX.setClickable(false);
            mExportGPX.setAlpha(0.4f);

//            if (activity.getNightMode()) {
//
//                mUseSDCard.setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
//                mCreateDateView.setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
//                mDataLengthView.setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
//                mDeviceView.setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
//                mFilenameView.setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
//                mSpeciesView.setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
//                mPlaceView.setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
//                mRecLengthView.setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
//
//                mExportCSV.setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
//                mExportGPX.setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
//            }

            // load waypoints if present
            if (transectList == null)
                transectList = new ArrayList<>();
            else
                transectList.clear();

            File wpFile = new File(sCurrentPath, "wp.bin");
            if (wpFile.exists()) {
                try {
                    FileInputStream fis = new FileInputStream(wpFile);
                    DataInputStream dis = new DataInputStream(fis);

                    while (dis.available() > 0) {
                        WavFileDescriptor desc = new WavFileDescriptor();
                        desc.fileName = null;
                        desc.createDate = new Date(dis.readLong());
                        desc.latitude = dis.readDouble();
                        desc.longitude = dis.readDouble();
                        desc.elevation = dis.readDouble();
                        transectList.add(desc);
                    }
                    dis.close();
                }
                catch (Exception e) {
                }
            }

            File[] files = sCurrentPath.listFiles(new FileFilter() {
                @Override
                public boolean accept(File file) {
//                    return (!file.isDirectory() && (file.canRead() && file.getName().toLowerCase().endsWith(sExtension)));
                    return (file.isDirectory() || ((file.canRead() && file.getName().toLowerCase().endsWith(sExtension))));
                }
            });
//           ((TextView) dialog.findViewById(R.id.dir_info)).setText(currentPath.+" "+activity.getString(R.string.files));

            // convert to an array
            fileList = new ArrayList<>();
            Arrays.sort(files);
            long[] audioParams = new long[4];

            for (File file : files) {

                WavFileDescriptor desc = new WavFileDescriptor();
                desc.fileName = file.getName();
                desc.createDate = null;
                desc.species = null;
                desc.device = null;
                desc.latitude = LocationData.INVALID_GPS_COORD;
                desc.longitude = LocationData.INVALID_GPS_COORD;
                desc.elevation = LocationData.INVALID_ELEVATION;

                if (file.isDirectory()) {
                    desc.isDirectory = true;
                    desc.fileLength = 0;
                    desc.samplingRate = 0;
                    desc.createDate = new Date(file.lastModified());
                    File[] recordings = file.listFiles(new FileFilter() {
                        @Override
                        public boolean accept(File file) {
                            return (!file.isDirectory() && ((file.canRead() && file.getName().toLowerCase().endsWith(sExtension))));
                        }
                    });
                    desc.dataLength = recordings.length;
                }
                else {
                    int result = MainActivity.readWAVHeader(file.getAbsolutePath(), audioParams);
                    if (result < 0) continue;
                    MetaData metadata = MetaDataParser.getMetadata(file);

                    mExportCSV.setClickable(true);
                    mExportCSV.setAlpha(1.0f);
                    mExportGPX.setClickable(true);
                    mExportGPX.setAlpha(1.0f);

                    desc.isDirectory = false;
                    desc.fileLength = file.length();
                    desc.channels = (int) audioParams[0];
                    desc.dataLength = audioParams[3] / desc.channels;
                    desc.samplingRate = (int) audioParams[1];
                    desc.recordingLength = (float) desc.dataLength / (float) desc.samplingRate;

                    if (metadata != null) {

                        desc.createDate = metadata.timestamp;

                        if (metadata.species != null) desc.species = metadata.species;
                        if (metadata.captureDevice != null) desc.device = metadata.captureDevice;

                        if ((metadata.latitude != LocationData.INVALID_GPS_COORD) && (metadata.longitude != LocationData.INVALID_GPS_COORD)) {
                            desc.latitude = metadata.latitude;
                            desc.longitude = metadata.longitude;
                        }
                        if (metadata.elevation != LocationData.INVALID_ELEVATION)
                            desc.elevation = metadata.elevation;
                    }
                    if (desc.createDate == null) desc.createDate = new Date(file.lastModified());
                }
                fileList.add(desc);
            }

            // Add actual files into transect...
            for (WavFileDescriptor desc : fileList) {
                if (!desc.isDirectory) transectList.add(desc);
            }
            Collections.sort(transectList, new Comparator<WavFileDescriptor>() {
                @Override
                public int compare(WavFileDescriptor e1, WavFileDescriptor e2) {
                    return e1.createDate.compareTo(e2.createDate);
                }
            });

            while ((transectList.size() > 1)
                    && (transectList.get(0).fileName == null)
                    && (transectList.get(1).fileName == null))
                        transectList.remove(0);
            while ((transectList.size() > 1)
                    && (transectList.get(transectList.size()-2).fileName == null)
                    && (transectList.get(transectList.size()-1).fileName == null))
                        transectList.remove(transectList.size()-1);

            // refresh the user interface
//            dialog.setTitle(R.string.archive_title);
            mListView.setAdapter(new FileListAdapter(activity, fileList));
            mSortOrder = (short) activity.getPreferences().getInt("sort_order", SORT_ORDER_DOWN);
            if (mSortOrder == SORT_ORDER_DOWN) {
//                mSortOrderView.setBackgroundResource(R.drawable.down);
                mSortOrderView.setImageResource(R.drawable.down);
            } else {
//                mSortOrderView.setBackgroundResource(R.drawable.up);
                mSortOrderView.setImageResource(R.drawable.up);
            }
            mSortKey = UNSORTED;
            sortDescriptors((short) activity.getPreferences().getInt("sort_key", SORT_FILENAME));

            rebuildMap();
         }
    }

    /**
     * Convert a relative filename into an actual File object.
     */
    private File getChosenFile(WavFileDescriptor fileChosen) {
//        if (fileChosen.equals(activity.getStorageDirectory().getName())) {
//            return currentPath.getParentFile();
//        } else {
        return new File(sCurrentPath, fileChosen.fileName);
//        }
    }

    private class WavFileDescriptor {

        String fileName;
        long fileLength;
        Date createDate;
        float recordingLength;
        long dataLength;
        int samplingRate;
        int channels;
        double latitude;
        double longitude;
        double elevation;
        String species;
        String device;
        boolean isDirectory;
        boolean exposeCtrl;
        boolean pressed;

        @Override
        public boolean equals(Object obj) {
            if ((obj == null) || (!WavFileDescriptor.class.isAssignableFrom(obj.getClass()))) return false;

            final WavFileDescriptor other = (WavFileDescriptor) obj;
            if (!this.fileName.equals(other.fileName)
                    || (this.fileLength != other.fileLength)
                    || (this.dataLength != other.dataLength)
                    || (this.samplingRate != other.samplingRate)
                    || !this.createDate.equals(other.createDate)
                    ) {
                return false;
            }
            return true;
        }
    }

    private static boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (String child : children) {
                boolean success = deleteDir(new File(dir, child));
                if (!success) return false;
            }
        }

        return dir.delete(); // The directory is empty now and can be deleted.
    }

    private class FileListAdapter extends ArrayAdapter<WavFileDescriptor> {

        public FileListAdapter(Context context, ArrayList<WavFileDescriptor> files) {
            super(context, 0, files);
        }

        @NonNull
        @Override
        public View getView(final int position, View convertView, @NonNull ViewGroup parent) {
            // Get the data item for this position
            WavFileDescriptor desc = getItem(position);
            // Check if an existing view is being reused, otherwise inflate the view
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_list_item, parent, false);
            }

            // Populate the data into the template view using the data object
            mapDataToListItem(convertView, desc);

            if (desc.pressed || desc.exposeCtrl) {
//                if (activity.getNightMode())
//                    convertView.setBackgroundColor(Color.parseColor("#ff440000"));
//                else
                    convertView.setBackgroundColor(activity.getResources().getColor(R.color.file_list_press));
            }
            else
                convertView.setBackgroundColor(activity.getResources().getColor(R.color.file_list_default));

            if (desc.exposeCtrl) {

                if (desc.isDirectory) {
//                    convertView.findViewById(R.id.rename).setVisibility(View.INVISIBLE);
                    convertView.findViewById(R.id.share).setVisibility(View.GONE);
                    convertView.findViewById(R.id.export).setVisibility(View.GONE);
                }
                else {
//                    convertView.findViewById(R.id.rename).setVisibility(View.VISIBLE);
                    convertView.findViewById(R.id.share).setVisibility(View.VISIBLE);
                    convertView.findViewById(R.id.export).setVisibility(View.VISIBLE);
               }
                convertView.findViewById(R.id.file_control_layout).setVisibility(View.VISIBLE);

                convertView.findViewById(R.id.share).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        WavFileDescriptor desc = (WavFileDescriptor) mListView.getItemAtPosition(position);
                        if (desc == null) {
                            Log.d("FileChooser", "null file descriptor");
                            return;
                        }
                        File fileChosen = getChosenFile(desc);
                        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
                        sharingIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        sharingIntent.putExtra(Intent.EXTRA_STREAM, FileAccessManager.getFileUri(getContext(), fileChosen));
//                        sharingIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(fileChosen));
                        sharingIntent.setType("audio/wav");
                        FileChooser.this.activity.startActivity(Intent.createChooser(sharingIntent, FileChooser.this.activity.getString(R.string.share)));
                    }
                });

                convertView.findViewById(R.id.rename).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        WavFileDescriptor desc = (WavFileDescriptor) mListView.getItemAtPosition(position);
                        if (desc == null) {
                            Log.d("FileChooser", "null WAV file descriptor");
                            return;
                        }
                        final File fileChosen = getChosenFile(desc);
                        final EditText nameText = new EditText(activity);
                        InputFilter filter = new InputFilter() {
                            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                                if (source.length() < 1) return null;
                                char last = source.charAt(source.length() - 1);
                                String reservedChars = "?:\"*|/\\<>";
                                if (reservedChars.indexOf(last) > -1)
                                    return source.subSequence(0, source.length() - 1);
                                return null;
                            }
                        };
                        nameText.setFilters(new InputFilter[]{filter});
                        if (fileChosen.isDirectory())
                            nameText.setText(fileChosen.getName());
                        else
                            nameText.setText(fileChosen.getName().substring(0, fileChosen.getName().toLowerCase().indexOf(".wav")));
                        nameText.setTextColor(Color.parseColor("#33b5e5"));

                        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity, R.style.CustomDialog)
                                .setTitle(activity.getString(R.string.rename))
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
                                            String path = sCurrentPath + File.separator + filename;
                                            if (!fileChosen.isDirectory()) path += ".wav";
                                            File renameFile = new File(path);
                                            if (renameFile.exists())
                                                Toast.makeText(activity, R.string.already_exists, Toast.LENGTH_SHORT).show();
                                            else {
                                                File activeDirectory = FileAccessManager.getActiveDirectory(false);
                                                if ((activeDirectory != null) && activeDirectory.getAbsolutePath().equals(fileChosen.getAbsolutePath())) {
                                                    fileChosen.renameTo(renameFile);
                                                    FileAccessManager.setActiveDirectory(renameFile);
                                                }
                                                else
                                                    fileChosen.renameTo(renameFile);
                                                ((WavFileDescriptor) mListView.getItemAtPosition(position)).fileName = renameFile.getName();

                                                Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
//                                                intent.setData(Uri.fromFile(renameFile));
                                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                                intent.setData(FileAccessManager.getFileUri(getContext(), fileChosen));
//                                                intent.setData(Uri.fromFile(fileChosen));
                                                activity.sendBroadcast(intent);

                                                short sortKey = mSortKey;
                                                mSortKey = UNSORTED;
                                                sortDescriptors(sortKey);

                                                alertDialog.dismiss();
                                                mListView.invalidateViews();
                                            }
                                        } else
                                            Toast.makeText(activity, R.string.empty_name, Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        });
                        alertDialog.show();
                    }
                });

                convertView.findViewById(R.id.export).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        WavFileDescriptor desc = (WavFileDescriptor) mListView.getItemAtPosition(position);
                        if (desc == null) {
                            Log.d("FileChooser", "null WAV file descriptor");
                            return;
                        }
                        final File fileChosen = getChosenFile(desc);
                        int namespace = MetaDataParser.getMetadataNamespace(fileChosen.getAbsolutePath());
                        if (namespace != MetaData.UNK_NAMESPACE) {
                            File exportedFile;
                            if (namespace == MetaData.XMP_NAMESPACE)
                                exportedFile = new XMPMetaDataParser().export(fileChosen.getAbsolutePath());
                            else if (namespace == MetaData.WAMD_NAMESPACE)
                                exportedFile = new WAMDMetaDataParser().export(fileChosen.getAbsolutePath());
                            else
                                exportedFile = new GUANOMetaDataParser().export(fileChosen.getAbsolutePath());

                            Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            intent.setData(FileAccessManager.getFileUri(getContext(), exportedFile));
//                            intent.setData(Uri.fromFile(exportedFile));
                            activity.sendBroadcast(intent);

                            Toast.makeText(activity, R.string.created_xmp, Toast.LENGTH_SHORT).show();
                        }
                    }
                });

                convertView.findViewById(R.id.delete).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final WavFileDescriptor desc = (WavFileDescriptor) mListView.getItemAtPosition(position);
                        if (desc == null) {
                            Log.d("FileChooser", "null WAV file descriptor");
                            return;
                        }
                        final File fileChosen = getChosenFile(desc);
                        new AlertDialog.Builder(activity, R.style.CustomDialog)
                                .setTitle(activity.getString(R.string.delete))
                                .setMessage(activity.getString(R.string.delete_file) + " " + fileChosen.getName() + "?")
                                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        boolean success;
                                        if (fileChosen.isDirectory())
                                            success = deleteDir(fileChosen);
                                        else
                                            success = fileChosen.delete();
                                        if (success) {
                                            transectList.remove(desc);
                                            fileList.remove(position);
                                            ((FileListAdapter) mListView.getAdapter()).notifyDataSetChanged();

                                            if (fileChosen.equals(FileAccessManager.getActiveRecording())) activity.handleRecordingDiscarded();

                                            Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                            intent.setData(FileAccessManager.getFileUri(getContext(), fileChosen));
//                                            intent.setData(Uri.fromFile(fileChosen));
                                            activity.sendBroadcast(intent);

                                            dialog.dismiss();

                                            mListView.invalidateViews();
                                            selectedPos = -1;
                                            lastTouched = -1;
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
            } else {
                convertView.findViewById(R.id.file_control_layout).setVisibility(View.GONE);
            }
            // Return the completed view to render on screen
            return convertView;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {

        private static final int SWIPE_THRESHOLD = 50;
        private static final int SWIPE_VELOCITY_THRESHOLD = 100;

        @Override
        public boolean onDown(MotionEvent e) {
//            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                int row = mListView.pointToPosition((int) e.getX(), (int) e.getY());
                if (row >= 0) {
                    if ((row != selectedPos) && (selectedPos != -1)) {
                        if (selectedPos >= 0) {
                            ((WavFileDescriptor) mListView.getItemAtPosition(selectedPos)).exposeCtrl = false;
                            selectedPos = -1;
                        }
                    }
                    ((WavFileDescriptor) mListView.getItemAtPosition(row)).pressed = true;
                    if ((lastTouched != row) && (lastTouched >= 0)) ((WavFileDescriptor) mListView.getItemAtPosition(lastTouched)).pressed = false;
                    mListView.invalidateViews();
                    lastTouched = row;
                    selectedPos = lastTouched;
                }
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            int row = mListView.pointToPosition((int) e.getX(), (int) e.getY());
            WavFileDescriptor fileChosen = (WavFileDescriptor) mListView.getItemAtPosition(row);
            if (fileChosen == null) {
                Log.d("FileChooser", "null file descriptor");
                return;
            }
            if (fileChosen.isDirectory) {
                sCurrentPath = new File(sCurrentPath, fileChosen.fileName);
                refresh();
            }
            else {
                File chosenFile = getChosenFile(fileChosen);
                if (!chosenFile.isDirectory()) {
                    if (fileListener != null) {
                        if (fileChosen.dataLength > MainActivity.MAX_SOUND_SAMPLES) {
                            String msg = activity.getResources().getString(R.string.file_too_long) + " " + sTimeFormatter.formatFull((float) MainActivity.MAX_SOUND_SAMPLES / (float) fileChosen.samplingRate);
                            Toast.makeText(activity,  msg, Toast.LENGTH_LONG).show();
                        }
                        sLastFileOpened = fileChosen;
                        fileListener.fileSelected(chosenFile);
                    }
                    dialog.dismiss();
                }
            }
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            boolean result = false;
            try {
                float diffY = e2.getY() - e1.getY();
                float diffX = e2.getX() - e1.getX();
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        int row = mListView.pointToPosition((int) e1.getX(), (int) e1.getY());
                        if (diffX > 0) {
                            onSwipeRight(row);
                        } else {
                            onSwipeLeft(row);
                        }
//                        if (row >= 0) mListView.setItemChecked(row, true);
                        mListView.invalidateViews();
                    }
                    result = true;
                }

            } catch (Exception exception) {
                exception.printStackTrace();
            }
            return result;
        }

        public void onSwipeRight(int pos) {
            if (pos == selectedPos) {
                if (selectedPos >= 0) {
                    ((WavFileDescriptor) mListView.getItemAtPosition(selectedPos)).exposeCtrl = false;
                }
//                selectedPos = pos;
            }
//           if (pos >= 0) {
//               ((WavFileDescriptor) mListView.getItemAtPosition(pos)).exposeCtrl = false;
//            }
        }

        public void onSwipeLeft(final int pos) {

            if (pos != selectedPos) {
                if (selectedPos >= 0) {
                    ((WavFileDescriptor) mListView.getItemAtPosition(selectedPos)).exposeCtrl = false;
                }
                selectedPos = pos;
            }
            if (pos >= 0) {
                ((WavFileDescriptor) mListView.getItemAtPosition(pos)).exposeCtrl = true;
            }
        }
    }

    private static double haversine(double lat1, double long1, double lat2, double long2)
    {
        final double D2R = Math.PI / 180.0;

        double dlong = (long2 - long1) * D2R;
        double dlat = (lat2 - lat1) * D2R;
        double a = Math.pow(Math.sin(dlat / 2.0), 2) + Math.cos(lat1 * D2R) * Math.cos(lat2 * D2R) * Math.pow(Math.sin(dlong / 2.0), 2);
        return (2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
    }

    private void sortDescriptors(short key) {

        if (mSortKey == key) return;

        if (selectedPos >= 0) {
            ((WavFileDescriptor) mListView.getItemAtPosition(selectedPos)).exposeCtrl = false;
            selectedPos = -1;
        }
        if (lastTouched >= 0) {
            ((WavFileDescriptor) mListView.getItemAtPosition(lastTouched)).pressed = false;
            lastTouched = -1;
        }

        ImageView view;
        if (mSortKey == SORT_FILENAME)
            view = mFilenameView;
        else if (mSortKey == SORT_DATE)
            view = mCreateDateView;
        else if (mSortKey == SORT_RECORD_LENGTH)
            view = mRecLengthView;
        else if (mSortKey == SORT_DATA_LENGTH)
            view = mDataLengthView;
        else if (mSortKey == SORT_LOCATION)
            view = mPlaceView;
        else if (mSortKey == SORT_DEVICE)
            view = mDeviceView;
        else
            view = mSpeciesView;
        view.setBackgroundResource(0);

        ArrayAdapter<WavFileDescriptor> adapter = (ArrayAdapter<WavFileDescriptor>) mListView.getAdapter();

        mSortKey = key;
        activity.getPreferences().edit().putInt("sort_key", mSortKey).apply();

        if (mSortKey == SORT_FILENAME) {
  //          mFilenameView.setBackgroundResource(activity.getNightMode() ? R.drawable.night_icon_border : R.drawable.icon_border);
            mFilenameView.setBackgroundResource(R.drawable.icon_border);
            adapter.sort(new Comparator<WavFileDescriptor>() {
                public int compare(WavFileDescriptor e1, WavFileDescriptor e2) {
                    if (e1.isDirectory != e2.isDirectory) return (mSortOrder == SORT_ORDER_DOWN) ? (e1.isDirectory ? -1 : 1) : (e1.isDirectory ? 1 : -1);
                    int val = e1.fileName.compareToIgnoreCase(e2.fileName);
                    return (mSortOrder == SORT_ORDER_DOWN) ? val : -val;
                }
            });
        }
        else if (mSortKey == SORT_DATE) {
 //           mCreateDateView.setBackgroundResource(activity.getNightMode() ? R.drawable.night_icon_border : R.drawable.icon_border);
            mCreateDateView.setBackgroundResource(R.drawable.icon_border);
            adapter.sort(new Comparator<WavFileDescriptor>() {
               public int compare(WavFileDescriptor e1, WavFileDescriptor e2) {
                   if (e1.isDirectory != e2.isDirectory) return (mSortOrder == SORT_ORDER_DOWN) ? (e1.isDirectory ? -1 : 1) : (e1.isDirectory ? 1 : -1);
                   int val;
                   if (e1.createDate == null && e2.createDate != null)
                       val = 1;
                   else if (e1.createDate != null && e2.createDate == null)
                       val = -1;
                   else if (e1.createDate == null && e2.createDate == null)
                       val = 0;
                   else
                       val = e2.createDate.compareTo(e1.createDate);
                   if (val == 0) val = e1.fileName.compareToIgnoreCase(e2.fileName);
                   return (mSortOrder == SORT_ORDER_DOWN) ? val : -val;
               }
           });
        }
        else if (mSortKey == SORT_LOCATION) {
//            mPlaceView.setBackgroundResource(activity.getNightMode() ? R.drawable.night_icon_border : R.drawable.icon_border);
            mPlaceView.setBackgroundResource(R.drawable.icon_border);
            adapter.sort(new Comparator<WavFileDescriptor>() {
                public int compare(WavFileDescriptor e1, WavFileDescriptor e2) {
                    if (e1.isDirectory != e2.isDirectory) return (mSortOrder == SORT_ORDER_DOWN) ? (e1.isDirectory ? -1 : 1) : (e1.isDirectory ? 1 : -1);
                    int val;
                    if (e1.latitude == LocationData.INVALID_GPS_COORD && e2.latitude != LocationData.INVALID_GPS_COORD)
                        val = 1;
                    else if (e1.latitude != LocationData.INVALID_GPS_COORD && e2.latitude == LocationData.INVALID_GPS_COORD)
                        val = -1;
                    else if (e1.latitude == LocationData.INVALID_GPS_COORD && e2.latitude == LocationData.INVALID_GPS_COORD)
                        val = 0;
                    else {
                        LocationData location = activity.getLocationData();
                        double lat, lon;
                        if (location != null) {
                            lat = location.latitude;
                            lon = location.longitude;
                         } else {
                            lat = 0.0;
                            lon = 0.0;
                        }
                        double dist1 = haversine(lat, lon, e1.latitude, e1.longitude);
                        double dist2 = haversine(lat, lon, e2.latitude, e2.longitude);
                        if (dist1 > dist2)
                            val = 1;
                        else if (dist1 < dist2)
                            val = -1;
                        else
                            val = 0;
                    }
                    if (val == 0) val = e1.fileName.compareToIgnoreCase(e2.fileName);
                    return (mSortOrder == SORT_ORDER_DOWN) ? val : -val;
                }
            });
        }
        else if (mSortKey == SORT_RECORD_LENGTH) {
 //           mRecLengthView.setBackgroundResource(activity.getNightMode() ? R.drawable.night_icon_border : R.drawable.icon_border);
            mRecLengthView.setBackgroundResource(R.drawable.icon_border);
            adapter.sort(new Comparator<WavFileDescriptor>() {
                public int compare(WavFileDescriptor e1, WavFileDescriptor e2) {
                    if (e1.isDirectory != e2.isDirectory) return (mSortOrder == SORT_ORDER_DOWN) ? (e1.isDirectory ? -1 : 1) : (e1.isDirectory ? 1 : -1);
                    int val;
                    if (e1.recordingLength > e2.recordingLength)
                        val = -1;
                    else if (e1.recordingLength < e2.recordingLength)
                        val = 1;
                    else
                        val = 0;
                    if (val == 0) val = e1.fileName.compareToIgnoreCase(e2.fileName);
                    return (mSortOrder == SORT_ORDER_DOWN) ? val : -val;
                }
            });
        }
        else if (mSortKey == SORT_DATA_LENGTH) {
//            mDataLengthView.setBackgroundResource(activity.getNightMode() ? R.drawable.night_icon_border : R.drawable.icon_border);
            mDataLengthView.setBackgroundResource(R.drawable.icon_border);
            adapter.sort(new Comparator<WavFileDescriptor>() {
                public int compare(WavFileDescriptor e1, WavFileDescriptor e2) {
                    if (e1.isDirectory != e2.isDirectory) return (mSortOrder == SORT_ORDER_DOWN) ? (e1.isDirectory ? -1 : 1) : (e1.isDirectory ? 1 : -1);
                    int val;
                    if (e1.dataLength > e2.dataLength)
                        val = -1;
                    else if (e1.dataLength < e2.dataLength)
                        val = 1;
                    else
                        val = 0;
                    if (val == 0) val = e1.fileName.compareToIgnoreCase(e2.fileName);
                    return (mSortOrder == SORT_ORDER_DOWN) ? val : -val;
                }
            });
        }
        else if (mSortKey == SORT_DEVICE) {
//            mDeviceView.setBackgroundResource(activity.getNightMode() ? R.drawable.night_icon_border : R.drawable.icon_border);
            mDeviceView.setBackgroundResource(R.drawable.icon_border);
            adapter.sort(new Comparator<WavFileDescriptor>() {
                public int compare(WavFileDescriptor e1, WavFileDescriptor e2) {
                    if (e1.isDirectory != e2.isDirectory) return (mSortOrder == SORT_ORDER_DOWN) ? (e1.isDirectory ? -1 : 1) : (e1.isDirectory ? 1 : -1);
                    int val;
                    if (e1.device == null && e2.device != null)
                        val = 1;
                    else if (e1.device != null && e2.device == null)
                        val = -1;
                    else if (e1.device == null && e2.device == null)
                        val = 0;
                    else
                        val = e1.device.compareToIgnoreCase(e2.device);
                    if (val == 0) val = e1.fileName.compareToIgnoreCase(e2.fileName);
                    return (mSortOrder == SORT_ORDER_DOWN) ? val : -val;
                }
            });
        }
        else if (mSortKey == SORT_SPECIES) {
 //           mSpeciesView.setBackgroundResource(activity.getNightMode() ? R.drawable.night_icon_border : R.drawable.icon_border);
            mSpeciesView.setBackgroundResource(R.drawable.icon_border);
            adapter.sort(new Comparator<WavFileDescriptor>() {
                public int compare(WavFileDescriptor e1, WavFileDescriptor e2) {
                    if (e1.isDirectory != e2.isDirectory) return (mSortOrder == SORT_ORDER_DOWN) ? (e1.isDirectory ? -1 : 1) : (e1.isDirectory ? 1 : -1);
                    int val;
                    if (e1.species == null && e2.species != null)
                        val = 1;
                    else if (e1.species != null && e2.species == null)
                        val = -1;
                    else if (e1.species == null && e2.species == null)
                        val = 0;
                    else
                        val = e1.species.compareToIgnoreCase(e2.species);
                    if (val == 0) val = e1.fileName.compareToIgnoreCase(e2.fileName);
                    return (mSortOrder == SORT_ORDER_DOWN) ? val : -val;
                }
            });
        }
        adapter.notifyDataSetChanged();
    }

    private void mapDataToListItem(View view, WavFileDescriptor desc) {

        TextView line1b;
        TextView line2b;
        TextView line2a;
        TextView line3a;
        TextView line3b;

        TextView nameView = (TextView) view.findViewById(R.id.line1a);
        nameView.setText(desc.fileName);

        ImageView iconView = (ImageView) view.findViewById(R.id.file_icon);
        iconView.setBackground(null);
        if (desc.isDirectory) {
            nameView.setTypeface(Typeface.DEFAULT_BOLD);
            iconView.setImageResource(R.drawable.folder);
            line1b = (TextView) view.findViewById(R.id.line1b);
            line1b.setText("");
            line2a = (TextView) view.findViewById(R.id.line2a);
            line2a.setText(sOutDateFormatter.format(desc.createDate));
            String recStr = Long.toString(desc.dataLength)+" ";
            if (desc.dataLength == 1)
                recStr += activity.getString(R.string.recording);
            else
                recStr += activity.getString(R.string.recordings);
            line2b = (TextView) view.findViewById(R.id.line2b);
            line2b.setText(recStr);
            line3a = (TextView) view.findViewById(R.id.line3a);
            line3a.setText("");
            line3b = (TextView) view.findViewById(R.id.line3b);
            line3b.setText("");
        }
        else {
            nameView.setTypeface(Typeface.DEFAULT);
            iconView.setImageResource(R.drawable.speaker);
            line2a = (TextView) view.findViewById(R.id.line2a);
            line2a.setText(sOutDateFormatter.format(desc.createDate));
            String text = sTimeFormatter.format(desc.recordingLength) + " @ " + desc.samplingRate / 1000 + " kHz ";
            if (desc.channels == 2) text += "[stereo] ";
            text += "\u2248 " + sFileSizeFormatter.format((float) desc.fileLength / 1048576.f) + " MB";
            line1b = (TextView) view.findViewById(R.id.line1b);
            line1b.setText(text);

            text = (desc.species != null) ? desc.species : "";
            line3a = (TextView) view.findViewById(R.id.line3a);
            line3a.setText(text);

            text = (desc.device != null) ? desc.device : "";
            line2b = (TextView) view.findViewById(R.id.line2b);
            line2b.setText(text);

            if (desc.latitude == LocationData.INVALID_GPS_COORD)
                text = "";
            else {
                text = MetaDataParser.latitude2String(desc.latitude) + "   " + MetaDataParser.longitude2String(desc.longitude);
                if (desc.elevation != LocationData.INVALID_ELEVATION) text += "   " + sAltFormatter.format(desc.elevation) + " m";
            }
            line3b = (TextView) view.findViewById(R.id.line3b);
            line3b.setText(text);
        }

//        if (activity.getNightMode()) {
//            nameView.setTextColor(Color.RED);
//            line2a.setTextColor(Color.RED);
//            line1b.setTextColor(Color.RED);
//            line2b.setTextColor(Color.RED);
//            line3a.setTextColor(Color.RED);
//            line3b.setTextColor(Color.RED);
//            iconView.getDrawable().setColorFilter(0xffff0000, PorterDuff.Mode.MULTIPLY);
//        }
    }

    private void showSDCardAlert() {

        View checkBoxView = View.inflate(activity, R.layout.checkbox, null);
        CheckBox checkBox = (CheckBox) checkBoxView.findViewById(R.id.checkbox);
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // Save to shared preferences
                activity.getPreferences().edit().putBoolean("sdcard_alert", !isChecked).apply();
            }
        });
        checkBox.setText(R.string.not_show);
        checkBox.setTextColor(Color.WHITE);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.CustomAlertDialog);
        builder.setMessage(R.string.sdcard_warning)
                .setCancelable(true)
                .setTitle(R.string.sdcard_title)
                .setView(checkBoxView)
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        activity.getPreferences().edit().putBoolean("sdcard_alert", true).apply();
                        dialog.dismiss();
                    }
                })
                .setPositiveButton(R.string.use_sdcard, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        useSDCard = true;
//                        mUseSDCard.setBackgroundResource(activity.getNightMode() ? R.drawable.night_icon_border : R.drawable.icon_border);
                        mUseSDCard.setBackgroundResource(R.drawable.icon_border);
                        activity.getPreferences().edit().putBoolean("storage", true).apply();
                        sCurrentPath = FileAccessManager.getStorageDirectory(useSDCard);
                        refresh();
                    }
                });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    static {
        sOutDateFormatter = new SimpleDateFormat("EE MMM dd yyyy HH:mm:ss zz");
        sFileSizeFormatter = new DecimalFormat("#0.0");
//        sTimeFormatter = new DecimalFormat("#0.000 s");
        sTimeFormatter = new TimeAxisFormat();
    }
}
