package com.friends.npcs;

import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.persistence.PersistentDataType;

public class NpcListener implements Listener {

    private boolean isNpc(Entity e) {
        return e != null && e.getPersistentDataContainer()
                .has(NpcManager.KEY, PersistentDataType.STRING);
    }

    @EventHandler
    public void onTarget(EntityTargetEvent e) {
        // Los NPC no atacan y nadie los ataca
        if (isNpc(e.getEntity()) || isNpc(e.getTarget())) e.setCancelled(true);
    }

    @EventHandler
    public void onCombust(EntityCombustEvent e) {
        if (isNpc(e.getEntity())) e.setCancelled(true);
    }
}
