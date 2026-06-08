package com.rpg.skygen.managers;

import com.rpg.skygen.SkygenPlugin;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CooldownManager {

    private final SkygenPlugin plugin;
    private final Map<UUID, Long> globalCd = new HashMap<>();
    private final Map<String, Map<UUID, Long>> skillCd = new HashMap<>();

    public CooldownManager(SkygenPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean checkAndSetGlobal(Player p) {
        long now    = System.currentTimeMillis();
        long gcd    = plugin.getConfig().getLong("settings.global_cooldown_ms", 800L);
        long expiry = globalCd.getOrDefault(p.getUniqueId(), 0L);
        if (expiry > now) return false;
        globalCd.put(p.getUniqueId(), now + gcd);
        return true;
    }

    public boolean checkAndSetSkill(Player p, String weaponId, String skillType) {
        long now    = System.currentTimeMillis();
        int baseCd  = plugin.getWeaponsConfig().getInt("weapons." + weaponId + "." + skillType + ".cooldown", 5);
        double mult = plugin.getConfig().getDouble("weapons." + weaponId + ".cooldown_multiplier", 1.0);
        long cdMs   = (long)(baseCd * mult * 1000L);

        String mapKey = weaponId + ":" + skillType;
        Map<UUID, Long> map = skillCd.computeIfAbsent(mapKey, k -> new HashMap<>());
        long expiry = map.getOrDefault(p.getUniqueId(), 0L);

        if (expiry > now) {
            long leftSec = (expiry - now) / 1000L;
            String skillName = plugin.getWeaponsConfig()
                    .getString("weapons." + weaponId + "." + skillType + ".name", skillType);
            p.sendActionBar("§c⏳ " + skillName + ": §f" + leftSec + "s");
            return false;
        }
        map.put(p.getUniqueId(), now + cdMs);
        return true;
    }

    public void clearPlayer(UUID uuid) {
        globalCd.remove(uuid);
        skillCd.values().forEach(m -> m.remove(uuid));
    }
}
