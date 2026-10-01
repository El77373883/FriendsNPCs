package com.friends.npcs;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

public class NpcBehavior {

    private final FriendsNPCs plugin;
    private final NpcManager mgr;

    public NpcBehavior(FriendsNPCs plugin, NpcManager mgr) {
        this.plugin = plugin;
        this.mgr = mgr;
    }

    // ---------- Utilidades de ruta ----------

    public static String format(Location l) {
        return String.format(Locale.US, "%s,%.2f,%.2f,%.2f",
                l.getWorld().getName(), l.getX(), l.getY(), l.getZ());
    }

    public static Location parse(String s) {
        String[] p = s.split(",");
        if (p.length < 4) return null;
        World w = Bukkit.getWorld(p[0]);
        if (w == null) return null;
        try {
            return new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static void resetPath(Npc n) {
        n.pathIndex = 0;
        n.pathDir = 1;
        n.pathDone = false;
        n.waitUntil = 0;
        n.lastDist = Double.MAX_VALUE;
        n.stuckSince = System.currentTimeMillis();
    }

    private void advance(Npc n) {
        int size = n.path.size();
        n.lastDist = Double.MAX_VALUE;
        n.stuckSince = System.currentTimeMillis();
        if (size <= 1) {
            n.pathIndex = 0;
            return;
        }
        switch (n.pathMode) {
            case "pingpong" -> {
                int next = n.pathIndex + n.pathDir;
                if (next >= size) {
                    n.pathDir = -1;
                    next = size - 2;
                } else if (next < 0) {
                    n.pathDir = 1;
                    next = 1;
                }
                n.pathIndex = next;
            }
            case "una_vez" -> {
                if (n.pathIndex >= size - 1) n.pathDone = true;
                else n.pathIndex++;
            }
            default -> n.pathIndex = (n.pathIndex + 1) % size;
        }
    }

    // ---------- Animaciones ----------

    public static void play(Plugin plugin, Npc n, String anim) {
        if (n.body == null || !n.body.isValid()) return;
        switch (anim) {
            case "golpear" -> n.body.swingMainHand();
            case "saludar" -> {
                for (int i = 0; i < 3; i++) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (n.body != null && n.body.isValid()) n.body.swingMainHand();
                    }, i * 8L);
                }
            }
            default -> { }
        }
    }

    // ---------- Tareas ----------

    public void start() {
        // Cada medio segundo: ruta, seguir, ir a un punto
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Npc n : mgr.all()) {
                if (!mgr.alive(n)) continue;
                Mob m = n.mover();
                if (m == null) continue;
                if (n.gotoTarget != null) {
                    goTo(n, m, now);
                    continue;
                }
                if (n.pose.equals("dormir")) continue;
                switch (n.mode) {
                    case "patrullar" -> patrol(n, m, now);
                    case "seguir" -> follow(m);
                    default -> { }
                }
            }
        }, 20L, 10L);

        // Cada 2 ticks: mirar al jugador cercano
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            double r = plugin.getConfig().getDouble("mirar-radio", 8);
            for (Npc n : mgr.all()) {
                if (!n.look || n.pose.equals("dormir") || !mgr.alive(n)) continue;
                Mob m = n.mover();
                if (m == null || m.getPathfinder().hasPath()) continue;
                Player p = nearest(m, r);
                if (p == null) continue;
                Location from = m.getEyeLocation();
                Location to = p.getEyeLocation();
                double dx = to.getX() - from.getX();
                double dy = to.getY() - from.getY();
                double dz = to.getZ() - from.getZ();
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                m.setRotation(yaw, pitch);
            }
        }, 20L, 2L);

        // Cada segundo: animaciones repetidas
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Npc n : mgr.all()) {
                if (n.anim.equals("off") || n.animEvery <= 0 || !mgr.alive(n)) continue;
                if (now - n.lastAnim >= n.animEvery * 1000L) {
                    n.lastAnim = now;
                    play(plugin, n, n.anim);
                }
            }
        }, 20L, 20L);
    }

    private Player nearest(Mob m, double radius) {
        Player best = null;
        double bd = radius * radius;
        for (Player p : m.getWorld().getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            double d = p.getLocation().distanceSquared(m.getLocation());
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    private void goTo(Npc n, Mob m, long now) {
        Location t = n.gotoTarget;
        if (t.getWorld() != m.getWorld() || now > n.gotoUntil
                || m.getLocation().distanceSquared(t) < 2.25) {
            n.gotoTarget = null;
            m.getPathfinder().stopPathfinding();
            mgr.apply(n); // restaura la IA segun el modo
            return;
        }
        m.getPathfinder().moveTo(t, 1.0);
    }

    private void patrol(Npc n, Mob m, long now) {
        if (n.path.isEmpty() || !n.pathRunning || n.pathDone) return;
        if (now < n.waitUntil) return;
        if (n.pathIndex >= n.path.size()) n.pathIndex = 0;

        Location t = parse(n.path.get(n.pathIndex));
        if (t == null || t.getWorld() != m.getWorld()) {
            advance(n);
            return;
        }
        double d = m.getLocation().distance(t);
        if (d < 1.5) {
            m.getPathfinder().stopPathfinding();
            n.waitUntil = now + n.pathWait * 1000L;
            advance(n);
            return;
        }
        m.getPathfinder().moveTo(t, n.pathSpeed);

        // Si lleva 6 segundos sin acercarse, pasa al siguiente punto
        if (d < n.lastDist - 0.3) {
            n.lastDist = d;
            n.stuckSince = now;
        } else if (now - n.stuckSince > 6000) {
            advance(n);
        }
    }

    private void follow(Mob m) {
        double r = plugin.getConfig().getDouble("seguir-radio", 20);
        Player p = nearest(m, r);
        if (p == null) {
            m.getPathfinder().stopPathfinding();
            return;
        }
        if (p.getLocation().distanceSquared(m.getLocation()) > 9) {
            m.getPathfinder().moveTo(p.getLocation(), 1.0);
        } else {
            m.getPathfinder().stopPathfinding();
        }
    }
}
