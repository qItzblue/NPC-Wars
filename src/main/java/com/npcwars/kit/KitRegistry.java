package com.npcwars.kit;

import com.npcwars.NpcWarsPlugin;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Combines every {@link KitProvider}. Providers are detected at runtime: unavailable ones are skipped silently, and a
 * provider that throws is isolated (one console warning per provider) so the menu always opens with whatever works.
 */
public final class KitRegistry {

    private final NpcWarsPlugin plugin;
    private final List<KitProvider> providers = new ArrayList<>();
    private final Set<String> reportedFailures = new HashSet<>();
    private final BuiltInKitProvider builtIn;

    public KitRegistry(NpcWarsPlugin plugin) {
        this.plugin = plugin;
        this.builtIn = new BuiltInKitProvider(plugin);
        register(builtIn);
        register(new EssentialsKitProvider(plugin));
        register(new CmiKitProvider(plugin));
    }

    /** Adds a provider (for other plugins or future hooks). Replaces an existing one with the same id. */
    public void register(KitProvider provider) {
        providers.removeIf(existing -> existing.id().equals(provider.id()));
        providers.add(provider);
    }

    public BuiltInKitProvider builtIn() {
        return builtIn;
    }

    public List<KitProvider> providers() {
        return List.copyOf(providers);
    }

    /** Re-reads the built-in kit file; other providers read live data every time. */
    public void reload() {
        reportedFailures.clear();
        builtIn.reload();
    }

    /** @return every kit from every available provider, ordered by provider then name */
    public List<Kit> allKits() {
        List<Kit> all = new ArrayList<>();
        for (KitProvider provider : providers) {
            if (!safeAvailable(provider)) {
                continue;
            }
            try {
                all.addAll(provider.kits());
            } catch (Exception | LinkageError ex) {
                reportFailure(provider, ex);
            }
        }
        all.sort(Comparator.comparing(Kit::providerId).thenComparing(Kit::id));
        return all;
    }

    /** @return the provider with this id, or {@code null} */
    public KitProvider provider(String id) {
        for (KitProvider provider : providers) {
            if (provider.id().equals(id)) {
                return provider;
            }
        }
        return null;
    }

    /** @return the display name of the kit's provider (falls back to the raw id) */
    public String providerName(Kit kit) {
        KitProvider provider = provider(kit.providerId());
        return provider == null ? kit.providerId() : provider.displayName();
    }

    /** @return the kit's items; empty if the provider is gone or failed */
    public List<ItemStack> itemsOf(Kit kit, Player context) {
        KitProvider provider = provider(kit.providerId());
        if (provider == null || !safeAvailable(provider)) {
            return List.of();
        }
        try {
            return provider.items(kit, context);
        } catch (Exception | LinkageError ex) {
            reportFailure(provider, ex);
            return List.of();
        }
    }

    private boolean safeAvailable(KitProvider provider) {
        try {
            return provider.isAvailable();
        } catch (Exception | LinkageError ex) {
            reportFailure(provider, ex);
            return false;
        }
    }

    private void reportFailure(KitProvider provider, Throwable error) {
        if (reportedFailures.add(provider.id())) {
            plugin.getLogger().warning("Kit provider '" + provider.id() + "' failed and is being skipped: " + error);
        }
    }
}
