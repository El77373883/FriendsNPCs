package com.friends.npcs;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.friends.npcs.FriendsNPCs.c;

/**
 * Parte 6: policia, arrestos, carcel automatica y trabajo forzado (minar).
 * Comandos: /arrestar, /liberar, /carcel. Permiso: fnpc.policia
 */
public class PoliceManager implements Listener, CommandExecutor, TabCompleter {

    private static class Sentence {
        long until;  // epoch ms
        int quota;   // bloques que aun debe picar
        Sentence(long until, int quota) {
            this.until = until;
            this.quota = quota;
        }
    }

    private final FriendsNPCs plugin;
    private final Eco eco = new Eco();
    private final File file;
    private final Map<UUID, Sentence> jailed = new HashMap<>();
    private Location jail;
    private boolean dirty;

    public PoliceManager(FriendsNPCs plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "presos.yml");
    }

    // ---------- Arranque ----------

    public void start() {
        load();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (String name : new String[]{"arrestar", "liberar", "carcel"}) {
            PluginCommand pc = plugin.getCommand(name);
            if (pc != null) {
                pc.setExecutor(this);
                pc.setTabCompleter(this);
            }
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public boolean isJailed(Player p) {
        return jailed.containsKey(p.getUniqueId());
    }

    // ---------- Config ----------

    private double radius() {
        return plugin.getConfig().getDouble("policia.radio-carcel", 15);
    }

    private int quotaDefault() {
        return plugin.getConfig().getInt("policia.cuota-bloques", 20);
    }

    private int regenSeconds() {
        return plugin.getConfig().getInt("policia.regenerar-segundos", 10);
    }

    private Set<Material> workBlocks() {
        List<String> names = plugin.getConfig().getStringList("policia.bloques-trabajo");
        if (names.isEmpty()) {
            names = List.of("STONE", "COBBLESTONE", "DEEPSLATE", "ANDESITE", "DIORITE", "GRANITE");
        }
        Set<Material> out = new HashSet<>();
        for (String n : names) {
            Material m = Material.matchMaterial(n);
            if (m != null) out.add(m);
        }
        return out;
    }

    private Set<String> allowedCommands() {
        List<String> l = plugin.getConfig().getStringList("policia.comandos-permitidos");
        if (l.isEmpty()) l = List.of("msg", "tell", "r");
        Set<String> out = new HashSet<>();
        for (String s : l) out.add(s.toLowerCase());
        return out;
    }

    // ---------- Guardado ----------

    private void load() {
        jailed.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection cs = y.getConfigurationSection("carcel");
        if (cs != null) {
            World w = Bukkit.getWorld(cs.getString("world", "world"));
            if (w != null) {
                jail = new Location(w, cs.getDouble("x"), cs.getDouble("y"), cs.getDouble("z"),
                        (float) cs.getDouble("yaw"), (float) cs.getDouble("pitch"));
            }
        }
        ConfigurationSection ps = y.getConfigurationSection("presos");
        if (ps == null) return;
        for (String k : ps.getKeys(false)) {
            try {
                jailed.put(UUID.fromString(k),
                        new Sentence(ps.getLong(k + ".hasta"), ps.getInt(k + ".cuota")));
            } catch (IllegalArgumentException ignored) { }
        }
    }

    public void save() {
        dirty = false;
        YamlConfiguration y = new YamlConfiguration();
        if (jail != null && jail.getWorld() != null) {
            y.set("carcel.world", jail.getWorld().getName());
            y.set("carcel.x", jail.getX());
            y.set("carcel.y", jail.getY());
            y.set("carcel.z", jail.getZ());
            y.set("carcel.yaw", jail.getYaw());
            y.set("carcel.pitch", jail.getPitch());
        }
        for (Map.Entry<UUID, Sentence> en : jailed.entrySet()) {
            String p = "presos." + en.getKey() + ".";
            y.set(p + "hasta", en.getValue().until);
            y.set(p + "cuota", en.getValue().quota);
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo guardar presos.yml: " + e.getMessage());
        }
    }

    // ---------- Arrestar / liberar ----------

    public String arrest(Player target, int minutes, double fine) {
        if (jail == null || jail.getWorld() == null) return "Aun no hay carcel. Usa /carcel set.";
        if (isJailed(target)) return "Ese jugador ya esta preso.";
        jailed.put(target.getUniqueId(),
                new Sentence(System.currentTimeMillis() + minutes * 60_000L, quotaDefault()));
        save();
        target.teleportAsync(jail);
        target.sendMessage(c("&c&lFuiste arrestado. &7Condena: &e" + minutes + " min &7y &e"
                + quotaDefault() + " bloques &7de trabajo forzado."));
        target.sendMessage(c("&7Pica piedra dentro de la carcel para cumplir tu cuota."));

        if (fine > 0 && eco.available()) {
            if (eco.has(target, fine) && eco.withdraw(target, fine)) {
                target.sendMessage(c("&cMulta: &e" + eco.format(fine)));
            } else {
                target.sendMessage(c("&7No pudiste pagar la multa de &e" + eco.format(fine) + "&7."));
            }
        }
        return null;
    }

    private void release(Player p, String msg) {
        jailed.remove(p.getUniqueId());
        save();
        p.teleportAsync(Bukkit.getWorlds().get(0).getSpawnLocation());
        p.sendMessage(c(msg));
    }

    // ---------- Tarea cada segundo ----------

    private void tick() {
        long now = System.currentTimeMillis();
        for (Player p : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            Sentence s = jailed.get(p.getUniqueId());
            if (s == null) continue;
            long left = s.until - now;
            if (left <= 0 && s.quota <= 0) {
                release(p, "&aCumpliste tu condena. Eres libre.");
                continue;
            }
            String t = left > 0 ? "&eTiempo: &f" + fmt(left / 1000) : "&eTiempo: &aCumplido";
            String q = s.quota > 0 ? " &7| &eBloques: &f" + s.quota : "";
            p.sendActionBar(c(t + q));
        }
        if (dirty) save();
    }

    private static String fmt(long sec) {
        return (sec / 60) + "m " + String.format("%02d", sec % 60) + "s";
    }

    // ---------- Eventos ----------

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!isJailed(p) || jail == null) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) p.teleportAsync(jail);
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (isJailed(e.getPlayer())) save();
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (isJailed(e.getPlayer()) && jail != null) e.setRespawnLocation(jail);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!e.hasChangedBlock() || jail == null) return;
        Player p = e.getPlayer();
        if (!isJailed(p)) return;
        double r = radius();
        Location to = e.getTo();
        if (to.getWorld() == jail.getWorld() && to.distanceSquared(jail) <= r * r) return;
        Location from = e.getFrom();
        if (from.getWorld() != jail.getWorld() || from.distanceSquared(jail) > r * r) {
            p.teleportAsync(jail); // estaba fuera (por ejemplo, tras morir lejos)
        } else {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (!isJailed(e.getPlayer())) return;
          String cause = e.getCause().name();
        if (cause.equals("ENDER_PEARL") || cause.equals("CHORUS_FRUIT") || cause.equals("COMMAND")) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!isJailed(e.getPlayer())) return;
        String m = e.getMessage().substring(1).split("\\s+")[0].toLowerCase();
        if (m.contains(":")) m = m.substring(m.indexOf(':') + 1);
        if (allowedCommands().contains(m)) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(c("&cNo puedes usar comandos estando preso."));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (isJailed(e.getPlayer())) e.setCancelled(true);
    }

    /** Minar: solo bloques de trabajo dentro de la carcel; no dan drops y se regeneran. */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Sentence s = jailed.get(p.getUniqueId());
        if (s == null) return;

        Block b = e.getBlock();
        double r = radius();
        boolean inZone = jail != null && b.getWorld() == jail.getWorld()
                && b.getLocation().distanceSquared(jail) <= r * r;
        if (!inZone || !workBlocks().contains(b.getType())) {
            e.setCancelled(true);
            p.sendMessage(c("&cEstando preso solo puedes picar piedra dentro de la carcel."));
            return;
        }

        e.setDropItems(false);
        e.setExpToDrop(0);
        if (s.quota > 0) {
            s.quota--;
            dirty = true;
            if (s.quota == 0) p.sendMessage(c("&aCuota de trabajo completada."));
        }

        BlockData data = b.getBlockData();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (b.getType().isAir()) b.setBlockData(data, false);
        }, Math.max(1, regenSeconds()) * 20L);
    }

    // ---------- Comandos ----------

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!s.hasPermission("fnpc.policia")) {
            s.sendMessage(c("&cNo tienes permiso."));
            return true;
        }
        switch (cmd.getName().toLowerCase()) {
            case "arrestar" -> arrestarCmd(s, a);
            case "liberar" -> liberarCmd(s, a);
            case "carcel" -> carcelCmd(s, a);
            default -> { }
        }
        return true;
    }

    private void arrestarCmd(CommandSender s, String[] a) {
        if (a.length < 1) {
            s.sendMessage(c("&eUso: /arrestar <jugador> [minutos] [multa]"));
            return;
        }
        Player t = Bukkit.getPlayerExact(a[0]);
        if (t == null) {
            s.sendMessage(c("&cJugador no encontrado."));
            return;
        }
        int min = plugin.getConfig().getInt("policia.minutos-por-defecto", 5);
        double fine = 0;
        try {
            if (a.length > 1) min = Integer.parseInt(a[1]);
            if (a.length > 2) fine = Double.parseDouble(a[2].replace(',', '.'));
            if (min < 1 || min > 1440 || fine < 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            s.sendMessage(c("&cMinutos: 1-1440. Multa: numero positivo."));
            return;
        }
        String err = arrest(t, min, fine);
        s.sendMessage(err != null ? c("&c" + err) : c("&a" + t.getName() + " arrestado por &e" + min + " min&a."));
    }

    private void liberarCmd(CommandSender s, String[] a) {
        if (a.length < 1) {
            s.sendMessage(c("&eUso: /liberar <jugador>"));
            return;
        }
        Player t = Bukkit.getPlayerExact(a[0]);
        if (t == null || !isJailed(t)) {
            s.sendMessage(c("&cEse jugador no esta preso (o no esta conectado)."));
            return;
        }
        release(t, "&aUn oficial te libero.");
        s.sendMessage(c("&a" + t.getName() + " liberado."));
    }

    private void carcelCmd(CommandSender s, String[] a) {
        String sub = a.length > 0 ? a[0].toLowerCase() : "";
        switch (sub) {
            case "set" -> {
                if (!(s instanceof Player p)) {
                    s.sendMessage(c("&cSolo jugadores."));
                    return;
                }
                jail = p.getLocation().clone();
                save();
                s.sendMessage(c("&aCarcel establecida aqui (radio &e" + radius() + "&a)."));
            }
            case "lista" -> {
                if (jailed.isEmpty()) {
                    s.sendMessage(c("&7No hay presos."));
                    return;
                }
                long now = System.currentTimeMillis();
                for (Map.Entry<UUID, Sentence> en : jailed.entrySet()) {
                    String name = Bukkit.getOfflinePlayer(en.getKey()).getName();
                    s.sendMessage(c("&e" + (name == null ? en.getKey() : name) + " &7- "
                            + fmt(Math.max(0, (en.getValue().until - now) / 1000))
                            + " &7- bloques: &f" + en.getValue().quota));
                }
            }
            default -> s.sendMessage(c("&eUso: /carcel <set|lista>"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        if (!s.hasPermission("fnpc.policia")) return List.of();
        String name = cmd.getName().toLowerCase();
        if (name.equals("carcel") && a.length == 1) return List.of("set", "lista");
        if (!name.equals("carcel") && a.length == 1) {
            String st = a[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(st)).toList();
        }
        return List.of();
    }
}
