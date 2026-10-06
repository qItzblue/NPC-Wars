package com.npcwars.dependency;

import java.net.URI;
import java.net.http.HttpClient;

/**
 * Citizens, pinned to one build. The newest Citizens build only supports the newest Minecraft version, so "latest" would
 * download something that refuses to start on a 1.21.11 server. Build 4250 (2.0.43) is the one NPC-Wars is built and
 * tested against; its SHA-512 is fixed here, so a changed or tampered file is rejected. There is no API key: Citizens
 * publishes its builds on a public build server.
 */
public final class CitizensSource implements PluginSource {

    /** The Citizens build NPC-Wars supports. */
    public static final int BUILD = 4250;
    public static final String VERSION = "2.0.43";

    static final String FILE_NAME = "Citizens-" + VERSION + "-b" + BUILD + ".jar";
    static final String URL = "https://ci.citizensnpcs.co/job/Citizens2/" + BUILD + "/artifact/dist/target/" + FILE_NAME;
    static final String SHA512 = "2418329d22d0774c8bcc04959be7117968cd5a919260b3243ad9a723002c8df6"
            + "a65de61fd088e2aab3418027e9ba66879bc630701f5c849beac8537bc496ba0e";

    @Override
    public String configKey() {
        return "citizens";
    }

    @Override
    public String pluginName() {
        return "Citizens";
    }

    @Override
    public Download resolve(HttpClient http, String minecraftVersion) {
        return new Download("Citizens", URI.create(URL), FILE_NAME, SHA512);
    }
}
