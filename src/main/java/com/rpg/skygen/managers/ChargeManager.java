package com.rpg.skygen.managers;

import com.rpg.skygen.SkygenPlugin;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChargeManager {

    private final SkygenPlugin plugin;
    private final Map<UUID, Double> charges   = new HashMap<>();
    private final Map<UUID, BossBar> bossBars = new HashMap<>();

    public ChargeManager(SkygenPlugin plugin) {
        this.plugin = plugin;
        startRegenTask();
    }

    public double getCharge(Player p) {
        return charges.getOrDefault(p.getUniqueId(), 10.0);
    }

    public boolean consumeCharge(Player p, double amount) {
        double current = getCharge(p);
        if (current < amount) {
            p.sendActionBar("§c§lNot enough Sky Charge! (" + (int) current + "/10)");
            return false;
        }
        charges.put(p.getUniqueId(), current - amount);
        refreshBar(p);
        return true;
    }

    public void addCharge(Player p, double amount) {
        double max = plugin.getConfig().getInt("settings.max_charge", 10);
        double current = getCharge(p);
        charges.put(p.getUniqueId(), Math.min(max, current + amount));
        refreshBar(p);
    }

    public void initBar(Player p) {
        if (!bossBars.containsKey(p.getUniqueId())) {
            BossBar bar = Bukkit.createBossBar(buildTitle(p), BarColor.BLUE, BarStyle.SEGMENTED_10);
            bar.addPlayer(p);
            bossBars.put(p.getUniqueId(), bar);
        }
        refreshBar(p);
    }

    public void removeBar(Player p) {
        BossBar bar = bossBars.remove(p.getUniqueId());
        if (bar != null) bar.removeAll();
        charges.remove(p.getUniqueId());
    }

    public void clearAll() {
        bossBars.values().forEach(BossBar::removeAll);
        bossBars.clear();
        charges.clear();
    }

    private void refreshBar(Player p) {
        BossBar bar = bossBars.get(p.getUniqueId());
        if (bar == null) { initBar(p); return; }
        double charge = getCharge(p);
        double max    = plugin.getConfig().getInt("settings.max_charge", 10);
        bar.setTitle(buildTitle(p));
        bar.setProgress(Math.max(0.0, Math.min(1.0, charge / max)));
        bar.setColor(charge >= 5 ? BarColor.BLUE : (charge >= 2 ? BarColor.CYAN : BarColor.WHITE));
    }

    private String buildTitle(Player p) {
        double c   = getCharge(p);
        double max = plugin.getConfig().getInt("settings.max_charge", 10);
        return "§b§l✦ SKY CHARGE §3" + (int) c + " §7/ §3" + (int) max;
    }

    private void startRegenTask() {
        long ticks   = plugin.getConfig().getLong("settings.charge_regen_ticks", 60L);
        double regen = plugin.getConfig().getDouble("settings.charge_regen_amount", 1.0);
        new BukkitRunnable() {
            @Override public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    var item = p.getInventory().getItemInMainHand();
                    if (item.getItemMeta() != null &&
                        item.getItemMeta().getPersistentDataContainer()
                            .has(plugin.getWeaponKey(), PersistentDataType.STRING)) {
                        addCharge(p, regen);
                    }
                }
            }
        }.runTaskTimer(plugin, ticks, ticks);
    }
}
