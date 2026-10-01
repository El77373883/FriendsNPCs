package com.friends.npcs;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.friends.npcs.FriendsNPCs.c;

public class NpcActions implements Listener {

    private static final List<String> TYPES =
            List.of("tp", "comando", "consola", "mensaje", "sonido", "titulo", "espera", "servidor");
    private static final List<String> SUBS =
            List.of("add", "list", "remove", "clear", "clic", "cooldown", "permiso");
    private static final int MAX_ACTIONS = 50;

    private final FriendsNPCs plugin;
    private final NpcManager mgr;
    private final Map<UUID, Long> debounce = new HashMap<>();
    private final Set<UUID> running = new HashSet<>();

    public NpcActions(FriendsNPCs plugin, NpcManager mgr) {
        this.plugin = plugin;
        this.mgr = mgr;
    }

    // ---------- Clics ----------

    private Npc npcOf(Entity e) {
        if (e == null) return null;
        String id = e.getPersistentDataContainer().get(NpcManager.KEY, PersistentDataType.STRING);
        return id == null ? null : mgr.get(id);
    }

    @EventHandler
    public void onRightClick(PlayerInteractEntityEvent e) {
        Npc n = npcOf(e.getRightClicked());
        if (n == null) return;
        e.setCancelled(true); // evita abrir el comercio de un aldeano, etc.
        if (e.getHand() != EquipmentSlot.HAND) return;
        click(e.getPlayer(), n, true);
    }

    @EventHandler
    public void onLeftClick(PrePlayerAttackEntityEvent e) {
        Npc n = npcOf(e.getAttacked());
        if (n == null) return;
        e.setCancelled(true);
        click(e.getPlayer(), n, false);
    }

    private void click(Player p, Npc n, boolean right) {
        if (n.actions.isEmpty()) return;
        String t = n.clickType;
        if ((right && t.equals("izquierdo")) || (!right && t.equals("derecho"))) return;

        long now = System.currentTimeMillis();
        Long last = debounce.get(p.getUniqueId());
        if (last != null && now - last < 300) return;
        debounce.put(p.getUniqueId(), now);

        if (!n.clickPerm.isBlank() && !p.hasPermission(n.clickPerm)) {
            p.sendMessage(c("&cNo tienes permiso para usar esto."));
            return;
        }
        if (running.contains(p.getUniqueId())) return;

        if (n.cooldown > 0) {
            Long used = n.lastUse.get(p.getUniqueId());
            if (used != null && now - used < n.cooldown * 1000L) {
                long left = (n.cooldown * 1000L - (now - used)) / 1000 + 1;
                p.sendMessage(c("&cEspera &e" + left + "s &cpara volver a usarlo."));
                return;
            }
            n.lastUse.put(p.getUniqueId(), now);
        }

        running.add(p.getUniqueId());
        run(p, n, new ArrayList<>(n.actions), 0);
    }

    // ---------- Ejecucion ----------

    private void run(Player p, Npc n, List<String> acts, int start) {
        if (!p.isOnline()) {
            running.remove(p.getUniqueId());
            return;
        }
        int i = start;
        while (i < acts.size()) {
            String raw = acts.get(i++);
            int k = raw.indexOf(':');
            String type = (k < 0 ? raw : raw.substring(0, k)).trim().toLowerCase();
            String val = k < 0 ? "" : raw.substring(k + 1).trim();

            if (type.equals("espera")) {
                long ticks = 20;
                try {
                    ticks = Long.parseLong(val);
                } catch (NumberFormatException ignored) { }
                final int next = i;
                Bukkit.getScheduler().runTaskLater(plugin, () -> run(p, n, acts, next), Math.max(1, ticks));
                return;
            }
            try {
                exec(p, n, type, replace(val, p, n));
            } catch (Exception ex) {
                plugin.getLogger().warning("Error en accion '" + raw + "' de " + n.id + ": " + ex.getMessage());
            }
        }
        running.remove(p.getUniqueId());
    }

    private String replace(String v, Player p, Npc n) {
        return v.replace("%jugador%", p.getName())
                .replace("%mundo%", p.getWorld().getName())
                .replace("%npc%", n.id)
                .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
    }

    private void exec(Player p, Npc n, String type, String val) {
        switch (type) {
            case "mensaje" -> p.sendMessage(FriendsNPCs.rich(val));
            case "comando" -> p.performCommand(val.startsWith("/") ? val.substring(1) : val);
            case "consola" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    val.startsWith("/") ? val.substring(1) : val);
            case "sonido" -> p.playSound(p.getLocation(), val, 1f, 1f);
            case "titulo" -> {
                String[] t = val.split("\\|", 2);
                p.showTitle(Title.title(FriendsNPCs.rich(t[0].trim()),
                        FriendsNPCs.rich(t.length > 1 ? t[1].trim() : "")));
            }
            case "servidor" -> {
                try {
                    ByteArrayOutputStream b = new ByteArrayOutputStream();
                    DataOutputStream o = new DataOutputStream(b);
                    o.writeUTF("Connect");
                    o.writeUTF(val);
                    p.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
                } catch (IOException ignored) { }
            }
            case "tp" -> teleport(p, val);
            default -> { }
        }
    }

    private World findWorld(String name) {
        World w = Bukkit.getWorld(name);
        if (w != null) return w;
        World.Environment env = switch (name.toLowerCase()) {
            case "nether" -> World.Environment.NETHER;
            case "end" -> World.Environment.THE_END;
            case "overworld", "mundo" -> World.Environment.NORMAL;
            default -> null;
        };
        if (env == null) return null;
        for (World x : Bukkit.getWorlds()) {
            if (x.getEnvironment() == env) return x;
        }
        return null;
    }

    private void teleport(Player p, String val) {
        String[] t = val.replace(',', ' ').trim().split("\\s+");
        if (t.length < 4) {
            p.sendMessage(c("&cTeletransporte mal configurado."));
            return;
        }
        World w = findWorld(t[0]);
        if (w == null) {
            p.sendMessage(c("&cEse mundo no existe."));
            return;
        }
        try {
            Location cur = p.getLocation();
            Location l = new Location(w, Double.parseDouble(t[1]), Double.parseDouble(t[2]),
                    Double.parseDouble(t[3]),
                    t.length > 4 ? Float.parseFloat(t[4]) : cur.getYaw(),
                    t.length > 5 ? Float.parseFloat(t[5]) : cur.getPitch());
            p.teleportAsync(l);
        } catch (NumberFormatException ex) {
            p.sendMessage(c("&cCoordenadas invalidas."));
        }
    }

    // ---------- Comando /fnpc action ----------

    public static void command(NpcManager mgr, CommandSender s, String[] a) {
        if (a.length < 3) {
            s.sendMessage(c("&eUso: /fnpc action <id> <" + String.join("|", SUBS) + ">"));
            return;
        }
        Npc n = mgr.get(a[1]);
        if (n == null) {
            s.sendMessage(c("&cNo existe ese NPC."));
            return;
        }
        switch (a[2].toLowerCase()) {
            case "add" -> {
                String type = a.length > 3 ? a[3].toLowerCase() : "";
                if (!TYPES.contains(type) || a.length < 5) {
                    s.sendMessage(c("&eUso: /fnpc action " + n.id + " add <" + String.join("|", TYPES) + "> <valor>"));
                    s.sendMessage(c("&7Ej: add tp nether 0 80 0  |  add comando spawn  |  add espera 20"));
                    return;
                }
                if (n.actions.size() >= MAX_ACTIONS) {
                    s.sendMessage(c("&cLimite de " + MAX_ACTIONS + " acciones."));
                    return;
                }
                String val = String.join(" ", java.util.Arrays.copyOfRange(a, 4, a.length));
                if (type.equals("espera")) {
                    try {
                        if (Long.parseLong(val) < 1) throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        s.sendMessage(c("&cLa espera va en ticks (20 = 1 segundo)."));
                        return;
                    }
                }
                if (type.equals("tp") && val.replace(',', ' ').trim().split("\\s+").length < 4) {
                    s.sendMessage(c("&eUso: add tp <mundo> <x> <y> <z> [yaw] [pitch]"));
                    return;
                }
                n.actions.add(type + ": " + val);
                mgr.save();
                s.sendMessage(c("&aAccion &e" + n.actions.size() + " &aagregada."));
            }
            case "list" -> {
                if (n.actions.isEmpty()) {
                    s.sendMessage(c("&7Ese NPC no tiene acciones."));
                    return;
                }
                s.sendMessage(c("&6Acciones de &e" + n.id + " &7(clic: " + n.clickType + ", cooldown: "
                        + n.cooldown + "s, permiso: " + (n.clickPerm.isBlank() ? "ninguno" : n.clickPerm) + ")"));
                for (int i = 0; i < n.actions.size(); i++) {
                    s.sendMessage(c("&e" + (i + 1) + "&7: &f" + n.actions.get(i)));
                }
            }
            case "remove" -> {
                try {
                    int k = Integer.parseInt(a[3]);
                    if (k < 1 || k > n.actions.size()) throw new NumberFormatException();
                    n.actions.remove(k - 1);
                    mgr.save();
                    s.sendMessage(c("&aAccion &e" + k + " &aborrada."));
                } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                    s.sendMessage(c("&eUso: /fnpc action " + n.id + " remove <numero>"));
                }
            }
            case "clear" -> {
                n.actions.clear();
                mgr.save();
                s.sendMessage(c("&aAcciones borradas."));
            }
            case "clic" -> {
                String v = a.length > 3 ? a[3].toLowerCase() : "";
                if (!List.of("izquierdo", "derecho", "ambos").contains(v)) {
                    s.sendMessage(c("&eUso: /fnpc action " + n.id + " clic <izquierdo|derecho|ambos>"));
                    return;
                }
                n.clickType = v;
                mgr.save();
                s.sendMessage(c("&aSe activa con clic &e" + v));
            }
            case "cooldown" -> {
                try {
                    int v = Integer.parseInt(a[3]);
                    if (v < 0 || v > 3600) throw new NumberFormatException();
                    n.cooldown = v;
                    mgr.save();
                    s.sendMessage(c("&aCooldown: &e" + v + "s"));
                } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                    s.sendMessage(c("&eUso: /fnpc action " + n.id + " cooldown <0-3600 segundos>"));
                }
            }
            case "permiso" -> {
                if (a.length < 4) {
                    s.sendMessage(c("&eUso: /fnpc action " + n.id + " permiso <permiso|off>"));
                    return;
                }
                n.clickPerm = a[3].equalsIgnoreCase("off") ? "" : a[3];
                mgr.save();
                s.sendMessage(c(n.clickPerm.isBlank() ? "&aPermiso quitado." : "&aPermiso: &e" + n.clickPerm));
            }
            default -> s.sendMessage(c("&eUso: /fnpc action <id> <" + String.join("|", SUBS) + ">"));
        }
    }

    public static List<String> tab(NpcManager mgr, String[] a) {
        if (a.length == 2) return filter(mgr.all().stream().map(n -> n.id).toList(), a[1]);
        if (a.length == 3) return filter(SUBS, a[2]);
        if (a.length == 4) {
            if (a[2].equalsIgnoreCase("add")) return filter(TYPES, a[3]);
            if (a[2].equalsIgnoreCase("clic")) return filter(List.of("izquierdo", "derecho", "ambos"), a[3]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> list, String start) {
        String st = start.toLowerCase();
        return list.stream().filter(x -> x.toLowerCase().startsWith(st)).toList();
    }
}
