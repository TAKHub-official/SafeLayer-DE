package com.takhub.safelayerde.source.common;

import android.util.Base64;
import android.util.Log;

import com.takhub.safelayerde.debug.SafeLayerDebugLog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class HttpClient {

    private static final String TAG = "SafeLayerHttp";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final int MAX_TEXT_BODY_BYTES = 2 * 1024 * 1024;
    private static final int MAX_BINARY_BODY_BYTES = 12 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 5;
    private static final Map<RemoteEndpointPolicy, SSLSocketFactory> TLS_SOCKET_FACTORY_CACHE =
            new ConcurrentHashMap<>();

    public String fetchString(RemoteRequest request) throws IOException {
        return new String(executeRequest(request, MAX_TEXT_BODY_BYTES, true), StandardCharsets.UTF_8);
    }

    public byte[] fetchBytes(RemoteRequest request) throws IOException {
        return executeRequest(request, MAX_BINARY_BODY_BYTES, true);
    }

    private byte[] executeRequest(
            RemoteRequest request,
            int maxBodyBytes,
            boolean requireBody) throws IOException {
        HttpURLConnection connection = null;
        InputStream responseStream = null;
        try {
            RemoteRequest currentRequest = requireRequest(request);
            URL url = currentRequest.getUrl();
            int redirectCount = 0;
            while (true) {
                String target = describeTarget(url);
                SafeLayerDebugLog.i(TAG, "GET " + target);
                connection = openConnection(currentRequest);
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setDoInput(true);
                connection.setUseCaches(false);
                connection.setDefaultUseCaches(false);
                connection.setInstanceFollowRedirects(false);
                connection.connect();

                int responseCode = connection.getResponseCode();
                verifyPinnedTlsPeer(currentRequest, connection);
                SafeLayerDebugLog.i(TAG, "HTTP " + responseCode + " " + target);
                if (isRedirectResponseCode(responseCode)) {
                    if (redirectCount >= MAX_REDIRECTS) {
                        throw new IOException("Too many HTTP redirects.");
                    }
                    RemoteRequest redirectRequest = resolveRedirectRequest(connection, currentRequest);
                    URL redirectUrl = redirectRequest.getUrl();
                    SafeLayerDebugLog.i(TAG, "redirect " + target + " -> " + describeTarget(redirectUrl));
                    connection.disconnect();
                    connection = null;
                    currentRequest = redirectRequest;
                    url = redirectUrl;
                    redirectCount++;
                    continue;
                }
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw buildHttpError(connection);
                }

                responseStream = connection.getInputStream();
                byte[] body = readFully(responseStream, maxBodyBytes);
                if (requireBody && body.length == 0) {
                    throw new IOException("Empty HTTP response body.");
                }
                SafeLayerDebugLog.i(TAG, "received-bytes " + target + ", bytes=" + body.length);
                return body;
            }
        } catch (IOException exception) {
            logError("Request failed.", exception);
            SafeLayerDebugLog.e(TAG, "request-failed " + describeTarget(request), exception);
            throw exception;
        } finally {
            closeQuietly(responseStream);
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public static URL validateUrl(String rawUrl, RemoteEndpointPolicy policy) throws IOException {
        if (policy == null) {
            throw new IOException("HTTP request policy is missing.");
        }
        return policy.validate(rawUrl);
    }

    public static URL validateUrl(URL url, RemoteEndpointPolicy policy) throws IOException {
        if (policy == null) {
            throw new IOException("HTTP request policy is missing.");
        }
        return policy.validate(url);
    }

    private HttpURLConnection openConnection(RemoteRequest request) throws IOException {
        RemoteRequest validatedRequest = requireRequest(request);
        URL url = validatedRequest.getUrl();
        Object candidate = url == null ? null : url.openConnection();
        if (!(candidate instanceof HttpURLConnection)) {
            throw new IOException("URL does not resolve to an HTTP connection.");
        }
        HttpURLConnection connection = (HttpURLConnection) candidate;
        if (connection instanceof HttpsURLConnection) {
            configurePinnedTls(validatedRequest.getPolicy(), (HttpsURLConnection) connection);
        }
        return connection;
    }

    private RemoteRequest resolveRedirectRequest(
            HttpURLConnection connection,
            RemoteRequest currentRequest) throws IOException {
        String location = connection == null ? null : connection.getHeaderField("Location");
        if (location == null || location.trim().isEmpty()) {
            throw new IOException("HTTP redirect is missing a Location header.");
        }
        return currentRequest.getPolicy().resolve(currentRequest.getUrl(), location);
    }

    private void configurePinnedTls(RemoteEndpointPolicy policy, HttpsURLConnection connection) throws IOException {
        if (policy == null || connection == null || !policy.hasPinnedPublicKeyHashes()) {
            return;
        }
        connection.setSSLSocketFactory(getPinnedSocketFactory(policy));
    }

    private SSLSocketFactory getPinnedSocketFactory(RemoteEndpointPolicy policy) throws IOException {
        SSLSocketFactory existingFactory = TLS_SOCKET_FACTORY_CACHE.get(policy);
        if (existingFactory != null) {
            return existingFactory;
        }
        SSLSocketFactory createdFactory = buildPinnedSocketFactory(policy.getPinnedPublicKeyHashes());
        SSLSocketFactory racedFactory = TLS_SOCKET_FACTORY_CACHE.putIfAbsent(policy, createdFactory);
        return racedFactory == null ? createdFactory : racedFactory;
    }

    private SSLSocketFactory buildPinnedSocketFactory(Set<String> allowedPins) throws IOException {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            TrustManager[] trustManagers = new TrustManager[]{
                    new PinnedX509TrustManager(getDefaultTrustManager(), allowedPins)
            };
            sslContext.init(null, trustManagers, null);
            return sslContext.getSocketFactory();
        } catch (Exception exception) {
            throw new IOException("Failed to initialize pinned TLS context.", exception);
        }
    }

    private X509TrustManager getDefaultTrustManager() throws IOException {
        try {
            TrustManagerFactory factory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init((KeyStore) null);
            for (TrustManager trustManager : factory.getTrustManagers()) {
                if (trustManager instanceof X509TrustManager) {
                    return (X509TrustManager) trustManager;
                }
            }
        } catch (Exception exception) {
            throw new IOException("Failed to resolve the default TLS trust manager.", exception);
        }
        throw new IOException("Default TLS trust manager is unavailable.");
    }

    private void verifyPinnedTlsPeer(RemoteRequest request, HttpURLConnection connection) throws IOException {
        if (request == null || connection == null) {
            return;
        }
        RemoteEndpointPolicy policy = request.getPolicy();
        if (policy == null || !policy.hasPinnedPublicKeyHashes()) {
            return;
        }
        if (!(connection instanceof HttpsURLConnection)) {
            throw new IOException("Pinned TLS policy requires an HTTPS connection.");
        }
        try {
            HttpsURLConnection httpsConnection = (HttpsURLConnection) connection;
            if (!matchesPinnedCertificates(
                    httpsConnection.getServerCertificates(),
                    policy.getPinnedPublicKeyHashes())) {
                throw new IOException("TLS public key pin verification failed for "
                        + describeTarget(request.getUrl()) + ".");
            }
        } catch (SSLPeerUnverifiedException exception) {
            throw new IOException("TLS peer identity could not be verified for "
                    + describeTarget(request.getUrl()) + ".", exception);
        }
    }

    static boolean matchesPinnedCertificates(
            Certificate[] serverCertificates,
            Set<String> allowedPins) throws IOException {
        if (allowedPins == null || allowedPins.isEmpty()) {
            return true;
        }
        if (serverCertificates == null || serverCertificates.length == 0) {
            return false;
        }
        for (Certificate certificate : serverCertificates) {
            if (!(certificate instanceof X509Certificate)) {
                continue;
            }
            if (allowedPins.contains(sha256PublicKeyPin((X509Certificate) certificate))) {
                return true;
            }
        }
        return false;
    }

    static String sha256PublicKeyPin(X509Certificate certificate) throws IOException {
        if (certificate == null || certificate.getPublicKey() == null) {
            throw new IOException("TLS certificate is missing a public key.");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashedKey = digest.digest(certificate.getPublicKey().getEncoded());
            return encodeBase64(hashedKey);
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 is unavailable for TLS pin verification.", exception);
        }
    }

    private static String encodeBase64(byte[] value) {
        try {
            Class<?> base64Class = Class.forName("java.util.Base64");
            Object encoder = base64Class.getMethod("getEncoder").invoke(null);
            return (String) encoder.getClass().getMethod("encodeToString", byte[].class).invoke(encoder, value);
        } catch (Exception ignored) {
            return Base64.encodeToString(value, Base64.NO_WRAP);
        }
    }

    private boolean isRedirectResponseCode(int responseCode) {
        return responseCode == HttpURLConnection.HTTP_MULT_CHOICE
                || responseCode == HttpURLConnection.HTTP_MOVED_PERM
                || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                || responseCode == HttpURLConnection.HTTP_SEE_OTHER
                || responseCode == 307
                || responseCode == 308;
    }

    private IOException buildHttpError(HttpURLConnection connection) {
        int responseCode = -1;
        String responseMessage = null;
        try {
            responseCode = connection.getResponseCode();
            responseMessage = connection.getResponseMessage();
        } catch (IOException ignored) {
            // Preserve the primary HTTP error even if secondary inspection fails.
        }

        StringBuilder message = new StringBuilder()
                .append("Unexpected HTTP ")
                .append(responseCode);
        if (responseMessage != null && !responseMessage.trim().isEmpty()) {
            message.append(" ").append(responseMessage.trim());
        }
        message.append(" from remote server.");
        return new IOException(message.toString());
    }

    private byte[] readUpTo(InputStream inputStream, int maxBytes) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[512];
        int totalBytes = 0;
        int bytesRead;
        while (totalBytes < maxBytes
                && (bytesRead = inputStream.read(buffer, 0, Math.min(buffer.length, maxBytes - totalBytes))) != -1) {
            outputStream.write(buffer, 0, bytesRead);
            totalBytes += bytesRead;
        }
        return outputStream.toByteArray();
    }

    private byte[] readFully(InputStream inputStream, int maxBytes) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int totalBytes = 0;
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            totalBytes += bytesRead;
            if (maxBytes > 0 && totalBytes > maxBytes) {
                throw new IOException("HTTP response body exceeds limit of " + maxBytes + " bytes.");
            }
            outputStream.write(buffer, 0, bytesRead);
        }
        return outputStream.toByteArray();
    }

    private void closeQuietly(InputStream inputStream) {
        if (inputStream == null) {
            return;
        }
        try {
            inputStream.close();
        } catch (IOException ignored) {
            // Preserve the primary exception path.
        }
    }

    private void logError(String message, Throwable throwable) {
        try {
            Log.e(TAG, message, throwable);
        } catch (RuntimeException ignored) {
            // Host-side unit tests may not provide a full Android logging runtime.
        }
    }

    private static RemoteRequest requireRequest(RemoteRequest request) throws IOException {
        if (request == null) {
            throw new IOException("HTTP request is missing.");
        }
        RemoteEndpointPolicy policy = request.getPolicy();
        if (policy == null) {
            throw new IOException("HTTP request policy is missing.");
        }
        policy.validate(request.getUrl());
        return request;
    }

    private static String describeTarget(RemoteRequest request) {
        try {
            return request == null ? "target=unknown" : describeTarget(requireRequest(request).getUrl());
        } catch (IOException ignored) {
            return "target=unknown";
        }
    }

    private static String describeTarget(URL url) {
        if (url == null) {
            return "target=unknown";
        }
        String host = url.getHost();
        if (host == null || host.trim().isEmpty()) {
            return "target=unknown";
        }
        int port = url.getPort() >= 0 ? url.getPort() : url.getDefaultPort();
        return port <= 0 || port == 443
                ? "host=" + host
                : "host=" + host + ":" + port;
    }

    public static final class RemoteRequest {

        private final RemoteEndpointPolicy policy;
        private final URL url;

        RemoteRequest(RemoteEndpointPolicy policy, URL url) {
            this.policy = policy;
            this.url = url;
        }

        public RemoteEndpointPolicy getPolicy() {
            return policy;
        }

        public URL getUrl() {
            return url;
        }

        public String getUrlString() {
            return url == null ? null : url.toString();
        }
    }

    public static final class RemoteEndpointPolicy {

        private static final Pattern IPV4_LITERAL_PATTERN =
                Pattern.compile("^\\d{1,3}(?:\\.\\d{1,3}){3}$");
        private static final Pattern IPV6_LITERAL_PATTERN =
                Pattern.compile("^[0-9a-f:]+$", Pattern.CASE_INSENSITIVE);

        private final String label;
        private final URL baseUrl;
        private final String scheme;
        private final String host;
        private final int port;
        private final String pathPrefix;
        private final Set<String> pinnedPublicKeyHashes;

        private RemoteEndpointPolicy(String label, String baseUrl, String... pinnedPublicKeyHashes) {
            this.label = normalizeLabel(label);
            this.baseUrl = parseTrustedBaseUrl(baseUrl);
            this.scheme = normalizeScheme(this.baseUrl.getProtocol());
            this.host = normalizeHost(this.baseUrl.getHost());
            this.port = effectivePort(this.baseUrl);
            this.pathPrefix = normalizePathPrefix(this.baseUrl.getPath());
            this.pinnedPublicKeyHashes = normalizePins(pinnedPublicKeyHashes);
        }

        public static RemoteEndpointPolicy https(String label, String baseUrl, String... pinnedPublicKeyHashes) {
            return new RemoteEndpointPolicy(label, baseUrl, pinnedPublicKeyHashes);
        }

        public RemoteRequest baseRequest() {
            return new RemoteRequest(this, baseUrl);
        }

        public RemoteRequest request(String rawUrl) throws IOException {
            return new RemoteRequest(this, validate(rawUrl));
        }

        public RemoteRequest resolve(String relativeOrAbsolutePath) throws IOException {
            String candidate = relativeOrAbsolutePath == null ? null : relativeOrAbsolutePath.trim();
            if (candidate == null || candidate.isEmpty()) {
                throw new IOException(label + " request target is empty.");
            }
            if (looksLikeAbsoluteUrl(candidate)) {
                return request(candidate);
            }

            URL resolutionBase = baseUrl;
            if (!baseUrl.toString().endsWith("/") && !candidate.startsWith("?")) {
                resolutionBase = new URL(baseUrl.toString() + "/");
            }
            return new RemoteRequest(this, validate(new URL(resolutionBase, candidate)));
        }

        public RemoteRequest resolve(URL currentUrl, String location) throws IOException {
            if (currentUrl == null) {
                throw new IOException(label + " redirect source is empty.");
            }
            String normalizedLocation = location == null ? null : location.trim();
            if (normalizedLocation == null || normalizedLocation.isEmpty()) {
                throw new IOException(label + " redirect target is empty.");
            }
            validate(currentUrl);
            return new RemoteRequest(this, validate(new URL(currentUrl, normalizedLocation)));
        }

        public URL validate(String rawUrl) throws IOException {
            String normalizedUrl = rawUrl == null ? null : rawUrl.trim();
            if (normalizedUrl == null || normalizedUrl.isEmpty()) {
                throw new IOException(label + " URL is empty.");
            }

            try {
                return validate(new URL(normalizedUrl));
            } catch (RuntimeException | MalformedURLException exception) {
                throw new IOException("Invalid " + label + " URL.", exception);
            }
        }

        public URL validate(URL url) throws IOException {
            if (url == null) {
                throw new IOException(label + " URL is empty.");
            }
            String protocol = normalizeScheme(url.getProtocol());
            if (!scheme.equals(protocol)) {
                throw new IOException(label + " requires HTTPS.");
            }
            if (url.getUserInfo() != null && !url.getUserInfo().trim().isEmpty()) {
                throw new IOException(label + " must not include user info.");
            }

            String candidateHost = normalizeHost(url.getHost());
            if (!host.equals(candidateHost)) {
                throw new IOException(label + " host is not allowed.");
            }
            if (isIpLiteral(candidateHost) || "localhost".equals(candidateHost)) {
                throw new IOException(label + " host is not allowed.");
            }

            int candidatePort = effectivePort(url);
            if (candidatePort != port) {
                throw new IOException(label + " port is not allowed.");
            }

            String candidatePath = canonicalizeRequestPath(url.getPath());
            if (!matchesPathPrefix(candidatePath)) {
                throw new IOException(label + " path is not allowed.");
            }
            return url;
        }

        boolean hasPinnedPublicKeyHashes() {
            return !pinnedPublicKeyHashes.isEmpty();
        }

        Set<String> getPinnedPublicKeyHashes() {
            return pinnedPublicKeyHashes;
        }

        private boolean matchesPathPrefix(String candidatePath) {
            if (pathPrefix.endsWith("/")) {
                return candidatePath.startsWith(pathPrefix);
            }
            return pathPrefix.equals(candidatePath) || candidatePath.startsWith(pathPrefix + "/");
        }

        private static URL parseTrustedBaseUrl(String rawBaseUrl) {
            try {
                URL url = new URL(rawBaseUrl);
                String protocol = normalizeScheme(url.getProtocol());
                if (!"https".equals(protocol)) {
                    throw new IllegalArgumentException("Trusted endpoint base URL must use HTTPS.");
                }
                String host = normalizeHost(url.getHost());
                if (host.isEmpty() || host.contains(" ") || isIpLiteral(host) || "localhost".equals(host)) {
                    throw new IllegalArgumentException("Trusted endpoint base URL host is invalid.");
                }
                if (url.getRef() != null) {
                    throw new IllegalArgumentException("Trusted endpoint base URL must not include a fragment.");
                }
                return url;
            } catch (MalformedURLException exception) {
                throw new IllegalArgumentException("Trusted endpoint base URL is invalid.", exception);
            }
        }

        private static String normalizeLabel(String label) {
            String value = label == null ? "" : label.trim();
            return value.isEmpty() ? "HTTP endpoint" : value;
        }

        private static String normalizeScheme(String scheme) {
            return scheme == null ? "" : scheme.trim().toLowerCase(Locale.ROOT);
        }

        private static String normalizeHost(String host) {
            return host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
        }

        private static String normalizePathPrefix(String path) {
            String normalized = normalizeRequestPath(path);
            if (!normalized.startsWith("/")) {
                normalized = "/" + normalized;
            }
            return normalized;
        }

        private String canonicalizeRequestPath(String path) throws IOException {
            String normalizedPath = normalizeRequestPath(path);
            if (normalizedPath.indexOf('\\') >= 0) {
                throw new IOException(label + " path is not allowed.");
            }
            String lowerCasePath = normalizedPath.toLowerCase(Locale.ROOT);
            if (lowerCasePath.contains("%2f") || lowerCasePath.contains("%5c")) {
                throw new IOException(label + " path is not allowed.");
            }
            if (containsDotSegment(normalizedPath)) {
                throw new IOException(label + " path is not allowed.");
            }

            try {
                URI normalizedUri = new URI(null, null, normalizedPath, null).normalize();
                return normalizeRequestPath(normalizedUri.getPath());
            } catch (URISyntaxException exception) {
                throw new IOException("Invalid " + label + " URL.", exception);
            }
        }

        private static String normalizeRequestPath(String path) {
            if (path == null || path.trim().isEmpty()) {
                return "/";
            }
            return path.startsWith("/") ? path : "/" + path;
        }

        private static boolean containsDotSegment(String path) {
            String normalizedPath = normalizeRequestPath(path);
            String[] segments = normalizedPath.split("/", -1);
            for (String segment : segments) {
                if (segment == null || segment.isEmpty()) {
                    continue;
                }
                String decodedSegment = decodePercentEscapes(segment);
                if (".".equals(decodedSegment) || "..".equals(decodedSegment)) {
                    return true;
                }
            }
            return false;
        }

        private static String decodePercentEscapes(String value) {
            StringBuilder builder = new StringBuilder(value.length());
            for (int index = 0; index < value.length(); index++) {
                char current = value.charAt(index);
                if (current == '%' && index + 2 < value.length()) {
                    int decoded = decodeHexByte(value.charAt(index + 1), value.charAt(index + 2));
                    if (decoded >= 0) {
                        builder.append((char) decoded);
                        index += 2;
                        continue;
                    }
                }
                builder.append(current);
            }
            return builder.toString();
        }

        private static int decodeHexByte(char high, char low) {
            int highValue = Character.digit(high, 16);
            int lowValue = Character.digit(low, 16);
            if (highValue < 0 || lowValue < 0) {
                return -1;
            }
            return (highValue << 4) + lowValue;
        }

        private static boolean looksLikeAbsoluteUrl(String candidate) {
            int schemeIndex = candidate.indexOf("://");
            return schemeIndex > 0;
        }

        private static int effectivePort(URL url) {
            if (url == null) {
                return -1;
            }
            return url.getPort() >= 0 ? url.getPort() : url.getDefaultPort();
        }

        private static Set<String> normalizePins(String... pins) {
            LinkedHashSet<String> normalizedPins = new LinkedHashSet<>();
            if (pins == null) {
                return Collections.emptySet();
            }
            for (String pin : pins) {
                String normalizedPin = pin == null ? "" : pin.trim();
                if (normalizedPin.isEmpty()) {
                    continue;
                }
                normalizedPins.add(normalizedPin);
            }
            return normalizedPins.isEmpty()
                    ? Collections.<String>emptySet()
                    : Collections.unmodifiableSet(normalizedPins);
        }

        private static boolean isIpLiteral(String host) {
            if (host == null || host.isEmpty()) {
                return false;
            }
            String candidate = host;
            if (candidate.startsWith("[") && candidate.endsWith("]")) {
                candidate = candidate.substring(1, candidate.length() - 1);
            }
            return IPV4_LITERAL_PATTERN.matcher(candidate).matches()
                    || IPV6_LITERAL_PATTERN.matcher(candidate).matches() && candidate.indexOf(':') >= 0;
        }
    }

    private static final class PinnedX509TrustManager implements X509TrustManager {

        private final X509TrustManager delegate;
        private final Set<String> allowedPins;

        private PinnedX509TrustManager(X509TrustManager delegate, Set<String> allowedPins) {
            this.delegate = delegate;
            this.allowedPins = allowedPins;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            delegate.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            delegate.checkServerTrusted(chain, authType);
            try {
                if (!matchesPinnedCertificates(chain, allowedPins)) {
                    throw new CertificateException("TLS public key pin verification failed.");
                }
            } catch (IOException exception) {
                throw new CertificateException("TLS public key pin verification failed.", exception);
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return delegate.getAcceptedIssuers();
        }
    }
}
