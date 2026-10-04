package com.npcwars.command;

import com.npcwars.config.Messages;
import com.npcwars.npc.Npc;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Turns the words staff type into NPCs and team members.
 * <ul>
 *   <li>NPC words: {@code 5}, {@code #5}, {@code npc:5}, {@code 1,2,7}, {@code 3-9}, {@code all}, {@code selected},
 *       {@code look}, {@code team:<n>}</li>
 *   <li>Member words (team commands) additionally accept a player name.</li>
 * </ul>
 */
public final class Targets {

    /** A team member: either an NPC or a player. */
    public record Member(Npc npc, UUID playerId, String playerName) {
        public boolean isNpc() {
            return npc != null;
        }
    }

    private Targets() {
    }

    /** Resolves exactly one NPC. */
    public static Npc single(CommandContext ctx, String token) throws CommandException {
        List<Npc> found = many(ctx, token);
        if (found.size() != 1) {
            throw new CommandException(found.isEmpty() ? "npc.not-found" : "npc.need-single", Messages.var("input", token));
        }
        return found.get(0);
    }

    /** Resolves a word that may name several NPCs; throws if it names none. */
    public static List<Npc> many(CommandContext ctx, String token) throws CommandException {
        Map<Integer, Npc> found = new LinkedHashMap<>();
        resolveInto(ctx, token, found);
        if (found.isEmpty()) {
            throw new CommandException("npc.not-found", Messages.var("input", token));
        }
        return new ArrayList<>(found.values());
    }

    /** Resolves several words and merges the result (duplicates removed). */
    public static List<Npc> manyOf(CommandContext ctx, List<String> tokens) throws CommandException {
        Map<Integer, Npc> found = new LinkedHashMap<>();
        for (String token : tokens) {
            int before = found.size();
            resolveInto(ctx, token, found);
            if (found.size() == before && isUnresolvable(ctx, token)) {
                throw new CommandException("npc.not-found", Messages.var("input", token));
            }
        }
        if (found.isEmpty()) {
            throw new CommandException("npc.none-matched");
        }
        return new ArrayList<>(found.values());
    }

    /** Resolves a team member word: any NPC word, or a player name (online, or known to the server). */
    public static List<Member> members(CommandContext ctx, String token) throws CommandException {
        String lower = token.toLowerCase(Locale.ROOT);
        boolean npcWord = lower.equals("all") || lower.equals("selected") || lower.equals("look")
                || lower.startsWith("team:") || lower.startsWith("npc:") || lower.startsWith("#")
                || lower.contains(",") || (lower.contains("-") && isRange(lower));
        List<Member> out = new ArrayList<>();
        if (!npcWord && isNumber(token) && ctx.plugin().npcs().get(Integer.parseInt(token)) != null) {
            npcWord = true;
        }
        if (npcWord) {
            for (Npc npc : many(ctx, token)) {
                out.add(new Member(npc, null, null));
            }
            return out;
        }
        Player online = Bukkit.getPlayerExact(token);
        if (online != null) {
            out.add(new Member(null, online.getUniqueId(), online.getName()));
            return out;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(token);
        if (offline != null) {
            out.add(new Member(null, offline.getUniqueId(), offline.getName() == null ? token : offline.getName()));
            return out;
        }
        throw new CommandException("team.unknown-member", Messages.var("input", token));
    }

    /** Suggestions for NPC words. */
    public static List<String> npcSuggestions(CommandContext ctx) {
        List<String> out = new ArrayList<>(ctx.plugin().npcs().idStrings());
        out.add("all");
        out.add("selected");
        out.add("look");
        for (com.npcwars.team.Team team : ctx.plugin().teams().teams()) {
            out.add("team:" + team.id());
        }
        return out;
    }

    private static boolean isUnresolvable(CommandContext ctx, String token) {
        // A word like "team:9" for an empty team resolves to nothing without being an error on its own.
        return !token.toLowerCase(Locale.ROOT).startsWith("team:") && !token.equalsIgnoreCase("selected");
    }

    private static void resolveInto(CommandContext ctx, String token, Map<Integer, Npc> out) throws CommandException {
        String lower = token.toLowerCase(Locale.ROOT);
        switch (lower) {
            case "all" -> {
                for (Npc npc : ctx.plugin().npcs().all()) {
                    out.put(npc.id(), npc);
                }
                return;
            }
            case "selected" -> {
                Set<Integer> ids = ctx.sender() instanceof Player player
                        ? ctx.plugin().npcs().selection().get(player.getUniqueId()) : Set.of();
                for (int id : ids) {
                    Npc npc = ctx.plugin().npcs().get(id);
                    if (npc != null) {
                        out.put(id, npc);
                    }
                }
                return;
            }
            case "look" -> {
                Player player = ctx.player();
                Entity target = player.getTargetEntity(16);
                Npc npc = target == null ? null : ctx.plugin().npcs().byEntity(target);
                if (npc != null) {
                    out.put(npc.id(), npc);
                }
                return;
            }
            default -> { }
        }
        if (lower.startsWith("team:")) {
            int team = ctx.teamNumber(lower.substring(5));
            for (int id : ctx.plugin().teams().npcsOf(team)) {
                Npc npc = ctx.plugin().npcs().get(id);
                if (npc != null) {
                    out.put(id, npc);
                }
            }
            return;
        }
        String body = lower.startsWith("npc:") ? lower.substring(4) : lower.startsWith("#") ? lower.substring(1) : lower;
        Set<Integer> ids = new LinkedHashSet<>();
        for (String part : body.split(",")) {
            if (isNumber(part)) {
                ids.add(Integer.parseInt(part));
            } else if (isRange(part)) {
                String[] bounds = part.split("-");
                int from = Integer.parseInt(bounds[0]);
                int to = Integer.parseInt(bounds[1]);
                if (to - from > 5000) {
                    throw new CommandException("npc.range-too-large");
                }
                for (int id = from; id <= to; id++) {
                    ids.add(id);
                }
            } else {
                throw new CommandException("npc.invalid-target", Messages.var("input", token));
            }
        }
        for (int id : ids) {
            Npc npc = ctx.plugin().npcs().get(id);
            if (npc != null) {
                out.put(id, npc);
            }
        }
    }

    private static boolean isNumber(String text) {
        return !text.isEmpty() && text.length() <= 9 && text.chars().allMatch(Character::isDigit);
    }

    private static boolean isRange(String text) {
        String[] bounds = text.split("-");
        return bounds.length == 2 && isNumber(bounds[0]) && isNumber(bounds[1]);
    }
}
