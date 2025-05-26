package com.digitalbiology.audio.metadata;

import android.os.Build;

import com.digitalbiology.audio.MainActivity;
import com.digitalbiology.audio.Microphone;
//import com.digitalbiology.audio.NotificationToken;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public class WAMDMetaDataParser extends MetaDataParser {

    private static final int BUFFER_SIZE                = 1000;

    private static final short META_VERSION            = 1;

    private static final short METATAG_VERSION         = 0;
    private static final short METATAG_DEV_MODEL       = 1;
    private static final short METATAG_DEV_NAME        = 4;
    private static final short METATAG_FILE_START_TIME = 5;
    private static final short METATAG_GPS_FIRST       = 6;
    private static final short METATAG_SOFTWARE        = 8;
    private static final short METATAG_MANUAL_ID       = 12;
    private static final short METATAG_MIC_TYPE        = 18;
    private static final short METATAG_TEMP_EXT        = 22;
    private static final short METATAG_HUMIDITY        = 23;
    private static final short METATAG_LIGHT           = 24;
    private static final short METATAG_PRESSURE        = 25;

    private static final DecimalFormat sLengthFormatter = new DecimalFormat("#0.000000", new DecimalFormatSymbols(Locale.ENGLISH));
    private static final DecimalFormat sCoordFormatter = new DecimalFormat("#0.00000", new DecimalFormatSymbols(Locale.ENGLISH));
    private static final SimpleDateFormat sDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ");

    public int getNamespace() {
        return MetaData.WAMD_NAMESPACE;
    }

//    public byte[] create(Date date, float secs, Microphone microphone, LocationData location, SensorData sensors, NotificationToken token) {
    public byte[] create(Date date, float secs, Microphone microphone, LocationData location, SensorData sensors) {

        ByteBuffer b = ByteBuffer.allocate(BUFFER_SIZE);
        b.order(ByteOrder.LITTLE_ENDIAN);

        b.putShort(METATAG_VERSION);
        b.putInt(2);
        b.putShort(META_VERSION);

        String text;
        text = Build.MANUFACTURER + " " + Build.MODEL;
        int len = text.length();
        b.putShort(METATAG_DEV_NAME);
        b.putInt(len);
        b.put(text.getBytes());

        text = microphone.getManufacturerName() + " " + microphone.getProductName();
        len = text.length();
        b.putShort(METATAG_DEV_MODEL);
        b.putInt(len);
        b.put(text.getBytes());

        b.putShort(METATAG_MIC_TYPE);
        b.putInt(len);
        b.put(text.getBytes());

        text = "BatRecorder "+ MainActivity.getVersion();
        len = text.length();
        b.putShort(METATAG_SOFTWARE);
        b.putInt(len);
        b.put(text.getBytes());

        text = sDateFormat.format(date);
        b.putShort(METATAG_FILE_START_TIME);
        b.putInt(text.length());
        b.put(text.getBytes());

        if (location != null) {
            double latitude = location.latitude;
            double longitude = location.longitude;
            double lat = Math.abs(latitude);
            double lon = Math.abs(longitude);
            text = "WGS84," + sCoordFormatter.format(lat) + "," + ((latitude < 0.0f) ? 'S' : 'N') + "," + sCoordFormatter.format(lon) + "," + ((longitude < 0.0f) ? 'W' : 'E');
            if (location.elevation != LocationData.INVALID_ELEVATION) text += ","+ Double.toString(location.elevation);
            len = text.length();
            b.putShort(METATAG_GPS_FIRST);
            b.putInt(len);
            b.put(text.getBytes());
//            if (token != null) {
//                token.latitude = (int) lat + "," + (lat - (int) lat) * 60.0f + ((latitude < 0.0f) ? 'S' : 'N');
//                token.longitude = (int) lon + "," + (lon - (int) lon) * 60.0f + ((longitude < 0.0f) ? 'W' : 'E');
//            }
        }
        if (sensors != null) {
            if (sensors.temperature != SensorData.INVALID_SENSOR_DATA) {
                text = sensors.temperature + "C";
                b.putShort(METATAG_TEMP_EXT);
                b.putInt(text.length());
                b.put(text.getBytes());
            }
            if (sensors.humidity != SensorData.INVALID_SENSOR_DATA) {
                text = sensors.humidity + "%RH";
                b.putShort(METATAG_HUMIDITY);
                b.putInt(text.length());
                b.put(text.getBytes());
            }
            if (sensors.illuminance != SensorData.INVALID_SENSOR_DATA) {
                text = sensors.illuminance + "lx";
                b.putShort(METATAG_LIGHT);
                b.putInt(text.length());
                b.put(text.getBytes());
            }
            if (sensors.pressure != SensorData.INVALID_SENSOR_DATA) {
                text = sensors.pressure + "mbar";
                b.putShort(METATAG_PRESSURE);
                b.putInt(text.length());
                b.put(text.getBytes());
            }
        }
//        if (token != null) {
//            token.timestamp = date;
//            NotificationToken.getNotifications().add(token);
//        }
        return Arrays.copyOfRange(b.array(), 0, BUFFER_SIZE - b.remaining());
    }

    public MetaData read(File file) {

        MetaData metadata = null;
        byte[] bytes = MetaDataParser.readMetadata(file.getAbsolutePath(), MetaData.WAMD_NAMESPACE);
        if (bytes != null) {

            metadata = new MetaData(MetaData.WAMD_NAMESPACE);

            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            while (buffer.remaining() > 0) {
                // get next field
                short fieldID = buffer.getShort();
                int fieldLength = buffer.getInt();
                byte[] value = new byte[fieldLength];
                buffer.get(value);
                if (fieldID == METATAG_FILE_START_TIME) {
                    try {
                        metadata.timestamp = sDateFormat.parse(new String(value));
                    }
                    catch (ParseException e) {
                        metadata.timestamp = null;
                    }
                }
                else if (fieldID == METATAG_SOFTWARE) {
                    metadata.software = new String(value);
                }
                else if (fieldID == METATAG_MANUAL_ID) {
                    metadata.species = new String(value);
                }
                else if (fieldID == METATAG_DEV_MODEL) {
                    metadata.captureDevice = new String(value);
                }
                else if (fieldID == METATAG_MIC_TYPE) {
                    metadata.captureDevice = new String(value);
                }
                else if (fieldID == METATAG_DEV_NAME) {
                    metadata.hostDevice = new String(value);
                }
                else if (fieldID == METATAG_GPS_FIRST) {
                    String[] tokens = new String(value).split(",");
                    if (tokens.length > 4) {
                        metadata.latitude = Double.parseDouble(tokens[1]);
                        if (tokens[2].equals("S")) metadata.latitude = -metadata.latitude;
                        metadata.longitude = Double.parseDouble(tokens[3]);
                        if (tokens[4].equals("W")) metadata.longitude = -metadata.longitude;
                        if (tokens.length == 6) metadata.elevation = Double.parseDouble(tokens[5]);
                    }
                    else if (tokens.length > 1){
                        metadata.latitude = Double.parseDouble(tokens[1]);
                        metadata.longitude = Double.parseDouble(tokens[2]);
                    }
                }
                else if (fieldID == METATAG_TEMP_EXT) {
                    String[] tmp = new String(value).split("(?<=\\d)\\s*(?=[a-zA-Z])");
                    metadata.temperature = Float.parseFloat(tmp[0]);
                    if (tmp.length > 1 && tmp[1].equalsIgnoreCase("F")) metadata.temperature = (metadata.temperature - 32.0f) * 5.0f / 9.0f;
                }
                else if (fieldID == METATAG_HUMIDITY) {
                    String[] tmp = new String(value).split("(?<=\\d)\\s*(?=[%a-zA-Z])");
                    metadata.humidity = Float.parseFloat(tmp[0]);
                }
                else if (fieldID == METATAG_LIGHT) {
                    String[] tmp = new String(value).split("(?<=\\d)\\s*(?=[a-zA-Z])");
                    metadata.illuminance = Float.parseFloat(tmp[0]);
                }
                else if (fieldID == METATAG_PRESSURE) {
                    String[] tmp = new String(value).split("(?<=\\d)\\s*(?=[a-zA-Z])");
                    metadata.pressure = Float.parseFloat(tmp[0]);
                }
            }
        }
        return metadata;
    }

    public void update(File file, MetaData data) {

        ByteBuffer b = ByteBuffer.allocate(BUFFER_SIZE);
        b.order(ByteOrder.LITTLE_ENDIAN);

        String text;

        b.putShort(METATAG_VERSION);
        b.putInt(2);
        b.putShort(META_VERSION);

        if (data.captureDevice != null) {
            text = data.captureDevice;
            b.putShort(METATAG_DEV_MODEL);
            b.putInt(text.length());
            b.put(text.getBytes());
            b.putShort(METATAG_MIC_TYPE);
            b.putInt(text.length());
            b.put(text.getBytes());
        }

        if (data.hostDevice != null) {
            text = data.hostDevice;
            b.putShort(METATAG_DEV_NAME);
            b.putInt(text.length());
            b.put(text.getBytes());
        }

        if (data.software != null) {
            text = data.software;
            b.putShort(METATAG_SOFTWARE);
            b.putInt(text.length());
            b.put(text.getBytes());
        }

        if (data.timestamp != null) {
            text = sDateFormat.format(data.timestamp);
            b.putShort(METATAG_FILE_START_TIME);
            b.putInt(text.length());
            b.put(text.getBytes());
        }

        if ((data.latitude != LocationData.INVALID_GPS_COORD) && (data.longitude != LocationData.INVALID_GPS_COORD)) {
            double lat = Math.abs(data.latitude);
            double lon = Math.abs(data.longitude);
            text = "WGS84," + sCoordFormatter.format(lat) + "," + ((data.latitude < 0.0f) ? 'S' : 'N') + "," + sCoordFormatter.format(lon) + "," + ((data.longitude < 0.0f) ? 'W' : 'E');
            if (data.elevation != LocationData.INVALID_ELEVATION) text += "," + Double.toString(data.elevation);
            b.putShort(METATAG_GPS_FIRST);
            b.putInt(text.length());
            b.put(text.getBytes());
        }

        if (data.species != null) {
            text = data.species;
            b.putShort(METATAG_MANUAL_ID);
            b.putInt(text.length());
            b.put(text.getBytes());
        }

        if (data.temperature != SensorData.INVALID_SENSOR_DATA) {
            text = data.temperature + "C";
            b.putShort(METATAG_TEMP_EXT);
            b.putInt(text.length());
            b.put(text.getBytes());
        }
        if (data.humidity != SensorData.INVALID_SENSOR_DATA) {
            text = data.humidity + "%RH";
            b.putShort(METATAG_HUMIDITY);
            b.putInt(text.length());
            b.put(text.getBytes());
        }
        if (data.illuminance != SensorData.INVALID_SENSOR_DATA) {
            text = data.illuminance + "lx";
            b.putShort(METATAG_LIGHT);
            b.putInt(text.length());
            b.put(text.getBytes());
        }
        if (data.pressure != SensorData.INVALID_SENSOR_DATA) {
            text = data.pressure + "mbar";
            b.putShort(METATAG_PRESSURE);
            b.putInt(text.length());
            b.put(text.getBytes());
        }

        byte[] wamd = Arrays.copyOfRange(b.array(), 0, BUFFER_SIZE - b.remaining());
        MetaDataParser.updateMetadata(file.getAbsolutePath(), MetaData.WAMD_NAMESPACE, wamd);
    }
}
