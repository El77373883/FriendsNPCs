package com.friends.npcs;

import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.friends.npcs.FriendsNPCs.c;

public class NpcEquip {

    public static final Map<String, EquipmentSlot> SLOTS = new LinkedHashMap<>();

    static {
        SLOTS.put("casco", EquipmentSlot.HEAD);
        SLOTS.put("pechera", EquipmentSlot.CHEST);
        SLOTS.put("pantalones", EquipmentSlot.LEGS);
        SLOTS.put("botas", EquipmentSlot.FEET);
        SLOTS.put("mano", EquipmentSlot.HAND);
        SLOTS.put("mano2", EquipmentSlot.OFF_HAND);
    }

    private static final List<String> EXTRA = List.of("quitar", "copiar", "limpiar", "ver");

    // ---------- Aplicar al NPC ----------

    public static void apply(Npc n) {
        if (n.body == null) return;
        EntityEquipment eq = n.body.getEquipment();
        if (eq == null) return;
        for (Map.Entry<String, EquipmentSlot> en : SLOTS.entrySet()) {
            ItemStack it = n.equip.get(en.getKey());
            eq.setItem(en.getValue(), it == null ? new ItemStack(Material.AIR) : it.clone(), true);
            if (n.body instanceof Mob) eq.setDropChance(en.getValue(), 0f);
        }
    }

    // ---------- Guardado ----------

    public static void save(YamlConfiguration y, String p, Npc n) {
        for (Map.Entry<String, ItemStack> en : n.equip.entrySet()) {
            y.set(p + "equipo." + en.getKey(),
                    Base64.getEncoder().encodeToString(en.getValue().serializeAsBytes()));
        }
    }

    public static void load(ConfigurationSection s, Npc n) {
        n.equip.clear();
        for (String slot : SLOTS.keySet()) {
            String raw = s.getString("equipo." + slot);
            if (raw == null) continue;
            try {
                n.equip.put(slot, ItemStack.deserializeBytes(Base64.getDecoder().decode(raw)));
            } catch (Exception ignored) { }
        }
    }

    // ---------- Comando /fnpc equip ----------

    private static void set(Npc n, String slot, ItemStack it) {
        if (it == null || it.getType().isAir()) {
            n.equip.remove(slot);
            return;
        }
        ItemStack one = it.clone();
        one.setAmount(1);
        n.equip.put(slot, one);
    }

    public static void command(NpcManager mgr, CommandSender s, String[] a) {
        if (a.length < 3) {
            s.sendMessage(c("&eUso: /fnpc equip <id> <" + String.join("|", SLOTS.keySet()) + ">"));
            s.sendMessage(c("&7Pon en tu mano el item y usa el comando. Extras: &equitar <slot>&7, &ecopiar&7, &elimpiar&7, &ever"));
            return;
        }
        Npc n = mgr.get(a[1]);
        if (n == null) {
            s.sendMessage(c("&cNo existe ese NPC."));
            return;
        }
        String sub = a[2].toLowerCase();

        switch (sub) {
            case "ver" -> {
                if (n.equip.isEmpty()) {
                    s.sendMessage(c("&7Ese NPC no tiene equipo."));
                    return;
                }
                for (String slot : SLOTS.keySet()) {
                    ItemStack it = n.equip.get(slot);
                    if (it != null) {
                        s.sendMessage(c("&e" + slot + "&7: &f" + it.getType().name().toLowerCase().replace('_', ' ')));
                    }
                }
            }
            case "limpiar" -> {
                n.equip.clear();
                mgr.refresh(n);
                s.sendMessage(c("&aEquipo borrado."));
            }
            case "quitar" -> {
                String slot = a.length > 3 ? a[3].toLowerCase() : "";
                if (!SLOTS.containsKey(slot)) {
                    s.sendMessage(c("&eUso: /fnpc equip " + n.id + " quitar <" + String.join("|", SLOTS.keySet()) + ">"));
                    return;
                }
                n.equip.remove(slot);
                mgr.refresh(n);
                s.sendMessage(c("&aQuitado: &e" + slot));
            }
            case "copiar" -> {
                if (!(s instanceof Player p)) {
                    s.sendMessage(c("&cSolo jugadores."));
                    return;
                }
                PlayerInventory inv = p.getInventory();
                set(n, "casco", inv.getHelmet());
                set(n, "pechera", inv.getChestplate());
                set(n, "pantalones", inv.getLeggings());
                set(n, "botas", inv.getBoots());
                set(n, "mano", inv.getItemInMainHand());
                set(n, "mano2", inv.getItemInOffHand());
                mgr.refresh(n);
                s.sendMessage(c("&aEl NPC ahora lleva lo mismo que tu."));
            }
            default -> {
                if (!SLOTS.containsKey(sub)) {
                    s.sendMessage(c("&eUso: /fnpc equip " + n.id + " <" + String.join("|", SLOTS.keySet()) + ">"));
                    return;
                }
                if (!(s instanceof Player p)) {
                    s.sendMessage(c("&cSolo jugadores."));
                    return;
                }
                ItemStack held = p.getInventory().getItemInMainHand();
                if (held.getType().isAir()) {
                    s.sendMessage(c("&cTen el item en la mano. Para quitar usa &e/fnpc equip " + n.id + " quitar " + sub));
                    return;
                }
                set(n, sub, held);
                mgr.refresh(n);
                s.sendMessage(c("&aPuesto en &e" + sub + "&a."));
            }
        }
    }

    public static List<String> tab(NpcManager mgr, String[] a) {
        if (a.length == 2) return filter(mgr.all().stream().map(n -> n.id).toList(), a[1]);
        if (a.length == 3) {
            List<String> l = new ArrayList<>(SLOTS.keySet());
            l.addAll(EXTRA);
            return filter(l, a[2]);
        }
        if (a.length == 4 && a[2].equalsIgnoreCase("quitar")) {
            return filter(new ArrayList<>(SLOTS.keySet()), a[3]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> list, String start) {
        String st = start.toLowerCase();
        return list.stream().filter(x -> x.toLowerCase().startsWith(st)).toList();
    }
}
