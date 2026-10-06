package com.npcwars.dependency;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.yaml.snakeyaml.Yaml;

/** Reads plugin names out of jar files. Pure file I/O, safe off the main thread. */
final class Jars {

    private Jars() {
    }

    /** @return the plugin name declared in the jar's plugin.yml / paper-plugin.yml, or {@code null} if there is none */
    static String pluginName(Path jar) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (String descriptor : new String[] {"plugin.yml", "paper-plugin.yml"}) {
                ZipEntry entry = zip.getEntry(descriptor);
                if (entry == null || entry.getSize() > 1_000_000) {
                    continue;
                }
                try (InputStream in = zip.getInputStream(entry);
                     Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    Object loaded = new Yaml().load(reader);
                    if (loaded instanceof Map<?, ?> map && map.get("name") instanceof String name) {
                        return name;
                    }
                }
            }
        } catch (IOException | RuntimeException ex) {
            // not a readable jar: treated as "no plugin"
        }
        return null;
    }

    /** @return the lower-case plugin names of every jar in the folder */
    static Set<String> installedNames(Path pluginsFolder) {
        Set<String> names = new HashSet<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsFolder, "*.jar")) {
            for (Path jar : stream) {
                String name = pluginName(jar);
                if (name != null) {
                    names.add(name.toLowerCase(Locale.ROOT));
                }
            }
        } catch (IOException ex) {
            // an unreadable folder just means nothing is detected
        }
        return names;
    }
}
