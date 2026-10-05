package com.npcwars.chat;

import java.util.List;

/** Produces an NPC's chat reply. Called off the main thread, so implementations must not touch the Bukkit API. */
public interface ChatBrain extends AutoCloseable {

    /**
     * @param system the NPC's instructions
     * @param turns  the conversation so far, oldest first, starting with a user turn and alternating roles
     * @return the reply text (may need {@link ChatText#clean})
     */
    String reply(String system, List<ChatTurn> turns) throws ChatException;

    @Override
    void close();
}
