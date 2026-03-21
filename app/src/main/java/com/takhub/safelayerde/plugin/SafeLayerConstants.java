package com.takhub.safelayerde.plugin;

import com.takhub.safelayerde.source.common.HttpClient.RemoteEndpointPolicy;

public final class SafeLayerConstants {

    public static final String PLUGIN_PACKAGE = "com.takhub.safelayerde";
    public static final String CACHE_DIR_NAME = "safelayer-cache";
    public static final String LEGACY_CACHE_DIR_NAME = "cache";
    public static final String BBK_BASE_URL = "https://warnung.bund.de/api31/";
    public static final String BBK_MOWAS_MAPDATA_PATH = "mowas/mapData.json";
    public static final String DWD_CAP_BASE_URL = "https://opendata.dwd.de/weather/alerts/cap/";
    public static final String DWD_CAP_COMMUNEUNION_LATEST_DIRECTORY_URL =
            DWD_CAP_BASE_URL + "COMMUNEUNION_DWD_STAT/";
    public static final String DWD_CAP_COMMUNEUNION_LATEST_DE_URL =
            DWD_CAP_COMMUNEUNION_LATEST_DIRECTORY_URL
                    + "Z_CAP_C_EDZW_LATEST_PVW_STATUS_PREMIUMDWD_COMMUNEUNION_DE.zip";
    public static final String RADAR_BASE_URL = "https://opendata.dwd.de/weather/radar/";
    public static final String DWD_WMS_BASE_URL = "https://maps.dwd.de/geoserver/dwd/ows";
    public static final String DWD_WMS_CAPABILITIES_URL =
            DWD_WMS_BASE_URL + "?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetCapabilities";
    private static final String[] BBK_API_TLS_PINS = {
            "3Z9Di+pkSBPJ1nE8sH103LjRn2k9ir9g68CMN2cvfxw=",
            "6YBE8kK4d5J1qu1wEjyoKqzEIvyRY5HyM/NB2wKdcZo="
    };
    private static final String[] DWD_CAP_TLS_PINS = {
            "iSQGUY3CYWHMiwEmFYbeEF/EfGSxf8kr/Lsg7Y+dLQk=",
            "ejFKBydz15Z1N1P+b5wA1BLK28O3vLnf7cUQW4rq3+o="
    };
    private static final String[] DWD_WMS_TLS_PINS = {
            "7rsXZEXUXJTTMnd4QJxIsko7v4lsOfKMPvFUZjNdkPE=",
            "KqkYYX5LYAYP7XGemqzbtPPIA8x7BS/BbOIcAXf3j2k="
    };
    public static final RemoteEndpointPolicy BBK_API_ENDPOINT =
            RemoteEndpointPolicy.https("BBK API", BBK_BASE_URL, BBK_API_TLS_PINS);
    public static final RemoteEndpointPolicy DWD_CAP_DIRECTORY_ENDPOINT =
            RemoteEndpointPolicy.https("DWD CAP", DWD_CAP_COMMUNEUNION_LATEST_DIRECTORY_URL, DWD_CAP_TLS_PINS);
    public static final RemoteEndpointPolicy DWD_WMS_ENDPOINT =
            RemoteEndpointPolicy.https("DWD WMS", DWD_WMS_BASE_URL, DWD_WMS_TLS_PINS);
    public static final String RADAR_RV_PRODUCT_ID = "RV";
    public static final String RADAR_RV_PRODUCT_LABEL = "DWD Regenradar RV - Niederschlagsmenge 5 Min / 1 km";
    public static final String RADAR_RV_LAYER_NAME = "Radar_rv_product_1x1km_ger";
    public static final String RADAR_RV_STYLE_NAME = "radar_rv_product_1x1km_ger";
    public static final String RADAR_RV_IMAGE_FORMAT = "image/png";
    public static final String RADAR_RV_CRS = "EPSG:4326";
    public static final String RADAR_RV_GEOREFERENCE_ID = "dwd-rv-epsg4326-germany";
    public static final long WARNING_REFRESH_INTERVAL_MS = 15L * 60L * 1000L;
    public static final long RADAR_REFRESH_INTERVAL_MS = 5L * 60L * 1000L;
    public static final int DEFAULT_RADAR_TRANSPARENCY_PERCENT = 35;

    private SafeLayerConstants() {
    }
}
