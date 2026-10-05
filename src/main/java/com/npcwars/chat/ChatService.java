package com.npcwars.chat;

import com.npcwars.NpcWarsPlugin;
import com.npcwars.config.Settings;
import com.npcwars.npc.Behavior;
import com.npcwars.npc.Npc;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.IllegalPluginAccessException;

/**
 * Lets NPCs in life mode answer player chat, with replies written by Claude. Off by default ({@code ai.enabled}).
 *
 * <p>The chat event itself is asynchronous, so the only thing done there is copying the message; everything that needs
 * the Bukkit API runs on the main thread. The model call blocks, so it runs on an async task and only carries plain
 * strings in and out. Concurrency, per-NPC and per-player cooldowns keep the cost and the chat spam bounded.
 */
public final class ChatService implements Listener {

    private static final long PLAYER_MEMORY_LIMIT = 512;

    private final NpcWarsPlugin plugin;
    private final ChatMemory memory;
    private final Map<Integer, Long> lastReply = new HashMap<>();
    private final Map<UUID, Long> lastPlayerMessage = new HashMap<>();
    private ChatBrain brain;
    private int inFlight;
    private long replies;
    private long failures;
    private long lastErrorLog = Long.MIN_VALUE / 2;
    private String lastError = "";

    public ChatService(NpcWarsPlugin plugin) {
        this.plugin = plugin;
        this.memory = new ChatMemory(plugin.settings().aiMemoryTurns);
    }

    // ---------------------------------------------------------------- state

    public boolean isEnabled() {
        return plugin.settings().aiEnabled;
    }

    /** @return {@code true} if a key is configured (config or the ANTHROPIC_API_KEY environment variable) */
    public boolean hasKey() {
        return !apiKey().isBlank();
    }

    public int inFlight() {
        return inFlight;
    }

    public long replies() {
        return replies;
    }

    public long failures() {
        return failures;
    }

    public String lastError() {
        return lastError;
    }

    /** Applies new settings: the connection is rebuilt on the next use. */
    public void reload() {
        closeBrain();
        memory.setMaxTurns(plugin.settings().aiMemoryTurns);
    }

    public void shutdown() {
        closeBrain();
        memory.clear();
        lastReply.clear();
        lastPlayerMessage.clear();
    }

    public void forget(Npc npc) {
        memory.forget(npc.id());
        lastReply.remove(npc.id());
    }

    private void closeBrain() {
        if (brain != null) {
            try {
                brain.close();
            } catch (RuntimeException ex) {
                plugin.getLogger().fine("Closing the AI client failed: " + ex.getMessage());
            }
            brain = null;
        }
    }

    private String apiKey() {
        String configured = plugin.settings().aiApiKey;
        if (configured != null && !configured.isBlank()) {
            return configured.strip();
        }
        String env = System.getenv("ANTHROPIC_API_KEY");
        return env == null ? "" : env.strip();
    }

    private ChatBrain brain() {
        if (brain == null) {
            Settings s = plugin.settings();
            brain = new AnthropicChatBrain(apiKey(), s.aiBaseUrl, s.aiModel, s.aiEffort, s.aiMaxTokens,
                    Duration.ofSeconds(s.aiTimeoutSeconds));
        }
        return brain;
    }

    // ---------------------------------------------------------------- player chat

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!isEnabled()) {
            return;
        }
        // Asynchronous event: copy the data and continue on the main thread.
        UUID playerId = event.getPlayer().getUniqueId();
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    handle(player, text);
                }
            });
        } catch (IllegalPluginAccessException ex) {
            // shutting down
        }
    }

    private void handle(Player player, String text) {
        Settings s = plugin.settings();
        if (!isEnabled() || !hasKey() || text.isBlank()) {
            return;
        }
        long now = plugin.currentTick();
        Long last = lastPlayerMessage.get(player.getUniqueId());
        if (last != null && now - last < s.aiPlayerCooldownTicks) {
            return;
        }
        List<ChatRouting.Candidate> candidates = new ArrayList<>();
        Map<Integer, Npc> byId = new HashMap<>();
        for (Npc npc : plugin.npcs().all()) {
            if (npc.behavior() != Behavior.LIFE || !npc.isLive() || plugin.isNpcBusy(npc)
                    || npc.entity().getWorld() != player.getWorld()) {
                continue;
            }
            double distance = npc.entity().getLocation().distance(player.getLocation());
            long since = now - lastReply.getOrDefault(npc.id(), Long.MIN_VALUE / 2);
            candidates.add(new ChatRouting.Candidate(npc.id(), distance, ChatText.mentions(npc.label(), text),
                    since < s.aiNpcCooldownTicks));
            byId.put(npc.id(), npc);
        }
        int chosen = ChatRouting.pick(candidates, s.aiHearRadius, s.aiMentionRadius);
        if (chosen < 0) {
            return;
        }
        boolean named = candidates.stream().anyMatch(c -> c.id() == chosen && c.mentioned());
        if (!named && ThreadLocalRandom.current().nextDouble() >= s.aiReplyChance) {
            return;
        }
        if (lastPlayerMessage.size() > PLAYER_MEMORY_LIMIT) {
            lastPlayerMessage.clear();
        }
        if (respond(byId.get(chosen), player.getName(), text)) {
            lastPlayerMessage.put(player.getUniqueId(), now);
        }
    }

    // ---------------------------------------------------------------- replying

    /**
     * Has the NPC answer a line from {@code speaker}. Used for chat and for {@code /npc ask}.
     *
     * @return {@code false} if too many requests are already running or no key is set
     */
    public boolean respond(Npc npc, String speaker, String text) {
        Settings s = plugin.settings();
        if (!hasKey() || inFlight >= s.aiMaxConcurrent) {
            return false;
        }
        String line = speaker + ": " + text.strip();
        memory.add(npc.id(), ChatTurn.Role.USER, line.length() > 500 ? line.substring(0, 500) : line);
        String system = PromptBuilder.system(npc.label(), npc.persona() != null ? npc.persona() : s.aiPersona);
        List<ChatTurn> turns = memory.snapshot(npc.id());
        ChatBrain activeBrain = brain();
        int npcId = npc.id();
        inFlight++;
        lastReply.put(npcId, plugin.currentTick());
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                String reply = null;
                String error = null;
                try {
                    reply = activeBrain.reply(system, turns);
                } catch (ChatException ex) {
                    error = ex.getMessage();
                } catch (RuntimeException ex) {
                    error = ex.getClass().getSimpleName() + ": " + ex.getMessage();
                }
                String finalReply = reply;
                String finalError = error;
                try {
                    Bukkit.getScheduler().runTask(plugin, () -> finish(npcId, finalReply, finalError));
                } catch (IllegalPluginAccessException ex) {
                    // shutting down
                }
            });
        } catch (RuntimeException ex) {
            inFlight--;
            throw ex;
        }
        return true;
    }

    private void finish(int npcId, String reply, String error) {
        inFlight = Math.max(0, inFlight - 1);
        if (error != null) {
            failures++;
            lastError = error;
            long now = plugin.currentTick();
            if (now - lastErrorLog > 20 * 60) {
                lastErrorLog = now;
                plugin.getLogger().warning("NPC chat request failed: " + error);
            }
            return;
        }
        Settings s = plugin.settings();
        Npc npc = plugin.npcs().get(npcId);
        String clean = ChatText.clean(reply, s.aiMaxChars);
        if (npc == null || clean.isEmpty()) {
            return;
        }
        memory.add(npcId, ChatTurn.Role.ASSISTANT, clean);
        long delay = Math.max(10, Math.min(100, Math.round(clean.length() * s.aiTypingTicksPerChar)));
        Bukkit.getScheduler().runTaskLater(plugin, () -> say(plugin.npcs().get(npcId), clean), delay);
    }

    /** Makes an NPC say a line in public chat, in the usual {@code <Name> message} form. */
    public void say(Npc npc, String message) {
        if (npc == null) {
            return;
        }
        Settings s = plugin.settings();
        String name = npc.label() == null || npc.label().isBlank() ? "NPC " + npc.id() : npc.label();
        Component line = Component.text(s.aiFormat.replace("{name}", name).replace("{message}", message));
        replies++;
        Bukkit.getConsoleSender().sendMessage(line);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!s.aiReplyNearbyOnly || (npc.isLive() && player.getWorld() == npc.entity().getWorld()
                    && player.getLocation().distance(npc.entity().getLocation()) <= s.aiReplyRadius)) {
                player.sendMessage(line);
            }
        }
        if (npc.isLive()) {
            npc.entity().swingMainHand();
        }
    }
}
