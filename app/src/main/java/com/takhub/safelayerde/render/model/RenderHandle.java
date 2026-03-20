package com.takhub.safelayerde.render.model;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RenderHandle {

    private final String stableId;
    private final List<MapItem> mapItems;
    private WarningRenderSpec spec;

    public RenderHandle(String stableId, MapItem mapItem) {
        this(stableId, null, mapItem == null ? null : Collections.singletonList(mapItem));
    }

    public RenderHandle(String stableId, List<MapItem> mapItems) {
        this(stableId, null, mapItems);
    }

    public RenderHandle(String stableId, WarningRenderSpec spec, List<MapItem> mapItems) {
        this.stableId = stableId;
        this.spec = spec;
        this.mapItems = mapItems == null ? new ArrayList<MapItem>() : new ArrayList<>(mapItems);
    }

    public String getStableId() {
        return stableId;
    }

    public MapItem getMapItem() {
        return mapItems.isEmpty() ? null : mapItems.get(0);
    }

    public List<MapItem> getMapItems() {
        return Collections.unmodifiableList(mapItems);
    }

    public WarningRenderSpec getSpec() {
        return spec;
    }

    public void setSpec(WarningRenderSpec spec) {
        this.spec = spec;
    }

    public int size() {
        return mapItems.size();
    }

    public boolean isEmpty() {
        return mapItems.isEmpty();
    }

    public void add(MapGroup group) {
        if (group == null || mapItems.isEmpty()) {
            return;
        }

        List<MapItem> addedItems = new ArrayList<>();
        try {
            for (MapItem mapItem : mapItems) {
                if (mapItem == null) {
                    continue;
                }
                group.addItem(mapItem);
                addedItems.add(mapItem);
            }
        } catch (RuntimeException exception) {
            removeItems(group, addedItems);
            throw exception;
        }
    }

    public void remove(MapGroup group) {
        if (group == null || mapItems.isEmpty()) {
            return;
        }

        removeItems(group, mapItems);
    }

    private void removeItems(MapGroup group, List<MapItem> items) {
        if (group == null || items == null || items.isEmpty()) {
            return;
        }
        for (MapItem mapItem : mapItems) {
            if (mapItem == null) {
                continue;
            }
            group.removeItem(mapItem);
        }
    }
}
