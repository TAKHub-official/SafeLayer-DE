package com.takhub.safelayerde.render.map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.util.Pair;

import com.atakmap.android.grg.ImageOverlay;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.graphics.GLMapItem2;
import com.atakmap.android.maps.graphics.GLMapItemSpi2;
import com.atakmap.map.MapRenderer;
import com.takhub.safelayerde.render.model.RadarRenderSpec;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RadarLayerFactoryTest {

    @Before
    public void setUp() {
        RadarLayerFactory.resetRendererRegistrationStateForTest();
        FakeMapItemFactory.reset();
    }

    @After
    public void tearDown() {
        RadarLayerFactory.resetRendererRegistrationStateForTest();
        FakeMapItemFactory.reset();
    }

    @Test
    public void ensureHostRendererRegistered_registersStockAndCustomSpiOnce() {
        TestRadarLayerFactory factory = new TestRadarLayerFactory(true);

        RadarLayerFactory.RendererPath firstPath = factory.ensureRegistered(spec());
        RadarLayerFactory.RendererPath secondPath = factory.ensureRegistered(spec());

        assertSame(RadarLayerFactory.RendererPath.CUSTOM, firstPath);
        assertSame(RadarLayerFactory.RendererPath.CUSTOM, secondPath);
        assertEquals(1, FakeMapItemFactory.stockRegistrations);
        assertEquals(1, FakeMapItemFactory.customRegistrations);
    }

    @Test
    public void ensureHostRendererRegistered_fallsBackToStockWhenCustomRegistrationFails() {
        TestRadarLayerFactory factory = new TestRadarLayerFactory(true);
        FakeMapItemFactory.failCustomRegistration = true;

        RadarLayerFactory.RendererPath path = factory.ensureRegistered(spec());

        assertSame(RadarLayerFactory.RendererPath.STOCK, path);
        assertEquals(1, FakeMapItemFactory.stockRegistrations);
        assertEquals(1, FakeMapItemFactory.customRegistrations);
    }

    @Test
    public void ensureHostRendererRegistered_prefersCustomRendererByDefault() {
        DefaultRadarLayerFactory factory = new DefaultRadarLayerFactory();

        RadarLayerFactory.RendererPath path = factory.ensureRegistered(spec());

        assertSame(RadarLayerFactory.RendererPath.CUSTOM, path);
        assertEquals(1, FakeMapItemFactory.stockRegistrations);
        assertEquals(1, FakeMapItemFactory.customRegistrations);
    }

    @Test
    public void ensureHostRendererRegistered_keepsStockPathWhenCompatibilityCheckFails() {
        TestRadarLayerFactory factory = new TestRadarLayerFactory(false);

        RadarLayerFactory.RendererPath path = factory.ensureRegistered(spec());

        assertSame(RadarLayerFactory.RendererPath.STOCK, path);
        assertEquals(1, FakeMapItemFactory.stockRegistrations);
        assertEquals(0, FakeMapItemFactory.customRegistrations);
    }

    @Test
    public void configureOverlay_marksOnlyCustomPathForCustomRendererSelection() {
        TestRadarLayerFactory factory = new TestRadarLayerFactory(true);
        MapItem customOverlay = mock(MapItem.class);
        MapItem stockOverlay = mock(MapItem.class);

        factory.configureForTest(spec(), customOverlay, RadarLayerFactory.RendererPath.CUSTOM);
        factory.configureForTest(spec(), stockOverlay, RadarLayerFactory.RendererPath.STOCK);

        verify(customOverlay).setMetaBoolean(RadarImageOverlayRenderer.META_KEY, true);
        verify(stockOverlay).setMetaBoolean(RadarImageOverlayRenderer.META_KEY, false);
    }

    @Test
    public void resolveOverlayIdentifier_sanitizesAttachmentUnsafeFrameIds() {
        TestRadarLayerFactory factory = new TestRadarLayerFactory(true);

        assertEquals("safelayer-radar-RV-1768396200000", factory.resolveOverlayIdentifierForTest(spec()));
    }

    @Test
    public void configureOverlay_keepsOriginalFrameIdInMetadata() {
        TestRadarLayerFactory factory = new TestRadarLayerFactory(true);
        MapItem overlay = mock(MapItem.class);

        factory.configureForTest(spec(), overlay, RadarLayerFactory.RendererPath.STOCK);

        verify(overlay).setMetaString("frameId", "RV:1768396200000");
        verify(overlay).setMetaString("sourceIdentity", "RADAR");
        verify(overlay).setMetaString("sourceType", "RADAR");
        verify(overlay).setMetaString("georeferenceId", "radar-test");
        verify(overlay).setMetaBoolean(RadarImageOverlayRenderer.META_KEY, false);
        verify(overlay).setMetaBoolean(RadarImageOverlayRenderer.FAILURE_FLAG_META_KEY, false);
        verify(overlay).setMetaString(RadarImageOverlayRenderer.FAILURE_MESSAGE_META_KEY, "");
        verify(overlay).setClickable(false);
    }

    @Test
    public void configureOverlay_disablesRadarOutlineStroke() {
        TestRadarLayerFactory factory = new TestRadarLayerFactory(true);
        ImageOverlay overlay = mock(ImageOverlay.class);

        factory.configureForTest(spec(), overlay, RadarLayerFactory.RendererPath.STOCK);

        verify(overlay).setStrokeColor(0x00000000);
        verify(overlay).setStrokeWeight(0D);
    }

    private static RadarRenderSpec spec() {
        return new RadarRenderSpec(
                "RV:1768396200000",
                1768396200000L,
                "RV",
                "/tmp/radar.png",
                "EPSG:4326",
                "radar-test",
                47.0,
                5.0,
                55.0,
                15.0,
                900,
                900,
                true,
                10);
    }

    private static final class TestRadarLayerFactory extends RadarLayerFactory {

        private final boolean useCustomRenderer;

        private TestRadarLayerFactory(boolean useCustomRenderer) {
            this.useCustomRenderer = useCustomRenderer;
        }

        private RendererPath ensureRegistered(RadarRenderSpec spec) {
            return ensureHostRendererRegistered(spec);
        }

        private void configureForTest(RadarRenderSpec spec, Object overlay, RendererPath rendererPath) {
            configureOverlay(spec, overlay, rendererPath);
        }

        private String resolveOverlayIdentifierForTest(RadarRenderSpec spec) {
            return resolveOverlayIdentifier(spec);
        }

        @Override
        protected boolean shouldUseCustomRenderer(RadarRenderSpec spec) {
            return useCustomRenderer;
        }

        @Override
        protected Class<?> resolveClass(String className) throws ClassNotFoundException {
            if (className.endsWith("GLMapItemFactory")) {
                return FakeMapItemFactory.class;
            }
            if (className.endsWith("GLMapItemSpi2")) {
                return GLMapItemSpi2.class;
            }
            if (className.endsWith("GLImageOverlay")) {
                return FakeGlImageOverlay.class;
            }
            return super.resolveClass(className);
        }
    }

    private static final class DefaultRadarLayerFactory extends RadarLayerFactory {

        private RendererPath ensureRegistered(RadarRenderSpec spec) {
            return ensureHostRendererRegistered(spec);
        }

        @Override
        protected Class<?> resolveClass(String className) throws ClassNotFoundException {
            if (className.endsWith("GLMapItemFactory")) {
                return FakeMapItemFactory.class;
            }
            if (className.endsWith("GLMapItemSpi2")) {
                return GLMapItemSpi2.class;
            }
            if (className.endsWith("GLImageOverlay")) {
                return FakeGlImageOverlay.class;
            }
            return super.resolveClass(className);
        }
    }

    public static final class FakeMapItemFactory {

        private static int stockRegistrations;
        private static int customRegistrations;
        private static boolean failCustomRegistration;

        public static void registerSpi(GLMapItemSpi2 spi) {
            if (spi == FakeGlImageOverlay.SPI2) {
                stockRegistrations++;
                return;
            }
            if (spi == RadarImageOverlayRenderer.SPI) {
                customRegistrations++;
                if (failCustomRegistration) {
                    throw new IllegalStateException("custom registration failed");
                }
                return;
            }
            throw new IllegalArgumentException("Unexpected SPI registration: " + spi);
        }

        private static void reset() {
            stockRegistrations = 0;
            customRegistrations = 0;
            failCustomRegistration = false;
        }
    }

    public static final class FakeGlImageOverlay {
        public static final GLMapItemSpi2 SPI2 = new GLMapItemSpi2() {
            @Override
            public int getPriority() {
                return 0;
            }

            @Override
            public GLMapItem2 create(Pair<MapRenderer, MapItem> argument) {
                return null;
            }
        };
    }
}
