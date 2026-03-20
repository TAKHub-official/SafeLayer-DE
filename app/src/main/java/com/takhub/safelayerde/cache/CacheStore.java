package com.takhub.safelayerde.cache;

import com.takhub.safelayerde.domain.model.RadarFrame;
import com.takhub.safelayerde.domain.model.SourceState;
import com.takhub.safelayerde.domain.model.WarningSnapshot;
import com.takhub.safelayerde.domain.model.WarningSourceType;

import java.io.IOException;
import java.util.List;

public interface CacheStore {

    void writeWarningSnapshot(WarningSnapshot snapshot) throws IOException;

    WarningSnapshot readWarningSnapshot(WarningSourceType sourceType);

    void writeSourceState(List<SourceState> states) throws IOException;

    List<SourceState> readSourceState();

    void writeRadarFrame(RadarFrame radarFrame) throws IOException;

    RadarFrame readRadarFrame();

    default void upsertSourceState(SourceState state) throws IOException {
        writeSourceState(SourceStateCacheHelper.upsert(readSourceState(), state));
    }
}
