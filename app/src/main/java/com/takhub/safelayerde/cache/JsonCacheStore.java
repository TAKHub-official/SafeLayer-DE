package com.takhub.safelayerde.cache;

import android.util.Log;

import com.takhub.safelayerde.domain.model.RadarFrame;
import com.takhub.safelayerde.domain.model.SourceState;
import com.takhub.safelayerde.domain.model.WarningSnapshot;
import com.takhub.safelayerde.domain.model.WarningSourceType;
import com.takhub.safelayerde.source.radar.DwdRadarFrameStore;
import com.takhub.safelayerde.util.IoUtils;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class JsonCacheStore implements CacheStore {

    private static final String TAG = "SafeLayerCache";

    private final CachePaths cachePaths;
    private final CacheIntegrityChecker integrityChecker;
    private final DwdRadarFrameStore radarFrameStore;

    public JsonCacheStore(CachePaths cachePaths) {
        this(cachePaths, new CacheIntegrityChecker(cachePaths));
    }

    public JsonCacheStore(CachePaths cachePaths, CacheIntegrityChecker integrityChecker) {
        this.cachePaths = cachePaths;
        this.integrityChecker = integrityChecker == null ? new CacheIntegrityChecker(cachePaths) : integrityChecker;
        this.radarFrameStore = new DwdRadarFrameStore(cachePaths, this.integrityChecker);
    }

    @Override
    public void writeWarningSnapshot(WarningSnapshot snapshot) throws IOException {
        File targetFile = resolveWarningSnapshotFile(snapshot == null ? null : snapshot.getSourceType());
        if (targetFile == null) {
            return;
        }

        try {
            integrityChecker.writeSignedString(targetFile, SnapshotSerializer.toJson(snapshot).toString());
        } catch (IOException ioException) {
            logError("Failed to write warning snapshot.", ioException);
            throw ioException;
        } catch (RuntimeException runtimeException) {
            logError("Failed to serialize warning snapshot.", runtimeException);
            throw new IOException("Failed to serialize warning snapshot.", runtimeException);
        }
    }

    @Override
    public WarningSnapshot readWarningSnapshot(WarningSourceType sourceType) {
        File targetFile = resolveWarningSnapshotFile(sourceType);
        for (File sourceFile : resolveWarningSnapshotReadCandidates(sourceType)) {
            if (!isReadableFile(sourceFile, CacheIntegrityChecker.ArtifactType.JSON_DOCUMENT)) {
                continue;
            }

            try {
                JSONObject parsed = readJsonObject(sourceFile);
                WarningSnapshot snapshot = SnapshotSerializer.fromJson(parsed);
                migrateToPrimaryLocation(
                        sourceFile,
                        targetFile,
                        CacheIntegrityChecker.ArtifactType.JSON_DOCUMENT);
                return snapshot;
            } catch (IOException | JSONException | RuntimeException exception) {
                logError("Failed to read warning snapshot.", exception);
            }
        }
        return null;
    }

    @Override
    public void writeSourceState(List<SourceState> states) throws IOException {
        try {
            integrityChecker.writeSignedString(
                    cachePaths.getSourceStateFile(),
                    SnapshotSerializer.toSourceStateDocument(states).toString());
        } catch (IOException ioException) {
            logError("Failed to write source states.", ioException);
            throw ioException;
        } catch (RuntimeException runtimeException) {
            logError("Failed to serialize source states.", runtimeException);
            throw new IOException("Failed to serialize source states.", runtimeException);
        }
    }

    @Override
    public List<SourceState> readSourceState() {
        for (File sourceStateFile : cachePaths.getSourceStateReadCandidates()) {
            if (!isReadableFile(sourceStateFile, CacheIntegrityChecker.ArtifactType.JSON_DOCUMENT)) {
                continue;
            }

            try {
                List<SourceState> states =
                        SnapshotSerializer.fromSourceStateDocument(readJsonObject(sourceStateFile));
                migrateToPrimaryLocation(
                        sourceStateFile,
                        cachePaths.getSourceStateFile(),
                        CacheIntegrityChecker.ArtifactType.JSON_DOCUMENT);
                return states;
            } catch (IOException | JSONException | RuntimeException exception) {
                logError("Failed to read source states.", exception);
            }
        }
        return new ArrayList<>();
    }

    @Override
    public void writeRadarFrame(RadarFrame radarFrame) throws IOException {
        radarFrameStore.write(radarFrame);
    }

    @Override
    public RadarFrame readRadarFrame() {
        return radarFrameStore.read();
    }

    private File resolveWarningSnapshotFile(WarningSourceType sourceType) {
        if (sourceType == null) {
            return null;
        }

        switch (sourceType) {
            case BBK:
                return cachePaths.getBbkSnapshotFile();
            case DWD:
                return cachePaths.getDwdSnapshotFile();
            default:
                return null;
        }
    }

    File findReadableWarningSnapshotFile(WarningSourceType sourceType) {
        return findReadableFile(resolveWarningSnapshotReadCandidates(sourceType));
    }

    File findReadableSourceStateFile() {
        return findReadableFile(cachePaths.getSourceStateReadCandidates());
    }

    private List<File> resolveWarningSnapshotReadCandidates(WarningSourceType sourceType) {
        if (sourceType == null) {
            return new ArrayList<>();
        }

        switch (sourceType) {
            case BBK:
                return cachePaths.getBbkSnapshotReadCandidates();
            case DWD:
                return cachePaths.getDwdSnapshotReadCandidates();
            default:
                return new ArrayList<>();
        }
    }

    private File findReadableFile(List<File> candidates) {
        return findReadableFile(candidates, CacheIntegrityChecker.ArtifactType.JSON_DOCUMENT);
    }

    private File findReadableFile(List<File> candidates, CacheIntegrityChecker.ArtifactType artifactType) {
        if (candidates == null) {
            return null;
        }

        for (File candidate : candidates) {
            if (isReadableFile(candidate, artifactType)) {
                return candidate;
            }
        }
        return null;
    }

    void migrateToPrimaryLocation(File sourceFile, File targetFile) {
        migrateToPrimaryLocation(sourceFile, targetFile, CacheIntegrityChecker.ArtifactType.JSON_DOCUMENT);
    }

    void migrateToPrimaryLocation(
            File sourceFile,
            File targetFile,
            CacheIntegrityChecker.ArtifactType artifactType) {
        if (sourceFile == null || targetFile == null) {
            return;
        }
        if (!isReadableFile(sourceFile, artifactType)) {
            return;
        }
        if (sourceFile.equals(targetFile) && isVerifiedReadableFile(targetFile, artifactType)) {
            return;
        }
        if (isVerifiedReadableFile(targetFile, artifactType)) {
            return;
        }

        try {
            rewriteSignedArtifact(sourceFile, targetFile, artifactType);
        } catch (IOException exception) {
            logWarning("Failed to migrate cache file to canonical location: "
                    + describeFile(targetFile), exception);
        }
    }

    private boolean isReadableFile(File candidate, CacheIntegrityChecker.ArtifactType artifactType) {
        CacheIntegrityChecker.ArtifactType type = artifactType == null
                ? CacheIntegrityChecker.ArtifactType.BINARY_BLOB
                : artifactType;
        if (!integrityChecker.canMigrate(candidate, type, resolveIntegrityKeyFile(candidate))) {
            return false;
        }
        if (type != CacheIntegrityChecker.ArtifactType.JSON_DOCUMENT) {
            return true;
        }
        try {
            readJsonObject(candidate);
            return true;
        } catch (IOException | JSONException | RuntimeException exception) {
            logWarning("JSON cache document is not readable: "
                    + describeFile(candidate), exception);
            return false;
        }
    }

    private boolean isVerifiedReadableFile(File candidate, CacheIntegrityChecker.ArtifactType artifactType) {
        CacheIntegrityChecker.ArtifactType type = artifactType == null
                ? CacheIntegrityChecker.ArtifactType.BINARY_BLOB
                : artifactType;
        File integrityKeyFile = resolveIntegrityKeyFile(candidate);
        switch (type) {
            case JSON_DOCUMENT:
                return integrityChecker.isReadableJsonDocument(candidate, integrityKeyFile);
            case PNG_IMAGE:
                return integrityChecker.isReadablePngImage(candidate, integrityKeyFile);
            case BINARY_BLOB:
            default:
                return integrityChecker.isReadableBinaryFile(candidate, integrityKeyFile);
        }
    }

    private File resolveIntegrityKeyFile(File cacheArtifact) {
        return cachePaths.getIntegrityKeyFile(cacheArtifact);
    }

    private void rewriteSignedArtifact(
            File sourceFile,
            File targetFile,
            CacheIntegrityChecker.ArtifactType artifactType) throws IOException {
        CacheIntegrityChecker.ArtifactType type = artifactType == null
                ? CacheIntegrityChecker.ArtifactType.BINARY_BLOB
                : artifactType;
        switch (type) {
            case JSON_DOCUMENT:
                integrityChecker.writeSignedString(targetFile, IoUtils.readUtf8(sourceFile));
                return;
            case PNG_IMAGE:
            case BINARY_BLOB:
            default:
                integrityChecker.writeSignedBytes(targetFile, IoUtils.readBytes(sourceFile));
        }
    }

    private JSONObject readJsonObject(File sourceFile) throws IOException, JSONException {
        Object parsed = new JSONTokener(IoUtils.readUtf8(sourceFile)).nextValue();
        if (parsed instanceof JSONObject) {
            return (JSONObject) parsed;
        }
        throw new JSONException("Cache document is not a JSON object.");
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

    private void logError(String message, Throwable throwable) {
        try {
            Log.e(TAG, message, throwable);
        } catch (RuntimeException ignored) {
            // Host-side unit tests may not provide a full Android logging runtime.
        }
    }

    private String describeFile(File file) {
        return file == null ? "unknown" : file.getName();
    }
}
