package com.digitalbiology.audio.metadata;

import android.os.Build;
import androidx.annotation.NonNull;

import com.digitalbiology.audio.MainActivity;
import com.digitalbiology.audio.Microphone;
//import com.digitalbiology.audio.NotificationToken;

import java.io.File;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public class GUANOMetaDataParser extends MetaDataParser {

    private static final String KEY_GUANO_VERS = "GUANO|Version: 1.0";
    private static final String KEY_TIMESTAMP = "Timestamp:";
    private static final String KEY_SAMPLERATE = "Samplerate:";
    private static final String KEY_TIME_EXPANSION = "TE:";
    private static final String KEY_MAKE = "Make:";
    private static final String KEY_MODEL = "Model:";
    private static final String KEY_SPECIES = "Species Manual ID:";
    private static final String KEY_LENGTH = "Length:";
    private static final String KEY_LOCATION = "Loc Position:";
    private static final String KEY_ELEVATION = "Loc Elevation:";
    private static final String KEY_BATREC = "BATREC|Version:";
    private static final String KEY_HOST_DEVICE = "BATREC|Host Device:";
    private static final String KEY_HOST_OS = "BATREC|Host OS:";
    private static final String KEY_ILLUMINANCE = "BATREC|Illuminance:";
    private static final String KEY_TEMPERATURE = "BATREC|Temperature:";
    private static final String KEY_HUMIDITY = "BATREC|Humidity:";
    private static final String KEY_PRESSURE = "BATREC|Pressure:";

    private static final DecimalFormat sLengthFormatter = new DecimalFormat("#0.000000", new DecimalFormatSymbols(Locale.ENGLISH));
    private static final DecimalFormat sCoordFormatter = new DecimalFormat("#0.0000000", new DecimalFormatSymbols(Locale.ENGLISH));
    public static final SimpleDateFormat sDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ") {
        public StringBuffer format(@NonNull Date date, @NonNull StringBuffer toAppendTo, @NonNull java.text.FieldPosition pos) {
            StringBuffer toFix = super.format(date, toAppendTo, pos);
            return toFix.insert(toFix.length()-2, ':');
        }
    };

    public int getNamespace() {
        return MetaData.GUANO_NAMESPACE;
    }

//    public byte[] create(Date date, float secs, Microphone microphone, LocationData location, SensorData sensors, NotificationToken token) {
    public byte[] create(Date date, float secs, Microphone microphone, LocationData location, SensorData sensors) {

        String guano = KEY_GUANO_VERS + "\n";

        guano += KEY_TIMESTAMP + " " + sDateFormat.format(date) + "\n";
        guano += KEY_SAMPLERATE + " " + microphone.getSampleRate() + "\n";
        guano += KEY_TIME_EXPANSION + " 1\n";
        guano += KEY_LENGTH + " " + sLengthFormatter.format(secs) + "\n";
        guano += KEY_MAKE + " " + microphone.getManufacturerName() + "\n";
        guano += KEY_MODEL + " " + microphone.getProductName() + "\n";

        if (location != null) {
            double latitude = location.latitude;
            double longitude = location.longitude;
            guano += KEY_LOCATION + " " + sCoordFormatter.format(latitude) + " " + sCoordFormatter.format(longitude) + "\n";
//            if (token != null) {
//                double lat = Math.abs(latitude);
//                double lon = Math.abs(longitude);
//                token.latitude = (int) lat + "," + (lat - (int) lat) * 60.0f + ((latitude < 0.0f) ? 'S' : 'N');
//                token.longitude = (int) lon + "," + (lon - (int) lon) * 60.0f + ((longitude < 0.0f) ? 'W' : 'E');
//            }
            if (location.elevation != LocationData.INVALID_ELEVATION) guano += KEY_ELEVATION + " " +  sCoordFormatter.format(location.elevation) + "\n";
        }
        if (sensors != null) {
            if (sensors.illuminance != SensorData.INVALID_SENSOR_DATA) guano += KEY_ILLUMINANCE + " " + sensors.illuminance + "\n";
            if (sensors.temperature != SensorData.INVALID_SENSOR_DATA) guano += KEY_TEMPERATURE + " " + sensors.temperature + "\n";
            if (sensors.humidity != SensorData.INVALID_SENSOR_DATA) guano += KEY_HUMIDITY + " " + sensors.humidity + "\n";
            if (sensors.pressure != SensorData.INVALID_SENSOR_DATA) guano += KEY_PRESSURE + " " + sensors.pressure + "\n";
        }
//        if (token != null) {
//            token.timestamp = date;
//            NotificationToken.getNotifications().add(token);
//        }
        guano += KEY_BATREC + "BatRecorder " + MainActivity.getVersion() + "\n";
        guano += KEY_HOST_DEVICE + " " + Build.BRAND + " " + Build.PRODUCT + " ("+Build.MANUFACTURER + " " + Build.MODEL+")\n";
        guano += KEY_HOST_OS + " Android " + Build.VERSION.RELEASE + "\n";
        return guano.getBytes();
    }

    public MetaData read(File file) {

        MetaData metadata = null;

        byte[] bytes = readMetadata(file.getAbsolutePath(), MetaData.GUANO_NAMESPACE);
        if (bytes != null) {
            String guano = new String(bytes);
            metadata = new MetaData(MetaData.GUANO_NAMESPACE);
            String value;
            String[] lines = guano.split("\\n");
            for(String s: lines){

                if (s.startsWith(KEY_TIMESTAMP)) {
                    try {
                        metadata.timestamp = sDateFormat.parse(s.substring(KEY_TIMESTAMP.length()).trim());
                    }
                    catch (ParseException e) {
                        metadata.timestamp = null;
                    }
                }
                else if (s.startsWith(KEY_SAMPLERATE)) {
                    value = s.substring(KEY_SAMPLERATE.length()).trim();
                    if (!value.isEmpty()) metadata.sampleRate = Integer.parseInt(value);
                }
                else if (s.startsWith(KEY_TIME_EXPANSION)) {
                    value = s.substring(KEY_TIME_EXPANSION.length()).trim();
                    if (!value.isEmpty()) metadata.timeExpansion = Integer.parseInt(value);
                }
                else if (s.startsWith(KEY_LENGTH)) {
                    value = s.substring(KEY_LENGTH.length()).trim();
                    if (!value.isEmpty()) metadata.length = Float.parseFloat(value);
                }
                else if (s.startsWith(KEY_MAKE)) {
                    value = s.substring(KEY_MAKE.length()).trim();
                    if (!value.isEmpty()) metadata.captureMake = value;
                }
                else if (s.startsWith(KEY_MODEL)) {
                    value = s.substring(KEY_MODEL.length()).trim();
                    if (!value.isEmpty()) metadata.captureDevice = value;
                }
                else if (s.startsWith(KEY_SPECIES)) {
                    value = s.substring(KEY_SPECIES.length()).trim();
                    if (!value.isEmpty()) metadata.species = value;
                }
                else if (s.startsWith(KEY_LOCATION)) {
                    String temp = s.substring(KEY_LOCATION.length()).trim();
                    if (!temp.isEmpty()) {
                        String[] coords = temp.split("\\s+");
                        if (coords.length == 2) {
                            metadata.latitude = Double.parseDouble(coords[0]);
                            metadata.longitude = Double.parseDouble(coords[1]);
                        }
                    }
                }
                else if (s.startsWith(KEY_ELEVATION)) {
                    value = s.substring(KEY_ELEVATION.length()).trim();
                    if (!value.isEmpty()) metadata.elevation = Double.parseDouble(value);
                }
                else if (s.startsWith(KEY_HOST_DEVICE)) {
                    value = s.substring(KEY_HOST_DEVICE.length()).trim();
                    if (!value.isEmpty()) metadata.hostDevice = value;
                }
                else if (s.startsWith(KEY_HOST_OS)) {
                    value = s.substring(KEY_HOST_OS.length()).trim();
                    if (!value.isEmpty()) metadata.hostOS = value;
                }
                else if (s.startsWith(KEY_BATREC)) {
                    value = s.substring(KEY_BATREC.length()).trim();
                    if (!value.isEmpty()) metadata.software = value;
                }
                else if (s.startsWith(KEY_ILLUMINANCE)) {
                    value = s.substring(KEY_ILLUMINANCE.length()).trim();
                    if (!value.isEmpty()) metadata.illuminance = Float.parseFloat(value);
                }
                else if (s.startsWith(KEY_TEMPERATURE)) {
                    value = s.substring(KEY_TEMPERATURE.length()).trim();
                    if (!value.isEmpty()) metadata.temperature = Float.parseFloat(value);
                }
                else if (s.startsWith(KEY_HUMIDITY)) {
                    value = s.substring(KEY_HUMIDITY.length()).trim();
                    if (!value.isEmpty()) metadata.humidity = Float.parseFloat(value);
                }
                else if (s.startsWith(KEY_PRESSURE)) {
                    value = s.substring(KEY_PRESSURE.length()).trim();
                    if (!value.isEmpty()) metadata.pressure = Float.parseFloat(value);
                }
            }
        }
        return metadata;
    }

    public void update(File file, MetaData data) {

        String guano;
        byte[] bytes = readMetadata(file.getAbsolutePath(), MetaData.GUANO_NAMESPACE);
        if (bytes == null) {
            guano = KEY_GUANO_VERS + "\n";
            guano += KEY_TIMESTAMP + " " + data.timestamp + "\n";
            if (data.sampleRate > 0) guano += KEY_SAMPLERATE + " " + data.sampleRate + "\n";
            guano += KEY_TIME_EXPANSION + " " + data.timeExpansion + "\n";
            if (data.length > 0.0f) guano += KEY_LENGTH + " " + sLengthFormatter.format(data.length) + "\n";
            if (data.captureMake != null) guano += KEY_MAKE + " " + data.captureMake + "\n";
            if (data.captureDevice != null) guano += KEY_MODEL + " " + data.captureDevice + "\n";

            if (data.species != null) guano += KEY_SPECIES + " " + data.species + "\n";

            if ((data.latitude != LocationData.INVALID_GPS_COORD) && (data.longitude != LocationData.INVALID_GPS_COORD))
                guano += KEY_LOCATION + " " + sCoordFormatter.format(data.latitude) + " " + sCoordFormatter.format(data.longitude) + "\n";

            if (data.elevation != LocationData.INVALID_ELEVATION) guano += KEY_ELEVATION + " " +  sCoordFormatter.format(data.elevation) + "\n";

            if (data.illuminance != SensorData.INVALID_SENSOR_DATA) guano += KEY_ILLUMINANCE + " " + Float.toString(data.illuminance);
            if (data.temperature != SensorData.INVALID_SENSOR_DATA) guano += KEY_TEMPERATURE + " " + Float.toString(data.temperature);
            if (data.humidity != SensorData.INVALID_SENSOR_DATA) guano += KEY_HUMIDITY + " " + Float.toString(data.humidity);
            if (data.pressure != SensorData.INVALID_SENSOR_DATA) guano += KEY_PRESSURE + " " + Float.toString(data.pressure);

            guano += KEY_BATREC + "BatRecorder " + MainActivity.getVersion() + "\n";
            guano += KEY_HOST_DEVICE + " " + Build.BRAND + " " + Build.PRODUCT + " ("+Build.MANUFACTURER + " " + Build.MODEL+")\n";
            guano += KEY_HOST_OS + " Android " + Build.VERSION.RELEASE + "\n";
        }
        else {
            guano = new String(bytes);
            ArrayList<String> lines = new ArrayList<>(Arrays.asList(guano.split("\\n")));
            String value = (data.timestamp != null) ? sDateFormat.format(data.timestamp) : null;
            updateValue(lines, KEY_TIMESTAMP, value);

            value = (data.sampleRate > 0) ? Integer.toString(data.sampleRate) : null;
            updateValue(lines, KEY_SAMPLERATE, value);

            updateValue(lines, KEY_LENGTH, Integer.toString(data.timeExpansion));

            value = (data.length > 0.0f) ? sLengthFormatter.format(data.length) : null;
            updateValue(lines, KEY_LENGTH, value);

            updateValue(lines, KEY_MAKE, data.captureMake);
            updateValue(lines, KEY_MODEL, data.captureDevice);
            updateValue(lines, KEY_SPECIES, data.species);

            updateValue(lines, KEY_HOST_DEVICE, data.hostDevice);
            updateValue(lines, KEY_HOST_OS, data.hostOS);

            value = null;
            if ((data.latitude != LocationData.INVALID_GPS_COORD) && (data.longitude != LocationData.INVALID_GPS_COORD))
                value = sCoordFormatter.format(data.latitude) + " " + sCoordFormatter.format(data.longitude);
            updateValue(lines, KEY_LOCATION, value);

            value = null;
            if (data.elevation != LocationData.INVALID_ELEVATION) value = sCoordFormatter.format(data.elevation);
            updateValue(lines, KEY_ELEVATION, value);

            value = null;
            if (data.illuminance != SensorData.INVALID_SENSOR_DATA) value = Float.toString(data.illuminance);
            updateValue(lines, KEY_ILLUMINANCE, value);

            value = null;
            if (data.temperature != SensorData.INVALID_SENSOR_DATA) value = Float.toString(data.temperature);
            updateValue(lines, KEY_TEMPERATURE, value);

            value = null;
            if (data.humidity != SensorData.INVALID_SENSOR_DATA) value = Float.toString(data.humidity);
            updateValue(lines, KEY_HUMIDITY, value);

            value = null;
            if (data.pressure != SensorData.INVALID_SENSOR_DATA) value = Float.toString(data.pressure);
            updateValue(lines, KEY_PRESSURE, value);

            updateValue(lines, KEY_BATREC, "BatRecorder " + MainActivity.getVersion());

            guano = "";
            for (String s : lines) guano += s + "\n";
        }
        updateMetadata(file.getAbsolutePath(), MetaData.GUANO_NAMESPACE, guano.getBytes());
    }

     private static void updateValue(ArrayList<String> lines, String key, String value) {
        for(int ii = 0; ii < lines.size(); ii++) {
            if (lines.get(ii).startsWith(key)) {
                if (value == null)
                    lines.remove(ii);
                else
                    lines.set(ii, key+" "+value);
                return;
            }
        }
        if (value != null) lines.add(key+" "+value);
    }
 }
