package com.rpg.skygen.managers;

import org.bukkit.entity.Player;

public class CrossPlayManager {

    private static Boolean floodgatePresent = null;

    private CrossPlayManager() {}

    public static boolean isBedrockPlayer(Player player) {
        if (floodgatePresent == null) {
            try {
                Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                floodgatePresent = true;
            } catch (ClassNotFoundException e) {
                floodgatePresent = false;
            }
        }
        if (!floodgatePresent) return false;
        try {
            return org.geysermc.floodgate.api.FloodgateApi.getInstance()
                    .isFloodgatePlayer(player.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }
}
