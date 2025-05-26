package com.digitalbiology.audio.metadata;

import com.adobe.xmp.XMPException;
import com.adobe.xmp.XMPMeta;
import com.adobe.xmp.XMPMetaFactory;
import com.adobe.xmp.XMPSchemaRegistry;
import com.adobe.xmp.options.SerializeOptions;
import com.adobe.xmp.properties.XMPProperty;
import com.digitalbiology.audio.MainActivity;
import com.digitalbiology.audio.Microphone;
//import com.digitalbiology.audio.NotificationToken;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class XMPMetaDataParser extends MetaDataParser {

    private static final String NS_DWC = "http://rs.tdwg.org/dwc/terms/";
    private static final String NS_XMP = "http://ns.adobe.com/xap/1.0/";
    private static final String NS_EXIF = "http://ns.adobe.com/exif/1.0/";
    private static final String NS_AC = "http://rs.tdwg.org/ac/terms/";

    private static final String PROP_CREATOR_TOOL = "xmp:CreatorTool";
    private static final String PROP_CREATE_DATE = "xmp:CreateDate";
    private static final String PROP_GPSVERSION = "exif:GPSVersionID";
    private static final String PROP_LATITUDE = "exif:GPSLatitude";
    private static final String PROP_LONGITUDE = "exif:GPSLongitude";
    private static final String PROP_ALTITUDE = "exif:GPSAltitude";
    private static final String PROP_CAPTURE_DEVICE = "ac:captureDevice";
    private static final String PROP_SPECIES = "dwc:scientificName";
    private static final String PROP_DERIVED_FROM = "ac:derivedFrom";
    private static final String PROP_ILLUMANCE = "exif:AmbientLight";
    private static final String PROP_TEMPERATURE = "exif:AmbientTemperature";
    private static final String PROP_HUMIDITY = "exif:Humidity";
    private static final String PROP_PRESSURE = "exif:Pressure";

    private static final SimpleDateFormat sDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

    public int getNamespace() {
        return MetaData.XMP_NAMESPACE;
    }

//    public byte[] create(Date date, float secs, Microphone microphone, LocationData location, SensorData sensors, NotificationToken token) {
      public byte[] create(Date date, float secs, Microphone microphone, LocationData location, SensorData sensors) {

        String xmp = "<?xpacket begin=\"?\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?><x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">";
        xmp += "<rdf:Description rdf:about=\"\" xmlns:xmp=\"http://ns.adobe.com/xap/1.0/\"><xmp:CreateDate>"
                + sDateFormat.format(date) + "</xmp:CreateDate><xmp:CreatorTool>BatRecorder "
                + MainActivity.getVersion()
                + "</xmp:CreatorTool></rdf:Description>";
        xmp += "<rdf:Description rdf:about=\"\" xmlns:ac=\"http://rs.tdwg.org/ac/terms/\"><ac:captureDevice>"
                + microphone.getProductName()
                + "</ac:captureDevice></rdf:Description>";
        if (location != null) {
            double latitude = location.latitude;
            double longitude = location.longitude;
            double lat = Math.abs(latitude);
            double lon = Math.abs(longitude);
            String latString = (int) lat + "," + (lat - (int) lat) * 60.0f + ((latitude < 0.0f) ? 'S' : 'N');
            String lonString = (int) lon + "," + (lon - (int) lon) * 60.0f + ((longitude < 0.0f) ? 'W' : 'E');
            xmp += "<rdf:Description rdf:about=\"\" xmlns:exif=\"http://ns.adobe.com/exif/1.0/\"><exif:GPSVersionID>2.2.0.0</exif:GPSVersionID><exif:GPSLatitude>"
                    + latString
                    + "</exif:GPSLatitude><exif:GPSLongitude>"
                    + lonString
                    + "</exif:GPSLongitude>";
            if (location.elevation != LocationData.INVALID_ELEVATION) xmp += "<exif:GPSAltitude>" + location.elevation + "</exif:GPSAltitude>";
            xmp += "</rdf:Description>";
//            if (token != null) {
//                token.latitude = latString;
//                token.longitude = lonString;
//            }
        }
        if (sensors != null) {
            xmp += "<rdf:Description rdf:about=\"\" xmlns:exif=\"http://ns.adobe.com/exif/1.0/\">";
            if (sensors.illuminance != SensorData.INVALID_SENSOR_DATA) xmp += "<exif:AmbientLight>" + sensors.illuminance + "</exif:AmbientLight>";
            if (sensors.temperature != SensorData.INVALID_SENSOR_DATA) xmp += "<exif:AmbientTemperature>" + sensors.temperature + "</exif:AmbientTemperature>";
            if (sensors.humidity != SensorData.INVALID_SENSOR_DATA) xmp += "<exif:Humidity>" + sensors.humidity + "</exif:Humidity>";
            if (sensors.pressure != SensorData.INVALID_SENSOR_DATA) xmp += "<exif:Pressure>" + sensors.pressure + "</exif:Pressure>";
            xmp += "</rdf:Description>";
        }
//        if (token != null) {
//            token.timestamp = date;
//            NotificationToken.getNotifications().add(token);
//        }
        xmp += "</rdf:RDF></x:xmpmeta><?xpacket end=\"w\"?>";
        return xmp.getBytes();
    }

    public MetaData read(File file) {

        MetaData metadata = null;
        byte[] bytes = MetaDataParser.readMetadata(file.getAbsolutePath(), MetaData.XMP_NAMESPACE);
        if (bytes != null) {
//            Log.d("XMP", new String(bytes));
            metadata = new MetaData(MetaData.XMP_NAMESPACE);
            try {
                XMPMeta xmpMeta = XMPMetaFactory.parse(new ByteArrayInputStream(bytes));
                if (xmpMeta != null) {
                    XMPProperty xmp = xmpMeta.getProperty(NS_XMP, PROP_CREATE_DATE);
                    if (xmp != null) {
                        try {
                            metadata.timestamp = sDateFormat.parse(xmp.getValue());
                        }
                        catch (ParseException e) {
                            try {
                                metadata.timestamp = new SimpleDateFormat().parse(xmp.getValue());
                            }
                            catch (ParseException e1) {
                                metadata.timestamp = new Date(file.lastModified());
                            }
                        }
                    }

                    xmp = xmpMeta.getProperty(NS_XMP, PROP_CREATOR_TOOL);
                    if (xmp != null) metadata.software = xmp.getValue();

                    xmp = xmpMeta.getProperty(NS_AC, PROP_CAPTURE_DEVICE);
                    if (xmp != null) metadata.captureDevice = xmp.getValue();

                    xmp = xmpMeta.getProperty(NS_AC, PROP_DERIVED_FROM);
                    if (xmp != null) metadata.derivedFrom = xmp.getValue();

                    XMPSchemaRegistry registry = XMPMetaFactory.getSchemaRegistry();
                    registry.registerNamespace(NS_DWC, "dwc:");
                    xmp = xmpMeta.getProperty(NS_DWC, PROP_SPECIES);
                    if (xmp != null) metadata.species = xmp.getValue();

                    XMPProperty xmpLat = xmpMeta.getProperty(NS_EXIF, PROP_LATITUDE);
                    XMPProperty xmpLon = xmpMeta.getProperty(NS_EXIF, PROP_LONGITUDE);
                    if (xmpLat != null && xmpLon != null) {
                        metadata.latitude = MetaDataParser.convertLatLon(xmpLat.getValue());
                        metadata.longitude = MetaDataParser.convertLatLon(xmpLon.getValue());
                    }
                    xmp = xmpMeta.getProperty(NS_EXIF, PROP_ALTITUDE);
                    if (xmp != null) metadata.elevation = Double.parseDouble(xmp.getValue());

                    xmp = xmpMeta.getProperty(NS_EXIF, PROP_ILLUMANCE);
                    if (xmp != null) metadata.illuminance = Float.parseFloat(xmp.getValue());

                    xmp = xmpMeta.getProperty(NS_EXIF, PROP_TEMPERATURE);
                    if (xmp != null) metadata.temperature = Float.parseFloat(xmp.getValue());

                    xmp = xmpMeta.getProperty(NS_EXIF, PROP_HUMIDITY);
                    if (xmp != null) metadata.humidity = Float.parseFloat(xmp.getValue());

                    xmp = xmpMeta.getProperty(NS_EXIF, PROP_PRESSURE);
                    if (xmp != null) metadata.pressure = Float.parseFloat(xmp.getValue());

                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return metadata;
    }

    public void update(File file, MetaData data) {

        XMPMeta xmpMeta = null;
        byte[] bytes = MetaDataParser.readMetadata(file.getAbsolutePath(), MetaData.XMP_NAMESPACE);
        if (bytes != null) {
            try {
                xmpMeta = XMPMetaFactory.parse(new ByteArrayInputStream(bytes));
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (xmpMeta == null) xmpMeta = XMPMetaFactory.create();

        try {
            XMPSchemaRegistry registry = XMPMetaFactory.getSchemaRegistry();
            registry.registerNamespace(NS_XMP, "xmp:");
            registry.registerNamespace(NS_EXIF, "exif:");
            registry.registerNamespace(NS_AC, "ac:");
            registry.registerNamespace(NS_DWC, "dwc:");

            if (data.timestamp != null) xmpMeta.setProperty(NS_XMP, PROP_CREATE_DATE, sDateFormat.format(data.timestamp));
            xmpMeta.setProperty(NS_XMP, PROP_CREATOR_TOOL, "BatRecorder " + MainActivity.getVersion());
            if (data.latitude != LocationData.INVALID_GPS_COORD && data.longitude != LocationData.INVALID_GPS_COORD) {
                xmpMeta.setProperty(NS_EXIF, PROP_GPSVERSION, "2.2.0.0");
                double lat = Math.abs(data.latitude);
                double lon = Math.abs(data.longitude);
                String latString = (int) lat + "," + (lat - (int) lat) * 60.0f + ((data.latitude < 0.0f) ? 'S' : 'N');
                String lonString = (int) lon + "," + (lon - (int) lon) * 60.0f + ((data.longitude < 0.0f) ? 'W' : 'E');
                xmpMeta.setProperty(NS_EXIF, PROP_LATITUDE, latString);
                xmpMeta.setProperty(NS_EXIF, PROP_LONGITUDE, lonString);
            }
            if (data.elevation != LocationData.INVALID_ELEVATION) xmpMeta.setProperty(NS_EXIF, PROP_ALTITUDE, Double.toString(data.elevation));

            if (data.illuminance != SensorData.INVALID_SENSOR_DATA) xmpMeta.setProperty(NS_EXIF, PROP_ILLUMANCE, Float.toString(data.illuminance));
            if (data.temperature != SensorData.INVALID_SENSOR_DATA) xmpMeta.setProperty(NS_EXIF, PROP_TEMPERATURE, Float.toString(data.temperature));
            if (data.humidity != SensorData.INVALID_SENSOR_DATA) xmpMeta.setProperty(NS_EXIF, PROP_HUMIDITY, Float.toString(data.humidity));
            if (data.pressure != SensorData.INVALID_SENSOR_DATA) xmpMeta.setProperty(NS_EXIF, PROP_PRESSURE, Float.toString(data.pressure));

            if (data.captureDevice != null) xmpMeta.setProperty(NS_AC, PROP_CAPTURE_DEVICE, data.captureDevice);
            if (data.derivedFrom != null) xmpMeta.setProperty(NS_AC, PROP_DERIVED_FROM, data.derivedFrom);
            if (data.species != null) xmpMeta.setProperty(NS_DWC, PROP_SPECIES, data.species);

            SerializeOptions options = new SerializeOptions();
            options.setUseCanonicalFormat(true);
            String xmpString = XMPMetaFactory.serializeToString(xmpMeta, options);
            MetaDataParser.updateMetadata(file.getAbsolutePath(), MetaData.XMP_NAMESPACE, xmpString.getBytes());

        } catch (XMPException e) {
            e.printStackTrace();
        }
    }
}
