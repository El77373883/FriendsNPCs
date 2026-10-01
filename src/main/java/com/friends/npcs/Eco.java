package com.friends.npcs;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Locale;

public class Eco {

    private Class<?> cls;
    private Object provider;

    private Class<?> findClass() {
        try {
            return Class.forName("net.milkbowl.vault.economy.Economy");
        } catch (Throwable ignored) { }
        Plugin v = Bukkit.getPluginManager().getPlugin("Vault");
        if (v != null) {
            try {
                return v.getClass().getClassLoader().loadClass("net.milkbowl.vault.economy.Economy");
            } catch (Throwable ignored) { }
        }
        return null;
    }

    public boolean available() {
        if (cls == null) cls = findClass();
        if (cls == null) return false;
        if (provider == null) {
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(cls);
            if (rsp != null) provider = rsp.getProvider();
        }
        return provider != null;
    }

    public boolean has(OfflinePlayer p, double amount) {
        if (!available()) return false;
        try {
            return (boolean) cls.getMethod("has", OfflinePlayer.class, double.class).invoke(provider, p, amount);
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean withdraw(OfflinePlayer p, double amount) {
        return transaction("withdrawPlayer", p, amount);
    }

    public boolean deposit(OfflinePlayer p, double amount) {
        return transaction("depositPlayer", p, amount);
    }

    private boolean transaction(String method, OfflinePlayer p, double amount) {
        if (!available()) return false;
        try {
            Object r = cls.getMethod(method, OfflinePlayer.class, double.class).invoke(provider, p, amount);
            return (boolean) r.getClass().getMethod("transactionSuccess").invoke(r);
        } catch (Throwable t) {
            return false;
        }
    }

    public String format(double amount) {
        if (available()) {
            try {
                return (String) cls.getMethod("format", double.class).invoke(provider, amount);
            } catch (Throwable ignored) { }
        }
        return String.format(Locale.US, "$%,.2f", amount);
    }
}
