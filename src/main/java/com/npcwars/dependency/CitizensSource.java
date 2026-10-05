package com.npcwars.dependency;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Citizens publishes its builds on its own Jenkins server. There is no API key and no JSON API that is reachable from
 * outside, so the newest successful build's page is read and the jar link is picked out of it.
 */
public final class CitizensSource implements PluginSource {

    static final String BUILD_PAGE = "https://ci.citizensnpcs.co/job/Citizens2/lastSuccessfulBuild/";
    private static final Pattern ARTIFACT = Pattern.compile("artifact/(dist/target/(Citizens-[A-Za-z0-9._-]+\\.jar))");

    @Override
    public String configKey() {
        return "citizens";
    }

    @Override
    public String pluginName() {
        return "Citizens";
    }

    @Override
    public Download resolve(HttpClient http, String minecraftVersion) throws IOException, InterruptedException {
        String page = Downloader.fetchText(http, URI.create(BUILD_PAGE));
        return parse(page);
    }

    /** @return the download for the first Citizens jar linked on the build page, or {@code null} */
    static Download parse(String html) {
        Matcher matcher = ARTIFACT.matcher(html);
        if (!matcher.find()) {
            return null;
        }
        return new Download("Citizens", URI.create(BUILD_PAGE + "artifact/" + matcher.group(1)), matcher.group(2), null);
    }
}
