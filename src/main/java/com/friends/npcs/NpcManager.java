package com.friends.npcs;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Pose;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

public class NpcManager {

    public static NamespacedKey KEY;

    public static final Map<String, Pose> POSES = new LinkedHashMap<>();
    public static final Map<String, NamedTextColor> COLORS = new LinkedHashMap<>();
    private static final List<String> RAINBOW =
            List.of("rojo", "naranja", "amarillo", "verde", "celeste", "azul", "morado", "rosa");

    static {
        POSES.put("normal", Pose.STANDING);
        POSES.put("dormir", Pose.SLEEPING);
        POSES.put("nadar", Pose.SWIMMING);
        POSES.put("agachado", Pose.CROUCHING);
        POSES.put("volar", Pose.FALL_FLYING);
        POSES.put("girar", Pose.SPIN_ATTACK);

        COLORS.put("blanco", NamedTextColor.WHITE);
        COLORS.put("gris", NamedTextColor.GRAY);
        COLORS.put("gris_oscuro", NamedTextColor.DARK_GRAY);
        COLORS.put("negro", NamedTextColor.BLACK);
        COLORS.put("rojo", NamedTextColor.RED);
        COLORS.put("rojo_oscuro", NamedTextColor.DARK_RED);
        COLORS.put("naranja", NamedTextColor.GOLD);
        COLORS.put("amarillo", NamedTextColor.YELLOW);
        COLORS.put("verde", NamedTextColor.GREEN);
        COLORS.put("verde_oscuro", NamedTextColor.DARK_GREEN);
        COLORS.put("celeste", NamedTextColor.AQUA);
        COLORS.put("turquesa", NamedTextColor.DARK_AQUA);
        COLORS.put("azul", NamedTextColor.BLUE);
        COLORS.put("azul_oscuro", NamedTextColor.DARK_BLUE);
        COLORS.put("rosa", NamedTextColor.LIGHT_PURPLE);
        COLORS.put("morado", NamedTextColor.DARK_PURPLE);
    }

    private final FriendsNPCs plugin;
    private final Map<String, Npc> npcs = new LinkedHashMap<>();
    private final File file;
    private final Random random = new Random();
    private int rainbowIndex = 0;

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

    /** Vuelve a aplicar todo (nombre, holograma, pose, glow...) y guarda. */
    public void refresh(Npc n) {
        apply(n);
        rebuildHolo(n);
        save();
    }

    public void rename(Npc n, String name) {
        n.name = name;
        refresh(n);
    }

    public void move(Npc n, Location l) {
        n.home = l.clone();
        n.worldName = l.getWorld().getName();
        removeHolo(n); // un vehiculo con pasajero no se puede teletransportar
        if (n.brain != null) n.brain.teleport(l);
        if (n.body != null) n.body.teleport(l);
        rebuildHolo(n);
        save();
    }

    public void setMode(Npc n, String mode) {
        n.mode = mode;
        refresh(n);
    }

    public void reload() {
        removeAll();
        load();
    }

    /** Pide la skin a Mojang y la guarda. done recibe null si salio bien, o el error. */
    public void setSkin(Npc n, String skin, Consumer<String> done) {
        PlayerProfile pp = Bukkit.createProfile(skin);
        pp.update().whenComplete((res, ex) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (ex != null || res == null) {
                done.accept("No se pudo consultar la skin (revisa el nombre o la conexion).");
                return;
            }
            ProfileProperty tex = null;
            for (ProfileProperty p : res.getProperties()) {
                if (p.getName().equals("textures")) tex = p;
            }
            if (tex == null) {
                done.accept("Skin no encontrada: " + skin);
                return;
            }
            n.skinName = res.getName() != null ? res.getName() : skin;
            n.skinValue = tex.getValue();
            n.skinSignature = tex.getSignature();
            applySkin(n);
            save();
            done.accept(null);
        }));
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
                m.setDescription(null);
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
        rebuildHolo(n);
    }

    void apply(Npc n) {
        if (n.body == null) return;
        n.body.customName(FriendsNPCs.rich(n.name));
        n.body.setCustomNameVisible(n.lines.isEmpty());

        Mob m = n.mover();
        if (m != null) m.setAI(!n.mode.equals("quieto") && !n.pose.equals("dormir"));

        n.body.setPose(POSES.getOrDefault(n.pose, Pose.STANDING), !n.pose.equals("normal"));
        n.body.setVisualFire(n.effects.contains("fuego"));
        n.body.setInvisible(n.effects.contains("invisible"));

        applyGlow(n);
        applySkin(n);
    }

    void applySkin(Npc n) {
        if (!n.isPlayer() || n.skinName == null || !(n.body instanceof Mannequin m)) return;
        ResolvableProfile.Builder b = ResolvableProfile.resolvableProfile().name(n.skinName);
        if (n.skinValue != null) {
            b.addProperty(new ProfileProperty("textures", n.skinValue, n.skinSignature));
        }
        m.setProfile(b.build());
    }

    // ---------- Glow ----------

    private Scoreboard board() {
        return Bukkit.getScoreboardManager().getMainScoreboard();
    }

    private Team team(String color) {
        Scoreboard sb = board();
        Team t = sb.getTeam("fnpc_" + color);
        if (t == null) {
            t = sb.registerNewTeam("fnpc_" + color);
            t.color(COLORS.get(color));
            t.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        }
        return t;
    }

    private void removeFromTeams(Entity e) {
        if (e == null) return;
        String entry = e.getUniqueId().toString();
        for (Team t : board().getTeams()) {
            if (t.getName().startsWith("fnpc_")) t.removeEntry(entry);
        }
    }

    void applyGlow(Npc n) {
        if (n.body == null) return;
        removeFromTeams(n.body);
        if (n.glow.equals("off")) {
            n.body.setGlowing(false);
            return;
        }
        n.body.setGlowing(true);
        String color = n.glow.equals("arcoiris") ? RAINBOW.get(rainbowIndex % RAINBOW.size()) : n.glow;
        if (COLORS.containsKey(color)) team(color).addEntry(n.body.getUniqueId().toString());
    }

    // ---------- Holograma ----------

    void removeHolo(Npc n) {
        if (n.holo != null) n.holo.remove();
        n.holo = null;
    }

    void rebuildHolo(Npc n) {
        removeHolo(n);
        Mob carrier = n.mover();
        if (carrier == null || !carrier.isValid() || n.lines.isEmpty()) return;

        List<Component> comps = new ArrayList<>();
        for (String l : n.lines) comps.add(FriendsNPCs.rich(l));
        Component text = Component.join(JoinConfiguration.newlines(), comps);
        float lift = (float) (0.15 + n.lines.size() * 0.13
                + plugin.getConfig().getDouble("holograma-ajuste", 0.0));

        n.holo = carrier.getWorld().spawn(carrier.getLocation(), TextDisplay.class, d -> {
            d.text(text);
            d.setBillboard(Display.Billboard.CENTER);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setLineWidth(1000);
            d.setViewRange(0.6f);
            d.setPersistent(false);
            d.setTransformation(new Transformation(
                    new Vector3f(0f, lift, 0f), new AxisAngle4f(),
                    new Vector3f(1f, 1f, 1f), new AxisAngle4f()));
            d.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, n.id);
        });
        carrier.addPassenger(n.holo); // va montado: se mueve con el NPC sin lag
    }

    void kill(Npc n) {
        removeHolo(n);
        removeFromTeams(n.body);
        if (n.body != null) n.body.remove();
        if (n.brain != null) n.brain.remove();
        n.body = null;
        n.brain = null;
    }

    boolean alive(Npc n) {
        if (n.body == null || !n.body.isValid()) return false;
        if (n.isPlayer() && (n.brain == null || !n.brain.isValid())) return false;
        return n.lines.isEmpty() || (n.holo != null && n.holo.isValid());
    }

    public void removeAll() {
        for (Npc n : npcs.values()) kill(n);
    }

    /** Limpia entidades y equipos que hayan quedado de un cierre mal hecho. */
    public void cleanOrphans() {
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e.getPersistentDataContainer().has(KEY, PersistentDataType.STRING)) e.remove();
            }
        }
        for (Team t : new ArrayList<>(board().getTeams())) {
            if (t.getName().startsWith("fnpc_")) t.unregister();
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
                if (!n.mode.equals("deambular") || n.pose.equals("dormir") || !alive(n)) continue;
                Mob m = n.mover();
                if (m != null) m.getPathfinder().moveTo(randomPoint(n));
            }
        }, 40L, 80L);

        // Cada segundo: arcoiris
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            rainbowIndex++;
            for (Npc n : npcs.values()) {
                if (n.glow.equals("arcoiris") && alive(n)) applyGlow(n);
            }
        }, 20L, 20L);
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
            y.set(p + "lineas", n.lines);
            y.set(p + "pose", n.pose);
            y.set(p + "glow", n.glow);
            y.set(p + "efectos", new ArrayList<>(n.effects));
            if (n.skinName != null) {
                y.set(p + "skin.nombre", n.skinName);
                y.set(p + "skin.value", n.skinValue);
                y.set(p + "skin.signature", n.skinSignature);
            }
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
            World w = Bukkit.getWorld(worldName);
            Location l = new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                    (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
            Npc n = new Npc(id, s.getString("type", "PLAYER"), s.getString("name", id), l, worldName);
            n.mode = s.getString("mode", "deambular");
            n.lines = new ArrayList<>(s.getStringList("lineas"));
            n.pose = s.getString("pose", "normal");
            n.glow = s.getString("glow", "off");
            n.effects = new java.util.HashSet<>(s.getStringList("efectos"));
            n.skinName = s.getString("skin.nombre");
            n.skinValue = s.getString("skin.value");
            n.skinSignature = s.getString("skin.signature");
            npcs.put(id, n);
            if (w != null) spawn(n);
        }
        plugin.getLogger().info("NPCs cargados: " + npcs.size());
    }
}
