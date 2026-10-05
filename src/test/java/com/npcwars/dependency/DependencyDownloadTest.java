package com.npcwars.dependency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DependencyDownloadTest {

    @TempDir
    Path folder;

    private HttpServer server;
    private byte[] served;
    private int status = 200;
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(status, served.length);
            exchange.getResponseBody().write(served);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private URI url(String file) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/" + file);
    }

    private static byte[] jar(String pluginName) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("plugin.yml"));
            zip.write(("name: " + pluginName + "\nmain: x.Y\nversion: 1\n").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static String sha512(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest(data));
    }

    private Path fetch(Download download) throws Exception {
        return Downloader.fetch(http, download, folder, host -> true, false);
    }

    @Test
    void installsAVerifiedJar() throws Exception {
        served = jar("Essentials");
        Path installed = fetch(new Download("Essentials", url("e.jar"), "EssentialsX-2.0.jar", sha512(served)));
        assertEquals(folder.resolve("EssentialsX-2.0.jar"), installed);
        assertTrue(Files.exists(installed));
        assertFalse(Files.exists(folder.resolve("EssentialsX-2.0.jar.part")));
        assertEquals("Essentials", Jars.pluginName(installed));
        assertEquals(Set.of("essentials"), Jars.installedNames(folder));
    }

    @Test
    void checksumMismatchInstallsNothing() throws Exception {
        served = jar("Essentials");
        Download wrong = new Download("Essentials", url("e.jar"), "E.jar", "00".repeat(64));
        IOException error = assertThrows(IOException.class, () -> fetch(wrong));
        assertTrue(error.getMessage().contains("Checksum"));
        try (var files = Files.list(folder)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void wrongPluginInsideTheJarIsRejected() throws Exception {
        served = jar("SomethingElse");
        assertThrows(IOException.class, () -> fetch(new Download("Citizens", url("c.jar"), "Citizens-1.jar", null)));
        assertFalse(Files.exists(folder.resolve("Citizens-1.jar")));
    }

    @Test
    void nonJarContentIsRejected() {
        served = "<html>blocked</html>".getBytes(StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> fetch(new Download("Citizens", url("c.jar"), "Citizens-1.jar", null)));
    }

    @Test
    void httpErrorsAndExistingFilesAreNotInstalledOver() throws Exception {
        served = jar("Citizens");
        status = 404;
        assertThrows(IOException.class, () -> fetch(new Download("Citizens", url("c.jar"), "Citizens-1.jar", null)));
        status = 200;
        Files.writeString(folder.resolve("Citizens-1.jar"), "keep me");
        assertThrows(IOException.class, () -> fetch(new Download("Citizens", url("c.jar"), "Citizens-1.jar", null)));
        assertEquals("keep me", Files.readString(folder.resolve("Citizens-1.jar")));
    }

    @Test
    void hostsAndHttpsAreEnforced() throws Exception {
        served = jar("Citizens");
        Download download = new Download("Citizens", url("c.jar"), "Citizens-1.jar", null);
        assertThrows(IOException.class, () -> Downloader.fetch(http, download, folder, host -> false, false));
        // 127.0.0.1 is plain http: refused when https is required
        assertThrows(IOException.class, () -> Downloader.fetch(http, download, folder, host -> true, true));
    }

    @Test
    void fileNamesCannotEscapeTheFolder() {
        assertTrue(Downloader.safeFileName("Citizens-2.0.44-b4258.jar"));
        assertTrue(Downloader.safeFileName("EssentialsX-2.22.0.jar"));
        assertFalse(Downloader.safeFileName("../evil.jar"));
        assertFalse(Downloader.safeFileName("a/b.jar"));
        assertFalse(Downloader.safeFileName("evil.exe"));
        assertFalse(Downloader.safeFileName(null));
    }

    @Test
    void citizensBuildPageIsParsed() {
        String html = "<a href=\"artifact/dist/target/Citizens-2.0.44-b4258.jar\">x</a> "
                + "<a href=\"artifact/dist/target/Citizens-2.0.44-b4258.jar\">y</a>";
        Download download = CitizensSource.parse(html);
        assertNotNull(download);
        assertEquals("Citizens-2.0.44-b4258.jar", download.fileName());
        assertEquals("https://ci.citizensnpcs.co/job/Citizens2/lastSuccessfulBuild/artifact/dist/target/Citizens-2.0.44-b4258.jar",
                download.uri().toString());
        assertNull(CitizensSource.parse("<html>no build</html>"));
    }

    @Test
    void essentialsVersionListPicksNewestMatchingRelease() {
        String json = """
                [{"version_type":"beta","game_versions":["1.21.11"],"files":[{"filename":"beta.jar","url":"https://cdn.modrinth.com/b.jar","primary":true,"hashes":{"sha512":"AA"}}]},
                 {"version_type":"release","game_versions":["1.20.6"],"files":[{"filename":"old.jar","url":"https://cdn.modrinth.com/o.jar","primary":true,"hashes":{"sha512":"BB"}}]},
                 {"version_type":"release","game_versions":["1.21.11","26.1.2"],"files":[
                    {"filename":"EssentialsX-2.22.0-sources.zip","url":"https://cdn.modrinth.com/s.zip","primary":false,"hashes":{"sha512":"CC"}},
                    {"filename":"EssentialsX-2.22.0.jar","url":"https://cdn.modrinth.com/e.jar","primary":true,"hashes":{"sha512":"DDEE"}}]}]""";
        Download download = EssentialsXSource.parse(json, "1.21.11");
        assertNotNull(download);
        assertEquals("EssentialsX-2.22.0.jar", download.fileName());
        assertEquals("ddee", download.sha512());
        assertEquals("cdn.modrinth.com", download.uri().getHost());
        assertNull(EssentialsXSource.parse(json, "1.19"));
        assertNull(EssentialsXSource.parse("[]", "1.21.11"));
    }
}
