package com.friends.npcs;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
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

    private static final int MAX_LINE = 200;

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
            case "skin" -> skin(s, a);
            case "edit" -> edit(s, a);
            case "pose" -> pose(s, a);
            case "glow" -> glow(s, a);
            case "efecto" -> efecto(s, a);
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
        s.sendMessage(c("&e/fnpc create <id> <player|mob> [nombre]"));
        s.sendMessage(c("&e/fnpc borrar|tp|mover <id>"));
        s.sendMessage(c("&e/fnpc lista"));
        s.sendMessage(c("&e/fnpc nombre <id> <texto>"));
        s.sendMessage(c("&e/fnpc modo <id> <quieto|deambular>"));
        s.sendMessage(c("&e/fnpc skin <id> <jugador>"));
        s.sendMessage(c("&e/fnpc edit <setline|addline|insertline|removeline|lines|clearlines> <id> ..."));
        s.sendMessage(c("&e/fnpc pose <id> <normal|dormir|nadar|agachado|volar|girar>"));
        s.sendMessage(c("&e/fnpc glow <id> <color|off|arcoiris>"));
        s.sendMessage(c("&e/fnpc efecto <id> <fuego|invisible> [on|off]"));
        s.sendMessage(c("&e/fnpc reload &7| &e/fnpc info"));
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

    // ---------- Gestion ----------

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
        p.teleport(n.body != null ? n.body.getLocation() : n.home);
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

    // ---------- Skin ----------

    private void skin(CommandSender s, String[] a) {
        Npc n = a.length > 2 ? mgr.get(a[1]) : null;
        if (n == null) {
            s.sendMessage(c("&eUso: /fnpc skin <id> <jugador>"));
            return;
        }
        if (!n.isPlayer()) {
            s.sendMessage(c("&cSolo los NPC tipo player tienen skin."));
            return;
        }
        s.sendMessage(c("&7Buscando skin de &e" + a[2] + "&7..."));
        mgr.setSkin(n, a[2], err ->
                s.sendMessage(err != null ? c("&c" + err) : c("&aSkin cambiada a &e" + a[2])));
    }

    // ---------- Hologramas ----------

    private Integer num(String s) {
        try {
            int v = Integer.parseInt(s);
            return v >= 1 && v <= MAX_LINE ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void edit(CommandSender s, String[] a) {
        if (a.length < 3) {
            s.sendMessage(c("&eUso: /fnpc edit <setline|addline|insertline|removeline|lines|clearlines> <id> ..."));
            return;
        }
        String op = a[1].toLowerCase();
        Npc n = mgr.get(a[2]);
        if (n == null) {
            s.sendMessage(c("&cNo existe ese NPC."));
            return;
        }
        switch (op) {
            case "setline" -> {
                Integer k = a.length > 3 ? num(a[3]) : null;
                if (k == null || a.length < 5) {
                    s.sendMessage(c("&eUso: /fnpc edit setline <id> <linea 1-" + MAX_LINE + "> <texto>"));
                    return;
                }
                while (n.lines.size() < k) n.lines.add(" ");
                n.lines.set(k - 1, String.join(" ", Arrays.copyOfRange(a, 4, a.length)));
                mgr.refresh(n);
                s.sendMessage(c("&aLinea &e" + k + " &aactualizada."));
            }
            case "addline" -> {
                if (a.length < 4) {
                    s.sendMessage(c("&eUso: /fnpc edit addline <id> <texto>"));
                    return;
                }
                if (n.lines.size() >= MAX_LINE) {
                    s.sendMessage(c("&cLimite de " + MAX_LINE + " lineas."));
                    return;
                }
                n.lines.add(String.join(" ", Arrays.copyOfRange(a, 3, a.length)));
                mgr.refresh(n);
                s.sendMessage(c("&aLinea agregada (&e" + n.lines.size() + "&a)."));
            }
            case "insertline" -> {
                Integer k = a.length > 3 ? num(a[3]) : null;
                if (k == null || a.length < 5) {
                    s.sendMessage(c("&eUso: /fnpc edit insertline <id> <linea> <texto>"));
                    return;
                }
                while (n.lines.size() < k - 1) n.lines.add(" ");
                n.lines.add(k - 1, String.join(" ", Arrays.copyOfRange(a, 4, a.length)));
                mgr.refresh(n);
                s.sendMessage(c("&aLinea insertada en &e" + k + "&a."));
            }
            case "removeline" -> {
                Integer k = a.length > 3 ? num(a[3]) : null;
                if (k == null || k > n.lines.size()) {
                    s.sendMessage(c("&eUso: /fnpc edit removeline <id> <linea existente>"));
                    return;
                }
                n.lines.remove(k - 1);
                mgr.refresh(n);
                s.sendMessage(c("&aLinea &e" + k + " &aborrada."));
            }
            case "lines" -> {
                if (n.lines.isEmpty()) {
                    s.sendMessage(c("&7Ese NPC no tiene holograma."));
                    return;
                }
                for (int i = 0; i < n.lines.size(); i++) {
                    s.sendMessage(c("&e" + (i + 1) + "&7: &f" + n.lines.get(i)));
                }
            }
            case "clearlines" -> {
                n.lines.clear();
                mgr.refresh(n);
                s.sendMessage(c("&aHolograma borrado."));
            }
            default -> s.sendMessage(c("&cOpcion invalida."));
        }
    }

    // ---------- Pose / glow / efecto ----------

    private void pose(CommandSender s, String[] a) {
        Npc n = a.length > 2 ? mgr.get(a[1]) : null;
        if (n == null || !NpcManager.POSES.containsKey(a[2].toLowerCase())) {
            s.sendMessage(c("&eUso: /fnpc pose <id> <" + String.join("|", NpcManager.POSES.keySet()) + ">"));
            return;
        }
        n.pose = a[2].toLowerCase();
        mgr.refresh(n);
        s.sendMessage(c("&aPose: &e" + n.pose));
    }

    private void glow(CommandSender s, String[] a) {
        Npc n = a.length > 2 ? mgr.get(a[1]) : null;
        String v = a.length > 2 ? a[2].toLowerCase() : "";
        if (n == null || !(v.equals("off") || v.equals("arcoiris") || NpcManager.COLORS.containsKey(v))) {
            s.sendMessage(c("&eUso: /fnpc glow <id> <color|off|arcoiris>"));
            s.sendMessage(c("&7Colores: " + String.join(", ", NpcManager.COLORS.keySet())));
            return;
        }
        n.glow = v;
        mgr.refresh(n);
        s.sendMessage(c("&aGlow: &e" + v));
    }

    private void efecto(CommandSender s, String[] a) {
        Npc n = a.length > 2 ? mgr.get(a[1]) : null;
        String e = a.length > 2 ? a[2].toLowerCase() : "";
        if (n == null || !(e.equals("fuego") || e.equals("invisible"))) {
            s.sendMessage(c("&eUso: /fnpc efecto <id> <fuego|invisible> [on|off]"));
            return;
        }
        boolean on = a.length < 4 || !a[3].equalsIgnoreCase("off");
        if (on) n.effects.add(e);
        else n.effects.remove(e);
        mgr.refresh(n);
        s.sendMessage(c("&aEfecto &e" + e + "&a: " + (on ? "activado" : "desactivado")));
    }

    // ---------- Autocompletado ----------

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        if (!s.hasPermission("fnpc.admin")) return List.of();
        List<String> ids = mgr.all().stream().map(n -> n.id).toList();
        String sub = a[0].toLowerCase();

        if (a.length == 1) {
            return filter(List.of("create", "borrar", "lista", "tp", "mover", "nombre", "modo", "skin",
                    "edit", "pose", "glow", "efecto", "reload", "ayuda", "info"), a[0]);
        }
        if (a.length == 2) {
            if (sub.equals("edit")) {
                return filter(List.of("setline", "addline", "insertline", "removeline", "lines", "clearlines"), a[1]);
            }
            if (List.of("borrar", "delete", "tp", "mover", "nombre", "modo", "skin", "pose", "glow", "efecto")
                    .contains(sub)) {
                return filter(ids, a[1]);
            }
        }
        if (a.length == 3) {
            switch (sub) {
                case "edit" -> { return filter(ids, a[2]); }
                case "create", "crear" -> { return filter(TYPES, a[2]); }
                case "modo" -> { return filter(List.of("quieto", "deambular"), a[2]); }
                case "pose" -> { return filter(new ArrayList<>(NpcManager.POSES.keySet()), a[2]); }
                case "glow" -> {
                    List<String> l = new ArrayList<>(NpcManager.COLORS.keySet());
                    l.add("off");
                    l.add("arcoiris");
                    return filter(l, a[2]);
                }
                case "efecto" -> { return filter(List.of("fuego", "invisible"), a[2]); }
                case "skin" -> {
                    return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), a[2]);
                }
                default -> { }
            }
        }
        if (a.length == 4 && sub.equals("efecto")) return filter(List.of("on", "off"), a[3]);
        return List.of();
    }

    private List<String> filter(List<String> list, String start) {
        String st = start.toLowerCase();
        return list.stream().filter(x -> x.toLowerCase().startsWith(st)).toList();
    }
}
