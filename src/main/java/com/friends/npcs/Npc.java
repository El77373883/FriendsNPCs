package com.friends.npcs;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Zombie;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Npc {

    public final String id;
    public final String type; // "PLAYER" o un EntityType
    public String name;
    public String mode = "deambular";
    public Location home;
    public String worldName;

    public List<String> lines = new ArrayList<>();
    public String pose = "normal";
    public String glow = "off";
    public Set<String> effects = new HashSet<>();
    public String skinName, skinValue, skinSignature;

    public LivingEntity body;
    public Zombie brain;
    public TextDisplay holo;
    public long lastTry;

    public Npc(String id, String type, String name, Location home, String worldName) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.home = home;
        this.worldName = worldName;
    }

    public boolean isPlayer() {
        return type.equals("PLAYER");
    }

    /** La entidad que camina con pathfinder (y que carga el holograma). */
    public Mob mover() {
        if (isPlayer()) return brain;
        return body instanceof Mob m ? m : null;
    }
}
