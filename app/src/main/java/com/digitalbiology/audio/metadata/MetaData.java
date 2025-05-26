package com.digitalbiology.audio.metadata;

import java.util.Date;

public class MetaData {

    public static final int UNK_NAMESPACE = 0;
    public static final int XMP_NAMESPACE = 0x584d505f;
    public static final int GUANO_NAMESPACE = 0x6e617567;
    public static final int WAMD_NAMESPACE = 0x646d6177;

    public int      namespace;
    public int      sampleRate;
    public int      timeExpansion;
    public Date     timestamp;
    public String   captureMake;
    public String   captureDevice;
    public String   species;
    public String   derivedFrom;
    public String   hostDevice;
    public String   hostOS;
    public String   software;
    public float    length;
    public double    latitude;
    public double    longitude;
    public double    elevation;
    public float    illuminance;
    public float    temperature;
    public float    pressure;
    public float    humidity;

    public MetaData() {
        namespace       = UNK_NAMESPACE;
        sampleRate      = 0;
        timeExpansion   = 1;
        timestamp       = null;
        captureMake     = null;
        captureDevice   = null;
        species         = null;
        software        = null;
        hostDevice      = null;
        hostOS          = null;
        length          = 0.0f;
        latitude        = LocationData.INVALID_GPS_COORD;
        longitude       = LocationData.INVALID_GPS_COORD;
        elevation       = LocationData.INVALID_ELEVATION;
        derivedFrom     = null;
        illuminance     = SensorData.INVALID_SENSOR_DATA;
        temperature     = SensorData.INVALID_SENSOR_DATA;
        pressure        = SensorData.INVALID_SENSOR_DATA;
        humidity        = SensorData.INVALID_SENSOR_DATA;
    }

    public MetaData(int ns) {
        namespace       = ns;
        sampleRate      = 0;
        timeExpansion   = 1;
        timestamp       = null;
        captureMake     = null;
        captureDevice   = null;
        species         = null;
        software        = null;
        hostDevice      = null;
        hostOS          = null;
        length          = 0.0f;
        latitude        = LocationData.INVALID_GPS_COORD;
        longitude       = LocationData.INVALID_GPS_COORD;
        elevation       = LocationData.INVALID_ELEVATION;
        derivedFrom     = null;
        illuminance     = SensorData.INVALID_SENSOR_DATA;
        temperature     = SensorData.INVALID_SENSOR_DATA;
        pressure        = SensorData.INVALID_SENSOR_DATA;
        humidity        = SensorData.INVALID_SENSOR_DATA;
    }
}
