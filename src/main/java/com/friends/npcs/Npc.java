package com.friends.npcs;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Zombie;

public class Npc {

    public final String id;
    public final String type; // "PLAYER" o el nombre de un EntityType
    public String name;
    public String mode = "deambular";
    public Location home;
    public String worldName;

    public LivingEntity body; // lo que se ve
    public Zombie brain;      // cerebro invisible (solo tipo PLAYER)
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

    /** La entidad que camina con pathfinder. */
    public Mob mover() {
        if (isPlayer()) return brain;
        return body instanceof Mob m ? m : null;
    }
}
