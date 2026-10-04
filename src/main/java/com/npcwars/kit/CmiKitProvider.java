package com.npcwars.kit;

import com.npcwars.NpcWarsPlugin;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Hook for CMI kits. CMI is a paid plugin without a public API artifact, so this provider talks to it purely through
 * reflection ({@code CMI.getInstance().getKitsManager().getKitMap()}) and tries a few known accessor names for the
 * kit's items. If the installed CMI version exposes none of them the kit is listed but yields no items, and a single
 * warning is written to the console. Nothing here is loaded unless CMI is enabled.
 */
public final class CmiKitProvider implements KitProvider {

    public static final String ID = "cmi";
    private static final String[] ITEM_ACCESSORS = {"getItems", "getItemList", "getContents", "getItemsList"};

    private final NpcWarsPlugin plugin;
    private boolean warned;

    public CmiKitProvider(NpcWarsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "CMI";
    }

    @Override
    public boolean isAvailable() {
        Plugin cmi = Bukkit.getPluginManager().getPlugin("CMI");
        return cmi != null && cmi.isEnabled();
    }

    @Override
    public List<Kit> kits() throws Exception {
        List<Kit> out = new ArrayList<>();
        for (Map.Entry<?, ?> entry : kitMap().entrySet()) {
            String name = String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT);
            Material icon = Material.CHEST;
            List<ItemStack> items = readItems(entry.getValue());
            if (!items.isEmpty()) {
                icon = items.get(0).getType();
            }
            out.add(new Kit(ID, name, icon));
        }
        return out;
    }

    @Override
    public List<ItemStack> items(Kit kit, Player context) throws Exception {
        for (Map.Entry<?, ?> entry : kitMap().entrySet()) {
            if (String.valueOf(entry.getKey()).equalsIgnoreCase(kit.id())) {
                return readItems(entry.getValue());
            }
        }
        return List.of();
    }

    private Map<?, ?> kitMap() throws Exception {
        Class<?> cmi = Class.forName("com.Zrips.CMI.CMI", true, Bukkit.getPluginManager().getPlugin("CMI").getClass().getClassLoader());
        Object instance = cmi.getMethod("getInstance").invoke(null);
        Object manager = instance.getClass().getMethod("getKitsManager").invoke(instance);
        Object map = manager.getClass().getMethod("getKitMap").invoke(manager);
        if (map instanceof Map<?, ?> result) {
            return result;
        }
        throw new IllegalStateException("CMI getKitMap() did not return a map");
    }

    private List<ItemStack> readItems(Object kit) {
        List<ItemStack> items = new ArrayList<>();
        if (kit == null) {
            return items;
        }
        for (String accessor : ITEM_ACCESSORS) {
            try {
                Method method = kit.getClass().getMethod(accessor);
                collect(method.invoke(kit), items);
                if (!items.isEmpty()) {
                    return items;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // this accessor does not exist in the installed CMI version; try the next one
            }
        }
        if (!warned) {
            warned = true;
            plugin.getLogger().warning("CMI is installed, but its kit items could not be read through the known API "
                    + "methods. CMI kits will be listed without items. Please report your CMI version.");
        }
        return items;
    }

    private static void collect(Object value, List<ItemStack> out) {
        if (value instanceof ItemStack stack) {
            if (!stack.getType().isAir()) {
                out.add(stack.clone());
            }
        } else if (value instanceof Map<?, ?> map) {
            map.values().forEach(v -> collect(v, out));
        } else if (value instanceof Collection<?> collection) {
            collection.forEach(v -> collect(v, out));
        } else if (value instanceof Object[] array) {
            for (Object element : array) {
                collect(element, out);
            }
        }
    }
}
