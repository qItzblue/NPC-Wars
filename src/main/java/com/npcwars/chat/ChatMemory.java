package com.npcwars.chat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The last few lines each NPC said and heard. A snapshot is always a valid conversation for the Messages API: it starts
 * with a user turn and roles alternate (neighbouring lines of the same role are merged).
 */
public final class ChatMemory {

    private final Map<Integer, Deque<ChatTurn>> turns = new HashMap<>();
    private int maxTurns;

    public ChatMemory(int maxTurns) {
        this.maxTurns = Math.max(1, maxTurns);
    }

    public void setMaxTurns(int maxTurns) {
        this.maxTurns = Math.max(1, maxTurns);
    }

    public void add(int npcId, ChatTurn.Role role, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        Deque<ChatTurn> deque = turns.computeIfAbsent(npcId, id -> new ArrayDeque<>());
        deque.addLast(new ChatTurn(role, text.strip()));
        while (deque.size() > maxTurns) {
            deque.removeFirst();
        }
    }

    /** @return a conversation ready to send; empty if the NPC has heard nothing */
    public List<ChatTurn> snapshot(int npcId) {
        Deque<ChatTurn> deque = turns.get(npcId);
        List<ChatTurn> out = new ArrayList<>();
        if (deque == null) {
            return out;
        }
        for (ChatTurn turn : deque) {
            if (out.isEmpty() && turn.role() == ChatTurn.Role.ASSISTANT) {
                continue; // a conversation must open with a user turn
            }
            if (!out.isEmpty() && out.get(out.size() - 1).role() == turn.role()) {
                ChatTurn previous = out.remove(out.size() - 1);
                out.add(new ChatTurn(turn.role(), previous.text() + "\n" + turn.text()));
            } else {
                out.add(turn);
            }
        }
        return out;
    }

    public void forget(int npcId) {
        turns.remove(npcId);
    }

    public void clear() {
        turns.clear();
    }
}
