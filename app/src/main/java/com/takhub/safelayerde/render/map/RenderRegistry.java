package com.takhub.safelayerde.render.map;

import com.takhub.safelayerde.render.model.RenderHandle;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class RenderRegistry {

    private final Map<String, RenderHandle> handles = new LinkedHashMap<>();

    public void put(String stableId, RenderHandle handle) {
        handles.put(stableId, handle);
    }

    public RenderHandle get(String stableId) {
        return handles.get(stableId);
    }

    public RenderHandle remove(String stableId) {
        return handles.remove(stableId);
    }

    public Set<String> allStableIds() {
        return new LinkedHashSet<>(handles.keySet());
    }

    public void clear() {
        handles.clear();
    }
}
