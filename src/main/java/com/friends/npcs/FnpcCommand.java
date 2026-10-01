package com.friends.npcs;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.friends.npcs.FriendsNPCs.c;

public class FnpcCommand implements CommandExecutor, TabCompleter {

    private static final List<String> TYPES = new ArrayList<>();

    static {
        TYPES.add("player");
        for (EntityType t : EntityType.values()) {
            Class<?> cls = t.getEntityClass();
            if (t.isSpawnable() && cls != null && Mob.class.isAssignableFrom(cls)) {
                TYPES.add(t.name().toLowerCase());
            }
        }
    }

    private final FriendsNPCs plugin;
    private final NpcManager mgr;

    public FnpcCommand(FriendsNPCs plugin, NpcManager mgr) {
        this.plugin = plugin;
        this.mgr = mgr;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length > 0 && a[0].equalsIgnoreCase("info")) {
            info(s);
            return true;
        }
        if (!s.hasPermission("fnpc.admin")) {
            s.sendMessage(c("&cNo tienes permiso."));
            return true;
        }
        if (a.length == 0) {
            help(s);
            return true;
        }
        switch (a[0].toLowerCase()) {
            case "create", "crear" -> create(s, a);
            case "borrar", "delete" -> borrar(s, a);
            case "lista", "list" -> lista(s);
            case "tp" -> tp(s, a);
            case "mover" -> mover(s, a);
            case "nombre" -> nombre(s, a);
            case "modo" -> modo(s, a);
            case "reload" -> {
                plugin.reloadConfig();
                mgr.reload();
                s.sendMessage(c("&aFriendsNPCs recargado."));
            }
            default -> help(s);
        }
        return true;
    }

    private void help(CommandSender s) {
        s.sendMessage(c("&6&lFriendsNPCs &7- comandos"));
        s.sendMessage(c("&e/fnpc create <id> <player|mob> [nombre] &7- crear"));
        s.sendMessage(c("&e/fnpc borrar <id> &7- borrar"));
        s.sendMessage(c("&e/fnpc lista &7- ver NPCs"));
        s.sendMessage(c("&e/fnpc tp <id> &7- ir al NPC"));
        s.sendMessage(c("&e/fnpc mover <id> &7- traerlo a tu posicion"));
        s.sendMessage(c("&e/fnpc nombre <id> <texto> &7- cambiar nombre"));
        s.sendMessage(c("&e/fnpc modo <id> <quieto|deambular>"));
        s.sendMessage(c("&e/fnpc reload &7- recargar"));
        s.sendMessage(c("&e/fnpc info &7- creditos"));
    }

    private void info(CommandSender s) {
        s.sendMessage(c("&6&l★ FriendsNPCs v" + plugin.getPluginMeta().getVersion() + " ★"));
        s.sendMessage(c("&fHecho por &esoyadrianyt001"));
        for (String k : List.of("youtube", "tiktok", "discord", "instagram")) {
            String v = plugin.getConfig().getString("redes." + k, "");
            if (v == null || v.isBlank()) continue;
            Component line = c("&7" + k + ": &b" + v);
            if (v.startsWith("http")) line = line.clickEvent(ClickEvent.openUrl(v));
            s.sendMessage(line);
        }
    }

    private void create(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage(c("&cSolo jugadores."));
            return;
        }
        if (a.length < 3) {
            s.sendMessage(c("&eUso: /fnpc create <id> <player|mob> [nombre]"));
            return;
        }
        String name = a.length > 3 ? String.join(" ", Arrays.copyOfRange(a, 3, a.length)) : a[1];
        String err = mgr.create(a[1], a[2], name, p.getLocation());
        s.sendMessage(err != null ? c("&c" + err) : c("&aNPC &e" + a[1] + " &acreado."));
    }

    private void borrar(CommandSender s, String[] a) {
        if (a.length < 2) {
            s.sendMessage(c("&eUso: /fnpc borrar <id>"));
            return;
        }
        s.sendMessage(mgr.delete(a[1]) ? c("&aNPC borrado.") : c("&cNo existe ese NPC."));
    }

    private void lista(CommandSender s) {
        if (mgr.all().isEmpty()) {
            s.sendMessage(c("&7No hay NPCs."));
            return;
        }
        s.sendMessage(c("&6NPCs (" + mgr.all().size() + "):"));
        for (Npc n : mgr.all()) {
            Location l = n.home;
            s.sendMessage(c("&e" + n.id + " &7- " + n.type.toLowerCase() + " &7- " + n.worldName + " "
                    + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() + " &7- " + n.mode));
        }
    }

    private void tp(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage(c("&cSolo jugadores."));
            return;
        }
        Npc n = a.length > 1 ? mgr.get(a[1]) : null;
        if (n == null) {
            s.sendMessage(c("&cNo existe ese NPC. Uso: /fnpc tp <id>"));
            return;
        }
        Location l = n.body != null ? n.body.getLocation() : n.home;
        p.teleport(l);
        s.sendMessage(c("&aTeletransportado."));
    }

    private void mover(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage(c("&cSolo jugadores."));
            return;
        }
        Npc n = a.length > 1 ? mgr.get(a[1]) : null;
        if (n == null) {
            s.sendMessage(c("&cNo existe ese NPC. Uso: /fnpc mover <id>"));
            return;
        }
        mgr.move(n, p.getLocation());
        s.sendMessage(c("&aNPC movido a tu posicion."));
    }

    private void nombre(CommandSender s, String[] a) {
        Npc n = a.length > 2 ? mgr.get(a[1]) : null;
        if (n == null) {
            s.sendMessage(c("&eUso: /fnpc nombre <id> <texto> (el NPC debe existir)"));
            return;
        }
        mgr.rename(n, String.join(" ", Arrays.copyOfRange(a, 2, a.length)));
        s.sendMessage(c("&aNombre cambiado."));
    }

    private void modo(CommandSender s, String[] a) {
        Npc n = a.length > 2 ? mgr.get(a[1]) : null;
        if (n == null || !List.of("quieto", "deambular").contains(a[2].toLowerCase())) {
            s.sendMessage(c("&eUso: /fnpc modo <id> <quieto|deambular>"));
            return;
        }
        mgr.setMode(n, a[2].toLowerCase());
        s.sendMessage(c("&aModo cambiado a &e" + a[2].toLowerCase()));
    }

    // ---------- Autocompletado ----------

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        if (!s.hasPermission("fnpc.admin")) return List.of();
        if (a.length == 1) {
            return filter(List.of("create", "borrar", "lista", "tp", "mover", "nombre", "modo",
                    "reload", "ayuda", "info"), a[0]);
        }
        String sub = a[0].toLowerCase();
        if (a.length == 2 && List.of("borrar", "delete", "tp", "mover", "nombre", "modo").contains(sub)) {
            return filter(mgr.all().stream().map(n -> n.id).toList(), a[1]);
        }
        if (a.length == 3 && (sub.equals("create") || sub.equals("crear"))) return filter(TYPES, a[2]);
        if (a.length == 3 && sub.equals("modo")) return filter(List.of("quieto", "deambular"), a[2]);
        return List.of();
    }

    private List<String> filter(List<String> list, String start) {
        String st = start.toLowerCase();
        return list.stream().filter(x -> x.toLowerCase().startsWith(st)).toList();
    }
}
