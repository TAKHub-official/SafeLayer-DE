package com.takhub.safelayerde.render.map;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.takhub.safelayerde.debug.SafeLayerDebugLog;
import com.takhub.safelayerde.domain.model.RenderMode;
import com.takhub.safelayerde.domain.model.WarningRecord;
import com.takhub.safelayerde.domain.model.WarningSourceType;
import com.takhub.safelayerde.domain.policy.SeverityColorPolicy;
import com.takhub.safelayerde.render.model.RenderHandle;
import com.takhub.safelayerde.render.model.WarningRenderChangeDetector;
import com.takhub.safelayerde.render.model.WarningRenderSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WarningRenderController {

    private final MapGroup mapGroup;
    private final WarningSourceType sourceType;
    private final RenderRegistry registry;
    private final WarningMarkerFactory markerFactory;
    private final WarningShapeFactory shapeFactory;
    private final SeverityColorPolicy colorPolicy;

    public WarningRenderController(
            MapGroup mapGroup,
            WarningSourceType sourceType,
            RenderRegistry registry,
            WarningMarkerFactory markerFactory,
            WarningShapeFactory shapeFactory,
            SeverityColorPolicy colorPolicy) {
        this.mapGroup = mapGroup;
        this.sourceType = sourceType;
        this.registry = registry;
        this.markerFactory = markerFactory;
        this.shapeFactory = shapeFactory;
        this.colorPolicy = colorPolicy;
    }

    public void applySnapshot(List<WarningRecord> records) {
        if (mapGroup == null) {
            SafeLayerDebugLog.w("SafeLayerRender", "applySnapshot without mapGroup source=" + sourceType);
            return;
        }

        SnapshotWorkload workload = prepareWorkload(records);
        SnapshotStats stats = workload.stats;
        removeOrphanedHandles(workload.incomingStableIds, stats);

        for (WarningRenderSpec spec : workload.specs.values()) {
            try {
                applySpec(spec, stats);
            } catch (RuntimeException exception) {
                stats.failed++;
                SafeLayerDebugLog.e("SafeLayerRender", "warning-render-failed source=" + sourceType
                        + ", stableId=" + spec.getStableId()
                        + ", renderMode=" + spec.getRenderMode(), exception);
            }
        }

        SafeLayerDebugLog.i("SafeLayerRender", "applySnapshot source=" + sourceType
                + ", records=" + workload.recordCount
                + ", incoming=" + workload.incomingStableIds.size()
                + ", polygons=" + stats.polygonSpecs
                + ", markers=" + stats.markerSpecs
                + ", listOnly=" + stats.listOnlySpecs
                + ", added=" + stats.added
                + ", updated=" + stats.updated
                + ", recreated=" + stats.recreated
                + ", removed=" + stats.removed
                + ", failed=" + stats.failed);
    }

    public void clear() {
        int removed = 0;
        int failed = 0;
        for (String stableId : registry.allStableIds()) {
            RenderHandle handle = registry.get(stableId);
            if (handle == null) {
                continue;
            }
            try {
                handle.remove(mapGroup);
                registry.remove(stableId);
                removed++;
            } catch (RuntimeException exception) {
                failed++;
                SafeLayerDebugLog.w("SafeLayerRender", "clear-remove-failed source=" + sourceType
                        + ", stableId=" + stableId);
            }
        }
        SafeLayerDebugLog.i("SafeLayerRender", "clear source=" + sourceType
                + ", removed=" + removed
                + ", failed=" + failed);
    }

    public void setLayerVisible(boolean visible) {
        if (mapGroup != null) {
            mapGroup.setVisible(visible);
            SafeLayerDebugLog.i("SafeLayerRender", "layer-visible source=" + sourceType + ", visible=" + visible);
        }
    }

    public RenderHandle findHandle(String stableId) {
        return registry.get(stableId);
    }

    private SnapshotWorkload prepareWorkload(List<WarningRecord> records) {
        Map<String, WarningRenderSpec> specs = new LinkedHashMap<>();
        Set<String> incomingStableIds = new LinkedHashSet<>();
        SnapshotStats stats = new SnapshotStats();
        int recordCount = records == null ? 0 : records.size();
        if (records == null) {
            return new SnapshotWorkload(specs, incomingStableIds, stats, 0);
        }

        for (WarningRecord record : records) {
            if (record == null || record.getStableId() == null || record.getSourceType() != sourceType) {
                continue;
            }

            WarningRenderSpec spec = WarningRenderSpec.from(record);
            incomingStableIds.add(spec.getStableId());
            specs.put(spec.getStableId(), spec);
            if (spec.getRenderMode() == RenderMode.POLYGON) {
                stats.polygonSpecs++;
            } else if (spec.getRenderMode() == RenderMode.MARKER) {
                stats.markerSpecs++;
            } else {
                stats.listOnlySpecs++;
            }
        }
        return new SnapshotWorkload(specs, incomingStableIds, stats, recordCount);
    }

    private void removeOrphanedHandles(Set<String> incomingStableIds, SnapshotStats stats) {
        for (String stableId : registry.allStableIds()) {
            if (incomingStableIds.contains(stableId)) {
                continue;
            }
            RenderHandle handle = registry.get(stableId);
            if (handle == null) {
                continue;
            }
            try {
                handle.remove(mapGroup);
                registry.remove(stableId);
                stats.removed++;
            } catch (RuntimeException exception) {
                stats.failed++;
                SafeLayerDebugLog.e("SafeLayerRender", "orphan-remove-failed source=" + sourceType
                        + ", stableId=" + stableId, exception);
            }
        }
    }

    private void applySpec(WarningRenderSpec spec, SnapshotStats stats) {
        if (spec == null || spec.getStableId() == null) {
            return;
        }

        RenderHandle existing = registry.get(spec.getStableId());
        if (existing != null && !shouldRecreate(existing, spec)) {
            updateExisting(existing, spec);
            stats.updated++;
            return;
        }

        RenderHandle newHandle = buildHandle(spec);
        if (newHandle == null) {
            if (existing != null) {
                detachRegisteredHandle(spec.getStableId(), existing);
                stats.removed++;
            }
            return;
        }

        if (existing == null) {
            registry.put(spec.getStableId(), newHandle);
            stats.added++;
            return;
        }

        replaceHandle(spec.getStableId(), existing, newHandle);
        stats.recreated++;
    }

    private boolean shouldRecreate(RenderHandle existing, WarningRenderSpec spec) {
        return existing == null
                || existing.getMapItem() == null
                || WarningRenderChangeDetector.shouldRecreate(existing.getSpec(), spec);
    }

    private void updateExisting(RenderHandle existing, WarningRenderSpec spec) {
        WarningRenderSpec existingSpec = existing == null ? null : existing.getSpec();
        if (existingSpec != null && stringEquals(existingSpec.getTitle(), spec.getTitle())) {
            existing.setSpec(spec);
            return;
        }
        for (MapItem mapItem : existing.getMapItems()) {
            MapItemCompat.setTitle(mapItem, spec.getTitle());
        }
        existing.setSpec(spec);
    }

    private RenderHandle buildHandle(WarningRenderSpec spec) {
        List<MapItem> mapItems = buildMapItems(spec);
        if (mapItems.isEmpty()) {
            SafeLayerDebugLog.w("SafeLayerRender", "render-skip source=" + sourceType
                    + ", stableId=" + (spec == null ? null : spec.getStableId())
                    + ", renderMode=" + (spec == null ? null : spec.getRenderMode())
                    + ", geometrySignature=" + (spec == null ? null : spec.getGeometrySignature()));
            return null;
        }
        RenderHandle handle = new RenderHandle(spec.getStableId(), spec, mapItems);
        try {
            handle.add(mapGroup);
            return handle;
        } catch (RuntimeException exception) {
            handle.remove(mapGroup);
            throw exception;
        }
    }

    private void replaceHandle(String stableId, RenderHandle existing, RenderHandle replacement) {
        try {
            existing.remove(mapGroup);
        } catch (RuntimeException exception) {
            rollbackReplacement(stableId, replacement, exception);
            throw exception;
        }
        registry.put(stableId, replacement);
    }

    private void detachRegisteredHandle(String stableId, RenderHandle handle) {
        if (handle == null) {
            return;
        }
        handle.remove(mapGroup);
        registry.remove(stableId);
    }

    private void rollbackReplacement(String stableId, RenderHandle replacement, RuntimeException detachFailure) {
        if (replacement == null) {
            return;
        }
        try {
            replacement.remove(mapGroup);
        } catch (RuntimeException rollbackFailure) {
            detachFailure.addSuppressed(rollbackFailure);
            SafeLayerDebugLog.e("SafeLayerRender", "replacement-rollback-failed source=" + sourceType
                    + ", stableId=" + stableId, rollbackFailure);
        }
    }

    private List<MapItem> buildMapItems(WarningRenderSpec spec) {
        if (spec == null) {
            return Collections.emptyList();
        }

        if (spec.getRenderMode() == RenderMode.POLYGON) {
            List<MapItem> shapes = new ArrayList<>();
            for (MapItem shape : shapeFactory.createShapes(mapGroup, spec)) {
                if (shape != null) {
                    shapes.add(shape);
                }
            }
            return shapes;
        }

        if (spec.getRenderMode() == RenderMode.LIST_ONLY) {
            return Collections.emptyList();
        }

        MapItem marker = markerFactory.createMarker(spec);
        return marker == null ? Collections.<MapItem>emptyList() : Collections.singletonList(marker);
    }

    private boolean stringEquals(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    private static final class SnapshotWorkload {

        private final Map<String, WarningRenderSpec> specs;
        private final Set<String> incomingStableIds;
        private final SnapshotStats stats;
        private final int recordCount;

        private SnapshotWorkload(
                Map<String, WarningRenderSpec> specs,
                Set<String> incomingStableIds,
                SnapshotStats stats,
                int recordCount) {
            this.specs = specs;
            this.incomingStableIds = incomingStableIds;
            this.stats = stats;
            this.recordCount = recordCount;
        }
    }

    private static final class SnapshotStats {

        private int polygonSpecs;
        private int markerSpecs;
        private int listOnlySpecs;
        private int added;
        private int updated;
        private int recreated;
        private int removed;
        private int failed;
    }
}
