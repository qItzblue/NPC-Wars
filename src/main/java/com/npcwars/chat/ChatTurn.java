package com.npcwars.chat;

/** One line of an NPC's conversation memory. */
public record ChatTurn(Role role, String text) {

    public enum Role { USER, ASSISTANT }
}
