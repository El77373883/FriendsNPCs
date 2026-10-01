package com.friends.npcs;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class FriendsNPCs extends JavaPlugin {

    private NpcManager manager;

    public static Component c(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new NpcManager(this);
        manager.cleanOrphans();
        manager.load();
        manager.startTasks();

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
