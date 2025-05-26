package com.digitalbiology.audio.metadata;

public class SensorData {

    public static final float INVALID_SENSOR_DATA = -999999.9f;

    public float illuminance;
    public float temperature;
    public float pressure;
    public float humidity;

    public SensorData() {
        illuminance = INVALID_SENSOR_DATA;
        temperature = INVALID_SENSOR_DATA;
        pressure = INVALID_SENSOR_DATA;
        humidity = INVALID_SENSOR_DATA;

    }
}
