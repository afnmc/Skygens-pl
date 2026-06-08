package com.rpg.skygen.utils;

import com.rpg.skygen.SkygenPlugin;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class ItemUtils {

    private ItemUtils() {}

    public static ItemStack buildWeapon(String weaponId) {
        SkygenPlugin plugin = SkygenPlugin.getInstance();
        var cfg = plugin.getWeaponsConfig();
        String path = "weapons." + weaponId;
        if (!cfg.contains(path)) return null;

        String matStr = cfg.getString(path + ".material", "NETHERITE_SWORD");
        Material mat;
        try {
            mat = Material.valueOf(matStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown material '" + matStr + "' for weapon " + weaponId);
            return null;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta  = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(cfg.getString(path + ".name", weaponId));
        List<String> lore = new ArrayList<>(cfg.getStringList(path + ".lore"));
        lore.add("");
        lore.add(cfg.getString(path + ".rarity", "§fCOMMON"));
        lore.add("§8[Skygen Weapon]");
        meta.setLore(lore);

        int cmd = cfg.getInt(path + ".custom_model_data", 0);
        if (cmd > 0) meta.setCustomModelData(cmd);

        if (cfg.getBoolean(path + ".enchant_glow", false)) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(plugin.getWeaponKey(), PersistentDataType.STRING, weaponId);
        item.setItemMeta(meta);
        return item;
    }
}
