package com.rpg.skygen.utils;

import org.bukkit.Location;
import org.bukkit.Sound;

public class SoundUtils {

    private SoundUtils() {}

    public static void play(String soundName, Location loc, float volume, float pitch) {
        if (loc.getWorld() == null || soundName == null) return;
        Sound sound = parseSound(soundName);
        loc.getWorld().playSound(loc, sound, volume, pitch);
    }

    public static void play(String soundName, Location loc) {
        play(soundName, loc, 1.0f, 1.0f);
    }

    private static Sound parseSound(String name) {
        try {
            return Sound.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Sound.ENTITY_PLAYER_ATTACK_SWEEP;
        }
    }
}
