package com.npcwars.action;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** All registered {@link NpcAction}s, looked up by name or alias (case-insensitive). */
public final class ActionRegistry {

    private final Map<String, NpcAction> actions = new LinkedHashMap<>();
    private final Map<String, NpcAction> lookup = new HashMap<>();

    /**
     * Adds an action.
     *
     * @throws IllegalArgumentException if its name or an alias is already taken
     */
    public void register(NpcAction action) {
        List<String> keys = new ArrayList<>();
        keys.add(action.name().toLowerCase(Locale.ROOT));
        for (String alias : action.aliases()) {
            keys.add(alias.toLowerCase(Locale.ROOT));
        }
        for (String key : keys) {
            if (lookup.containsKey(key)) {
                throw new IllegalArgumentException("An action named '" + key + "' is already registered");
            }
        }
        actions.put(keys.get(0), action);
        keys.forEach(key -> lookup.put(key, action));
    }

    /** @return {@code true} if an action with this canonical name existed and was removed */
    public boolean unregister(String name) {
        NpcAction removed = actions.remove(name.toLowerCase(Locale.ROOT));
        if (removed == null) {
            return false;
        }
        lookup.values().removeIf(action -> action == removed);
        return true;
    }

    /** @return the action registered under this name or alias, or {@code null} */
    public NpcAction find(String nameOrAlias) {
        return lookup.get(nameOrAlias.toLowerCase(Locale.ROOT));
    }

    /** @return canonical names plus aliases, for tab completion */
    public List<String> allNames() {
        List<String> names = new ArrayList<>(lookup.keySet());
        Collections.sort(names);
        return names;
    }

    /** @return canonical names in registration order */
    public List<String> names() {
        return new ArrayList<>(actions.keySet());
    }

    public Collection<NpcAction> all() {
        return Collections.unmodifiableCollection(actions.values());
    }
}
