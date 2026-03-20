package com.takhub.safelayerde.source.radar;

import android.util.Log;

import com.takhub.safelayerde.cache.CacheIntegrityChecker;
import com.takhub.safelayerde.cache.CachePaths;
import com.takhub.safelayerde.cache.SnapshotSerializer;
import com.takhub.safelayerde.debug.SafeLayerDebugLog;
import com.takhub.safelayerde.domain.model.RadarFrame;
import com.takhub.safelayerde.util.IoUtils;
import com.takhub.safelayerde.util.StringUtils;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DwdRadarFrameStore {

    private static final String TAG = "SafeLayerRadarCache";
    private static final String LEGACY_RADAR_FRAME_FILE_NAME = "latest-frame.bin";

    private final CachePaths cachePaths;
    private final CacheIntegrityChecker integrityChecker;

    public DwdRadarFrameStore(CachePaths cachePaths, CacheIntegrityChecker integrityChecker) {
        this.cachePaths = cachePaths;
        this.integrityChecker = integrityChecker == null ? new CacheIntegrityChecker(cachePaths) : integrityChecker;
    }

    public void write(RadarFrame radarFrame) throws IOException {
        if (radarFrame == null || !radarFrame.hasImageBytes()) {
            return;
        }

        File frameFile = cachePaths.getRadarFrameFile();
        File snapshotFile = cachePaths.getRadarSnapshotFile();
        SafeLayerDebugLog.i(TAG, "cache-write-start " + describeFrame(radarFrame)
                + ", snapshotFile=" + describeFile(snapshotFile)
                + ", frameFile=" + describeFile(frameFile));
        try {
            writeDurableImageFile(frameFile, radarFrame);
            integrityChecker.writeSignedString(snapshotFile, toSnapshotJson(radarFrame, frameFile.getAbsolutePath()));
            SafeLayerDebugLog.i(TAG, "cache-write-success " + describeFrame(radarFrame)
                    + ", snapshotFile=" + describeFile(snapshotFile));
        } catch (IOException exception) {
            SafeLayerDebugLog.e(TAG, "cache-write-failed " + describeFrame(radarFrame)
                    + ", snapshotFile=" + describeFile(snapshotFile), exception);
            throw exception;
        } catch (RuntimeException exception) {
            SafeLayerDebugLog.e(TAG, "cache-write-failed " + describeFrame(radarFrame)
                    + ", snapshotFile=" + describeFile(snapshotFile), exception);
            throw new IOException("Failed to serialize radar snapshot.", exception);
        }
    }

    public RadarFrame read() {
        java.util.List<File> snapshotCandidates = cachePaths.getRadarSnapshotReadCandidates();
        SafeLayerDebugLog.i(TAG, "cache-read-start snapshotCandidates=" + snapshotCandidates.size()
                + ", frameCandidates=" + cachePaths.getRadarFrameReadCandidates().size());
        for (File snapshotFile : snapshotCandidates) {
            if (!integrityChecker.canMigrate(
                    snapshotFile,
                    CacheIntegrityChecker.ArtifactType.JSON_DOCUMENT,
                    resolveIntegrityKeyFile(snapshotFile))) {
                SafeLayerDebugLog.w(TAG, "cache-read-skip reason=invalid-snapshot snapshotFile="
                        + describeFile(snapshotFile));
                continue;
            }

            try {
                SafeLayerDebugLog.i(TAG, "cache-read-candidate snapshotFile=" + describeFile(snapshotFile));
                String snapshotJson = IoUtils.readUtf8(snapshotFile);
                RadarFrame radarFrame = fromSnapshotJson(snapshotJson);
                if (radarFrame == null) {
                    SafeLayerDebugLog.w(TAG, "cache-read-skip reason=empty-snapshot snapshotFile="
                            + describeFile(snapshotFile));
                    continue;
                }
                File frameFile = resolveFrameFile(radarFrame, snapshotFile);
                if (!isReadableFrameFile(frameFile)) {
                    SafeLayerDebugLog.w(TAG, "cache-read-skip reason=missing-frame snapshotFile="
                            + describeFile(snapshotFile)
                            + ", frameFile=" + describeFile(frameFile));
                    continue;
                }
                radarFrame.setDataBytes(IoUtils.readBytes(frameFile));
                radarFrame.setValid(radarFrame.hasImageBytes() && radarFrame.isValid());
                File renderableFrameFile = resolveRenderableFrameFile(radarFrame, frameFile);
                if (!isRenderablePngFrameFile(renderableFrameFile)) {
                    SafeLayerDebugLog.w(TAG, "cache-read-skip reason=missing-renderable-frame snapshotFile="
                            + describeFile(snapshotFile)
                            + ", frameFile=" + describeFile(frameFile));
                    continue;
                }
                radarFrame.setImagePath(renderableFrameFile.getAbsolutePath());
                rewriteCanonicalSnapshotBestEffort(
                        snapshotFile,
                        snapshotJson,
                        radarFrame,
                        frameFile,
                        renderableFrameFile);
                SafeLayerDebugLog.i(TAG, "cache-read-success " + describeFrame(radarFrame));
                return radarFrame;
            } catch (IOException exception) {
                SafeLayerDebugLog.e(TAG, "cache-read-failed snapshotFile=" + describeFile(snapshotFile), exception);
                logWarning("Failed to read radar cache candidate.", exception);
            } catch (JSONException exception) {
                SafeLayerDebugLog.e(TAG, "cache-read-failed snapshotFile=" + describeFile(snapshotFile), exception);
                logWarning("Failed to parse radar cache candidate.", exception);
            } catch (RuntimeException exception) {
                SafeLayerDebugLog.e(TAG, "cache-read-failed snapshotFile=" + describeFile(snapshotFile), exception);
                logWarning("Failed to parse radar cache candidate.", exception);
            }
        }
        SafeLayerDebugLog.w(TAG, "cache-read-miss");
        return null;
    }

    private File resolveFrameFile(RadarFrame radarFrame, File snapshotFile) {
        File fallbackReadableCandidate = null;
        for (File candidate : buildSnapshotContractFrameCandidates(radarFrame, snapshotFile)) {
            if (!isReadableFrameFile(candidate)) {
                continue;
            }
            if (isReadablePngImageCandidate(candidate) || !isPngCandidate(candidate)) {
                return candidate;
            }
            if (fallbackReadableCandidate == null) {
                fallbackReadableCandidate = candidate;
            }
        }
        return fallbackReadableCandidate;
    }

    private List<File> buildSnapshotContractFrameCandidates(RadarFrame radarFrame, File snapshotFile) {
        List<File> candidates = new ArrayList<>();
        Set<String> seenPaths = new LinkedHashSet<>();
        File radarDirectory = snapshotFile == null ? null : snapshotFile.getParentFile();
        String imagePath = radarFrame == null ? null : StringUtils.trimToNull(radarFrame.getImagePath());
        boolean allowLegacyFallback = imagePath == null || isLegacyFramePath(imagePath);

        addSnapshotImagePathCandidates(candidates, seenPaths, imagePath, radarDirectory);
        addFrameCandidate(candidates, seenPaths, buildCanonicalFrameCandidate(radarDirectory));
        if (allowLegacyFallback) {
            addFrameCandidate(candidates, seenPaths, buildLegacyFrameCandidate(radarDirectory));
        }
        addCompatibleFrameCandidates(candidates, seenPaths, allowLegacyFallback);
        return candidates;
    }

    private boolean isReadableFrameFile(File frameFile) {
        return integrityChecker.canMigrate(
                frameFile,
                CacheIntegrityChecker.ArtifactType.BINARY_BLOB,
                resolveIntegrityKeyFile(frameFile));
    }

    private boolean isReadablePngImageCandidate(File frameFile) {
        return isPngCandidate(frameFile) && isRenderablePngFrameFile(frameFile);
    }

    private boolean isPngCandidate(File frameFile) {
        return frameFile != null && frameFile.getName().endsWith(".png");
    }

    private boolean isRenderablePngFrameFile(File frameFile) {
        return integrityChecker.isReadablePngImage(frameFile, resolveIntegrityKeyFile(frameFile));
    }

    private File ensureCanonicalFrameFile(RadarFrame radarFrame, File sourceFrameFile) throws IOException {
        File canonicalFrameFile = cachePaths.getRadarFrameFile();
        if (sourceFrameFile == null) {
            throw new IOException("Radar frame file is null.");
        }
        if (sourceFrameFile.equals(canonicalFrameFile)
                && integrityChecker.isReadableBinaryFile(
                        canonicalFrameFile,
                        resolveIntegrityKeyFile(canonicalFrameFile))) {
            return canonicalFrameFile;
        }

        writeDurableImageFile(canonicalFrameFile, radarFrame);
        return canonicalFrameFile;
    }

    private void writeCanonicalSnapshot(RadarFrame radarFrame, String imagePath) throws IOException {
        File snapshotFile = cachePaths.getRadarSnapshotFile();
        integrityChecker.writeSignedString(snapshotFile, toSnapshotJson(radarFrame, imagePath));
    }

    public static void materializeRenderableImage(RadarFrame radarFrame, File frameFile) throws IOException {
        if (radarFrame == null || !radarFrame.hasImageBytes()) {
            throw new IOException("Radar frame is missing image bytes.");
        }
        if (frameFile == null) {
            throw new IOException("Radar frame file is null.");
        }

        SafeLayerDebugLog.i(TAG, "renderable-image-materialize-start " + describeFrame(radarFrame)
                + ", targetFile=" + describeFile(frameFile));
        writeImageFile(frameFile, radarFrame);
        radarFrame.setImagePath(frameFile.getAbsolutePath());
        SafeLayerDebugLog.i(TAG, "renderable-image-materialize-success " + describeFrame(radarFrame));
    }

    public static File materializeSessionFrame(
            RadarFrame radarFrame,
            File sessionFrameFile,
            String productId) throws IOException {
        File renderableFrameFile = sessionFrameFile == null
                ? createSessionFrameFile(radarFrame, productId)
                : sessionFrameFile;
        materializeRenderableImage(radarFrame, renderableFrameFile);
        return renderableFrameFile;
    }

    private static void writeImageFile(File frameFile, RadarFrame radarFrame) throws IOException {
        if (frameFile == null) {
            throw new IOException("Radar frame file is null.");
        }
        if (radarFrame == null || !radarFrame.hasImageBytes()) {
            throw new IOException("Radar frame is missing image bytes.");
        }
        IoUtils.atomicWriteBytes(frameFile, radarFrame.getDataBytes());
    }

    private void writeDurableImageFile(File frameFile, RadarFrame radarFrame) throws IOException {
        if (frameFile == null) {
            throw new IOException("Radar frame file is null.");
        }
        if (radarFrame == null || !radarFrame.hasImageBytes()) {
            throw new IOException("Radar frame is missing image bytes.");
        }
        integrityChecker.writeSignedBytes(frameFile, radarFrame.getDataBytes());
    }

    private File resolveRenderableFrameFile(RadarFrame radarFrame, File sourceFrameFile) {
        if (isRenderablePngFrameFile(sourceFrameFile)) {
            try {
                return ensureCanonicalFrameFile(radarFrame, sourceFrameFile);
            } catch (IOException exception) {
                SafeLayerDebugLog.w(TAG, "cache-read-best-effort-frame-migration-failed sourceFile="
                        + describeFile(sourceFrameFile)
                        + ", targetFile=" + describeFile(cachePaths.getRadarFrameFile())
                        + ", reason=" + exception.getMessage());
                logWarning("Failed to migrate cached radar PNG frame.", exception);
                return sourceFrameFile;
            }
        }

        for (File candidate : buildLegacyRenderableFrameCandidates(sourceFrameFile)) {
            File renderableFrameFile = materializeRenderableFrameBestEffort(radarFrame, sourceFrameFile, candidate);
            if (isRenderablePngFrameFile(renderableFrameFile)) {
                return renderableFrameFile;
            }
        }

        File temporaryFrameFile = createTemporaryRenderableFrameFile(sourceFrameFile);
        return materializeRenderableFrameBestEffort(radarFrame, sourceFrameFile, temporaryFrameFile);
    }

    private List<File> buildLegacyRenderableFrameCandidates(File sourceFrameFile) {
        List<File> candidates = new ArrayList<>();
        Set<String> seenPaths = new LinkedHashSet<>();
        addFrameCandidate(candidates, seenPaths, cachePaths.getRadarFrameFile());
        addFrameCandidate(candidates, seenPaths, buildSiblingRenderableFrameFile(sourceFrameFile));
        return candidates;
    }

    private File buildSiblingRenderableFrameFile(File sourceFrameFile) {
        File siblingFrameFile = buildStableSiblingRenderableFrameFile(sourceFrameFile);
        if (siblingFrameFile == null) {
            return null;
        }
        File parentDirectory = siblingFrameFile.getParentFile();
        if (parentDirectory == null || !parentDirectory.isDirectory() || !parentDirectory.canWrite()) {
            return null;
        }
        return siblingFrameFile;
    }

    private File buildStableSiblingRenderableFrameFile(File sourceFrameFile) {
        if (sourceFrameFile == null) {
            return null;
        }
        File parentDirectory = sourceFrameFile.getParentFile();
        String pngFileName = toPngFileName(sourceFrameFile.getName());
        if (pngFileName == null) {
            return null;
        }
        return new File(parentDirectory, pngFileName);
    }

    private String toPngFileName(String fileName) {
        String normalized = StringUtils.trimToNull(fileName);
        if (normalized == null) {
            return null;
        }
        int extensionStart = normalized.lastIndexOf('.');
        String baseName = extensionStart > 0 ? normalized.substring(0, extensionStart) : normalized;
        return baseName + ".png";
    }

    private File createTemporaryRenderableFrameFile(File sourceFrameFile) {
        try {
            File temporaryFrameFile = IoUtils.createTempFile("safelayer-radar-frame-", ".png");
            temporaryFrameFile.deleteOnExit();
            return temporaryFrameFile;
        } catch (IOException exception) {
            SafeLayerDebugLog.w(TAG, "cache-read-best-effort-frame-migration-failed sourceFile="
                    + describeFile(sourceFrameFile)
                    + ", targetFile=temp"
                    + ", reason=" + exception.getMessage());
            logWarning("Failed to allocate temporary radar frame.", exception);
            return null;
        }
    }

    private File materializeRenderableFrameBestEffort(
            RadarFrame radarFrame,
            File sourceFrameFile,
            File targetFrameFile) {
        if (targetFrameFile == null) {
            return null;
        }
        try {
            if (targetFrameFile.equals(cachePaths.getRadarFrameFile())) {
                return ensureCanonicalFrameFile(radarFrame, sourceFrameFile);
            }
            materializeRenderableImage(radarFrame, targetFrameFile);
            return targetFrameFile;
        } catch (IOException exception) {
            SafeLayerDebugLog.w(TAG, "cache-read-best-effort-frame-migration-failed sourceFile="
                    + describeFile(sourceFrameFile)
                    + ", targetFile=" + describeFile(targetFrameFile)
                    + ", reason=" + exception.getMessage());
            logWarning("Failed to migrate cached radar frame.", exception);
            return null;
        }
    }

    private void rewriteCanonicalSnapshotBestEffort(
            File sourceSnapshotFile,
            String sourceSnapshotJson,
            RadarFrame radarFrame,
            File sourceFrameFile,
            File renderableFrameFile) {
        if (!shouldWriteCanonicalSnapshot(
                sourceSnapshotFile,
                sourceSnapshotJson,
                radarFrame,
                sourceFrameFile,
                renderableFrameFile)) {
            return;
        }

        try {
            writeCanonicalSnapshot(radarFrame, renderableFrameFile.getAbsolutePath());
        } catch (IOException exception) {
            SafeLayerDebugLog.w(TAG, "cache-read-best-effort-snapshot-migration-failed sourceFile="
                    + describeFile(sourceSnapshotFile)
                    + ", targetFile=" + describeFile(cachePaths.getRadarSnapshotFile())
                    + ", reason=" + exception.getMessage());
            logWarning("Failed to rewrite radar cache snapshot.", exception);
        }
    }

    private boolean shouldWriteCanonicalSnapshot(
            File sourceSnapshotFile,
            String sourceSnapshotJson,
            RadarFrame radarFrame,
            File sourceFrameFile,
            File renderableFrameFile) {
        if (!isDurableRenderableFrameFile(renderableFrameFile)) {
            return false;
        }
        if (sourceSnapshotFile == null) {
            return true;
        }
        if (!sourceSnapshotFile.equals(cachePaths.getRadarSnapshotFile())) {
            return true;
        }
        String expectedSnapshotJson = toSnapshotJson(radarFrame, renderableFrameFile.getAbsolutePath());
        return !expectedSnapshotJson.equals(sourceSnapshotJson);
    }

    private boolean isDurableRenderableFrameFile(File renderableFrameFile) {
        return renderableFrameFile != null
                && renderableFrameFile.equals(cachePaths.getRadarFrameFile())
                && integrityChecker.isReadablePngImage(
                        renderableFrameFile,
                        resolveIntegrityKeyFile(renderableFrameFile));
    }

    private File resolveIntegrityKeyFile(File cacheArtifact) {
        return cachePaths.getIntegrityKeyFile(cacheArtifact);
    }

    private void addFrameCandidate(List<File> candidates, Set<String> seenPaths, File candidate) {
        if (candidate == null) {
            return;
        }
        String absolutePath = candidate.getAbsolutePath();
        if (seenPaths.add(absolutePath)) {
            candidates.add(candidate);
        }
    }

    private void addSnapshotImagePathCandidates(
            List<File> candidates,
            Set<String> seenPaths,
            String imagePath,
            File radarDirectory) {
        String normalizedImagePath = StringUtils.trimToNull(imagePath);
        if (normalizedImagePath == null) {
            return;
        }

        File requestedFrameFile = validateSnapshotImagePath(normalizedImagePath, radarDirectory);
        if (requestedFrameFile == null) {
            SafeLayerDebugLog.w(TAG, "cache-read-skip reason=invalid-image-path imagePath=" + normalizedImagePath);
            return;
        }
        addPreferredPngCandidate(candidates, seenPaths, requestedFrameFile);
        addFrameCandidate(candidates, seenPaths, requestedFrameFile);
        addPreferredPngCandidate(candidates, seenPaths, buildSiblingFrameCandidate(radarDirectory, requestedFrameFile));
        addFrameCandidate(candidates, seenPaths, buildSiblingFrameCandidate(radarDirectory, requestedFrameFile));
    }

    private File validateSnapshotImagePath(String imagePath, File radarDirectory) {
        if (radarDirectory == null) {
            return null;
        }

        File requestedFrameFile = new File(imagePath);
        if (!requestedFrameFile.isAbsolute()) {
            return null;
        }

        try {
            File canonicalRadarDirectory = radarDirectory.getCanonicalFile();
            File canonicalRequestedFrameFile = requestedFrameFile.getCanonicalFile();
            return isWithinDirectory(canonicalRadarDirectory, canonicalRequestedFrameFile)
                    ? canonicalRequestedFrameFile
                    : null;
        } catch (IOException ignored) {
            return null;
        }
    }

    private boolean isWithinDirectory(File parentDirectory, File candidateFile) {
        if (parentDirectory == null || candidateFile == null) {
            return false;
        }

        String parentPath = parentDirectory.getAbsolutePath();
        String candidatePath = candidateFile.getAbsolutePath();
        return candidatePath.startsWith(parentPath + File.separator);
    }

    private void addCompatibleFrameCandidates(
            List<File> candidates,
            Set<String> seenPaths,
            boolean allowLegacyFallback) {
        for (File candidate : cachePaths.getRadarFrameReadCandidates()) {
            if (!allowLegacyFallback && isLegacyFramePath(candidate == null ? null : candidate.getName())) {
                continue;
            }
            addFrameCandidate(candidates, seenPaths, candidate);
        }
    }

    private void addPreferredPngCandidate(List<File> candidates, Set<String> seenPaths, File sourceFrameFile) {
        File pngCandidate = toPngFrameCandidate(sourceFrameFile);
        if (pngCandidate == null) {
            return;
        }
        addFrameCandidate(candidates, seenPaths, pngCandidate);
    }

    private File buildCanonicalFrameCandidate(File radarDirectory) {
        if (radarDirectory == null) {
            return null;
        }
        return new File(radarDirectory, cachePaths.getRadarFrameFile().getName());
    }

    private File buildLegacyFrameCandidate(File radarDirectory) {
        if (radarDirectory == null) {
            return null;
        }
        return new File(radarDirectory, LEGACY_RADAR_FRAME_FILE_NAME);
    }

    private File buildSiblingFrameCandidate(File radarDirectory, File sourceFrameFile) {
        if (radarDirectory == null || sourceFrameFile == null) {
            return null;
        }
        return new File(radarDirectory, sourceFrameFile.getName());
    }

    private File toPngFrameCandidate(File sourceFrameFile) {
        if (sourceFrameFile == null) {
            return null;
        }
        File parentDirectory = sourceFrameFile.getParentFile();
        String pngFileName = toPngFileName(sourceFrameFile.getName());
        if (parentDirectory == null || pngFileName == null) {
            return null;
        }
        return new File(parentDirectory, pngFileName);
    }

    private boolean isLegacyFramePath(String imagePath) {
        String normalizedImagePath = StringUtils.trimToNull(imagePath);
        return normalizedImagePath != null && normalizedImagePath.endsWith(".bin");
    }

    private String toSnapshotJson(RadarFrame radarFrame, String imagePath) {
        JSONObject jsonObject = SnapshotSerializer.toJson(radarFrame);
        try {
            jsonObject.put("imagePath", imagePath);
        } catch (JSONException exception) {
            throw new IllegalStateException("Failed to write radar snapshot image path.", exception);
        }
        return jsonObject.toString();
    }

    private RadarFrame fromSnapshotJson(String snapshotJson) throws JSONException {
        Object parsed = new JSONTokener(snapshotJson).nextValue();
        if (!(parsed instanceof JSONObject)) {
            return null;
        }
        JSONObject jsonObject = (JSONObject) parsed;
        if (jsonObject.length() == 0) {
            return null;
        }
        return SnapshotSerializer.fromRadarFrameJson(jsonObject);
    }

    private static File createSessionFrameFile(RadarFrame radarFrame, String productId) throws IOException {
        String normalizedProductId = StringUtils.trimToNull(productId);
        String filePrefix = normalizedProductId == null ? "frame" : normalizedProductId.toLowerCase();
        File frameFile = IoUtils.createTempFile(
                "safelayer-radar-" + filePrefix + "-",
                imageFileSuffix(radarFrame == null ? null : radarFrame.getImageFormat()));
        frameFile.deleteOnExit();
        return frameFile;
    }

    private static String imageFileSuffix(String imageFormat) {
        if (imageFormat == null) {
            return ".bin";
        }
        String normalized = imageFormat.trim().toLowerCase();
        if (normalized.endsWith("png")) {
            return ".png";
        }
        if (normalized.endsWith("jpeg") || normalized.endsWith("jpg")) {
            return ".jpg";
        }
        return ".bin";
    }

    private void logWarning(String message, Throwable throwable) {
        try {
            Log.w(TAG, message, throwable);
        } catch (RuntimeException ignored) {
            // Host-side unit tests may not provide a full Android logging runtime.
        }
    }

    private static String describeFrame(RadarFrame radarFrame) {
        if (radarFrame == null) {
            return "frameId=null, productId=null, width=0, height=0, imageFile=null";
        }
        return "frameId=" + describeValue(radarFrame.getFrameId())
                + ", productId=" + describeValue(radarFrame.getProductId())
                + ", width=" + radarFrame.getWidth()
                + ", height=" + radarFrame.getHeight()
                + ", imageFile=" + describePathValue(radarFrame.getImagePath());
    }

    private static String describeValue(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String describePathValue(String path) {
        String normalizedPath = describeValue(path);
        if (normalizedPath == null) {
            return null;
        }
        return new File(normalizedPath).getName();
    }

    private static String describeFile(File file) {
        return file == null ? "null" : file.getName();
    }
}
