package com.npcwars.dependency;

import com.npcwars.NpcWarsPlugin;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;

/**
 * Downloads missing optional plugins (Citizens, EssentialsX) into the server's plugins folder.
 *
 * <p>Only the HTTP transfer and file work run off the main thread; everything that needs the Bukkit API (which plugins
 * are loaded, the Minecraft version, the result callback) happens on the main thread. Jars are never hot-loaded:
 * they are picked up by the next server start.
 */
public final class DependencyInstaller {

    public enum Status { INSTALLED, PRESENT, UNAVAILABLE, FAILED }

    public record Outcome(PluginSource source, Status status, String detail) {
    }

    private static final Set<String> ALLOWED_HOSTS = Set.of("ci.citizensnpcs.co", "api.modrinth.com", "cdn.modrinth.com");

    private final NpcWarsPlugin plugin;
    private final List<PluginSource> sources = List.of(new CitizensSource(), new EssentialsXSource());
    private final AtomicBoolean busy = new AtomicBoolean();

    public DependencyInstaller(NpcWarsPlugin plugin) {
        this.plugin = plugin;
    }

    public List<PluginSource> sources() {
        return sources;
    }

    /** @return the source with this config key, or {@code null} */
    public PluginSource source(String key) {
        for (PluginSource source : sources) {
            if (source.configKey().equalsIgnoreCase(key)) {
                return source;
            }
        }
        return null;
    }

    /** @return whether the plugin is loaded on this server right now (main thread) */
    public boolean isLoaded(PluginSource source) {
        Plugin loaded = Bukkit.getPluginManager().getPlugin(source.pluginName());
        return loaded != null;
    }

    public boolean isBusy() {
        return busy.get();
    }

    /**
     * Installs the wanted plugins that are missing. Call from the main thread; {@code onDone} runs on the main thread.
     *
     * @return {@code false} (and does nothing) if an install is already running
     */
    public boolean install(Collection<PluginSource> wanted, Consumer<List<Outcome>> onDone) {
        if (!busy.compareAndSet(false, true)) {
            return false;
        }
        Set<String> loaded = new HashSet<>();
        for (Plugin installed : Bukkit.getPluginManager().getPlugins()) {
            loaded.add(installed.getName().toLowerCase(Locale.ROOT));
        }
        String minecraft = Bukkit.getMinecraftVersion();
        Path folder = plugin.getDataFolder().getParentFile().toPath();
        List<PluginSource> list = List.copyOf(wanted);
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                List<Outcome> outcomes = run(list, loaded, minecraft, folder);
                try {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        busy.set(false);
                        onDone.accept(outcomes);
                    });
                } catch (IllegalPluginAccessException ex) {
                    busy.set(false); // the server is shutting down; nobody is left to tell
                }
            });
        } catch (RuntimeException ex) {
            busy.set(false);
            throw ex;
        }
        return true;
    }

    /** Runs on a worker thread: no Bukkit API in here. */
    private List<Outcome> run(List<PluginSource> list, Set<String> loaded, String minecraft, Path folder) {
        List<Outcome> outcomes = new ArrayList<>();
        HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10)).build();
        Set<String> onDisk = null;
        for (PluginSource source : list) {
            String name = source.pluginName().toLowerCase(Locale.ROOT);
            if (onDisk == null) {
                onDisk = Jars.installedNames(folder);
            }
            if (loaded.contains(name) || onDisk.contains(name)) {
                outcomes.add(new Outcome(source, Status.PRESENT, ""));
                continue;
            }
            try {
                Download download = source.resolve(http, minecraft);
                if (download == null) {
                    outcomes.add(new Outcome(source, Status.UNAVAILABLE, "no build found for Minecraft " + minecraft));
                    continue;
                }
                Path file = Downloader.fetch(http, download, folder, host -> Downloader.hostIn(ALLOWED_HOSTS, host), true);
                outcomes.add(new Outcome(source, Status.INSTALLED, file.getFileName().toString()));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                outcomes.add(new Outcome(source, Status.FAILED, "interrupted"));
                break;
            } catch (java.io.IOException | RuntimeException ex) {
                outcomes.add(new Outcome(source, Status.FAILED, String.valueOf(ex.getMessage())));
            }
        }
        return outcomes;
    }
}
