package com.npcwars.dependency;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Downloads a jar and refuses anything suspicious: wrong host (also after redirects), oversized body, checksum mismatch,
 * not a jar, or a jar whose plugin.yml names a different plugin. The file only appears under its final name once all of
 * that has passed, and an existing file is never overwritten.
 */
final class Downloader {

    static final long MAX_BYTES = 100L * 1024 * 1024;
    private static final long MAX_TEXT_BYTES = 8L * 1024 * 1024;
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+-]{0,100}\\.jar");
    private static final String USER_AGENT = "NPC-Wars-DependencyInstaller (Paper plugin)";

    private Downloader() {
    }

    static boolean safeFileName(String name) {
        return name != null && SAFE_NAME.matcher(name).matches() && !name.contains("..");
    }

    static String fetchText(HttpClient http, URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).header("User-Agent", USER_AGENT).GET().build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = response.body()) {
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " from " + uri.getHost());
            }
            byte[] bytes = in.readNBytes((int) MAX_TEXT_BYTES + 1);
            if (bytes.length > MAX_TEXT_BYTES) {
                throw new IOException("Answer from " + uri.getHost() + " is too large");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    /**
     * @param hostAllowed  decides whether the final host (after redirects) may serve the file
     * @param requireHttps always {@code true} in production; tests turn it off to talk to a local server
     * @return the installed file
     * @throws IOException with a human readable reason when anything does not check out
     */
    static Path fetch(HttpClient http, Download download, Path folder, Predicate<String> hostAllowed,
            boolean requireHttps)
            throws IOException, InterruptedException {
        if (!safeFileName(download.fileName())) {
            throw new IOException("Refusing unsafe file name '" + download.fileName() + "'");
        }
        if (!hostAllowed.test(download.uri().getHost())) {
            throw new IOException("Host " + download.uri().getHost() + " is not on the allow-list");
        }
        Path target = folder.resolve(download.fileName());
        if (Files.exists(target)) {
            throw new IOException(download.fileName() + " already exists in the plugins folder");
        }
        Path part = folder.resolve(download.fileName() + ".part");
        try {
            HttpRequest request = HttpRequest.newBuilder(download.uri()).timeout(Duration.ofMinutes(3))
                    .header("User-Agent", USER_AGENT).GET().build();
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = response.body()) {
                if (response.statusCode() != 200) {
                    throw new IOException("HTTP " + response.statusCode() + " from " + response.uri().getHost());
                }
                if (requireHttps && !"https".equalsIgnoreCase(response.uri().getScheme())) {
                    throw new IOException("Download was redirected to a non-https address");
                }
                if (!hostAllowed.test(response.uri().getHost())) {
                    throw new IOException("Download was redirected to " + response.uri().getHost() + ", which is not on the allow-list");
                }
                MessageDigest digest = MessageDigest.getInstance("SHA-512");
                long total = 0;
                try (OutputStream out = Files.newOutputStream(part)) {
                    byte[] buffer = new byte[16 * 1024];
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        total += read;
                        if (total > MAX_BYTES) {
                            throw new IOException("Download is larger than " + (MAX_BYTES / 1024 / 1024) + " MB");
                        }
                        digest.update(buffer, 0, read);
                        out.write(buffer, 0, read);
                    }
                }
                if (download.sha512() != null && !download.sha512().equalsIgnoreCase(HexFormat.of().formatHex(digest.digest()))) {
                    throw new IOException("Checksum mismatch, the file was not installed");
                }
            }
            String declared = Jars.pluginName(part);
            if (declared == null || !declared.equalsIgnoreCase(download.pluginName())) {
                throw new IOException("The file is not the " + download.pluginName() + " plugin (plugin.yml says "
                        + (declared == null ? "nothing" : "'" + declared + "'") + ")");
            }
            Files.move(part, target, StandardCopyOption.ATOMIC_MOVE);
            return target;
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException("SHA-512 is not available", ex);
        } finally {
            Files.deleteIfExists(part);
        }
    }

    static boolean hostIn(java.util.Set<String> hosts, String host) {
        return host != null && hosts.contains(host.toLowerCase(Locale.ROOT));
    }
}
