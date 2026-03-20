package com.takhub.safelayerde.source.common;

import android.util.Log;

import com.takhub.safelayerde.debug.SafeLayerDebugLog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class HttpClient {

    private static final String TAG = "SafeLayerHttp";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final int MAX_TEXT_BODY_BYTES = 2 * 1024 * 1024;
    private static final int MAX_BINARY_BODY_BYTES = 12 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 5;

    public interface RedirectValidator {
        void validateRedirect(URL fromUrl, URL redirectUrl) throws IOException;
    }

    public String fetchString(String url) throws IOException {
        return new String(executeRequest(url, MAX_TEXT_BODY_BYTES, true, null), StandardCharsets.UTF_8);
    }

    public byte[] fetchBytes(String url) throws IOException {
        return fetchBytes(url, null);
    }

    public byte[] fetchBytes(String url, RedirectValidator redirectValidator) throws IOException {
        return executeRequest(url, MAX_BINARY_BODY_BYTES, true, redirectValidator);
    }

    private byte[] executeRequest(
            String rawUrl,
            int maxBodyBytes,
            boolean requireBody,
            RedirectValidator redirectValidator) throws IOException {
        HttpURLConnection connection = null;
        InputStream responseStream = null;
        try {
            URL url = validateUrl(rawUrl);
            int redirectCount = 0;
            while (true) {
                String target = describeTarget(url);
                SafeLayerDebugLog.i(TAG, "GET " + target);
                connection = openConnection(url);
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setDoInput(true);
                connection.setUseCaches(false);
                connection.setDefaultUseCaches(false);
                connection.setInstanceFollowRedirects(false);
                connection.connect();

                int responseCode = connection.getResponseCode();
                SafeLayerDebugLog.i(TAG, "HTTP " + responseCode + " " + target);
                if (isRedirectResponseCode(responseCode)) {
                    if (redirectCount >= MAX_REDIRECTS) {
                        throw new IOException("Too many HTTP redirects.");
                    }
                    URL redirectUrl = resolveRedirectUrl(connection, url, redirectValidator);
                    SafeLayerDebugLog.i(TAG, "redirect " + target + " -> " + describeTarget(redirectUrl));
                    connection.disconnect();
                    connection = null;
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
            SafeLayerDebugLog.e(TAG, "request-failed " + describeTarget(rawUrl), exception);
            throw exception;
        } finally {
            closeQuietly(responseStream);
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public static URL validateUrl(String rawUrl) throws IOException {
        String normalizedUrl = rawUrl == null ? null : rawUrl.trim();
        if (normalizedUrl == null || normalizedUrl.isEmpty()) {
            throw new IOException("HTTP URL is empty.");
        }

        URL url;
        try {
            url = new URL(normalizedUrl);
        } catch (RuntimeException | IOException exception) {
            throw new IOException("Invalid HTTP URL.", exception);
        }
        String protocol = url.getProtocol();
        if (!"https".equalsIgnoreCase(protocol)) {
            throw new IOException("Only HTTPS URLs are allowed.");
        }
        String host = url.getHost();
        if (host == null || host.trim().isEmpty() || host.contains(" ")) {
            throw new IOException("Invalid HTTP URL.");
        }
        return url;
    }

    private HttpURLConnection openConnection(URL url) throws IOException {
        Object candidate = url == null ? null : url.openConnection();
        if (!(candidate instanceof HttpURLConnection)) {
            throw new IOException("URL does not resolve to an HTTP connection.");
        }
        return (HttpURLConnection) candidate;
    }

    private URL resolveRedirectUrl(
            HttpURLConnection connection,
            URL currentUrl,
            RedirectValidator redirectValidator) throws IOException {
        String location = connection == null ? null : connection.getHeaderField("Location");
        if (location == null || location.trim().isEmpty()) {
            throw new IOException("HTTP redirect is missing a Location header.");
        }

        URL redirectUrl = validateUrl(new URL(currentUrl, location.trim()).toString());
        if (redirectValidator != null) {
            redirectValidator.validateRedirect(currentUrl, redirectUrl);
        }
        return redirectUrl;
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

    private static String describeTarget(String rawUrl) {
        try {
            return describeTarget(validateUrl(rawUrl));
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
}
