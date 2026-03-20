package com.takhub.safelayerde.plugin;

import com.takhub.safelayerde.domain.model.SourceIdentity;
import com.takhub.safelayerde.domain.model.SourceState;
import com.takhub.safelayerde.domain.model.WarningRecord;
import com.takhub.safelayerde.domain.model.WarningSnapshot;
import com.takhub.safelayerde.domain.model.WarningSourceType;
import com.takhub.safelayerde.domain.service.WarningRepository;
import com.takhub.safelayerde.render.map.WarningRenderController;

import java.util.Collections;
import java.util.List;

final class WarningSourceRuntimeState {

    private final WarningSourceType sourceType;
    private final SourceIdentity sourceIdentity;
    private SourceState sourceState;
    private boolean layerVisible = true;
    private WarningRenderController renderController;

    WarningSourceRuntimeState(WarningSourceType sourceType) {
        this.sourceType = sourceType;
        this.sourceIdentity = SourceIdentity.fromWarningSourceType(sourceType);
    }

    WarningSourceType sourceType() {
        return sourceType;
    }

    SourceIdentity sourceIdentity() {
        return sourceIdentity;
    }

    SourceState sourceState() {
        return sourceState;
    }

    void setSourceState(SourceState sourceState) {
        this.sourceState = sourceState;
    }

    boolean isLayerVisible() {
        return layerVisible;
    }

    void setLayerVisible(boolean layerVisible) {
        this.layerVisible = layerVisible;
    }

    WarningRenderController renderController() {
        return renderController;
    }

    void setRenderController(WarningRenderController renderController) {
        this.renderController = renderController;
    }

    void clearRenderController() {
        if (renderController != null) {
            renderController.clear();
            renderController = null;
        }
    }

    List<WarningRecord> records(WarningRepository warningRepository) {
        return warningRepository == null
                ? Collections.<WarningRecord>emptyList()
                : warningRepository.getRecords(sourceType);
    }

    WarningSnapshot snapshot(WarningRepository warningRepository) {
        return warningRepository == null ? null : warningRepository.getSnapshot(sourceType);
    }

    void updateRepository(WarningRepository warningRepository, WarningSnapshot snapshot) {
        if (warningRepository == null || snapshot == null) {
            return;
        }
        warningRepository.update(sourceType, snapshot);
    }

    long lastSuccessEpochMsFromCache(WarningRepository warningRepository) {
        WarningSnapshot snapshot = snapshot(warningRepository);
        return snapshot == null ? 0L : snapshot.getFetchedAtEpochMs();
    }

    void reset() {
        clearRenderController();
        sourceState = null;
        layerVisible = true;
    }
}
