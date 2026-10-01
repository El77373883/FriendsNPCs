package com.friends.npcs;

import org.bukkit.inventory.ItemStack;

public class ShopItem {
    public ItemStack item;      // siempre con cantidad 1
    public double buy = -1;     // lo que paga el jugador por 1 (-1 = no se vende)
    public double sell = -1;    // lo que recibe el jugador por 1 (-1 = no se compra)
}
