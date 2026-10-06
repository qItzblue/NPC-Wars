package com.npcwars.dependency;

import java.io.IOException;
import java.net.http.HttpClient;

/** Where one optional plugin can be downloaded from. Implementations only talk HTTP and must not touch Bukkit. */
public interface PluginSource {

    /** Key under {@code auto-download.plugins} in config.yml and the argument of {@code /npcwars deps install}. */
    String configKey();

    /** The plugin's name as declared in its plugin.yml. */
    String pluginName();

    /**
     * Finds the newest suitable build.
     *
     * @return the download, or {@code null} if no build exists for this Minecraft version
     */
    Download resolve(HttpClient http, String minecraftVersion) throws IOException, InterruptedException;
}
