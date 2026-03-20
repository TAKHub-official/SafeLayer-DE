package com.takhub.safelayerde.cache;

import com.takhub.safelayerde.util.IoUtils;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class CacheIntegrityChecker {

    private static final int MAX_JSON_VALIDATION_BYTES = 2 * 1024 * 1024;
    private static final int MAX_SIGNATURE_BYTES = 512;
    private static final int PNG_SIGNATURE_BYTES = 8;
    private static final int HMAC_KEY_BYTES = 32;
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_SUFFIX = ".sig";
    private static final byte[] PNG_SIGNATURE =
            new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

    private final CachePaths cachePaths;
    private final Map<String, byte[]> cachedIntegrityKeys = new HashMap<>();

    public enum ArtifactType {
        JSON_DOCUMENT,
        BINARY_BLOB,
        PNG_IMAGE
    }

    public CacheIntegrityChecker() {
        this(null);
    }

    public CacheIntegrityChecker(CachePaths cachePaths) {
        this.cachePaths = cachePaths;
    }

    public boolean isReadableJsonDocument(File cacheFile) {
        return isReadableJsonDocument(cacheFile, resolveIntegrityKeyFile(cacheFile));
    }

    public boolean isReadableJsonDocument(File cacheFile, File integrityKeyFile) {
        if (!hasValidSignature(cacheFile, integrityKeyFile)) {
            return false;
        }

        try {
            Object parsed = new JSONTokener(IoUtils.readUtf8(cacheFile, MAX_JSON_VALIDATION_BYTES)).nextValue();
            return parsed instanceof JSONObject;
        } catch (IOException | JSONException | RuntimeException ignored) {
            return false;
        }
    }

    public boolean isReadableBinaryFile(File cacheFile) {
        return isReadableBinaryFile(cacheFile, resolveIntegrityKeyFile(cacheFile));
    }

    public boolean isReadableBinaryFile(File cacheFile, File integrityKeyFile) {
        return isStructurallyReadableBinaryFile(cacheFile) && hasValidSignature(cacheFile, integrityKeyFile);
    }

    public boolean isReadablePngImage(File cacheFile) {
        return isReadablePngImage(cacheFile, resolveIntegrityKeyFile(cacheFile));
    }

    public boolean isReadablePngImage(File cacheFile, File integrityKeyFile) {
        if (!isStructurallyReadablePngImage(cacheFile)) {
            return false;
        }
        return hasValidSignature(cacheFile, integrityKeyFile);
    }

    public boolean canMigrate(File cacheFile, ArtifactType artifactType) {
        return canMigrate(cacheFile, artifactType, resolveIntegrityKeyFile(cacheFile));
    }

    public boolean canMigrate(File cacheFile, ArtifactType artifactType, File integrityKeyFile) {
        ArtifactType type = artifactType == null ? ArtifactType.BINARY_BLOB : artifactType;
        if (!isStructurallyReadable(cacheFile, type)) {
            return false;
        }
        if (!isIntegrityProtectionEnabled()) {
            return true;
        }

        File signatureFile = signatureFile(cacheFile);
        return !signatureFile.isFile() || hasValidSignature(cacheFile, integrityKeyFile);
    }

    public void writeSignedString(File target, String content) throws IOException {
        String normalizedContent = content == null ? "" : content;
        IoUtils.atomicWrite(target, normalizedContent);
        writeDetachedSignature(target, normalizedContent.getBytes(StandardCharsets.UTF_8));
    }

    public void writeSignedBytes(File target, byte[] content) throws IOException {
        byte[] normalizedContent = content == null ? new byte[0] : content;
        IoUtils.atomicWriteBytes(target, normalizedContent);
        writeDetachedSignature(target, normalizedContent);
    }

    private boolean isStructurallyReadable(File cacheFile, ArtifactType artifactType) {
        switch (artifactType) {
            case JSON_DOCUMENT:
                return isStructurallyReadableJsonDocument(cacheFile);
            case PNG_IMAGE:
                return isStructurallyReadablePngImage(cacheFile);
            case BINARY_BLOB:
            default:
                return isStructurallyReadableBinaryFile(cacheFile);
        }
    }

    private boolean isStructurallyReadableJsonDocument(File cacheFile) {
        if (!isStructurallyReadableBinaryFile(cacheFile)) {
            return false;
        }

        try {
            Object parsed = new JSONTokener(IoUtils.readUtf8(cacheFile, MAX_JSON_VALIDATION_BYTES)).nextValue();
            return parsed instanceof JSONObject;
        } catch (IOException | JSONException | RuntimeException ignored) {
            return false;
        }
    }

    private boolean isStructurallyReadableBinaryFile(File cacheFile) {
        return cacheFile != null && cacheFile.isFile() && cacheFile.length() > 0L;
    }

    private boolean isStructurallyReadablePngImage(File cacheFile) {
        if (!isStructurallyReadableBinaryFile(cacheFile) || cacheFile == null || !cacheFile.getName().endsWith(".png")) {
            return false;
        }

        try {
            byte[] signature = IoUtils.readLeadingBytes(cacheFile, PNG_SIGNATURE_BYTES);
            if (signature.length != PNG_SIGNATURE.length) {
                return false;
            }
            for (int index = 0; index < PNG_SIGNATURE.length; index++) {
                if (signature[index] != PNG_SIGNATURE[index]) {
                    return false;
                }
            }
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private boolean hasValidSignature(File cacheFile) {
        return hasValidSignature(cacheFile, resolveIntegrityKeyFile(cacheFile));
    }

    private boolean hasValidSignature(File cacheFile, File integrityKeyFile) {
        if (!isStructurallyReadableBinaryFile(cacheFile)) {
            return false;
        }
        if (!isIntegrityProtectionEnabled()) {
            return true;
        }

        File signatureFile = signatureFile(cacheFile);
        if (!signatureFile.isFile()) {
            return false;
        }

        try {
            byte[] expectedSignature = decodeSignature(IoUtils.readUtf8(signatureFile, MAX_SIGNATURE_BYTES));
            byte[] resolvedIntegrityKey = loadIntegrityKey(integrityKeyFile, false);
            if (resolvedIntegrityKey == null) {
                return false;
            }
            byte[] actualSignature = computeSignature(IoUtils.readBytes(cacheFile), resolvedIntegrityKey);
            return MessageDigest.isEqual(expectedSignature, actualSignature);
        } catch (IOException | IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean isIntegrityProtectionEnabled() {
        return cachePaths != null;
    }

    private void writeDetachedSignature(File target, byte[] content) throws IOException {
        if (!isIntegrityProtectionEnabled()) {
            return;
        }
        String encodedSignature = Base64.getEncoder()
                .withoutPadding()
                .encodeToString(computeSignature(content, loadOrCreatePrimaryIntegrityKey()));
        IoUtils.atomicWrite(signatureFile(target), encodedSignature);
    }

    private byte[] computeSignature(byte[] content, byte[] integrityKey) throws IOException {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(integrityKey == null ? new byte[0] : integrityKey, HMAC_ALGORITHM));
            return mac.doFinal(content == null ? new byte[0] : content);
        } catch (GeneralSecurityException exception) {
            throw new IOException("Unable to compute cache integrity signature.", exception);
        }
    }

    private byte[] loadOrCreatePrimaryIntegrityKey() throws IOException {
        if (!isIntegrityProtectionEnabled()) {
            return new byte[0];
        }
        return loadIntegrityKey(cachePaths.getIntegrityKeyFile(), true);
    }

    private File resolveIntegrityKeyFile(File cacheFile) {
        if (!isIntegrityProtectionEnabled()) {
            return null;
        }
        return cachePaths.getIntegrityKeyFile(cacheFile);
    }

    private byte[] loadIntegrityKey(File keyFile, boolean createIfMissing) throws IOException {
        if (!isIntegrityProtectionEnabled()) {
            return new byte[0];
        }
        if (keyFile == null) {
            return null;
        }

        String keyPath = keyFile.getAbsolutePath();
        synchronized (cachedIntegrityKeys) {
            byte[] cachedIntegrityKey = cachedIntegrityKeys.get(keyPath);
            if (cachedIntegrityKey != null) {
                return cachedIntegrityKey.clone();
            }
        }

        synchronized (cachedIntegrityKeys) {
            byte[] cachedIntegrityKey = cachedIntegrityKeys.get(keyPath);
            if (cachedIntegrityKey != null) {
                return cachedIntegrityKey.clone();
            }

            if (keyFile.isFile()) {
                byte[] loadedKey = decodeSignature(IoUtils.readUtf8(keyFile, MAX_SIGNATURE_BYTES));
                cachedIntegrityKeys.put(keyPath, loadedKey);
                return loadedKey.clone();
            }
            if (!createIfMissing) {
                return null;
            }

            byte[] generatedKey = new byte[HMAC_KEY_BYTES];
            new SecureRandom().nextBytes(generatedKey);
            IoUtils.atomicWrite(
                    keyFile,
                    Base64.getEncoder().withoutPadding().encodeToString(generatedKey));
            cachedIntegrityKeys.put(keyPath, generatedKey);
            return generatedKey.clone();
        }
    }

    private byte[] decodeSignature(String encodedSignature) throws IOException {
        String normalizedSignature = encodedSignature == null ? null : encodedSignature.trim();
        if (normalizedSignature == null || normalizedSignature.isEmpty()) {
            throw new IOException("Cache integrity signature is empty.");
        }
        return Base64.getDecoder().decode(normalizedSignature);
    }

    private File signatureFile(File cacheFile) {
        return new File(cacheFile.getAbsolutePath() + SIGNATURE_SUFFIX);
    }
}
