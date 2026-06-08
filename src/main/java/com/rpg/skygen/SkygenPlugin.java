package com.rpg.skygen;

import com.rpg.skygen.commands.WeaponCommand;
import com.rpg.skygen.commands.WeaponGuiCommand;
import com.rpg.skygen.listeners.DeathListener;
import com.rpg.skygen.listeners.PlayerSessionListener;
import com.rpg.skygen.listeners.WeaponListener;
import com.rpg.skygen.managers.ChargeManager;
import com.rpg.skygen.managers.CooldownManager;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class SkygenPlugin extends JavaPlugin {

    private static SkygenPlugin instance;
    private ChargeManager chargeManager;
    private CooldownManager cooldownManager;
    private NamespacedKey weaponKey;
    private FileConfiguration weaponsConfig;
    private File weaponsFile;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        loadWeaponsConfig();

        this.weaponKey = new NamespacedKey(this, "skygen_weapon");
        this.chargeManager = new ChargeManager(this);
        this.cooldownManager = new CooldownManager(this);

        Bukkit.getPluginManager().registerEvents(new WeaponListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerSessionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new DeathListener(this), this);

        WeaponCommand cmd = new WeaponCommand(this);
        getCommand("skygenweapon").setExecutor(cmd);
        getCommand("skygenreload").setExecutor(cmd);

        WeaponGuiCommand guiCmd = new WeaponGuiCommand(this);
        Bukkit.getPluginManager().registerEvents(guiCmd, this);
        getCommand("skygengui").setExecutor(guiCmd);

        getLogger().info("SkygenSkills enabled.");
    }

    @Override
    public void onDisable() {
        if (chargeManager != null) chargeManager.clearAll();
        getLogger().info("SkygenSkills disabled.");
    }

    public void loadWeaponsConfig() {
        weaponsFile = new File(getDataFolder(), "weapons.yml");
        if (!weaponsFile.exists()) saveResource("weapons.yml", false);
        weaponsConfig = YamlConfiguration.loadConfiguration(weaponsFile);
    }

    public static SkygenPlugin getInstance() { return instance; }
    public ChargeManager getChargeManager()     { return chargeManager; }
    public CooldownManager getCooldownManager() { return cooldownManager; }
    public NamespacedKey getWeaponKey()         { return weaponKey; }
    public FileConfiguration getWeaponsConfig() { return weaponsConfig; }
}
