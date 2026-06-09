package com.rpg.skygen.listeners;

import com.rpg.skygen.SkygenPlugin;
import com.rpg.skygen.utils.ParticleUtils;
import com.rpg.skygen.utils.SoundUtils;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class WeaponListener implements Listener {

    private final SkygenPlugin plugin;

    private final Map<UUID, Integer> comboCount = new HashMap<>();
    private final Map<UUID, Long> comboTimer = new HashMap<>();
    private final Map<UUID, Map<String, Long>> skillCooldowns = new HashMap<>();
    private final Map<UUID, Long> globalCd = new HashMap<>();

    private static final long COMBO_WINDOW_MS = 2500L;
    private static final int COMBO_PASSIVE_AT = 4;
    private static final long GLOBAL_CD_MS = 600L;
    private static final double BASIC_ATTACK_DMG = 6.0;

    public WeaponListener(SkygenPlugin plugin) {
        this.plugin = plugin;
        startActionBarTask();
    }

    // ─────────────────────────── BASIC ATTACK ────────────────────────────
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBasicAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player p)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        String weaponId = getHeldWeaponId(p);
        if (weaponId == null) return;
        if (isWorldDisabled(p)) return;

        event.setDamage(BASIC_ATTACK_DMG);

        UUID uid = p.getUniqueId();
        long now = System.currentTimeMillis();
        int combo = comboCount.getOrDefault(uid, 0);
        if (now - comboTimer.getOrDefault(uid, 0L) > COMBO_WINDOW_MS) combo = 0;
        combo++;
        comboCount.put(uid, combo);
        comboTimer.put(uid, now);

        if (combo >= COMBO_PASSIVE_AT) {
            comboCount.put(uid, 0);
            triggerPassive(p, target, weaponId);
        }

        String passivePtc = plugin.getWeaponsConfig()
                .getString("weapons." + weaponId + ".passive.particle", "ELECTRIC_SPARK");
        ParticleUtils.spawnBurst(passivePtc, target.getLocation().add(0, 1, 0), 6, 0.25, 0.25);
    }

    // ─────────────────────────── SKILL / ULTIMATE ─────────────────────────
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        boolean isRight = (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK);
        if (!isRight) return;

        Player p = event.getPlayer();
        String weaponId = getHeldWeaponId(p);
        if (weaponId == null || isWorldDisabled(p) || !isWeaponEnabled(weaponId)) return;
        event.setCancelled(true);

        String slot = p.isSneaking() ? "ultimate" : "r_click";

        long now = System.currentTimeMillis();
        long gcExpiry = globalCd.getOrDefault(p.getUniqueId(), 0L);
        if (gcExpiry > now) return;
        globalCd.put(p.getUniqueId(), now + GLOBAL_CD_MS);

        if (!checkSkillCooldown(p, weaponId, slot)) return;

        double cost = plugin.getWeaponsConfig().getDouble("weapons." + weaponId + "." + slot + ".charge_cost", 2);
        if (!plugin.getChargeManager().consumeCharge(p, cost)) return;

        int cdSec = plugin.getWeaponsConfig().getInt("weapons." + weaponId + "." + slot + ".cooldown", 10);
        setSkillCooldown(p, weaponId, slot, cdSec * 1000L);

        executeSkill(p, null, weaponId, slot);
    }

    // ─────────────────────────── PASSIVE TRIGGER ──────────────────────────
    private void triggerPassive(Player caster, LivingEntity lastTarget, String weaponId) {
        String ptc = plugin.getWeaponsConfig()
                .getString("weapons." + weaponId + ".passive.particle", "ELECTRIC_SPARK");

        switch (weaponId) {
            case "Stormcaller" -> passiveStormcaller(caster, lastTarget, ptc);
            case "Aurorablaze" -> passiveAurorablaze(caster, ptc);
            case "ZenithLance" -> passiveZenithLance(caster, lastTarget, ptc);
            case "Thunderwing" -> passiveThunderwing(caster, ptc);
            case "Cloudpiercer" -> passiveCloudpiercer(caster, lastTarget, ptc);
            case "SolarFlare" -> passiveSolarFlare(caster, ptc);
            case "ZephyrBow" -> passiveZephyrBow(caster, lastTarget, ptc);
            case "TerraShatterer" -> passiveTerraShatterer(caster, lastTarget, ptc);
            case "StarlightWand" -> passiveStarlightWand(caster, ptc);
            case "NimbusHelm" -> passiveNimbusHelm(caster, ptc);
            case "AegisShield" -> passiveAegisShield(caster, ptc);
            case "GenesisPick" -> passiveGenesisPick(caster, lastTarget, ptc);
            case "WindShear" -> passiveWindShear(caster, lastTarget, ptc);
            case "GeneratorKey" -> passiveGeneratorKey(caster, ptc);
            case "CelestialWrath" -> passiveCelestialWrath(caster, lastTarget, ptc);
            default -> {
                ParticleUtils.spawnBurst(ptc, caster.getLocation().add(0, 1.5, 0), 20, 0.5, 0.5);
                caster.sendActionBar("§b✦ §bPASSIVE TRIGGERED!");
            }
        }
    }

    // ═══════════════════════ SKYGEN PASSIVE IMPLEMENTATIONS ════════════════

    private void passiveStormcaller(Player p, LivingEntity hit, String ptc) {
        p.sendActionBar("§b⚡ CHAIN LIGHTNING!");
        List<LivingEntity> chain = new ArrayList<>();
        chain.add(hit);
        LivingEntity prev = hit;
        for (int i = 0; i < 2; i++) {
            LivingEntity next = getNearestEnemyFrom(prev.getLocation(), p, 5, chain);
            if (next == null) break;
            chain.add(next);
            prev = next;
        }
        new BukkitRunnable() {
            int idx = 0;
            @Override public void run() {
                if (idx >= chain.size()) { cancel(); return; }
                LivingEntity t = chain.get(idx++);
                t.damage(5.0, p);
                t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1, false, false));
                t.getWorld().strikeLightningEffect(t.getLocation());
                ParticleUtils.spawnBurst("ELECTRIC_SPARK", t.getLocation().add(0, 1, 0), 15, 0.3, 0.3);
                SoundUtils.play("ENTITY_LIGHTNING_BOLT_IMPACT", t.getLocation(), 0.7f, 1.2f);
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    private void passiveAurorablaze(Player p, String ptc) {
        p.sendActionBar("§d⚡ AURORA BLESS!");
        new BukkitRunnable() {
            double r = 0;
            int t = 0;
            @Override public void run() {
                if (t++ >= 12) { cancel(); return; }
                r += 0.7;
                ParticleUtils.spawnRing("GLOW", p.getLocation(), r, (int)(r * 5));
                for (Entity e : p.getWorld().getNearbyEntities(p.getLocation(), r, r, r)) {
                    if (e instanceof Player ally) {
                        ally.setHealth(Math.min(ally.getMaxHealth(), ally.getHealth() + 0.5));
                    } else if (e instanceof LivingEntity le) {
                        le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
        SoundUtils.play("BLOCK_BEACON_ACTIVATE", p.getLocation(), 0.8f, 1.3f);
    }

    private void passiveZenithLance(Player p, LivingEntity target, String ptc) {
        p.sendActionBar("§e⚡ SKY IMPALE!");
        target.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 25, 3, false, false));
        ParticleUtils.spawnBurst("GLOW", target.getLocation().add(0, 1, 0), 20, 0.3, 0.3);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            target.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 1, -5, false, false));
            target.damage(7.0, p);
            ParticleUtils.spawnBurst("GLOW_SQUID_INK", target.getLocation().add(0, 1, 0), 25, 0.4, 0.4);
            SoundUtils.play("ENTITY_PLAYER_ATTACK_CRIT", target.getLocation(), 1.0f, 0.8f);
        }, 25L);
        SoundUtils.play("BLOCK_BEACON_ACTIVATE", p.getLocation(), 0.7f, 1.5f);
    }

    private void passiveThunderwing(Player p, String ptc) {
        p.sendActionBar("§3⚡ STORM BURST!");
        Vector dir = p.getLocation().getDirection().normalize();
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 2, false, false));
        p.setVelocity(dir.multiply(2.5).setY(0.4));
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t++ >= 10) { cancel(); return; }
                p.getWorld().strikeLightningEffect(p.getLocation());
                ParticleUtils.spawnBurst("ELECTRIC_SPARK", p.getLocation(), 8, 0.3, 0.3);
                for (Entity e : p.getWorld().getNearbyEntities(p.getLocation(), 2, 2, 2)) {
                    if (e instanceof LivingEntity le && le != p) {
                        le.damage(1.5, p);
                        le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 1, false, false));
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 2L);
        SoundUtils.play("ENTITY_LIGHTNING_BOLT_THUNDER", p.getLocation(), 0.8f, 1.1f);
    }

    private void passiveCloudpiercer(Player p, LivingEntity target, String ptc) {
        p.sendActionBar("§f⚡ CLOUD VOLLEY!");
        Location base = target.getLocation().clone().add(0, 6, 0);
        for (int i = 0; i < 5; i++) {
            int fi = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                double angle = fi * (2 * Math.PI / 5);
                double ox = Math.cos(angle) * 3;
                double oz = Math.sin(angle) * 3;
                Location drop = base.clone().add(ox, 0, oz);
                new BukkitRunnable() {
                    double y = 6;
                    @Override public void run() {
                        if (y <= 0) {
                            for (Entity e : drop.getWorld().getNearbyEntities(
                                    new Location(drop.getWorld(), drop.getX(), drop.getY() - y, drop.getZ()),
                                    1.5, 1.5, 1.5)) {
                                if (e instanceof LivingEntity le && le != p) {
                                    le.damage(3.5, p);
                                    le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1, false, false));
                                }
                            }
                            drop.getWorld().spawnParticle(Particle.CLOUD,
                                    new Location(drop.getWorld(), drop.getX(), drop.getY() - 5, drop.getZ()),
                                    15, 0.3, 0.3, 0.3, 0.02);
                            cancel(); return;
                        }
                        drop.getWorld().spawnParticle(Particle.CLOUD,
                                new Location(drop.getWorld(), drop.getX(), drop.getY() - y, drop.getZ()),
                                3, 0.1, 0, 0.1, 0.01);
                        y -= 0.6;
                    }
                }.runTaskTimer(plugin, 0L, 1L);
            }, (long)(fi * 4));
        }
        SoundUtils.play("ENTITY_ARROW_SHOOT", p.getLocation(), 1.0f, 0.7f);
    }

    private void passiveSolarFlare(Player p, String ptc) {
        p.sendActionBar("§6⚡ SOLAR PROMINENCE!");
        Location c = p.getLocation().clone();
        new BukkitRunnable() {
            double r = 0.5;
            int t = 0;
            @Override public void run() {
                if (t++ >= 10) { cancel(); return; }
                r += 0.8;
                ParticleUtils.spawnRing("GLOW", c, r, (int)(r * 5));
                for (Entity e : c.getWorld().getNearbyEntities(c, r, r, r)) {
                    if (!(e instanceof LivingEntity le) || le == p) continue;
                    le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false));
                    le.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 80, 0, false, false));
                    if (t == 5) le.damage(3.0, p);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
        SoundUtils.play("ENTITY_LIGHTNING_BOLT_THUNDER", c, 0.8f, 1.3f);
    }

    private void passiveZephyrBow(Player p, LivingEntity target, String ptc) {
        p.sendActionBar("§a⚡ WIND LAUNCH!");
        target.setVelocity(new Vector(0, 2.5, 0));
        ParticleUtils.spawnBurst("CLOUD", target.getLocation().add(0, 1, 0), 20, 0.5, 0.5);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            target.setVelocity(new Vector(0, -3.0, 0));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                target.damage(8.0, p);
                ParticleUtils.spawnBurst("CLOUD", target.getLocation(), 30, 0.8, 0.3);
                SoundUtils.play("ENTITY_PHANTOM_DEATH", target.getLocation(), 0.8f, 0.9f);
            }, 12L);
        }, 15L);
        SoundUtils.play("ENTITY_PHANTOM_FLAP", target.getLocation(), 0.9f, 1.4f);
    }

    private void passiveTerraShatterer(Player p, LivingEntity target, String ptc) {
        p.sendActionBar("§2⚡ TERRA SPIKE!");
        Location base = target.getLocation().clone();
        int spikes = 6;
        for (int i = 0; i < spikes; i++) {
            int fi = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                double angle = fi * (2 * Math.PI / spikes);
                for (double d = 1; d <= 5; d += 0.7) {
                    double sx = base.getX() + Math.cos(angle) * d;
                    double sz = base.getZ() + Math.sin(angle) * d;
                    Location spike = new Location(base.getWorld(), sx, base.getY(), sz);
                    base.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, spike,
                            5, 0.1, 0.3, 0.1, 0, Material.STONE.createBlockData());
                    for (Entity e : base.getWorld().getNearbyEntities(spike, 0.8, 1.5, 0.8)) {
                        if (e instanceof LivingEntity le && le != p) {
                            le.damage(2.5, p);
                            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 2, false, false));
                        }
                    }
                }
            }, (long)(fi * 2));
        }
        SoundUtils.play("ENTITY_IRON_GOLEM_ATTACK", base, 1.0f, 0.8f);
    }

    private void passiveStarlightWand(Player p, String ptc) {
        p.sendActionBar("§f⚡ STARFALL!");
        p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + 5.0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 80, 1, false, false));
        ParticleUtils.spawnBurst("GLOW", p.getLocation().add(0, 2, 0), 20, 0.4, 0.5);
        for (Entity e : p.getWorld().getNearbyEntities(p.getLocation(), 6, 5, 6)) {
            if (!(e instanceof LivingEntity le) || le == p) continue;
            le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 1, false, false));
            le.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0, false, false));
            ParticleUtils.spawnBurst("GLOW", le.getLocation().add(0, 1, 0), 8, 0.3, 0.3);
        }
        SoundUtils.play("ENTITY_PLAYER_LEVELUP", p.getLocation(), 0.7f, 1.3f);
    }

    private void passiveNimbusHelm(Player p, String ptc) {
        p.sendActionBar("§b⚡ CLOUD ASCENT!");
        p.setVelocity(new Vector(0, 2.2, 0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 60, 0, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 60, 4, false, false));
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t++ >= 15) { cancel(); return; }
                ParticleUtils.spawnRing("CLOUD", p.getLocation(), 2.0, 16);
                for (Entity e : p.getWorld().getNearbyEntities(p.getLocation(), 3, 3, 3)) {
                    if (e instanceof LivingEntity le && le != p) {
                        le.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 10, 1, false, false));
                        le.damage(1.5, p);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
        SoundUtils.play("ENTITY_PHANTOM_FLAP", p.getLocation(), 0.9f, 1.5f);
    }

    private void passiveAegisShield(Player p, String ptc) {
        p.sendActionBar("§f⚡ DIVINE REFLECT!");
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 80, 1, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 60, 1, false, false));
        Location c = p.getLocation().clone();
        new BukkitRunnable() {
            double r = 0.5;
            int t = 0;
            @Override public void run() {
                if (t++ >= 8) { cancel(); return; }
                r += 0.6;
                ParticleUtils.spawnRing("GLOW", c, r, (int)(r * 6));
                for (Entity e : c.getWorld().getNearbyEntities(c, r, r, r)) {
                    if (!(e instanceof LivingEntity le) || le == p) continue;
                    le.damage(4.0, p);
                    le.setVelocity(le.getLocation().toVector().subtract(c.toVector()).normalize().multiply(1.5).setY(0.5));
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
        SoundUtils.play("BLOCK_BEACON_ACTIVATE", p.getLocation(), 1.0f, 1.4f);
    }

    private void passiveGenesisPick(Player p, LivingEntity target, String ptc) {
        p.sendActionBar("§a⚡ GENESIS TREMOR!");
        p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 100, 2, false, false));
        Location c = target.getLocation().clone();
        ParticleUtils.spawnRing("BLOCK_CRUMBLE", c, 5.0, 30);
        ParticleUtils.spawnBurst("GLOW_SQUID_INK", c.clone().add(0, 1, 0), 30, 1.5, 0.3);
        for (Entity e : c.getWorld().getNearbyEntities(c, 5, 3, 5)) {
            if (!(e instanceof LivingEntity le) || le == p) continue;
            le.damage(5.0, p);
            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2, false, false));
            le.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 80, 1, false, false));
        }
        SoundUtils.play("ENTITY_IRON_GOLEM_ATTACK", c, 1.0f, 0.7f);
    }

    private void passiveWindShear(Player p, LivingEntity target, String ptc) {
        p.sendActionBar("§a⚡ WIND FLURRY!");
        List<Entity> nearby = new ArrayList<>(p.getWorld()
                .getNearbyEntities(p.getLocation(), 8, 5, 8)
                .stream().filter(e -> e instanceof LivingEntity && e != p).limit(3).toList());
        new BukkitRunnable() {
            int idx = 0;
            @Override public void run() {
                if (idx >= nearby.size()) { cancel(); return; }
                Entity t = nearby.get(idx++);
                Location at = t.getLocation().clone()
                        .add(t.getLocation().getDirection().multiply(-1.5));
                at.setYaw(t.getLocation().getYaw() + 180f);
                ParticleUtils.spawnBurst("CLOUD", p.getLocation(), 12, 0.3, 0.3);
                p.teleport(at);
                if (t instanceof LivingEntity le) {
                    le.damage(4.0, p);
                    le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1, false, false));
                }
                ParticleUtils.spawnBurst("CLOUD", at, 12, 0.3, 0.3);
                SoundUtils.play("ENTITY_PHANTOM_FLAP", at, 0.8f, 1.3f);
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void passiveGeneratorKey(Player p, String ptc) {
        p.sendActionBar("§e⚡ OVERCHARGE!");
        plugin.getChargeManager().addCharge(p, 5.0);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 0, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 60, 1, false, false));
        Location c = p.getLocation().clone();
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t++ >= 6) { cancel(); return; }
                ParticleUtils.spawnRing("ELECTRIC_SPARK", c, 4.0 + t * 0.3, 20);
                for (Entity e : c.getWorld().getNearbyEntities(c, 4, 3, 4)) {
                    if (e instanceof LivingEntity le && le != p) {
                        le.damage(2.0, p);
                        le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1, false, false));
                    }
                }
                if (t % 2 == 0) c.getWorld().strikeLightningEffect(c);
            }
        }.runTaskTimer(plugin, 0L, 3L);
        SoundUtils.play("ENTITY_LIGHTNING_BOLT_THUNDER", c, 1.0f, 0.8f);
    }

    private void passiveCelestialWrath(Player p, LivingEntity target, String ptc) {
        p.sendActionBar("§6⚡ HEAVEN'S JUDGMENT!");
        Location c = target.getLocation().clone();
        if (target.getHealth() <= target.getMaxHealth() * 0.25) {
            target.setHealth(0);
            p.sendActionBar("§6☀ DIVINE EXECUTION!");
            ParticleUtils.spawnBurst("GLOW", c.clone().add(0, 1, 0), 40, 1.0, 1.0);
            SoundUtils.play("ENTITY_PLAYER_LEVELUP", c, 1.2f, 1.5f);
        } else {
            target.damage(7.0, p);
        }
        new BukkitRunnable() {
            double r = 0.5;
            int t = 0;
            @Override public void run() {
                if (t++ >= 10) { cancel(); return; }
                r += 0.7;
                ParticleUtils.spawnRing("GLOW", c, r, (int)(r * 6));
                for (Entity e : c.getWorld().getNearbyEntities(c, r, r, r)) {
                    if (!(e instanceof LivingEntity le) || le == p || le == target) continue;
                    le.damage(3.0, p);
                    le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 1, false, false));
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
        SoundUtils.play("ENTITY_LIGHTNING_BOLT_THUNDER", c, 1.0f, 1.2f);
    }

    // ─────────────────────────── SKILL EXECUTOR ────────────────────────────

    private void executeSkill(Player caster, Entity primaryTarget, String weaponId, String slot) {
        String path = "weapons." + weaponId + "." + slot;
        double rawDmg = plugin.getWeaponsConfig().getDouble(path + ".damage", 0);
        double mult = plugin.getConfig().getDouble("weapons." + weaponId + ".damage_multiplier", 1.0);
        double damage = Math.min(rawDmg * mult, slot.equals("ultimate") ? 12.0 : 8.0);
        double range = plugin.getWeaponsConfig().getDouble(path + ".range", 4.0);
        String particle = plugin.getWeaponsConfig().getString(path + ".particle", "CLOUD");
        String sCast = plugin.getWeaponsConfig().getString(path + ".sound_cast");
        String sHit = plugin.getWeaponsConfig().getString(path + ".sound_impact");
        String sEcho = plugin.getWeaponsConfig().getString(path + ".sound_echo");
        String skillName = plugin.getWeaponsConfig().getString(path + ".name", slot);
        List<String> efx = plugin.getWeaponsConfig().getStringList(path + ".effects");

        caster.sendActionBar("§b✦ §f" + skillName + " §7► §bACTIVATED!");
        if (sCast != null) SoundUtils.play(sCast, caster.getLocation());

        boolean handled = dispatchCinematicSkill(caster, weaponId, slot, damage, range, particle, efx, sCast, sHit, sEcho);
        if (handled) return;

        String effType = plugin.getWeaponsConfig().getString(path + ".effect_type", "NONE");
        genericSkillExec(caster, primaryTarget, effType, damage, range, particle, efx, sHit, caster.getLocation());

        if (slot.equals("ultimate") && sEcho != null)
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    SoundUtils.play(sEcho, caster.getLocation(), 0.8f, 0.8f), 12L);
    }

    private boolean dispatchCinematicSkill(Player p, String wid, String slot,
                                           double dmg, double range, String ptc,
                                           List<String> efx, String sCast, String sHit, String sEcho) {
        String comboKey = wid + ":" + slot;
        switch (comboKey) {
            case "Stormcaller:r_click": {
                Location c = p.getLocation().clone();
                ParticleUtils.spawnRing("ELECTRIC_SPARK", c, range * 0.9, 30);
                int hits = 0;
                for (Entity e : p.getWorld().getNearbyEntities(c, range, range, range)) {
                    if (!(e instanceof LivingEntity le) || le == p || hits >= 8) continue;
                    hits++;
                    le.damage(dmg, p);
                    applyEffects(le, efx);
                    le.getWorld().strikeLightningEffect(le.getLocation());
                    ParticleUtils.spawnBurst("ELECTRIC_SPARK", le.getLocation().add(0, 1, 0), 12, 0.3, 0.3);
                    Bukkit.getScheduler().runTaskLater(plugin, () ->
                            SoundUtils.play(sHit != null ? sHit : "ENTITY_LIGHTNING_BOLT_IMPACT",
                                    le.getLocation(), 0.8f, 1.0f), 1L);
                }
                return true;
            }
            case "Stormcaller:ultimate": {
                Location c = p.getLocation().clone();
                SoundUtils.play(sCast != null ? sCast : "ENTITY_LIGHTNING_BOLT_THUNDER", c, 1.2f, 0.6f);
                new BukkitRunnable() {
                    int t = 0;
                    @Override public void run() {
                        if (t++ >= 50) {
                            if (sEcho != null) SoundUtils.play(sEcho, c, 0.7f, 0.9f);
                            cancel(); return;
                        }
                        ParticleUtils.spawnRing("ELECTRIC_SPARK", c, range * 0.85, 28);
                        if (t % 8 == 0) {
                            c.getWorld().strikeLightningEffect(c);
                            for (Entity e : c.getWorld().getNearbyEntities(c, range, range, range)) {
                                if (!(e instanceof LivingEntity le) || le == p) continue;
                                le.damage(dmg / 6.0, p);
                                applyEffects(le, efx);
                                le.getWorld().strikeLightningEffect(le.getLocation());
                            }
                        }
                        if (t % 5 == 0 && sHit != null) SoundUtils.play(sHit, c, 0.6f, 0.8f + t * 0.02f);
                    }
                }.runTaskTimer(plugin, 0L, 2L);
                return true;
            }
            case "ZenithLance:r_click": {
                Location origin = p.getLocation().clone();
                Vector fwd = p.getLocation().getDirection().normalize().multiply(range);
                ParticleUtils.spawnBurst("GLOW_SQUID_INK", origin, 25, 0.4, 0.4);
                p.setVelocity(fwd.setY(0.3));
                applyEffects(p, efx);
                if (sHit != null) Bukkit.getScheduler().runTaskLater(plugin,
                        () -> SoundUtils.play(sHit, p.getLocation()), 7L);
                return true;
            }
            case "ZenithLance:ultimate": {
                Location origin = p.getLocation().clone().add(0, 1.2, 0);
                Vector dir = p.getLocation().getDirection().normalize();
                SoundUtils.play(sCast != null ? sCast : "BLOCK_BEACON_ACTIVATE", origin, 1.0f, 1.2f);
                new BukkitRunnable() {
                    double dist = 0;
                    @Override public void run() {
                        if (dist > range) {
                            if (sEcho != null) SoundUtils.play(sEcho, p.getLocation(), 0.7f, 0.8f);
                            cancel(); return;
                        }
                        dist += 0.7;
                        Location pt = origin.clone().add(dir.clone().multiply(dist));
                        pt.getWorld().spawnParticle(Particle.GLOW, pt, 5, 0.15, 0.15, 0.15, 0);
                        pt.getWorld().spawnParticle(Particle.GLOW_SQUID_INK, pt, 3, 0.1, 0.1, 0.1, 0);
                        for (Entity e : pt.getWorld().getNearbyEntities(pt, 1.0, 1.2, 1.0)) {
                            if (!(e instanceof LivingEntity le) || le == p) continue;
                            le.damage(dmg, p);
                            applyEffects(le, efx);
                            ParticleUtils.spawnBurst("GLOW", le.getLocation().add(0, 1, 0), 15, 0.3, 0.3);
                            if (sHit != null) SoundUtils.play(sHit, le.getLocation(), 0.9f, 1.1f);
                        }
                    }
                }.runTaskTimer(plugin, 0L, 1L);
                return true;
            }
            case "SolarFlare:r_click": {
                Location c = p.getLocation().clone();
                new BukkitRunnable() {
                    double r = 0.5;
                    int t = 0;
                    @Override public void run() {
                        if (t++ >= 8) { cancel(); return; }
                        r += 0.65;
                        ParticleUtils.spawnRing("GLOW", c, r, (int)(r * 5));
                        for (Entity e : c.getWorld().getNearbyEntities(c, r, r, r)) {
                            if (!(e instanceof LivingEntity le) || le == p) continue;
                            le.damage(dmg * 0.3, p);
                            applyEffects(le, efx);
                        }
                    }
                }.runTaskTimer(plugin, 0L, 2L);
                if (sHit != null) SoundUtils.play(sHit, c);
                return true;
            }
            case "SolarFlare:ultimate": {
                Location c = p.getLocation().clone();
                SoundUtils.play(sCast != null ? sCast : "ENTITY_LIGHTNING_BOLT_THUNDER", c, 1.2f, 0.8f);
                c.getWorld().strikeLightningEffect(c);
                new BukkitRunnable() {
                    int wave = 0;
                    @Override public void run() {
                        if (wave++ >= 4) {
                            if (sEcho != null) SoundUtils.play(sEcho, c, 0.8f, 1.0f);
                            cancel(); return;
                        }
                        double r = 3 + wave * 2.0;
                        ParticleUtils.spawnRing("FIREWORKS_SPARK", c, r, (int)(r * 6));
                        ParticleUtils.spawnBurst("GLOW", c.clone().add(0, 1, 0), 30, r * 0.4, 1.0);
                        for (Entity e : c.getWorld().getNearbyEntities(c, range, range, range)) {
                            if (!(e instanceof LivingEntity le) || le == p) continue;
                            le.damage(dmg / 4.0, p);
                            applyEffects(le, efx);
                        }
                        c.getWorld().strikeLightningEffect(c);
                        if (sHit != null) SoundUtils.play(sHit, c, 0.8f, 0.7f + wave * 0.1f);
                    }
                }.runTaskTimer(plugin, 0L, 8L);
                return true;
            }
            case "CelestialWrath:r_click": {
                Entity t = getNearestEnemy(p, (int) range);
                if (t != null) {
                    Location at = t.getLocation().clone().add(0, 5, 0);
                    ParticleUtils.spawnBurst("GLOW", p.getLocation(), 20, 0.4, 0.4);
                    p.teleport(at);
                    applyEffects(p, efx);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        p.setVelocity(new Vector(0, -3.5, 0));
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            Location land = p.getLocation().clone();
                            ParticleUtils.spawnRing("GLOW", land, 5.0, 30);
                            ParticleUtils.spawnBurst("GLOW_SQUID_INK", land.clone().add(0, 1, 0), 40, 1.5, 0.5);
                            for (Entity e : land.getWorld().getNearbyEntities(land, 5, 3, 5)) {
                                if (e instanceof LivingEntity le && le != p) {
                                    le.damage(dmg, p);
                                    applyEffects(le, efx);
                                    le.setVelocity(le.getLocation().toVector()
                                            .subtract(land.toVector()).normalize().multiply(1.5).setY(0.8));
                                }
                            }
                            if (sHit != null) SoundUtils.play(sHit, land);
                        }, 8L);
                    }, 3L);
                }
                return true;
            }
            case "CelestialWrath:ultimate": {
                Location c = p.getLocation().clone();
                SoundUtils.play(sCast != null ? sCast : "ENTITY_LIGHTNING_BOLT_THUNDER", c, 1.2f, 0.6f);
                new BukkitRunnable() {
                    double y = 20;
                    @Override public void run() {
                        if (y <= 0) {
                            ParticleUtils.spawnBurst("GLOW", c.clone().add(0, 1, 0), 80, range * 0.5, 1.5);
                            ParticleUtils.spawnRing("GLOW", c, range * 0.9, 36);
                            c.getWorld().strikeLightningEffect(c);
                            for (Entity e : c.getWorld().getNearbyEntities(c, range, range, range)) {
                                if (!(e instanceof LivingEntity le) || le == p) continue;
                                le.damage(dmg, p);
                                applyEffects(le, efx);
                                le.setVelocity(le.getLocation().toVector()
                                        .subtract(c.toVector()).normalize().multiply(2.0).setY(1.0));
                            }
                            if (sHit != null) SoundUtils.play(sHit, c, 1.2f, 0.6f);
                            if (sEcho != null) Bukkit.getScheduler().runTaskLater(plugin,
                                    () -> SoundUtils.play(sEcho, c, 0.8f, 0.7f), 12L);
                            cancel(); return;
                        }
                        y -= 1.5;
                        Location current = c.clone().add(0, y, 0);
                        current.getWorld().spawnParticle(Particle.GLOW, current, 8, 0.4, 0.4, 0.4, 0);
                        current.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, current, 5, 0.2, 0.2, 0.2, 0.1);
                    }
                }.runTaskTimer(plugin, 0L, 1L);
                return true;
            }
            case "WindShear:r_click": {
                Vector fwd = p.getLocation().getDirection().normalize();
                p.setVelocity(fwd.multiply(range * 0.65).setY(0.25));
                applyEffects(p, efx);
                new BukkitRunnable() {
                    int t = 0;
                    @Override public void run() {
                        if (t++ >= 10) { cancel(); return; }
                        ParticleUtils.spawnBurst("CLOUD", p.getLocation(), 5, 0.2, 0.2);
                        for (Entity e : p.getWorld().getNearbyEntities(p.getLocation(), 1.5, 1.5, 1.5)) {
                            if (e instanceof LivingEntity le && le != p) {
                                le.damage(2.0, p);
                                le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 1, false, false));
                            }
                        }
                    }
                }.runTaskTimer(plugin, 0L, 1L);
                if (sHit != null) Bukkit.getScheduler().runTaskLater(plugin,
                        () -> SoundUtils.play(sHit, p.getLocation()), 8L);
                return true;
            }
            case "WindShear:ultimate": {
                Location c = p.getLocation().clone();
                SoundUtils.play(sCast != null ? sCast : "ENTITY_LIGHTNING_BOLT_THUNDER", c, 1.0f, 0.7f);
                new BukkitRunnable() {
                    double angle = 0;
                    int t = 0;
                    @Override public void run() {
                        if (t++ >= 60) {
                            if (sEcho != null) SoundUtils.play(sEcho, c, 0.7f, 0.8f);
                            cancel(); return;
                        }
                        angle += Math.PI / 8;
                        double x = Math.cos(angle) * range;
                        double z = Math.sin(angle) * range;
                        Location edge = c.clone().add(x, 0.5, z);
                        edge.getWorld().spawnParticle(Particle.CLOUD, edge, 5, 0.2, 0.3, 0.2, 0.03);
                        for (Entity e : c.getWorld().getNearbyEntities(c, range, range, range)) {
                            if (!(e instanceof LivingEntity le) || le == p) continue;
                            if (t % 8 == 0) {
                                le.damage(dmg / 8.0, p);
                                applyEffects(le, efx);
                            }
                            Vector toCenter = c.toVector().subtract(le.getLocation().toVector());
                            Vector spin = new Vector(-toCenter.getZ(), 0.1, toCenter.getX()).normalize().multiply(0.4);
                            le.setVelocity(le.getVelocity().add(spin));
                        }
                        if (t % 10 == 0 && sHit != null) SoundUtils.play(sHit, c, 0.6f, 0.9f + t * 0.01f);
                    }
                }.runTaskTimer(plugin, 0L, 1L);
                return true;
            }
            default:
                return false;
        }
    }

    // ═══════════════════════ GENERIC SKILL EXEC (fallback) ════════════════

    private void genericSkillExec(Player caster, Entity primaryTarget, String effType,
                                  double damage, double range, String particle,
                                  List<String> efx, String sHit, Location loc) {
        switch (effType.toUpperCase()) {
            case "STRIKE" -> {
                Entity t = (primaryTarget != null) ? primaryTarget : getTargetInLOS(caster, (int) range);
                if (t instanceof LivingEntity le && le != caster) {
                    le.damage(damage, caster);
                    applyEffects(le, efx);
                    ParticleUtils.spawnBurst(particle, le.getLocation().add(0, 1, 0), 25, 0.4, 0.4);
                    if (sHit != null) SoundUtils.play(sHit, le.getLocation());
                }
            }
            case "AOE" -> {
                ParticleUtils.spawnRing(particle, loc, range * 0.9, 30);
                int hits = 0;
                for (Entity e : caster.getWorld().getNearbyEntities(loc, range, range, range)) {
                    if (!(e instanceof LivingEntity le) || le == caster || hits >= 8) continue;
                    hits++;
                    le.damage(damage, caster);
                    applyEffects(le, efx);
                    ParticleUtils.spawnBurst(particle, le.getLocation().add(0, 1, 0), 10, 0.3, 0.3);
                }
                if (sHit != null) SoundUtils.play(sHit, loc);
            }
            case "BLINK" -> {
                Location origin = caster.getLocation().clone();
                caster.setVelocity(caster.getLocation().getDirection().normalize().multiply(range).setY(0.25));
                ParticleUtils.spawnBurst(particle, origin, 30, 0.5, 0.5);
                if (damage > 0) Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    for (Entity e : caster.getWorld().getNearbyEntities(caster.getLocation(), 2.5, 2.5, 2.5)) {
                        if (e instanceof LivingEntity le && le != caster) {
                            le.damage(damage, caster);
                            applyEffects(le, efx);
                        }
                    }
                    ParticleUtils.spawnBurst(particle, caster.getLocation(), 25, 0.8, 0.5);
                    if (sHit != null) SoundUtils.play(sHit, caster.getLocation());
                }, 6L);
            }
            case "AURA" -> {
                applyEffects(caster, efx);
                ParticleUtils.spawnBurst(particle, caster.getLocation().add(0, 1, 0), 40, 0.8, 1.0);
                if (sHit != null) SoundUtils.play(sHit, caster.getLocation());
            }
            case "HEAL" -> {
                double heal = Math.min(damage, 8.0);
                caster.setHealth(Math.min(caster.getMaxHealth(), caster.getHealth() + heal));
                applyEffects(caster, efx);
                ParticleUtils.spawnBurst("HEART", caster.getLocation().add(0, 2, 0), 12, 0.4, 0.4);
                if (sHit != null) SoundUtils.play(sHit, caster.getLocation());
            }
            case "PULL" -> {
                int hits = 0;
                for (Entity e : caster.getWorld().getNearbyEntities(loc, range, range, range)) {
                    if (!(e instanceof LivingEntity le) || le == caster || hits >= 8) continue;
                    hits++;
                    Vector pull = loc.toVector().subtract(le.getLocation().toVector()).normalize().multiply(2.0).setY(0.4);
                    le.setVelocity(pull);
                    le.damage(damage, caster);
                    applyEffects(le, efx);
                }
                ParticleUtils.spawnRing(particle, loc, range * 0.9, 24);
                if (sHit != null) SoundUtils.play(sHit, loc);
            }
            case "LIFESTEAL_AOE" -> {
                double totalHeal = 0;
                int hits = 0;
                for (Entity e : caster.getWorld().getNearbyEntities(loc, range, range, range)) {
                    if (!(e instanceof LivingEntity le) || le == caster || hits >= 8) continue;
                    hits++;
                    le.damage(damage, caster);
                    applyEffects(le, efx);
                    totalHeal += damage * 0.3;
                }
                double heal = Math.min(totalHeal, 8.0);
                caster.setHealth(Math.min(caster.getMaxHealth(), caster.getHealth() + heal));
                ParticleUtils.spawnBurst("HEART", caster.getLocation().add(0, 2, 0), 10, 0.3, 0.3);
            }
        }
    }

    // ═══════════════════════ ACTION BAR HUD ═══════════════════════════════

    private void startActionBarTask() {
        new BukkitRunnable() {
            @Override public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    String wid = getHeldWeaponId(p);
                    if (wid == null) continue;
                    p.sendActionBar(buildCooldownBar(p, wid));
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private String buildCooldownBar(Player p, String wid) {
        long now = System.currentTimeMillis();
        Map<String, Long> cdMap = skillCooldowns.getOrDefault(p.getUniqueId(), Collections.emptyMap());
        String[] slots = {"r_click", "ultimate"};
        String[] icons = {"§a[SKILL]", "§6[ULT]"};
        StringBuilder sb = new StringBuilder();
        double charge = plugin.getChargeManager().getCharge(p);
        int max = plugin.getConfig().getInt("settings.max_charge", 10);
        sb.append("§b✦§f").append((int) charge).append("§7/").append(max).append(" ");
        for (int i = 0; i < slots.length; i++) {
            String key = wid + ":" + slots[i];
            long expiry = cdMap.getOrDefault(key, 0L);
            long left = expiry - now;
            if (i > 0) sb.append(" §8| ");
            if (left > 0) sb.append("§7").append(icons[i]).append(" §c").append(left / 1000L + 1).append("s");
            else sb.append(icons[i]).append(" §aREADY");
        }
        return sb.toString();
    }

    // ═══════════════════════ COOLDOWN HELPERS ════════════════════════════

    private boolean checkSkillCooldown(Player p, String weaponId, String slot) {
        long now = System.currentTimeMillis();
        String key = weaponId + ":" + slot;
        Map<String, Long> cdMap = skillCooldowns.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        long expiry = cdMap.getOrDefault(key, 0L);
        if (expiry > now) {
            long leftSec = (expiry - now) / 1000L + 1;
            String name = plugin.getWeaponsConfig().getString("weapons." + weaponId + "." + slot + ".name", slot);
            p.sendActionBar("§c⏳ " + name + " §7cooldown: §c" + leftSec + "s");
            return false;
        }
        return true;
    }

    private void setSkillCooldown(Player p, String weaponId, String slot, long durationMs) {
        skillCooldowns.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>())
                .put(weaponId + ":" + slot, System.currentTimeMillis() + durationMs);
    }

    // ═══════════════════════ POTION UTILS ════════════════════════════════

    private PotionEffectType getEffType(String name) {
        try {
            PotionEffectType t = Registry.EFFECT.get(NamespacedKey.minecraft(name.toLowerCase()));
            if (t != null) return t;
        } catch (Exception ignored) {}
        return switch (name.toUpperCase()) {
            case "SLOWNESS" -> PotionEffectType.SLOWNESS;
            case "WEAKNESS" -> PotionEffectType.WEAKNESS;
            case "BLINDNESS" -> PotionEffectType.BLINDNESS;
            case "WITHER" -> PotionEffectType.WITHER;
            case "POISON" -> PotionEffectType.POISON;
            case "LEVITATION" -> PotionEffectType.LEVITATION;
            case "GLOWING" -> PotionEffectType.GLOWING;
            case "NAUSEA" -> PotionEffectType.NAUSEA;
            case "HUNGER" -> PotionEffectType.HUNGER;
            case "MINING_FATIGUE" -> PotionEffectType.MINING_FATIGUE;
            case "RESISTANCE" -> PotionEffectType.RESISTANCE;
            case "FIRE_RESISTANCE" -> PotionEffectType.FIRE_RESISTANCE;
            case "SPEED" -> PotionEffectType.SPEED;
            case "STRENGTH" -> PotionEffectType.STRENGTH;
            case "JUMP_BOOST" -> PotionEffectType.JUMP_BOOST;
            case "SLOW_FALLING" -> PotionEffectType.SLOW_FALLING;
            case "REGENERATION" -> PotionEffectType.REGENERATION;
            case "ABSORPTION" -> PotionEffectType.ABSORPTION;
            case "HASTE" -> PotionEffectType.HASTE;
            case "DARKNESS" -> PotionEffectType.DARKNESS;
            default -> null;
        };
    }

    private void applyEffects(LivingEntity target, List<String> effects) {
        for (String eff : effects) {
            String[] parts = eff.split(":");
            if (parts.length < 1) continue;
            PotionEffectType type = getEffType(parts[0]);
            if (type == null) continue;
            int amp = parts.length > 1 ? Math.max(0, Integer.parseInt(parts[1]) - 1) : 0;
            int dur = parts.length > 2 ? Math.min(Integer.parseInt(parts[2]), 200) : 100;
            target.addPotionEffect(new PotionEffect(type, dur, amp, false, true, true));
        }
    }

    private void applyEffects(Player p, List<String> effects) {
        applyEffects((LivingEntity) p, effects);
    }

    // ═══════════════════════ MISC HELPERS ════════════════════════════════

    private String getHeldWeaponId(Player p) {
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType().isAir()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer()
                .get(plugin.getWeaponKey(), PersistentDataType.STRING);
    }

    private boolean isWorldDisabled(Player p) {
        return plugin.getConfig().getStringList("disabled_worlds").contains(p.getWorld().getName());
    }

    private boolean isWeaponEnabled(String id) {
        return plugin.getWeaponsConfig().getBoolean("weapons." + id + ".enabled", true);
    }

    private Entity getNearestEnemy(Player p, int range) {
        Entity closest = null;
        double minDist = Double.MAX_VALUE;
        for (Entity e : p.getWorld().getNearbyEntities(p.getLocation(), range, range, range)) {
            if (!(e instanceof LivingEntity) || e == p) continue;
            double d = e.getLocation().distanceSquared(p.getLocation());
            if (d < minDist) { minDist = d; closest = e; }
        }
        return closest;
    }

    private LivingEntity getNearestEnemyFrom(Location loc, Player exclude, int range, List<LivingEntity> excludeList) {
        LivingEntity closest = null;
        double minDist = Double.MAX_VALUE;
        for (Entity e : loc.getWorld().getNearbyEntities(loc, range, range, range)) {
            if (!(e instanceof LivingEntity le) || le == exclude || excludeList.contains(le)) continue;
            double d = e.getLocation().distanceSquared(loc);
            if (d < minDist) { minDist = d; closest = le; }
        }
        return closest;
    }

    private Entity getTargetInLOS(Player caster, int range) {
        for (Entity e : caster.getNearbyEntities(range, range, range)) {
            if (!(e instanceof LivingEntity) || e == caster) continue;
            Vector toEnt = e.getLocation().toVector()
                    .subtract(caster.getLocation().toVector()).normalize();
            if (toEnt.dot(caster.getLocation().getDirection()) > 0.6) return e;
        }
        return null;
    }
}

