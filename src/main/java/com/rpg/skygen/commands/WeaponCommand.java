package com.rpg.skygen.commands;

import com.rpg.skygen.SkygenPlugin;
import com.rpg.skygen.utils.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class WeaponCommand implements CommandExecutor {

    private final SkygenPlugin plugin;

    public WeaponCommand(SkygenPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("skygenreload")) {
            plugin.reloadConfig();
            plugin.loadWeaponsConfig();
            sender.sendMessage("§aSkygenSkills config reloaded.");
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("skygenweapon")) {
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /skygenweapon <player> <weaponId>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) { sender.sendMessage("§cPlayer not found: " + args[0]); return true; }
            ItemStack item = ItemUtils.buildWeapon(args[1]);
            if (item == null) { sender.sendMessage("§cUnknown weapon ID: " + args[1]); return true; }
            target.getInventory().addItem(item);
            target.sendMessage("§aYou received: §r" + item.getItemMeta().getDisplayName());
            sender.sendMessage("§aGave §e" + args[1] + " §ato §e" + target.getName());
            return true;
        }
        return false;
    }
}
