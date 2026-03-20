package com.takhub.safelayerde.source.radar;

import com.takhub.safelayerde.debug.SafeLayerDebugLog;
import com.takhub.safelayerde.domain.model.RadarFrame;

import java.io.IOException;

public class DwdRadarDecoder {

    private static final String TAG = "SafeLayerRadarDecode";

    public RadarFrame decode(DwdRadarFetcher.FetchResult fetchResult, long fetchedAtEpochMs)
            throws InvalidRadarFrameException {
        String productId = fetchResult == null || fetchResult.getProduct() == null
                ? "unknown"
                : fetchResult.getProduct().getProductId();
        SafeLayerDebugLog.i(TAG, "decode-start productId=" + productId
                + ", frameEpochMs=" + (fetchResult == null ? 0L : fetchResult.getFrameEpochMs())
                + ", bytes=" + (fetchResult == null || fetchResult.getImageBytes() == null
                ? 0
                : fetchResult.getImageBytes().length));
        try {
            if (fetchResult == null || fetchResult.getProduct() == null) {
                throw new InvalidRadarFrameException("Radar fetch result is empty.");
            }

            DwdRadarProduct product = fetchResult.getProduct();
            byte[] imageBytes = fetchResult.getImageBytes();
            if (imageBytes.length < 24) {
                throw new InvalidRadarFrameException("Radar image is empty.");
            }

            if (!product.getImageFormat().equalsIgnoreCase(fetchResult.getImageFormat())) {
                throw new InvalidRadarFrameException("Unsupported radar image format: " + fetchResult.getImageFormat());
            }
            if (fetchResult.getFrameEpochMs() <= 0L) {
                throw new InvalidRadarFrameException("Radar frame timestamp is missing.");
            }

            int width = readPngInt(imageBytes, 16);
            int height = readPngInt(imageBytes, 20);
            if (width != product.getWidth() || height != product.getHeight()) {
                throw new InvalidRadarFrameException(
                        "Unexpected radar image dimensions: " + width + "x" + height);
            }

            RadarFrame radarFrame = new RadarFrame();
            radarFrame.setFrameId(product.buildFrameId(fetchResult.getFrameEpochMs()));
            radarFrame.setProductId(product.getProductId());
            radarFrame.setProductLabel(product.getProductLabel());
            radarFrame.setFrameEpochMs(fetchResult.getFrameEpochMs());
            radarFrame.setFetchedAtEpochMs(fetchedAtEpochMs);
            radarFrame.setImageFormat(fetchResult.getImageFormat());
            radarFrame.setRequestUrl(fetchResult.getRequestUrl());
            radarFrame.setCrs(product.getCrs());
            radarFrame.setGeoreferenceId(product.getGeoreferenceId());
            radarFrame.setMinLatitude(product.getMinLatitude());
            radarFrame.setMinLongitude(product.getMinLongitude());
            radarFrame.setMaxLatitude(product.getMaxLatitude());
            radarFrame.setMaxLongitude(product.getMaxLongitude());
            radarFrame.setWidth(width);
            radarFrame.setHeight(height);
            radarFrame.setDataBytes(imageBytes);
            radarFrame.setValid(true);
            SafeLayerDebugLog.i(TAG, "decode-success frameId=" + radarFrame.getFrameId()
                    + ", productId=" + radarFrame.getProductId()
                    + ", width=" + radarFrame.getWidth()
                    + ", height=" + radarFrame.getHeight());
            return radarFrame;
        } catch (InvalidRadarFrameException exception) {
            SafeLayerDebugLog.e(TAG, "decode-failed productId=" + productId, exception);
            throw exception;
        } catch (RuntimeException exception) {
            SafeLayerDebugLog.e(TAG, "decode-runtime-failed productId=" + productId, exception);
            throw exception;
        }
    }

    private int readPngInt(byte[] bytes, int offset) throws InvalidRadarFrameException {
        if (bytes == null || bytes.length < offset + 4 || !isPng(bytes)) {
            throw new InvalidRadarFrameException("Radar image is not a valid PNG.");
        }
        return ((bytes[offset] & 0xff) << 24)
                | ((bytes[offset + 1] & 0xff) << 16)
                | ((bytes[offset + 2] & 0xff) << 8)
                | (bytes[offset + 3] & 0xff);
    }

    private boolean isPng(byte[] bytes) {
        return bytes != null
                && bytes.length >= 8
                && bytes[0] == (byte) 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4e
                && bytes[3] == 0x47
                && bytes[4] == 0x0d
                && bytes[5] == 0x0a
                && bytes[6] == 0x1a
                && bytes[7] == 0x0a;
    }

    public static class InvalidRadarFrameException extends IOException {

        public InvalidRadarFrameException(String message) {
            super(message);
        }
    }
}
