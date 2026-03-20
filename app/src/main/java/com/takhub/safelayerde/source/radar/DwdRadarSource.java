package com.takhub.safelayerde.source.radar;

import android.util.Log;

import com.takhub.safelayerde.cache.CacheStore;
import com.takhub.safelayerde.debug.SafeLayerDebugLog;
import com.takhub.safelayerde.domain.model.RadarFrame;
import com.takhub.safelayerde.domain.model.SourceState;
import com.takhub.safelayerde.domain.service.DataAgeService;
import com.takhub.safelayerde.source.common.SourceAdapter;
import com.takhub.safelayerde.source.common.SourceClock;
import com.takhub.safelayerde.source.common.SourceRefreshFinalizer;
import com.takhub.safelayerde.source.common.SourceRefreshResult;
import com.takhub.safelayerde.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

public class DwdRadarSource implements SourceAdapter {

    private static final String TAG = "SafeLayerRadarSource";

    private final DwdRadarFetcher fetcher;
    private final DwdRadarDecoder decoder;
    private final CacheStore cacheStore;
    private final SourceClock sourceClock;
    private final DwdRadarProduct product;
    private final SourceRefreshFinalizer refreshFinalizer;

    private RadarFrame lastFrame;
    private File sessionFrameFile;

    public DwdRadarSource(
            DwdRadarFetcher fetcher,
            DwdRadarDecoder decoder,
            CacheStore cacheStore,
            SourceClock sourceClock,
            DwdRadarProduct product) {
        this(fetcher, decoder, cacheStore, sourceClock, product, new DataAgeService());
    }

    DwdRadarSource(
            DwdRadarFetcher fetcher,
            DwdRadarDecoder decoder,
            CacheStore cacheStore,
            SourceClock sourceClock,
            DwdRadarProduct product,
            DataAgeService dataAgeService) {
        this.fetcher = fetcher;
        this.decoder = decoder;
        this.cacheStore = cacheStore;
        this.sourceClock = sourceClock;
        this.product = product == null ? DwdRadarProduct.RV : product;
        this.refreshFinalizer = new SourceRefreshFinalizer(cacheStore, dataAgeService);
    }

    public RadarFrame loadFromCache() {
        SafeLayerDebugLog.i(TAG, "cache-restore-start productId=" + product.getProductId());
        RadarFrame cachedFrame = cacheStore == null ? null : cacheStore.readRadarFrame();
        if (cachedFrame != null && product.getProductId().equals(cachedFrame.getProductId())) {
            lastFrame = cachedFrame;
            SafeLayerDebugLog.i(TAG, "cache-restore-success " + describeFrame(cachedFrame));
            return lastFrame;
        }
        if (cachedFrame != null) {
            SafeLayerDebugLog.w(TAG, "cache-restore-skip reason=product-mismatch expected="
                    + product.getProductId() + ", actual=" + StringUtils.trimToNull(cachedFrame.getProductId()));
        } else {
            SafeLayerDebugLog.w(TAG, "cache-restore-miss productId=" + product.getProductId());
        }
        return null;
    }

    @Override
    public SourceRefreshResult refresh() {
        long now = sourceClock.nowMs();
        String stage = "capabilities-fetch";
        SafeLayerDebugLog.i(TAG, "refresh-start productId=" + product.getProductId());
        try {
            stage = "capabilities-fetch";
            DwdRadarFetcher.FetchResult fetchResult = fetcher.fetchLatestFrame(product);
            SafeLayerDebugLog.i(TAG, "refresh-fetch-success productId=" + product.getProductId()
                    + ", frameEpochMs=" + fetchResult.getFrameEpochMs()
                    + ", bytes=" + fetchResult.getImageBytes().length);
            stage = "decode";
            RadarFrame radarFrame = decoder.decode(fetchResult, now);
            SafeLayerDebugLog.i(TAG, "refresh-decode-success " + describeFrame(radarFrame));
            stage = "materialize-renderable";
            prepareRenderableFrame(radarFrame);
            SourceRefreshResult result = refreshFinalizer.finalizeRadarRefresh(
                    radarFrame,
                    lastFrame,
                    true,
                    now,
                    product,
                    Collections.<String>emptyList());
            lastFrame = result.getRadarFrame();
            return result;
        } catch (DwdRadarDecoder.InvalidRadarFrameException exception) {
            return fallbackResult(stage, exception, now);
        } catch (IOException exception) {
            return fallbackResult(stage, exception, now);
        } catch (RuntimeException exception) {
            return fallbackResult(stage, exception, now);
        }
    }

    public DwdRadarProduct getProduct() {
        return product;
    }

    private void prepareRenderableFrame(RadarFrame radarFrame) throws IOException {
        if (radarFrame == null || !radarFrame.hasImageBytes()) {
            throw new IOException("Radar frame is missing image bytes.");
        }
        SafeLayerDebugLog.i(TAG, "renderable-materialize-start " + describeFrame(radarFrame));
        sessionFrameFile = DwdRadarFrameStore.materializeSessionFrame(
                radarFrame,
                sessionFrameFile,
                product.getProductId());
        SafeLayerDebugLog.i(TAG, "renderable-materialize-success " + describeFrame(radarFrame));
    }

    private SourceRefreshResult fallbackResult(String stage, Throwable throwable, long now) {
        String errorMessage = buildErrorMessage(stage, throwable);
        SafeLayerDebugLog.e(TAG, "refresh-fallback stage=" + stage, throwable);
        logWarning("Radar refresh fallback at " + stage + ": " + errorMessage, throwable);
        SourceRefreshResult result = refreshFinalizer.finalizeRadarRefresh(
                null,
                lastFrame,
                false,
                now,
                product,
                Collections.singletonList(errorMessage));
        lastFrame = result.getRadarFrame();
        return result;
    }

    private String buildErrorMessage(String stage, Throwable throwable) {
        String detail = StringUtils.trimToNull(
                SourceState.sanitizeErrorMessage(throwable == null ? null : throwable.getMessage()));
        if (detail == null) {
            detail = throwable == null ? "unknown" : throwable.getClass().getSimpleName();
        }
        return stage + ": " + detail;
    }

    private String describeFrame(RadarFrame radarFrame) {
        if (radarFrame == null) {
            return "frameId=null, productId=" + product.getProductId();
        }
        return "frameId=" + StringUtils.trimToNull(radarFrame.getFrameId())
                + ", productId=" + StringUtils.trimToNull(radarFrame.getProductId())
                + ", width=" + radarFrame.getWidth()
                + ", height=" + radarFrame.getHeight();
    }

    private void logWarning(String message, Throwable throwable) {
        try {
            if (throwable == null) {
                Log.w(TAG, message);
            } else {
                Log.w(TAG, message, throwable);
            }
        } catch (RuntimeException ignored) {
            // Host-side unit tests may not provide a full Android logging runtime.
        }
    }
}
