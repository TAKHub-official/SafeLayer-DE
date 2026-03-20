package com.takhub.safelayerde.cache;

import com.takhub.safelayerde.plugin.SafeLayerConstants;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class CachePaths {

    private static final String CANONICAL_RADAR_FRAME_FILE_NAME = "latest-frame.png";
    private static final String LEGACY_RADAR_FRAME_FILE_NAME = "latest-frame.bin";

    private final File baseDirectory;
    private final List<File> legacyBaseDirectories;

    public CachePaths(File baseDirectory) {
        this(baseDirectory, null);
    }

    public CachePaths(File baseDirectory, List<File> legacyBaseDirectories) {
        this.baseDirectory = baseDirectory;
        this.legacyBaseDirectories = sanitizeBaseDirectories(legacyBaseDirectories);
    }

    public File getBbkSnapshotFile() {
        return new File(getWarningsDirectory(), "bbk-snapshot.json");
    }

    public List<File> getBbkSnapshotReadCandidates() {
        return buildWarningReadCandidates("bbk-snapshot.json");
    }

    public File getDwdSnapshotFile() {
        return new File(getWarningsDirectory(), "dwd-snapshot.json");
    }

    public List<File> getDwdSnapshotReadCandidates() {
        return buildWarningReadCandidates("dwd-snapshot.json");
    }

    public File getMergedSnapshotFile() {
        return new File(getWarningsDirectory(), "merged-warning-snapshot.json");
    }

    public File getSourceStateFile() {
        return new File(getCacheDirectory(), "source-state.json");
    }

    public List<File> getSourceStateReadCandidates() {
        List<File> candidates = new ArrayList<>();
        for (File cacheDirectory : getCompatibleCacheDirectories()) {
            candidates.add(new File(cacheDirectory, "source-state.json"));
        }
        return candidates;
    }

    public File getRadarSnapshotFile() {
        return new File(getRadarDirectory(), "radar-snapshot.json");
    }

    public File getRadarFrameFile() {
        return new File(getRadarDirectory(), CANONICAL_RADAR_FRAME_FILE_NAME);
    }

    public List<File> getRadarSnapshotReadCandidates() {
        List<File> candidates = new ArrayList<>();
        for (File cacheDirectory : getCompatibleCacheDirectories()) {
            candidates.add(new File(new File(cacheDirectory, "radar"), "radar-snapshot.json"));
        }
        return candidates;
    }

    public List<File> getRadarFrameReadCandidates() {
        List<File> candidates = new ArrayList<>();
        for (File cacheDirectory : getCompatibleCacheDirectories()) {
            File radarDirectory = new File(cacheDirectory, "radar");
            candidates.add(new File(radarDirectory, CANONICAL_RADAR_FRAME_FILE_NAME));
            candidates.add(new File(radarDirectory, LEGACY_RADAR_FRAME_FILE_NAME));
        }
        return candidates;
    }

    public File getIntegrityKeyFile() {
        return new File(getCacheDirectory(), ".integrity.key");
    }

    public File getIntegrityKeyFile(File cacheArtifact) {
        File cacheDirectory = resolveCacheDirectory(cacheArtifact);
        if (cacheDirectory == null) {
            return getIntegrityKeyFile();
        }
        return new File(cacheDirectory, ".integrity.key");
    }

    private File getCacheDirectory() {
        return new File(baseDirectory, SafeLayerConstants.CACHE_DIR_NAME);
    }

    private File getWarningsDirectory() {
        return new File(getCacheDirectory(), "warnings");
    }

    private File getRadarDirectory() {
        return new File(getCacheDirectory(), "radar");
    }

    private File resolveCacheDirectory(File cacheArtifact) {
        if (cacheArtifact == null) {
            return null;
        }

        Set<String> compatiblePaths = new LinkedHashSet<>();
        for (File cacheDirectory : getCompatibleCacheDirectories()) {
            compatiblePaths.add(cacheDirectory.getAbsolutePath());
        }

        File current = cacheArtifact.isDirectory() ? cacheArtifact : cacheArtifact.getParentFile();
        while (current != null) {
            if (compatiblePaths.contains(current.getAbsolutePath())) {
                return current;
            }
            current = current.getParentFile();
        }
        return null;
    }

    private List<File> buildWarningReadCandidates(String fileName) {
        List<File> candidates = new ArrayList<>();
        for (File cacheDirectory : getCompatibleCacheDirectories()) {
            candidates.add(new File(new File(cacheDirectory, "warnings"), fileName));
        }
        return candidates;
    }

    private List<File> getCompatibleCacheDirectories() {
        Set<String> seenPaths = new LinkedHashSet<>();
        List<File> cacheDirectories = new ArrayList<>();
        addCacheDirectories(cacheDirectories, seenPaths, baseDirectory);
        for (File legacyBaseDirectory : legacyBaseDirectories) {
            addCacheDirectories(cacheDirectories, seenPaths, legacyBaseDirectory);
        }
        return cacheDirectories;
    }

    private void addCacheDirectories(List<File> cacheDirectories, Set<String> seenPaths, File parentDirectory) {
        if (parentDirectory == null) {
            return;
        }

        String directoryName = parentDirectory.getName();
        if (SafeLayerConstants.CACHE_DIR_NAME.equals(directoryName)
                || SafeLayerConstants.LEGACY_CACHE_DIR_NAME.equals(directoryName)) {
            addDirectory(cacheDirectories, seenPaths, parentDirectory);
        }
        addCacheDirectory(cacheDirectories, seenPaths, parentDirectory, SafeLayerConstants.CACHE_DIR_NAME, true);
        addCacheDirectory(
                cacheDirectories,
                seenPaths,
                parentDirectory,
                SafeLayerConstants.LEGACY_CACHE_DIR_NAME,
                false);
    }

    private void addCacheDirectory(
            List<File> cacheDirectories,
            Set<String> seenPaths,
            File parentDirectory,
            String cacheDirectoryName,
            boolean includeNestedCanonicalDirectory) {
        File cacheDirectory = new File(parentDirectory, cacheDirectoryName);
        addDirectory(cacheDirectories, seenPaths, cacheDirectory);
        if (includeNestedCanonicalDirectory) {
            addDirectory(cacheDirectories, seenPaths, new File(cacheDirectory, SafeLayerConstants.CACHE_DIR_NAME));
        }
    }

    private void addDirectory(List<File> directories, Set<String> seenPaths, File directory) {
        if (directory == null) {
            return;
        }

        String absolutePath = directory.getAbsolutePath();
        if (seenPaths.add(absolutePath)) {
            directories.add(directory);
        }
    }

    private List<File> sanitizeBaseDirectories(List<File> baseDirectories) {
        List<File> sanitized = new ArrayList<>();
        if (baseDirectories == null) {
            return sanitized;
        }

        String primaryPath = baseDirectory == null ? null : baseDirectory.getAbsolutePath();
        Set<String> seenPaths = new LinkedHashSet<>();
        for (File directory : baseDirectories) {
            if (directory == null) {
                continue;
            }

            String absolutePath = directory.getAbsolutePath();
            if (absolutePath.equals(primaryPath)) {
                continue;
            }
            if (seenPaths.add(absolutePath)) {
                sanitized.add(directory);
            }
        }
        return sanitized;
    }
}
