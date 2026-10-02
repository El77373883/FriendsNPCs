package com.friends.npcs;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class FriendsNPCs extends JavaPlugin {
    public NpcShop shop;
    public PoliceManager police;

    private NpcManager manager;

    /** Texto con codigos &a, &l, etc. */
    public static Component c(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
    }

    /** Si el texto tiene <tags> usa MiniMessage (degradados), si no usa & clasico. */
    public static Component rich(String s) {
        if (s.contains("<")) return MiniMessage.miniMessage().deserialize(s);
        return c(s);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new NpcManager(this);
        manager.cleanOrphans();
        manager.load();
        manager.startTasks();
        new NpcBehavior(this, manager).start();
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getPluginManager().registerEvents(new NpcActions(this, manager), this);
        shop = new NpcShop(this, manager);
        getServer().getPluginManager().registerEvents(shop, this);

        // Parte 6: policia
        police = new PoliceManager(this);
        police.start();

        getServer().getPluginManager().registerEvents(new NpcListener(), this);

        FnpcCommand cmd = new FnpcCommand(this, manager);
        PluginCommand pc = getCommand("fnpc");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }
        banner();
    }

    @Override
    public void onDisable() {
        if (police != null) police.save();
        if (manager != null) {
            manager.save();
            manager.removeAll();
        }
    }

    private void banner() {
        var out = Bukkit.getConsoleSender();
        out.sendMessage(c("&b&m                                          "));
        out.sendMessage(c("&6&l  ★ FriendsNPCs v" + getPluginMeta().getVersion() + " ★"));
        out.sendMessage(c("&7  NPCs con skin y movimiento real"));
        out.sendMessage(c("&f  Hecho por: &esoyadrianyt001"));
        for (String k : new String[]{"youtube", "tiktok", "discord", "instagram"}) {
            String v = getConfig().getString("redes." + k, "");
            if (v != null && !v.isBlank()) out.sendMessage(c("&f  " + k + ": &b" + v));
        }
        out.sendMessage(c("&b&m                                          "));
    }
}
