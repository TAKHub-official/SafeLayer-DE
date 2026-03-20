package com.takhub.safelayerde.source.dwd;

import com.takhub.safelayerde.debug.SafeLayerDebugLog;
import com.takhub.safelayerde.source.common.HttpClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class DwdCapZipFetcher {

    private static final int MAX_ENTRY_BYTES = 1024 * 1024;
    private static final int MAX_XML_ENTRY_COUNT = 128;
    private static final int MAX_TOTAL_XML_BYTES = 4 * 1024 * 1024;
    private static final int MAX_DISPLAY_ENTRY_NAME_LENGTH = 72;
    private static final Pattern DIRECTORY_HREF_PATTERN = Pattern.compile("href\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern CAP_ARCHIVE_NAME_PATTERN = Pattern.compile(
            "(Z_CAP_C_EDZW_((\\d{14})|LATEST)_[^\"/]*?_PREMIUMDWD_COMMUNEUNION_([A-Z]+)\\.zip)$",
            Pattern.CASE_INSENSITIVE);

    private final HttpClient httpClient;

    public DwdCapZipFetcher(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public FetchResult fetch(String url) throws IOException {
        ArchiveCandidate selectedArchive = resolveArchive(url);
        String zipUrl = selectedArchive.resolvedUrl;
        byte[] zipBytes = httpClient == null ? null : httpClient.fetchBytes(
                zipUrl,
                new HttpClient.RedirectValidator() {
                    @Override
                    public void validateRedirect(URL fromUrl, URL redirectUrl) throws IOException {
                        validateArchiveRedirect(selectedArchive.resolvedUrl, fromUrl, redirectUrl);
                    }
                });
        if (zipBytes == null || zipBytes.length == 0) {
            throw new IOException("DWD CAP ZIP response was empty.");
        }
        SafeLayerDebugLog.i("SafeLayerDwdZip", "archive-download archive="
                + selectedArchive.archiveName + ", bytes=" + zipBytes.length);

        List<XmlEntry> xmlEntries = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        boolean sawAnyEntry = false;
        int xmlEntryCount = 0;
        int totalXmlBytes = 0;

        ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipBytes));
        try {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                sawAnyEntry = true;
                if (zipEntry.isDirectory()) {
                    zipInputStream.closeEntry();
                    continue;
                }

                String entryName = zipEntry.getName();
                if (entryName == null || !entryName.toLowerCase().endsWith(".xml")) {
                    zipInputStream.closeEntry();
                    continue;
                }

                xmlEntryCount++;
                if (xmlEntryCount > MAX_XML_ENTRY_COUNT) {
                    throw new IOException("DWD CAP ZIP exceeds XML entry limit of " + MAX_XML_ENTRY_COUNT + ".");
                }

                byte[] entryBytes = readEntry(zipInputStream, zipEntry);
                zipInputStream.closeEntry();
                if (entryBytes == null || entryBytes.length == 0) {
                    continue;
                }
                totalXmlBytes += entryBytes.length;
                if (totalXmlBytes > MAX_TOTAL_XML_BYTES) {
                    throw new IOException("DWD CAP ZIP exceeds uncompressed XML limit of "
                            + MAX_TOTAL_XML_BYTES + " bytes.");
                }

                String xml = new String(entryBytes, StandardCharsets.UTF_8).trim();
                if (xml.isEmpty()) {
                    failures.add("Ignored empty DWD CAP entry " + summarizeEntryName(entryName));
                    continue;
                }

                xmlEntries.add(new XmlEntry(entryName, xml));
            }
        } finally {
            zipInputStream.close();
        }

        if (!sawAnyEntry) {
            throw new IOException("DWD CAP ZIP contained no entries.");
        }

        if (xmlEntries.isEmpty() && failures.isEmpty()) {
            throw new IOException("DWD CAP ZIP contained no XML payloads.");
        }

        SafeLayerDebugLog.i("SafeLayerDwdZip", "archive-read xmlEntries=" + xmlEntries.size()
                + ", failures=" + failures.size());
        return new FetchResult(
                xmlEntries,
                failures,
                selectedArchive.archiveName,
                selectedArchive.language,
                selectedArchive.resolvedUrl);
    }

    private ArchiveCandidate resolveArchive(String url) throws IOException {
        if (url == null || url.trim().isEmpty()) {
            throw new IOException("DWD CAP URL is empty.");
        }
        if (!url.endsWith("/")) {
            ArchiveCandidate directCandidate = ArchiveCandidate.fromHref(url);
            if (directCandidate != null) {
                return directCandidate.withResolvedUrl(HttpClient.validateUrl(url).toString());
            }
            return new ArchiveCandidate(url, "", false, Integer.MAX_VALUE, "unknown", url);
        }
        String html = httpClient == null ? null : httpClient.fetchString(url);
        if (html == null || html.trim().isEmpty()) {
            throw new IOException("DWD CAP directory listing was empty.");
        }

        List<ArchiveCandidate> candidates = new ArrayList<>();
        Matcher matcher = DIRECTORY_HREF_PATTERN.matcher(html);
        while (matcher.find()) {
            String href = matcher.group(1);
            ArchiveCandidate candidate = resolveArchiveHref(url, href);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        if (candidates.isEmpty()) {
            throw new IOException("No acceptable DWD CAP archive link found in directory listing.");
        }

        Collections.sort(candidates, new Comparator<ArchiveCandidate>() {
            @Override
            public int compare(ArchiveCandidate leftCandidate, ArchiveCandidate rightCandidate) {
                if (leftCandidate == null || rightCandidate == null) {
                    return rightCandidate == null ? -1 : 1;
                }
                int languageCompare = Integer.compare(leftCandidate.languageRank, rightCandidate.languageRank);
                if (languageCompare != 0) {
                    return languageCompare;
                }
                int latestCompare = Boolean.compare(rightCandidate.latestAlias, leftCandidate.latestAlias);
                if (latestCompare != 0) {
                    return latestCompare;
                }
                int timestampCompare = rightCandidate.timestamp.compareTo(leftCandidate.timestamp);
                if (timestampCompare != 0) {
                    return timestampCompare;
                }
                return rightCandidate.archiveName.compareTo(leftCandidate.archiveName);
            }
        });

        ArchiveCandidate selectedCandidate = candidates.get(0);
        SafeLayerDebugLog.i("SafeLayerDwdZip", "archive-selected candidateCount=" + candidates.size()
                + ", selected=" + selectedCandidate.archiveName
                + ", selectedLanguage=" + selectedCandidate.language
                + ", selectedTimestamp=" + selectedCandidate.timestamp
                + ", candidates=" + summarizeCandidates(candidates));
        return selectedCandidate;
    }

    private ArchiveCandidate resolveArchiveHref(String baseUrl, String href) {
        if (href == null) {
            return null;
        }
        String trimmedHref = href.trim();
        if (trimmedHref.isEmpty()) {
            return null;
        }

        String normalizedHref = stripQueryAndFragment(trimmedHref);
        ArchiveCandidate candidate = ArchiveCandidate.fromHref(normalizedHref);
        if (candidate == null) {
            return null;
        }
        try {
            URL normalizedBaseUrl = HttpClient.validateUrl(baseUrl);
            URL resolvedUrl = new URL(normalizedBaseUrl, normalizedHref);
            if (!"https".equalsIgnoreCase(resolvedUrl.getProtocol())) {
                return null;
            }
            if (!hasSameOrigin(normalizedBaseUrl, resolvedUrl)) {
                return null;
            }
            return candidate.withResolvedUrl(stripQueryAndFragment(resolvedUrl.toString()));
        } catch (IOException ignored) {
            return null;
        }
    }

    private boolean hasSameOrigin(URL baseUrl, URL candidateUrl) {
        if (baseUrl == null || candidateUrl == null) {
            return false;
        }
        return baseUrl.getProtocol().equalsIgnoreCase(candidateUrl.getProtocol())
                && baseUrl.getHost().equalsIgnoreCase(candidateUrl.getHost())
                && effectivePort(baseUrl) == effectivePort(candidateUrl);
    }

    private void validateArchiveRedirect(String archiveUrl, URL fromUrl, URL redirectUrl) throws IOException {
        URL normalizedArchiveUrl = HttpClient.validateUrl(archiveUrl);
        if (!hasSameOrigin(normalizedArchiveUrl, fromUrl) || !hasSameOrigin(normalizedArchiveUrl, redirectUrl)) {
            throw new IOException("DWD CAP archive redirects must stay on the source origin.");
        }
    }

    private int effectivePort(URL url) {
        if (url == null) {
            return -1;
        }
        return url.getPort() >= 0 ? url.getPort() : url.getDefaultPort();
    }

    private String summarizeCandidates(List<ArchiveCandidate> candidates) {
        List<String> values = new ArrayList<>();
        for (ArchiveCandidate candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            values.add(candidate.archiveName + "(" + candidate.language + "," + candidate.timestamp + ")");
        }
        return values.toString();
    }

    private String stripQueryAndFragment(String href) {
        int queryIndex = href.indexOf('?');
        int fragmentIndex = href.indexOf('#');
        int endIndex = href.length();
        if (queryIndex >= 0) {
            endIndex = Math.min(endIndex, queryIndex);
        }
        if (fragmentIndex >= 0) {
            endIndex = Math.min(endIndex, fragmentIndex);
        }
        return href.substring(0, endIndex);
    }

    private byte[] readEntry(ZipInputStream zipInputStream, ZipEntry zipEntry) throws IOException {
        String entryName = zipEntry == null ? null : zipEntry.getName();
        if (zipEntry != null && zipEntry.getSize() > MAX_ENTRY_BYTES) {
            throw new IOException("DWD CAP ZIP entry exceeds limit of "
                    + MAX_ENTRY_BYTES + " bytes: " + summarizeEntryName(entryName));
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int totalBytes = 0;
        int bytesRead;

        while ((bytesRead = zipInputStream.read(buffer)) != -1) {
            if (bytesRead <= 0) {
                continue;
            }
            if (totalBytes + bytesRead > MAX_ENTRY_BYTES) {
                throw new IOException("DWD CAP ZIP entry exceeds limit of "
                        + MAX_ENTRY_BYTES + " bytes: " + summarizeEntryName(entryName));
            }
            outputStream.write(buffer, 0, bytesRead);
            totalBytes += bytesRead;
        }
        return outputStream.toByteArray();
    }

    static String summarizeEntryName(String entryName) {
        if (entryName == null) {
            return "unknown.xml";
        }

        String trimmed = entryName.trim();
        if (trimmed.isEmpty()) {
            return "unknown.xml";
        }
        if (trimmed.length() <= MAX_DISPLAY_ENTRY_NAME_LENGTH) {
            return trimmed;
        }

        int keepEdgeLength = (MAX_DISPLAY_ENTRY_NAME_LENGTH - 3) / 2;
        int suffixStart = trimmed.length() - keepEdgeLength;
        if (suffixStart <= keepEdgeLength) {
            return trimmed.substring(0, MAX_DISPLAY_ENTRY_NAME_LENGTH - 3) + "...";
        }
        return trimmed.substring(0, keepEdgeLength)
                + "..."
                + trimmed.substring(suffixStart);
    }

    public static final class FetchResult {

        private final List<XmlEntry> xmlEntries;
        private final List<String> failures;
        private final String archiveName;
        private final String archiveLanguage;
        private final String archiveUrl;

        FetchResult(
                List<XmlEntry> xmlEntries,
                List<String> failures,
                String archiveName,
                String archiveLanguage,
                String archiveUrl) {
            this.xmlEntries = xmlEntries == null ? new ArrayList<XmlEntry>() : xmlEntries;
            this.failures = failures == null ? new ArrayList<String>() : failures;
            this.archiveName = archiveName;
            this.archiveLanguage = archiveLanguage;
            this.archiveUrl = archiveUrl;
        }

        public List<XmlEntry> getXmlEntries() {
            return new ArrayList<>(xmlEntries);
        }

        public List<String> getFailures() {
            return new ArrayList<>(failures);
        }

        public boolean hasFailures() {
            return !failures.isEmpty();
        }

        public String getArchiveName() {
            return archiveName;
        }

        public String getArchiveLanguage() {
            return archiveLanguage;
        }

        public String getArchiveUrl() {
            return archiveUrl;
        }
    }

    public static final class XmlEntry {

        private final String entryName;
        private final String xml;

        XmlEntry(String entryName, String xml) {
            this.entryName = entryName;
            this.xml = xml;
        }

        public String getEntryName() {
            return entryName;
        }

        public String getXml() {
            return xml;
        }
    }

    private static final class ArchiveCandidate {

        private final String archiveName;
        private final String timestamp;
        private final boolean latestAlias;
        private final int languageRank;
        private final String language;
        private final String resolvedUrl;

        private ArchiveCandidate(
                String archiveName,
                String timestamp,
                boolean latestAlias,
                int languageRank,
                String language,
                String resolvedUrl) {
            this.archiveName = archiveName;
            this.timestamp = timestamp;
            this.latestAlias = latestAlias;
            this.languageRank = languageRank;
            this.language = language;
            this.resolvedUrl = resolvedUrl;
        }

        private static ArchiveCandidate fromHref(String href) {
            if (href == null || href.trim().isEmpty()) {
                return null;
            }
            String normalized = href.trim();
            int lastSlash = normalized.lastIndexOf('/');
            String archiveName = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
            Matcher matcher = CAP_ARCHIVE_NAME_PATTERN.matcher(archiveName);
            if (!matcher.matches()) {
                return null;
            }
            String marker = matcher.group(2);
            String timestamp = matcher.group(3);
            String language = matcher.group(4);
            return new ArchiveCandidate(
                    archiveName,
                    timestamp == null ? "" : timestamp,
                    "LATEST".equalsIgnoreCase(marker),
                    languageRank(language),
                    language == null ? "unknown" : language,
                    null);
        }

        private ArchiveCandidate withResolvedUrl(String resolvedUrl) {
            return new ArchiveCandidate(
                    archiveName,
                    timestamp,
                    latestAlias,
                    languageRank,
                    language,
                    resolvedUrl);
        }

        private static int languageRank(String language) {
            if (language == null) {
                return 2;
            }
            if ("DE".equalsIgnoreCase(language)) {
                return 0;
            }
            if ("MUL".equalsIgnoreCase(language)) {
                return 1;
            }
            return 2;
        }
    }
}
