package com.rpg.skygen.listeners;

import com.rpg.skygen.SkygenPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class DeathListener implements Listener {

    private final SkygenPlugin plugin;

    public DeathListener(SkygenPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (event.getKeepInventory()) return;
        List<ItemStack> toRemove = new ArrayList<>();
        for (ItemStack item : event.getDrops()) {
            if (item == null || item.getType().isAir() || item.getItemMeta() == null) continue;
            if (item.getItemMeta().getPersistentDataContainer()
                    .has(plugin.getWeaponKey(), PersistentDataType.STRING)) {
                event.getItemsToKeep().add(item);
                toRemove.add(item);
            }
        }
        event.getDrops().removeAll(toRemove);
    }
}
