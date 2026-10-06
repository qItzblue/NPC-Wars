package com.npcwars.dependency;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * EssentialsX (the kit source) from its Modrinth project. Modrinth publishes a SHA-512 for every file, which is
 * verified after the download. Only release builds that list the running Minecraft version are considered.
 */
public final class EssentialsXSource implements PluginSource {

    private static final String API = "https://api.modrinth.com/v2/project/essentialsx/version";

    @Override
    public String configKey() {
        return "essentialsx";
    }

    @Override
    public String pluginName() {
        return "Essentials";
    }

    @Override
    public Download resolve(HttpClient http, String minecraftVersion) throws IOException, InterruptedException {
        String loaders = URLEncoder.encode("[\"paper\"]", StandardCharsets.UTF_8);
        String versions = URLEncoder.encode("[\"" + minecraftVersion.replace("\"", "") + "\"]", StandardCharsets.UTF_8);
        String json = Downloader.fetchText(http, URI.create(API + "?loaders=" + loaders + "&game_versions=" + versions));
        try {
            return parse(json, minecraftVersion);
        } catch (JsonParseException | IllegalStateException | ClassCastException ex) {
            throw new IOException("Unexpected answer from Modrinth: " + ex.getMessage(), ex);
        }
    }

    /** @return the newest release for the Minecraft version (Modrinth lists newest first), or {@code null} */
    static Download parse(String json, String minecraftVersion) {
        JsonArray versions = JsonParser.parseString(json).getAsJsonArray();
        for (JsonElement element : versions) {
            JsonObject version = element.getAsJsonObject();
            if (!"release".equals(string(version, "version_type")) || !contains(version, "game_versions", minecraftVersion)) {
                continue;
            }
            JsonArray files = version.getAsJsonArray("files");
            JsonObject chosen = null;
            for (JsonElement fileElement : files) {
                JsonObject file = fileElement.getAsJsonObject();
                String name = string(file, "filename");
                if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                    continue;
                }
                if (chosen == null || (file.has("primary") && file.get("primary").getAsBoolean())) {
                    chosen = file;
                }
            }
            if (chosen == null) {
                continue;
            }
            JsonObject hashes = chosen.getAsJsonObject("hashes");
            String sha512 = hashes == null ? null : string(hashes, "sha512");
            return new Download("Essentials", URI.create(string(chosen, "url")), string(chosen, "filename"),
                    sha512 == null ? null : sha512.toLowerCase(Locale.ROOT));
        }
        return null;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static boolean contains(JsonObject object, String key, String wanted) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonArray()) {
            return false;
        }
        for (JsonElement entry : value.getAsJsonArray()) {
            if (wanted.equals(entry.getAsString())) {
                return true;
            }
        }
        return false;
    }
}
