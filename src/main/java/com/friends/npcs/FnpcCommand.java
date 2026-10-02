package com.friends.npcs;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
    private static final int MAX_POINTS = 500;
    private static final List<String> MODES = List.of("quieto", "deambular", "patrullar", "seguir");
    private static final List<String> PATH_SUBS = List.of("add", "insert", "remove", "list", "clear",
            "tp", "show", "goto", "modo", "velocidad", "espera", "iniciar", "detener");
    private static final List<String> PATH_MODES = List.of("loop", "pingpong", "una_vez");

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
        if (a.length > 0 && a[0].equalsIgnoreCase("creator")) {
            creator(s);
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
            case "path" -> path(s, a);
            case "mirar", "look" -> mirar(s, a);
            case "anim" -> anim(s, a);
            case "action", "accion" -> NpcActions.command(mgr, s, a);
            case "equip", "equipo" -> NpcEquip.command(mgr, s, a);
            case "shop", "tienda" -> plugin.shop.command(s, a);
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
        s.sendMessage(c("&e/fnpc modo <id> <quieto|deambular|patrullar|seguir>"));
        s.sendMessage(c("&e/fnpc look <id> [on|off] &7(mirar al jugador)"));
        s.sendMessage(c("&e/fnpc path <id> <add|insert|remove|list|clear|tp|show|goto|modo|velocidad|espera|iniciar|detener>"));
        s.sendMessage(c("&e/fnpc anim <id> <saludar|golpear|off> [repetir <seg>]"));
        s.sendMessage(c("&e/fnpc skin <id> <jugador>"));
        s.sendMessage(c("&e/fnpc edit <setline|addline|insertline|removeline|lines|clearlines> <id> ..."));
        s.sendMessage(c("&e/fnpc pose <id> <normal|dormir|nadar|agachado|volar|girar>"));
        s.sendMessage(c("&e/fnpc glow <id> <color|off|arcoiris>"));
        s.sendMessage(c("&e/fnpc efecto <id> <fuego|invisible> [on|off]"));
        s.sendMessage(c("&e/fnpc shop <id> <crear|quitar|editar|items|precio|titulo|filas|color|limpiar>"));
        s.sendMessage(c("&e/fnpc action <id> <add|list|remove|clear|clic|cooldown|permiso>"));
        s.sendMessage(c("&e/fnpc reload &7| &e/fnpc info &7| &e/fnpc creator"));
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

    private void creator(CommandSender s) {
        String v = plugin.getPluginMeta().getVersion();
        s.sendMessage(c(" "));
        s.sendMessage(c("&8&m                                                  "));
        s.sendMessage(FriendsNPCs.rich("<gradient:#FFAA00:#FFFF55><bold>  ★ FriendsNPCs PREMIUM ★</bold></gradient>"));
        s.sendMessage(c("&7  Version &f" + v));
        s.sendMessage(c(" "));
        s.sendMessage(FriendsNPCs.rich("<gray>  Hecho por </gray><gradient:#55FFFF:#5555FF><bold>soyadrianyt001</bold></gradient>"));
        s.sendMessage(c("&7  NPCs con skin, tiendas, rutas y policia"));
        for (String k : List.of("youtube", "tiktok", "discord", "instagram")) {
            String val = plugin.getConfig().getString("redes." + k, "");
            if (val == null || val.isBlank()) continue;
            Component line = c("&7  " + k + ": &b" + val);
            if (val.startsWith("http")) line = line.clickEvent(ClickEvent.openUrl(val));
            s.sendMessage(line);
        }
        s.sendMessage(c("&8&m                                                  "));
        s.sendMessage(c(" "));
        if (s instanceof Player p) {
            p.showTitle(Title.title(
                    FriendsNPCs.rich("<gradient:#FFAA00:#FFFF55><bold>FriendsNPCs PREMIUM</bold></gradient>"),
                    FriendsNPCs.rich("<gray>v" + v + " · hecho por </gray><aqua>soyadrianyt001</aqua>")));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
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
        if (err != null) {
            s.sendMessage(c("&c" + err));
            return;
        }
        s.sendMessage(c("&aNPC &e" + a[1] + " &acreado."));
        if (mgr.all().size() == 1) {
            p.showTitle(Title.title(c("&6&l★ FriendsNPCs ★"), c("&7por &esoyadrianyt001")));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
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
        if (n == null || !MODES.contains(a[2].toLowerCase())) {
            s.sendMessage(c("&eUso: /fnpc modo <id> <" + String.join("|", MODES) + ">"));
            return;
        }
        String m = a[2].toLowerCase();
        mgr.setMode(n, m);
        s.sendMessage(c("&aModo cambiado a &e" + m));
        if (m.equals("patrullar") && n.path.isEmpty()) {
            s.sendMessage(c("&7Aun no tiene ruta. Agrega puntos con &e/fnpc path " + n.id + " add"));
        }
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

    // ---------- Mirar / animaciones ----------

    private void mirar(CommandSender s, String[] a) {
        Npc n = a.length > 1 ? mgr.get(a[1]) : null;
        if (n == null) {
            s.sendMessage(c("&eUso: /fnpc look <id> [on|off]"));
            return;
        }
        if (a.length > 2) {
            if (a[2].equalsIgnoreCase("on")) n.look = true;
            else if (a[2].equalsIgnoreCase("off")) n.look = false;
            else {
                s.sendMessage(c("&eUso: /fnpc look <id> [on|off]"));
                return;
            }
        } else {
            n.look = !n.look;
        }
        mgr.save();
        s.sendMessage(c("&aMirar al jugador: &e" + (n.look ? "activado" : "desactivado")));
    }

    private void anim(CommandSender s, String[] a) {
        Npc n = a.length > 2 ? mgr.get(a[1]) : null;
        String an = a.length > 2 ? a[2].toLowerCase() : "";
        if (n == null || !(an.equals("saludar") || an.equals("golpear") || an.equals("off"))) {
            s.sendMessage(c("&eUso: /fnpc anim <id> <saludar|golpear|off> [repetir <segundos>]"));
            return;
        }
        if (an.equals("off")) {
            n.anim = "off";
            n.animEvery = 0;
            mgr.save();
            s.sendMessage(c("&aAnimacion repetida desactivada."));
            return;
        }
        if (a.length > 3 && a[3].equalsIgnoreCase("repetir")) {
            Integer sec = intArg(a, 4, 1, 3600);
            if (sec == null) {
                s.sendMessage(c("&eUso: /fnpc anim <id> " + an + " repetir <1-3600>"));
                return;
            }
            n.anim = an;
            n.animEvery = sec;
            n.lastAnim = 0;
            mgr.save();
            s.sendMessage(c("&a" + an + " cada &e" + sec + "s&a."));
            return;
        }
        NpcBehavior.play(plugin, n, an);
        s.sendMessage(c("&aAnimacion: &e" + an));
    }

    // ---------- Rutas ----------

    private Integer intArg(String[] a, int i, int min, int max) {
        if (a.length <= i) return null;
        try {
            int v = Integer.parseInt(a[i]);
            return v >= min && v <= max ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void path(CommandSender s, String[] a) {
        if (a.length < 3) {
            s.sendMessage(c("&eUso: /fnpc path <id> <" + String.join("|", PATH_SUBS) + ">"));
            return;
        }
        Npc n = mgr.get(a[1]);
        if (n == null) {
            s.sendMessage(c("&cNo existe ese NPC."));
            return;
        }
        String sub = a[2].toLowerCase();
        Player p = s instanceof Player pl ? pl : null;
        if (p == null && List.of("add", "insert", "tp", "show", "goto").contains(sub)) {
            s.sendMessage(c("&cSolo jugadores."));
            return;
        }

        switch (sub) {
            case "add" -> {
                if (n.path.size() >= MAX_POINTS) {
                    s.sendMessage(c("&cLimite de " + MAX_POINTS + " puntos."));
                    return;
                }
                n.path.add(NpcBehavior.format(p.getLocation()));
                mgr.save();
                s.sendMessage(c("&aPunto &e" + n.path.size() + " &aagregado."));
            }
            case "insert" -> {
                Integer k = intArg(a, 3, 1, n.path.size() + 1);
                if (k == null || n.path.size() >= MAX_POINTS) {
                    s.sendMessage(c("&eUso: /fnpc path <id> insert <1-" + (n.path.size() + 1) + ">"));
                    return;
                }
                n.path.add(k - 1, NpcBehavior.format(p.getLocation()));
                mgr.save();
                s.sendMessage(c("&aPunto insertado en &e" + k + "&a."));
            }
            case "remove" -> {
                Integer k = intArg(a, 3, 1, n.path.size());
                if (k == null) {
                    s.sendMessage(c("&eUso: /fnpc path <id> remove <numero de punto>"));
                    return;
                }
                n.path.remove(k - 1);
                NpcBehavior.resetPath(n);
                mgr.save();
                s.sendMessage(c("&aPunto &e" + k + " &aborrado."));
            }
            case "list" -> {
                if (n.path.isEmpty()) {
                    s.sendMessage(c("&7Ese NPC no tiene ruta."));
                    return;
                }
                s.sendMessage(c("&6Ruta de &e" + n.id + " &7(" + n.pathMode + ", vel " + n.pathSpeed
                        + ", espera " + n.pathWait + "s, " + (n.pathRunning ? "activa" : "detenida") + ")"));
                for (int i = 0; i < n.path.size(); i++) {
                    s.sendMessage(c("&e" + (i + 1) + "&7: " + n.path.get(i).replace(",", ", ")));
                }
            }
            case "clear" -> {
                n.path.clear();
                NpcBehavior.resetPath(n);
                mgr.save();
                s.sendMessage(c("&aRuta borrada."));
            }
            case "tp" -> {
                Integer k = intArg(a, 3, 1, n.path.size());
                Location l = k == null ? null : NpcBehavior.parse(n.path.get(k - 1));
                if (l == null) {
                    s.sendMessage(c("&eUso: /fnpc path <id> tp <numero de punto> (el mundo debe estar cargado)"));
                    return;
                }
                p.teleport(l);
                s.sendMessage(c("&aTeletransportado al punto &e" + k + "&a."));
            }
            case "show" -> show(p, n);
            case "goto" -> {
                Mob m = n.mover();
                if (m == null || n.body == null) {
                    s.sendMessage(c("&cEl NPC no esta cargado."));
                    return;
                }
                Location t;
                if (a.length > 3) {
                    Integer k = intArg(a, 3, 1, n.path.size());
                    t = k == null ? null : NpcBehavior.parse(n.path.get(k - 1));
                    if (t == null) {
                        s.sendMessage(c("&eUso: /fnpc path <id> goto [numero de punto]"));
                        return;
                    }
                } else {
                    t = p.getLocation();
                }
                n.gotoTarget = t.clone();
                n.gotoUntil = System.currentTimeMillis() + 30000;
                m.setAI(true);
                s.sendMessage(c("&aEl NPC va hacia alla."));
            }
            case "modo" -> {
                String v = a.length > 3 ? a[3].toLowerCase() : "";
                if (!PATH_MODES.contains(v)) {
                    s.sendMessage(c("&eUso: /fnpc path <id> modo <loop|pingpong|una_vez>"));
                    return;
                }
                n.pathMode = v;
                NpcBehavior.resetPath(n);
                mgr.save();
                s.sendMessage(c("&aModo de ruta: &e" + v));
            }
            case "velocidad" -> {
                try {
                    double v = Double.parseDouble(a[3]);
                    if (v < 0.1 || v > 3.0) throw new NumberFormatException();
                    n.pathSpeed = v;
                    mgr.save();
                    s.sendMessage(c("&aVelocidad: &e" + v));
                } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                    s.sendMessage(c("&eUso: /fnpc path <id> velocidad <0.1-3.0>"));
                }
            }
            case "espera" -> {
                Integer v = intArg(a, 3, 0, 300);
                if (v == null) {
                    s.sendMessage(c("&eUso: /fnpc path <id> espera <0-300 segundos>"));
                    return;
                }
                n.pathWait = v;
                mgr.save();
                s.sendMessage(c("&aEspera en cada punto: &e" + v + "s"));
            }
            case "iniciar" -> {
                if (n.path.isEmpty()) {
                    s.sendMessage(c("&cPrimero agrega puntos: /fnpc path " + n.id + " add"));
                    return;
                }
                n.pathRunning = true;
                mgr.setMode(n, "patrullar"); // reinicia la ruta y guarda
                s.sendMessage(c("&aRuta iniciada (modo patrullar)."));
            }
            case "detener" -> {
                n.pathRunning = false;
                Mob m = n.mover();
                if (m != null) m.getPathfinder().stopPathfinding();
                mgr.save();
                s.sendMessage(c("&aRuta detenida."));
            }
            default -> s.sendMessage(c("&eUso: /fnpc path <id> <" + String.join("|", PATH_SUBS) + ">"));
        }
    }

    private void show(Player p, Npc n) {
        List<Location> pts = new ArrayList<>();
        for (String raw : n.path) {
            Location l = NpcBehavior.parse(raw);
            if (l != null && l.getWorld() == p.getWorld()) pts.add(l);
        }
        if (pts.isEmpty()) {
            p.sendMessage(c("&7No hay puntos para mostrar en este mundo."));
            return;
        }
        p.sendMessage(c("&aMostrando la ruta por 15 segundos."));
        int[] ticks = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!p.isOnline() || ticks[0]++ >= 30) {
                task.cancel();
                return;
            }
            for (int i = 0; i < pts.size(); i++) {
                Location pt = pts.get(i);
                p.spawnParticle(Particle.HAPPY_VILLAGER, pt.clone().add(0, 0.5, 0), 5, 0.2, 0.3, 0.2, 0);
                if (i + 1 < pts.size()) line(p, pt, pts.get(i + 1));
            }
        }, 0L, 10L);
    }

    private void line(Player p, Location a, Location b) {
        int steps = (int) Math.min(200, a.distance(b) * 2);
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            Location l = a.clone().add(b.clone().subtract(a).toVector().multiply(t)).add(0, 0.3, 0);
            p.spawnParticle(Particle.END_ROD, l, 1, 0, 0, 0, 0);
        }
    }

    // ---------- Autocompletado ----------

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 1 && !s.hasPermission("fnpc.admin")) return filter(List.of("info", "creator"), a[0]);
        if (!s.hasPermission("fnpc.admin")) return List.of();
        if (a[0].equalsIgnoreCase("action") || a[0].equalsIgnoreCase("accion")) return NpcActions.tab(mgr, a);
        if (a[0].equalsIgnoreCase("equip") || a[0].equalsIgnoreCase("equipo")) return NpcEquip.tab(mgr, a);
        if (a[0].equalsIgnoreCase("shop") || a[0].equalsIgnoreCase("tienda")) return plugin.shop.tab(a);
        List<String> ids = mgr.all().stream().map(n -> n.id).toList();
        String sub = a[0].toLowerCase();

        if (a.length == 1) {
            return filter(List.of("create", "borrar", "lista", "tp", "mover", "nombre", "modo", "mirar", "look",
                    "path", "anim", "skin", "edit", "pose", "glow", "efecto", "action", "shop",
                    "reload", "ayuda", "info", "creator"), a[0]);
        }
        if (a.length == 2) {
            if (sub.equals("edit")) {
                return filter(List.of("setline", "addline", "insertline", "removeline", "lines", "clearlines"), a[1]);
            }
            if (List.of("borrar", "delete", "tp", "mover", "nombre", "modo", "mirar", "look", "path", "anim",
                    "skin", "pose", "glow", "efecto").contains(sub)) {
                return filter(ids, a[1]);
            }
        }
        if (a.length == 3) {
            switch (sub) {
                case "edit" -> { return filter(ids, a[2]); }
                case "create", "crear" -> { return filter(TYPES, a[2]); }
                case "modo" -> { return filter(MODES, a[2]); }
                case "mirar", "look" -> { return filter(List.of("on", "off"), a[2]); }
                case "path" -> { return filter(PATH_SUBS, a[2]); }
                case "anim" -> { return filter(List.of("saludar", "golpear", "off"), a[2]); }
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
        if (a.length == 4) {
            if (sub.equals("efecto")) return filter(List.of("on", "off"), a[3]);
            if (sub.equals("path") && a[2].equalsIgnoreCase("modo")) return filter(PATH_MODES, a[3]);
            if (sub.equals("anim")) return filter(List.of("repetir"), a[3]);
        }
        return List.of();
    }

    private List<String> filter(List<String> list, String start) {
        String st = start.toLowerCase();
        return list.stream().filter(x -> x.toLowerCase().startsWith(st)).toList();
    }
}
