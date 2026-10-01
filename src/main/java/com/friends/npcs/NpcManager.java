package com.friends.npcs;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public class NpcManager {

    public static NamespacedKey KEY;

    private final FriendsNPCs plugin;
    private final Map<String, Npc> npcs = new LinkedHashMap<>();
    private final File file;
    private final Random random = new Random();

    public NpcManager(FriendsNPCs plugin) {
        this.plugin = plugin;
        KEY = new NamespacedKey(plugin, "fnpc_id");
        this.file = new File(plugin.getDataFolder(), "npcs.yml");
    }

    public Collection<Npc> all() {
        return npcs.values();
    }

    public Npc get(String id) {
        return npcs.get(id.toLowerCase());
    }

    // ---------- Crear / borrar ----------

    /** Devuelve un mensaje de error, o null si salio bien. */
    public String create(String id, String type, String name, Location loc) {
        id = id.toLowerCase();
        if (!id.matches("[a-z0-9_\\-]{1,32}")) {
            return "El ID solo puede tener letras, numeros, _ y - (max 32).";
        }
        if (npcs.containsKey(id)) return "Ya existe un NPC con ese ID.";

        String t = type.toUpperCase();
        if (!t.equals("PLAYER")) {
            EntityType et;
            try {
                et = EntityType.valueOf(t);
            } catch (IllegalArgumentException e) {
                return "Tipo invalido: " + type;
            }
            Class<? extends Entity> cls = et.getEntityClass();
            if (cls == null || !et.isSpawnable() || !Mob.class.isAssignableFrom(cls)) {
                return "Ese tipo no se puede usar como NPC.";
            }
        }

        Npc n = new Npc(id, t, name, loc.clone(), loc.getWorld().getName());
        npcs.put(id, n);
        spawn(n);
        save();
        return null;
    }

    public boolean delete(String id) {
        Npc n = npcs.remove(id.toLowerCase());
        if (n == null) return false;
        kill(n);
        save();
        return true;
    }

    // ---------- Edicion ----------

    public void rename(Npc n, String name) {
        n.name = name;
        apply(n);
        save();
    }

    public void move(Npc n, Location l) {
        n.home = l.clone();
        n.worldName = l.getWorld().getName();
        if (n.brain != null) n.brain.teleport(l);
        if (n.body != null) n.body.teleport(l);
        save();
    }

    public void setMode(Npc n, String mode) {
        n.mode = mode;
        apply(n);
        save();
    }

    public void reload() {
        removeAll();
        load();
    }

    // ---------- Entidades ----------

    void spawn(Npc n) {
        World w = n.home.getWorld();
        if (w == null) return;

        if (n.isPlayer()) {
            n.brain = w.spawn(n.home, Zombie.class, z -> {
                z.setInvisible(true);
                z.setSilent(true);
                z.setInvulnerable(true);
                z.setPersistent(false);
                z.setRemoveWhenFarAway(false);
                z.setShouldBurnInDay(false);
                z.setCanPickupItems(false);
                z.setBaby(false);
                z.getEquipment().clear();
                z.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, n.id);
            });
            n.body = w.spawn(n.home, Mannequin.class, m -> {
                m.setInvulnerable(true);
                m.setPersistent(false);
                m.setSilent(true);
                m.setDescription(null); // quita el texto "NPC" de abajo
                m.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, n.id);
            });
        } else {
            Entity e = w.spawnEntity(n.home, EntityType.valueOf(n.type));
            if (e instanceof LivingEntity le) {
                n.body = le;
                le.setInvulnerable(true);
                le.setPersistent(false);
                le.setRemoveWhenFarAway(false);
                le.setSilent(true);
                le.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, n.id);
                le.addPotionEffect(new PotionEffect(
                        PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false));
            } else {
                e.remove();
            }
        }
        apply(n);
    }

    void apply(Npc n) {
        if (n.body == null) return;
        n.body.customName(FriendsNPCs.c(n.name));
        n.body.setCustomNameVisible(true);
        Mob m = n.mover();
        if (m != null) m.setAI(!n.mode.equals("quieto"));
    }

    void kill(Npc n) {
        if (n.body != null) n.body.remove();
        if (n.brain != null) n.brain.remove();
        n.body = null;
        n.brain = null;
    }

    boolean alive(Npc n) {
        if (n.body == null || !n.body.isValid()) return false;
        return !n.isPlayer() || (n.brain != null && n.brain.isValid());
    }

    public void removeAll() {
        for (Npc n : npcs.values()) kill(n);
    }

    /** Borra entidades NPC que hayan quedado de un cierre mal hecho. */
    public void cleanOrphans() {
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e.getPersistentDataContainer().has(KEY, PersistentDataType.STRING)) e.remove();
            }
        }
    }

    // ---------- Tareas ----------

    public void startTasks() {
        // Cada tick: el cuerpo copia al cerebro y se reaparecen los NPC caidos
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Npc n : npcs.values()) {
                if (n.home.getWorld() == null) {
                    World w = Bukkit.getWorld(n.worldName);
                    if (w == null) continue;
                    n.home.setWorld(w);
                }
                if (!alive(n)) {
                    long now = System.currentTimeMillis();
                    if (n.home.isChunkLoaded() && now - n.lastTry > 3000) {
                        n.lastTry = now;
                        kill(n);
                        spawn(n);
                    }
                    continue;
                }
                if (n.isPlayer()) n.body.teleport(n.brain.getLocation());
            }
        }, 20L, 1L);

        // Cada 4 segundos: nuevo destino para los que deambulan
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Npc n : npcs.values()) {
                if (!n.mode.equals("deambular") || !alive(n)) continue;
                Mob m = n.mover();
                if (m != null) m.getPathfinder().moveTo(randomPoint(n));
            }
        }, 40L, 80L);
    }

    private Location randomPoint(Npc n) {
        int r = plugin.getConfig().getInt("deambular-radio", 8);
        double x = n.home.getX() + random.nextInt(r * 2 + 1) - r;
        double z = n.home.getZ() + random.nextInt(r * 2 + 1) - r;
        World w = n.home.getWorld();
        double y = n.home.getY();
        if (w.getEnvironment() == World.Environment.NORMAL) {
            int hy = w.getHighestBlockYAt((int) x, (int) z) + 1;
            if (Math.abs(hy - y) <= 4) y = hy;
        }
        return new Location(w, x, y, z);
    }

    // ---------- Guardado ----------

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Npc n : npcs.values()) {
            String p = "npcs." + n.id + ".";
            y.set(p + "type", n.type);
            y.set(p + "name", n.name);
            y.set(p + "mode", n.mode);
            y.set(p + "world", n.worldName);
            y.set(p + "x", n.home.getX());
            y.set(p + "y", n.home.getY());
            y.set(p + "z", n.home.getZ());
            y.set(p + "yaw", n.home.getYaw());
            y.set(p + "pitch", n.home.getPitch());
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo guardar npcs.yml: " + e.getMessage());
        }
    }

    public void load() {
        npcs.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("npcs");
        if (sec == null) return;

        for (String id : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(id);
            if (s == null) continue;
            String worldName = s.getString("world", "world");
            World w = Bukkit.getWorld(worldName); // puede ser null si el mundo carga despues
            Location l = new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                    (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
            Npc n = new Npc(id, s.getString("type", "PLAYER"), s.getString("name", id), l, worldName);
            n.mode = s.getString("mode", "deambular");
            npcs.put(id, n);
            if (w != null) spawn(n);
        }
        plugin.getLogger().info("NPCs cargados: " + npcs.size());
    }
}
