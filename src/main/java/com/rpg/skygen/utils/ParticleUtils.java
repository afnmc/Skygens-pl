package com.rpg.skygen.utils;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;

public class ParticleUtils {

    private ParticleUtils() {}

    public static void spawnBurst(String particleName, Location loc, int count, double spreadXZ, double spreadY) {
        if (loc.getWorld() == null) return;
        Particle particle = parseParticle(particleName);
        if (particle == Particle.DUST) {
            Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(100, 200, 255), 1.5f);
            loc.getWorld().spawnParticle(particle, loc, count, spreadXZ, spreadY, spreadXZ, 0.05, dust);
        } else {
            loc.getWorld().spawnParticle(particle, loc, count, spreadXZ, spreadY, spreadXZ, 0.05);
        }
    }

    public static void spawnRing(String particleName, Location center, double radius, int points) {
        if (center.getWorld() == null) return;
        Particle particle = parseParticle(particleName);
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            Location pt = new Location(center.getWorld(), x, center.getY() + 0.1, z);
            if (particle == Particle.DUST) {
                Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(100, 200, 255), 1.2f);
                center.getWorld().spawnParticle(particle, pt, 2, 0, 0, 0, 0, dust);
            } else {
                center.getWorld().spawnParticle(particle, pt, 2, 0, 0, 0, 0.01);
            }
        }
    }

    public static void spawnAmbient(String particleName, Location loc) {
        spawnBurst(particleName, loc.clone().add(0, 1, 0), 4, 0.4, 0.4);
    }

    private static Particle parseParticle(String name) {
        try {
            return Particle.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Particle.SMOKE;
        }
    }
}
