package com.rpg.skygen.listeners;

import com.rpg.skygen.SkygenPlugin;
import com.rpg.skygen.utils.ParticleUtils;
import com.rpg.skygen.utils.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;

public class WeaponListener implements Listener {

    private final SkygenPlugin plugin;
    private static final double MAX_DAMAGE_SKILL    = 8.0;
    private static final double MAX_DAMAGE_ULTIMATE = 12.0;
    private static final int MAX_STUN_TICKS  = 30;
    private static final int MAX_SLOW_TICKS  = 60;
    private static final int MAX_TARGETS     = 8;

    public WeaponListener(SkygenPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player p)) return;
        String weaponId = getHeldWeaponId(p);
        if (weaponId == null || isWorldDisabled(p) || !isWeaponEnabled(weaponId)) return;
        triggerPassive(p, weaponId);
        event.setCancelled(true);
        if (!plugin.getCooldownManager().checkAndSetGlobal(p)) return;
        if (!plugin.getCooldownManager().checkAndSetSkill(p, weaponId, "l_click")) return;
        double chargeCost = plugin.getWeaponsConfig().getDouble("weapons." + weaponId + ".l_click.charge_cost", 1);
        if (!plugin.getChargeManager().consumeCharge(p, chargeCost)) return;
        executeSkill(p, event.getEntity(), weaponId, "l_click");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        Player p = event.getPlayer();
        String weaponId = getHeldWeaponId(p);
        if (weaponId == null || isWorldDisabled(p) || !isWeaponEnabled(weaponId)) return;
        event.setCancelled(true);
        String skillType = p.isSneaking() ? "ultimate" : "r_click";
        if (!plugin.getCooldownManager().checkAndSetGlobal(p)) return;
        if (!plugin.getCooldownManager().checkAndSetSkill(p, weaponId, skillType)) return;
        double chargeCost = plugin.getWeaponsConfig().getDouble("weapons." + weaponId + "." + skillType + ".charge_cost", 2);
        if (!plugin.getChargeManager().consumeCharge(p, chargeCost)) return;
        executeSkill(p, null, weaponId, skillType);
    }

    private void executeSkill(Player caster, Entity primaryTarget, String weaponId, String skillType) {
        String path       = "weapons." + weaponId + "." + skillType;
        String effectType = plugin.getWeaponsConfig().getString(path + ".effect_type", "NONE");
        double rawDamage  = plugin.getWeaponsConfig().getDouble(path + ".damage", 0);
        double mult       = plugin.getConfig().getDouble("weapons." + weaponId + ".damage_multiplier", 1.0);
        double damage     = rawDamage * mult;
        damage = skillType.equals("ultimate") ? Math.min(damage, MAX_DAMAGE_ULTIMATE) : Math.min(damage, MAX_DAMAGE_SKILL);
        
        // FIX: Buat final variable untuk lambda
        final double finalDamage = damage;

        double range     = plugin.getWeaponsConfig().getDouble(path + ".range", 4.0);
        String particle  = plugin.getWeaponsConfig().getString(path + ".particle", "SMOKE");
        String soundCast = plugin.getWeaponsConfig().getString(path + ".sound_cast");
        String soundHit  = plugin.getWeaponsConfig().getString(path + ".sound_impact");
        String soundEcho = plugin.getWeaponsConfig().getString(path + ".sound_echo");
        String skillName = plugin.getWeaponsConfig().getString(path + ".name", skillType);
        List<String> effects = plugin.getWeaponsConfig().getStringList(path + ".effects");

        if (plugin.getConfig().getBoolean("settings.log_skill_usage", false))
            plugin.getLogger().info("[SkillLog] " + caster.getName() + " used " + weaponId + ":" + skillType);

        caster.sendActionBar("§b✦ §f" + skillName + " §7► §bACTIVE!");
        if (soundCast != null) SoundUtils.play(soundCast, caster.getLocation());

        switch (effectType.toUpperCase()) {
            case "STRIKE" -> {
                Entity target = (primaryTarget != null) ? primaryTarget : getTargetInLOS(caster, (int) range);
                if (target instanceof LivingEntity living && living != caster) {
                    living.damage(finalDamage, caster);
                    applyEffects(living, effects);
                    ParticleUtils.spawnBurst(particle, living.getLocation().add(0, 1, 0), 30, 0.4, 0.4);
                    if (soundHit != null) SoundUtils.play(soundHit, living.getLocation());
                }
            }
            case "AOE" -> {
                ParticleUtils.spawnRing(particle, caster.getLocation(), range * 0.9, 36);
                ParticleUtils.spawnBurst(particle, caster.getLocation().add(0, 1, 0), 80, range * 0.5, 1.0);
                if (soundHit != null) SoundUtils.play(soundHit, caster.getLocation());
                int hits = 0;
                for (Entity ent : caster.getWorld().getNearbyEntities(caster.getLocation(), range, range, range)) {
                    if (!(ent instanceof LivingEntity living) || living == caster || hits >= MAX_TARGETS) continue;
                    living.damage(finalDamage, caster);
                    applyEffects(living, effects);
                    ParticleUtils.spawnBurst(particle, living.getLocation().add(0, 1, 0), 15, 0.3, 0.3);
                    hits++;
                }
            }
            case "LIFESTEAL_AOE" -> {
                ParticleUtils.spawnRing(particle, caster.getLocation(), range * 0.9, 36);
                if (soundHit != null) SoundUtils.play(soundHit, caster.getLocation());
                double totalHealed = 0;
                int hits = 0;
                for (Entity ent : caster.getWorld().getNearbyEntities(caster.getLocation(), range, range, range)) {
                    if (!(ent instanceof LivingEntity living) || living == caster || hits >= MAX_TARGETS) continue;
                    living.damage(finalDamage, caster);
                    applyEffects(living, effects);
                    totalHealed += finalDamage * 0.3;
                    hits++;
                }
                double heal = Math.min(totalHealed, 6.0);
                caster.setHealth(Math.min(caster.getMaxHealth(), caster.getHealth() + heal));
                ParticleUtils.spawnBurst("HEART", caster.getLocation().add(0, 2, 0), 8, 0.3, 0.3);
            }
            case "BLINK" -> {
                Location origin = caster.getLocation().clone();
                Vector dir = caster.getLocation().getDirection().normalize().multiply(range);
                caster.setVelocity(dir);
                ParticleUtils.spawnBurst(particle, origin, 50, 0.5, 0.5);
                if (finalDamage > 0) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        for (Entity ent : caster.getWorld().getNearbyEntities(caster.getLocation(), 2.5, 2.5, 2.5)) {
                            if (ent instanceof LivingEntity living && living != caster) {
                                living.damage(finalDamage, caster);
                                applyEffects(living, effects);
                            }
                        }
                        ParticleUtils.spawnBurst(particle, caster.getLocation(), 40, 1.0, 0.5);
                        if (soundHit != null) SoundUtils.play(soundHit, caster.getLocation());
                    }, 6L);
                }
            }
            case "PULL" -> {
                if (soundHit != null) SoundUtils.play(soundHit, caster.getLocation());
                ParticleUtils.spawnBurst(particle, caster.getLocation().add(0, 1, 0), 60, range * 0.5, 1.0);
                int hits = 0;
                for (Entity ent : caster.getWorld().getNearbyEntities(caster.getLocation(), range, range, range)) {
                    if (!(ent instanceof LivingEntity living) || living == caster || hits >= MAX_TARGETS) continue;
                    Vector pull = caster.getLocation().toVector().subtract(living.getLocation().toVector()).normalize().multiply(1.8).setY(0.4);
                    living.setVelocity(pull);
                    living.damage(finalDamage, caster);
                    applyEffects(living, effects);
                    hits++;
                }
            }
            case "AURA" -> {
                applyEffectsToPlayer(caster, effects);
                ParticleUtils.spawnBurst(particle, caster.getLocation().add(0, 1, 0), 40, 0.8, 1.0);
                caster.sendActionBar("§a✦ §f" + skillName + " §7► §aACTIVE!");
            }
            case "HEAL" -> {
                double heal = Math.min(finalDamage, 6.0);
                caster.setHealth(Math.min(caster.getMaxHealth(), caster.getHealth() + heal));
                ParticleUtils.spawnBurst("HEART", caster.getLocation().add(0, 2, 0), 12, 0.4, 0.4);
                applyEffectsToPlayer(caster, effects);
            }
        }

        if (skillType.equals("ultimate") && soundEcho != null)
            Bukkit.getScheduler().runTaskLater(plugin, () -> SoundUtils.play(soundEcho, caster.getLocation(), 0.8f, 0.8f), 10L);
    }

    private void triggerPassive(Player p, String weaponId) {
        String particle = plugin.getWeaponsConfig().getString("weapons." + weaponId + ".passive.particle", "SMOKE");
        ParticleUtils.spawnAmbient(particle, p.getLocation());
    }

    private PotionEffectType getPotionEffectType(String name) {
        try {
            PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(name.toLowerCase()));
            if (type != null) return type;
        } catch (Exception ignored) {}
        return switch (name.toUpperCase()) {
            case "SLOWNESS"        -> PotionEffectType.SLOWNESS;
            case "WEAKNESS"        -> PotionEffectType.WEAKNESS;
            case "BLINDNESS"       -> PotionEffectType.BLINDNESS;
            case "WITHER"          -> PotionEffectType.WITHER;
            case "POISON"          -> PotionEffectType.POISON;
            case "LEVITATION"      -> PotionEffectType.LEVITATION;
            case "GLOWING"         -> PotionEffectType.GLOWING;
            case "NAUSEA"          -> PotionEffectType.NAUSEA;
            case "HUNGER"          -> PotionEffectType.HUNGER;
            case "MINING_FATIGUE"  -> PotionEffectType.MINING_FATIGUE;
            case "RESISTANCE"      -> PotionEffectType.RESISTANCE;
            case "FIRE_RESISTANCE" -> PotionEffectType.FIRE_RESISTANCE;
            case "SPEED"           -> PotionEffectType.SPEED;
            case "STRENGTH"        -> PotionEffectType.STRENGTH;
            case "JUMP_BOOST"      -> PotionEffectType.JUMP_BOOST;
            case "SLOW_FALLING"    -> PotionEffectType.SLOW_FALLING;
            case "REGENERATION"    -> PotionEffectType.REGENERATION;
            case "ABSORPTION"      -> PotionEffectType.ABSORPTION;
            case "HASTE"           -> PotionEffectType.HASTE;
            default                -> null;
        };
    }

    private void applyEffects(LivingEntity target, List<String> effects) {
        for (String eff : effects) {
            String[] parts = eff.split(":");
            if (parts.length < 1) continue;
            PotionEffectType type = getPotionEffectType(parts[0]);
            if (type == null) { plugin.getLogger().warning("Unknown PotionEffectType: " + parts[0]); continue; }
            int amp = parts.length > 1 ? Integer.parseInt(parts[1]) - 1 : 0;
            int dur = parts.length > 2 ? Integer.parseInt(parts[2]) : 100;
            if (type == PotionEffectType.SLOWNESS)   dur = Math.min(dur, MAX_SLOW_TICKS);
            if (type == PotionEffectType.LEVITATION) dur = Math.min(dur, MAX_STUN_TICKS);
            target.addPotionEffect(new PotionEffect(type, dur, amp, false, true, true));
        }
    }

    private void applyEffectsToPlayer(Player p, List<String> effects) {
        for (String eff : effects) {
            String[] parts = eff.split(":");
            if (parts.length < 1) continue;
            PotionEffectType type = getPotionEffectType(parts[0]);
            if (type == null) continue;
            int amp = parts.length > 1 ? Integer.parseInt(parts[1]) - 1 : 0;
            int dur = parts.length > 2 ? Integer.parseInt(parts[2]) : 100;
            p.addPotionEffect(new PotionEffect(type, dur, amp, false, true, true));
        }
    }

    private String getHeldWeaponId(Player p) {
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType().isAir()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(plugin.getWeaponKey(), PersistentDataType.STRING);
    }

    private boolean isWorldDisabled(Player p) {
        return plugin.getConfig().getStringList("disabled_worlds").contains(p.getWorld().getName());
    }

    private boolean isWeaponEnabled(String weaponId) {
        return plugin.getConfig().getBoolean("weapons." + weaponId + ".enabled", true);
    }

    private Entity getTargetInLOS(Player caster, int range) {
        for (Entity ent : caster.getNearbyEntities(range, range, range)) {
            if (!(ent instanceof LivingEntity) || ent == caster) continue;
            Vector toEnt = ent.getLocation().toVector().subtract(caster.getLocation().toVector()).normalize();
            if (toEnt.dot(caster.getLocation().getDirection()) > 0.6) return ent;
        }
        return null;
    }
                        }
