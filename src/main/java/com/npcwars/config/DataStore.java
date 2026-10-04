package com.npcwars.config;

import com.npcwars.NpcWarsPlugin;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

/**
 * Reads and writes data.yml. Saves are debounced: callers just {@link #requestSave()}, the YAML text is built on the
 * main thread (Bukkit objects are never touched off-thread) and only the file write runs asynchronously. On shutdown
 * {@link #saveNow()} writes synchronously.
 */
public final class DataStore {

    private static final long SAVE_DELAY_TICKS = 40L;

    private final NpcWarsPlugin plugin;
    private final File file;
    private final Object writeLock = new Object();
    private Supplier<YamlConfiguration> snapshotter = YamlConfiguration::new;
    private BukkitTask pendingSave;
    private long versionCounter;
    private long lastWrittenVersion;

    public DataStore(NpcWarsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    /** Sets the callback that builds the full file contents from live state (called on the main thread). */
    public void setSnapshotter(Supplier<YamlConfiguration> snapshotter) {
        this.snapshotter = snapshotter;
    }

    /**
     * Loads data.yml. A missing file yields an empty configuration; an unreadable one is moved aside to
     * {@code data.yml.corrupt-<time>} (so it is never overwritten) and an empty configuration is returned.
     */
    public YamlConfiguration load() {
        YamlConfiguration yaml = new YamlConfiguration();
        if (!file.isFile()) {
            return yaml;
        }
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            File backup = new File(file.getParentFile(), "data.yml.corrupt-" + System.currentTimeMillis());
            plugin.getLogger().severe("data.yml could not be read (" + ex.getMessage() + "). Moving it to "
                    + backup.getName() + " and starting empty.");
            if (!file.renameTo(backup)) {
                plugin.getLogger().severe("Could not move the corrupt data.yml aside; it will be overwritten on save.");
            }
            return new YamlConfiguration();
        }
        return yaml;
    }

    /** Schedules a save a couple of seconds from now; repeated calls within that window collapse into one. */
    public void requestSave() {
        if (!plugin.isEnabled() || pendingSave != null) {
            return;
        }
        pendingSave = Bukkit.getScheduler().runTaskLater(plugin, this::flushAsync, SAVE_DELAY_TICKS);
    }

    /** Builds the snapshot now (main thread) and writes the file on an async task. */
    public void flushAsync() {
        pendingSave = null;
        if (!plugin.isEnabled()) {
            return;
        }
        String yaml = snapshotter.get().saveToString();
        long version = ++versionCounter;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(yaml, version));
    }

    /** Cancels any pending save and writes the file immediately on the calling thread. */
    public void saveNow() {
        if (pendingSave != null) {
            pendingSave.cancel();
            pendingSave = null;
        }
        String yaml = snapshotter.get().saveToString();
        write(yaml, ++versionCounter);
    }

    private void write(String yaml, long version) {
        synchronized (writeLock) {
            if (version < lastWrittenVersion) {
                return;
            }
            lastWrittenVersion = version;
            try {
                Files.createDirectories(file.toPath().getParent());
                Path temp = file.toPath().resolveSibling("data.yml.tmp");
                Files.writeString(temp, yaml, StandardCharsets.UTF_8);
                try {
                    Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ex) {
                plugin.getLogger().severe("Could not save data.yml: " + ex.getMessage());
            }
        }
    }
}
