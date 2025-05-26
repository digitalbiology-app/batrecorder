package com.digitalbiology.audio.metadata;

public class LocationData {

    public static final double INVALID_GPS_COORD = 10000;
    public static final double INVALID_ELEVATION = -10000;

    public long timestamp;
    public double latitude;
    public double longitude;
    public double elevation;
}
