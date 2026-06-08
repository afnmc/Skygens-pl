package com.rpg.skygen.commands;

import com.rpg.skygen.SkygenPlugin;
import com.rpg.skygen.utils.ItemUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Set;

public class WeaponGuiCommand implements CommandExecutor, Listener {

    private final SkygenPlugin plugin;
    private static final String GUI_TITLE_STR = "§b§lSkygen Weapons Admin";
    private static final Component GUI_TITLE  = Component.text(GUI_TITLE_STR);

    public WeaponGuiCommand(SkygenPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cOnly players can use this!");
            return true;
        }
        openWeaponGui(p);
        return true;
    }

    private void openWeaponGui(Player p) {
        var config = plugin.getWeaponsConfig();
        if (!config.contains("weapons")) {
            p.sendMessage("§cNo weapons in weapons.yml!");
            return;
        }
        Set<String> weaponKeys = config.getConfigurationSection("weapons").getKeys(false);
        int size = ((weaponKeys.size() / 9) + 1) * 9;
        if (size > 54) size = 54;
        Inventory inv = Bukkit.createInventory(null, size, GUI_TITLE);
        int slot = 0;
        for (String id : weaponKeys) {
            if (slot >= size) break;
            ItemStack weaponItem = ItemUtils.buildWeapon(id);
            if (weaponItem != null) { inv.setItem(slot, weaponItem); slot++; }
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getOriginalTitle().equals(GUI_TITLE_STR)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p)) return;
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType().isAir()) return;
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) return;
        if (meta.getPersistentDataContainer().has(plugin.getWeaponKey(), PersistentDataType.STRING)) {
            String weaponId = meta.getPersistentDataContainer().get(plugin.getWeaponKey(), PersistentDataType.STRING);
            ItemStack weaponToGive = ItemUtils.buildWeapon(weaponId);
            if (weaponToGive != null) {
                p.getInventory().addItem(weaponToGive);
                p.sendMessage("§a[+] Received: " + meta.getDisplayName());
            }
        }
    }
              }
