package com.friends.npcs;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Npc {

    public final String id;
    public final String type; // "PLAYER" o un EntityType
    public String name;
    public String mode = "quieto"; // quieto | deambular | patrullar | seguir
    public Location home;
    public String worldName;

    public List<String> lines = new ArrayList<>();
    public String pose = "normal";
    public String glow = "off";
    public Set<String> effects = new HashSet<>();
    public String skinName, skinValue, skinSignature;

    // Ruta
    public List<String> path = new ArrayList<>(); // "mundo,x,y,z"
    public String pathMode = "loop";              // loop | pingpong | una_vez
    public double pathSpeed = 1.0;
    public int pathWait = 3;
    public boolean pathRunning = true;

    // Extras
    public boolean look = false;
    public Map<String, ItemStack> equip = new LinkedHashMap<>();

    // Acciones al hacer clic
    public List<String> actions = new ArrayList<>();
    public String clickType = "derecho"; // izquierdo | derecho | ambos
    public int cooldown = 0;
    public String clickPerm = "";
    public final Map<UUID, Long> lastUse = new HashMap<>();

    // Tienda
    public boolean shop = false;
    public String shopTitle = "&6&lTienda";
    public int shopRows = 5;
    public String shopColor = "celeste";
    public List<ShopItem> shopItems = new ArrayList<>();

    public String anim = "off";
    public int animEvery = 0;

    // Datos temporales (no se guardan)
    public int pathIndex = 0, pathDir = 1;
    public boolean pathDone = false;
    public long waitUntil, stuckSince, lastAnim;
    public double lastDist = Double.MAX_VALUE;
    public Location gotoTarget;
    public long gotoUntil;

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
