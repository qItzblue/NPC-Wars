package com.npcwars.combat.brain;

import java.util.List;
import java.util.Locale;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

/** Tells healing, helpful and harmful potions apart by their effects. */
final class PotionKinds {

    enum Kind { HEAL, BUFF, HARM, OTHER }

    private PotionKinds() {
    }

    static Kind of(ItemStack stack) {
        if (stack == null || !(stack.getItemMeta() instanceof PotionMeta meta)) {
            return Kind.OTHER;
        }
        Kind best = Kind.OTHER;
        PotionType base = meta.getBasePotionType();
        List<PotionEffect> effects = new java.util.ArrayList<>(meta.getCustomEffects());
        if (base != null) {
            effects.addAll(base.getPotionEffects());
        }
        for (PotionEffect effect : effects) {
            Kind kind = ofEffect(effect.getType().getKey().getKey());
            if (kind == Kind.HARM) {
                return Kind.HARM;
            }
            if (kind == Kind.HEAL) {
                best = Kind.HEAL;
            } else if (kind == Kind.BUFF && best == Kind.OTHER) {
                best = Kind.BUFF;
            }
        }
        return best;
    }

    static Kind ofEffect(String key) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "instant_health", "regeneration", "absorption" -> Kind.HEAL;
            case "strength", "speed", "resistance", "fire_resistance", "haste", "jump_boost" -> Kind.BUFF;
            case "instant_damage", "poison", "slowness", "weakness", "wither", "mining_fatigue", "blindness" -> Kind.HARM;
            default -> Kind.OTHER;
        };
    }
}
