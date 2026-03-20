package com.takhub.safelayerde.domain.service;

import com.takhub.safelayerde.domain.model.RadarFrame;
import com.takhub.safelayerde.domain.model.SourceState;

public class RadarRepository {

    private RadarFrame currentFrame;
    private SourceState sourceState;

    public void restore(RadarFrame radarFrame, SourceState sourceState) {
        this.currentFrame = radarFrame;
        this.sourceState = sourceState;
    }

    public void updateFrame(RadarFrame radarFrame) {
        currentFrame = radarFrame;
    }

    public RadarFrame getCurrentFrame() {
        return currentFrame;
    }

    public void updateSourceState(SourceState sourceState) {
        this.sourceState = sourceState;
    }

    public SourceState getSourceState() {
        return sourceState;
    }

    public void clear() {
        currentFrame = null;
        sourceState = null;
    }
}
