package com.digitalbiology.audio.metadata;

import com.digitalbiology.audio.Microphone;
//import com.digitalbiology.audio.NotificationToken;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MetaDataParser {

    private static Pattern sLatLonPattern = null;
    private static final DecimalFormat sCoordFormatter = new DecimalFormat("#0.0000000", new DecimalFormatSymbols(Locale.ENGLISH));

    public int getNamespace() {
        return MetaData.UNK_NAMESPACE;
    }

//    public byte[] create(Date date, float secs, Microphone microphone, LocationData location, SensorData sensors, NotificationToken token) {
//        return null;
//    }
    public byte[] create(Date date, float secs, Microphone microphone, LocationData location, SensorData sensors) {
        return null;
    }

    public MetaData read(File recording) {
        return new MetaData();
    }

    public void update(File recording, MetaData data) {
    }

    public File export(String path) {

        File file = null;
        MetaData metadata = getMetadata(new File(path));
        if (metadata != null) {
            file = new File(path.replace(".wav", ".txt"));
            try {
                PrintStream out = new PrintStream(new FileOutputStream(file));

                if (metadata.timestamp != null) out.print("TIMESTAMP: " + metadata.timestamp + "\n");
                if (metadata.captureDevice != null) {
                    String device;
                    if (metadata.captureMake != null)
                        device = metadata.captureMake + " " + metadata.captureDevice;
                    else
                        device = metadata.captureDevice;
                    out.print("CAPTURE DEVICE: " + device + "\n");
                }
                if (metadata.length > 0) out.print("LENGTH (SECS): " + metadata.length + "\n");
                if (metadata.sampleRate != 0) out.print("SAMPLING RATE: " + metadata.sampleRate + "\n");
                if (metadata.timeExpansion != 1)
                    out.print("TIME EXPANSION: " + metadata.timeExpansion + "\n");
                if (metadata.derivedFrom != null)
                    out.print("DERIVED FROM: " + metadata.derivedFrom + "\n");
                if (metadata.hostDevice != null) out.print("HOST DEVICE: " + metadata.hostDevice + "\n");
                if (metadata.hostOS != null) out.print("HOST OS: " + metadata.hostOS + "\n");
                if (metadata.species != null) out.print("MANUAL SPECIES ID: " + metadata.species + "\n");
                if ((metadata.latitude != LocationData.INVALID_GPS_COORD) && (metadata.longitude != LocationData.INVALID_GPS_COORD)) {
                    double lat = Math.abs(metadata.latitude);
                    double lon = Math.abs(metadata.longitude);
                    out.print("GPS LATITUDE: " + sCoordFormatter.format(lat) + " " + ((metadata.latitude < 0.0f) ? 'S' : 'N') + "\n");
                    out.print("GPS LONGITUDE: " + sCoordFormatter.format(lon) + " " + ((metadata.longitude < 0.0f) ? 'W' : 'E') + "\n");
                }
                if (metadata.elevation != LocationData.INVALID_ELEVATION) out.print("GPS ALTITUDE: " + metadata.elevation + "\n");

                if (metadata.illuminance != SensorData.INVALID_SENSOR_DATA) out.print("ILLUMINANCE: " + metadata.illuminance + " lx\n");
                if (metadata.temperature != SensorData.INVALID_SENSOR_DATA) out.print("TEMPERATURE: " + metadata.temperature + " °C\n");
                if (metadata.humidity != SensorData.INVALID_SENSOR_DATA) out.print("RELATIVE HUMIDITY: " + metadata.humidity + " %\n");
                if (metadata.pressure != SensorData.INVALID_SENSOR_DATA) out.print("PRESSURE: " + metadata.pressure + " mbar\n");

                out.close();
            } catch (Exception e) {
            }
        }
        return file;
    }

    public static MetaData getMetadata(File file) {
        int namespace = MetaDataParser.getMetadataNamespace(file.getAbsolutePath());
        MetaData metadata;
        if (namespace == MetaData.XMP_NAMESPACE)
            metadata = new XMPMetaDataParser().read(file);
        else if (namespace == MetaData.WAMD_NAMESPACE)
            metadata = new WAMDMetaDataParser().read(file);
        else
            metadata = new GUANOMetaDataParser().read(file);
        return metadata;
    }

    public static String formatLatLon(String latlon) {

        if (sLatLonPattern == null) {
            sLatLonPattern = Pattern.compile("(\\d+)\\,(\\d+\\.\\d+)(\\D)");
//			sSecondsFormatter = new DecimalFormat("#0.000");
        }
        Matcher m = sLatLonPattern.matcher(latlon);
        if (m.matches()) {
            double val = Double.parseDouble(m.group(2));
            long minutes = (long) val;
//		    double seconds = (val - minutes) * 60.0f;
            long seconds = Math.round((val - minutes) * 60.0);
            return m.group(1) + "\u00b0 " + Long.toString(minutes) + "\' " + Long.toString(seconds) + "\" " + m.group(3);
        }
        return latlon;
    }

    public static String latitude2String(double latitude) {

        double val = Math.abs(latitude);
        int degrees = (int) val;
        double remainder = (val - degrees) * 60.0;
        int minutes = (int) remainder;
        int seconds = (int) ((remainder - minutes) * 60.0);
        String latString = Integer.toString(degrees) + "\u00b0 " + Integer.toString(minutes) + "\' " + Integer.toString(seconds) + "\" ";
        if (latitude < 0)
            latString += "S";
        else
            latString += "N";
        return latString;
    }

    public static String longitude2String(double longitude) {

        double val = Math.abs(longitude);
        int degrees = (int) val;
        double remainder = (val - degrees) * 60.0;
        int minutes = (int) remainder;
        int seconds = (int) ((remainder - minutes) * 60.0);
        String lonString = Integer.toString(degrees) + "\u00b0 " + Integer.toString(minutes) + "\' " + Integer.toString(seconds) + "\" ";
        if (longitude < 0)
            lonString += "W";
        else
            lonString += "E";
        return lonString;
    }

    public static double convertLatLon(String latlon) {

        double coord = LocationData.INVALID_GPS_COORD;
        if (sLatLonPattern == null) {
            sLatLonPattern = Pattern.compile("(\\d+)\\,(\\d+\\.\\d+)(\\D)");
//			sSecondsFormatter = new DecimalFormat("#0.000");
        }
        if (!latlon.isEmpty()) {
            Matcher m = sLatLonPattern.matcher(latlon);
            if (m.matches()) {
                coord = Integer.parseInt(m.group(1)) + Double.parseDouble(m.group(2)) / 60.0;
                if (m.group(3).equals("W") || m.group(3).equals("S")) coord = -coord;
            }
        }
        return coord;
    }

    public native static int getMetadataNamespace(String path);
    protected native static byte[] readMetadata(String path, int namespace);
    protected native static void updateMetadata(String path, int namespace, byte[] metadata);
}
