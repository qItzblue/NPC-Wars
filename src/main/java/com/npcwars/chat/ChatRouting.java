package com.npcwars.chat;

import java.util.List;

/** Decides which NPC answers a player's chat message. Pure so it can be tested. */
public final class ChatRouting {

    /**
     * @param distance   blocks between the player and the NPC
     * @param mentioned  whether the message names this NPC
     * @param onCooldown whether the NPC spoke too recently
     */
    public record Candidate(int id, double distance, boolean mentioned, boolean onCooldown) {
    }

    private ChatRouting() {
    }

    /**
     * An NPC the message names answers if it is within {@code mentionRadius}; otherwise the nearest NPC within
     * {@code hearRadius} answers. NPCs on cooldown stay quiet, and a message that names an NPC is never answered by
     * a different one.
     *
     * @return the chosen NPC id, or {@code -1} for nobody
     */
    public static int pick(List<Candidate> candidates, double hearRadius, double mentionRadius) {
        boolean someoneNamed = false;
        Candidate bestNamed = null;
        for (Candidate candidate : candidates) {
            if (candidate.mentioned() && candidate.distance() <= mentionRadius) {
                someoneNamed = true;
                if (!candidate.onCooldown() && (bestNamed == null || candidate.distance() < bestNamed.distance())) {
                    bestNamed = candidate;
                }
            }
        }
        if (someoneNamed) {
            return bestNamed == null ? -1 : bestNamed.id();
        }
        Candidate nearest = null;
        for (Candidate candidate : candidates) {
            if (!candidate.onCooldown() && candidate.distance() <= hearRadius
                    && (nearest == null || candidate.distance() < nearest.distance())) {
                nearest = candidate;
            }
        }
        return nearest == null ? -1 : nearest.id();
    }
}
