package com.friends.npcs;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.friends.npcs.FriendsNPCs.c;

public class NpcShop implements Listener {

    public enum Type { MAIN, CHOICE, QTY, CONFIRM, EDIT }

    public static class Holder implements InventoryHolder {
        final Type type;
        final String npcId;
        int page, index, qty = 1, pages = 1;
        boolean buy, locked, saved;
        Inventory inv;
        final Map<Integer, Integer> slots = new HashMap<>();

        Holder(Type type, String npcId) {
            this.type = type;
            this.npcId = npcId;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static class Prompt {
        final String npcId;
        final int idx, page;
        int stage = 0;
        double buy = -1;

        Prompt(String npcId, int idx, int page) {
            this.npcId = npcId;
            this.idx = idx;
            this.page = page;
        }
    }

    private static final int MAXQ = 2304;
    private static final int EDIT_SLOTS = 45;
    private static final List<String> SUBS =
            List.of("crear", "quitar", "editar", "items", "precio", "titulo", "filas", "color", "limpiar");
    private static final Map<String, String> COLORS = new LinkedHashMap<>();
    private static final List<String> RAINBOW =
            List.of("rojo", "naranja", "amarillo", "verde", "celeste", "azul", "morado", "rosa");

    static {
        COLORS.put("blanco", "WHITE");
        COLORS.put("naranja", "ORANGE");
        COLORS.put("magenta", "MAGENTA");
        COLORS.put("celeste", "LIGHT_BLUE");
        COLORS.put("amarillo", "YELLOW");
        COLORS.put("verde", "LIME");
        COLORS.put("rosa", "PINK");
        COLORS.put("gris", "GRAY");
        COLORS.put("gris_claro", "LIGHT_GRAY");
        COLORS.put("turquesa", "CYAN");
        COLORS.put("morado", "PURPLE");
        COLORS.put("azul", "BLUE");
        COLORS.put("marron", "BROWN");
        COLORS.put("verde_oscuro", "GREEN");
        COLORS.put("rojo", "RED");
        COLORS.put("negro", "BLACK");
    }

    private final FriendsNPCs plugin;
    private final NpcManager mgr;
    private final Eco eco = new Eco();
    private final NamespacedKey kBuy, kSell, kExtra;
    private final Map<UUID, Long> npcDebounce = new HashMap<>();
    private final Map<UUID, Long> guiDebounce = new HashMap<>();
    private final Map<UUID, Prompt> prompts = new HashMap<>();

    public NpcShop(FriendsNPCs plugin, NpcManager mgr) {
        this.plugin = plugin;
        this.mgr = mgr;
        kBuy = new NamespacedKey(plugin, "shop_buy");
        kSell = new NamespacedKey(plugin, "shop_sell");
        kExtra = new NamespacedKey(plugin, "shop_extra");
    }

    // =====================================================
    //  Guardado
    // =====================================================

    public static List<Map<String, Object>> serialize(List<ShopItem> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ShopItem si : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("item", Base64.getEncoder().encodeToString(si.item.serializeAsBytes()));
            m.put("compra", si.buy);
            m.put("venta", si.sell);
            out.add(m);
        }
        return out;
    }

    public static List<ShopItem> deserialize(List<Map<?, ?>> raw) {
        List<ShopItem> out = new ArrayList<>();
        for (Map<?, ?> m : raw) {
            try {
                ShopItem si = new ShopItem();
                si.item = ItemStack.deserializeBytes(Base64.getDecoder().decode(String.valueOf(m.get("item"))));
                si.buy = m.get("compra") instanceof Number nb ? nb.doubleValue() : -1;
                si.sell = m.get("venta") instanceof Number ns ? ns.doubleValue() : -1;
                out.add(si);
            } catch (Exception ignored) { }
        }
        return out;
    }

    // =====================================================
    //  Utilidades
    // =====================================================

    private void later(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }

    private void go(Holder h, Runnable r) {
        h.locked = true;
        later(r);
    }

    private static Component noItalic(Component comp) {
        return comp.decoration(TextDecoration.ITALIC, false);
    }

    private ItemStack named(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(noItalic(c(name)));
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(noItalic(c(s)));
            meta.lore(l);
        }
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack button(String mat, String name, String... lore) {
        return named(Material.valueOf(mat + "_STAINED_GLASS_PANE"), name, lore);
    }

    private ItemStack pane(Npc n, int slot) {
        String color = n.shopColor;
        if (color.equals("arcoiris")) color = RAINBOW.get(slot % RAINBOW.size());
        return button(COLORS.getOrDefault(color, "LIGHT_BLUE"), " ");
    }

    private void fill(Holder h, Npc n) {
        for (int i = 0; i < h.inv.getSize(); i++) h.inv.setItem(i, pane(n, i));
    }

    private ItemStack withLore(ItemStack base, String... lines) {
        ItemStack it = base.clone();
        ItemMeta m = it.getItemMeta();
        List<Component> lore = new ArrayList<Component>();
        if (m.lore() != null) lore.addAll(m.lore());
        for (String l : lines) lore.add(noItalic(c(l)));
        m.lore(lore);
        it.setItemMeta(m);
        return it;
    }

    private String itemName(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        if (m != null && m.hasDisplayName()) {
            return PlainTextComponentSerializer.plainText().serialize(m.displayName());
        }
        return it.getType().name().toLowerCase().replace('_', ' ');
    }

    private String money(double v) {
        return eco.format(v);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static Double parsePrice(String t) {
        t = t.trim().replace(',', '.');
        if (t.equalsIgnoreCase("no")) return -1.0;
        try {
            double v = Double.parseDouble(t);
            if (v > 0 && v <= 1_000_000_000_000.0) return v;
        } catch (NumberFormatException ignored) { }
        return null;
    }

    private int rowsOf(Npc n) {
        return Math.max(3, Math.min(6, n.shopRows));
    }

    private ShopItem item(Npc n, int i) {
        return i >= 0 && i < n.shopItems.size() ? n.shopItems.get(i) : null;
    }

    private Holder holder(Type t, Npc n, int page, int idx, boolean buy, int qty, int size, Component title) {
        Holder h = new Holder(t, n.id);
        h.page = page;
        h.index = idx;
        h.buy = buy;
        h.qty = qty;
        h.inv = Bukkit.createInventory(h, size, title);
        return h;
    }

    private void sound(Player p, Sound s) {
        p.playSound(p.getLocation(), s, 0.7f, 1f);
    }

    // =====================================================
    //  Menus del cliente
    // =====================================================

    private ItemStack display(ShopItem si, boolean hint) {
        List<String> l = new ArrayList<>();
        l.add(" ");
        l.add(si.buy >= 0 ? "&7Compra: &a" + money(si.buy) : "&7Compra: &8no disponible");
        l.add(si.sell >= 0 ? "&7Venta: &6" + money(si.sell) : "&7Venta: &8no disponible");
        if (hint) {
            l.add(" ");
            l.add("&eClic para elegir");
        }
        return withLore(si.item, l.toArray(new String[0]));
    }

    private List<Integer> visible(Npc n) {
        List<Integer> v = new ArrayList<>();
        for (int i = 0; i < n.shopItems.size(); i++) {
            ShopItem si = n.shopItems.get(i);
            if (si.buy >= 0 || si.sell >= 0) v.add(i);
        }
        return v;
    }

    public void openMain(Player p, Npc n, int page) {
        List<Integer> vis = visible(n);
        int rows = rowsOf(n);
        int per = (rows - 2) * 7;
        int pages = Math.max(1, (vis.size() + per - 1) / per);
        page = Math.max(0, Math.min(page, pages - 1));

        Holder h = holder(Type.MAIN, n, page, 0, true, 1, rows * 9, FriendsNPCs.rich(n.shopTitle));
        h.pages = pages;
        fill(h, n);

        int start = page * per;
        for (int i = 0; i < per && start + i < vis.size(); i++) {
            int slot = (i / 7 + 1) * 9 + (i % 7 + 1);
            int idx = vis.get(start + i);
            h.inv.setItem(slot, display(n.shopItems.get(idx), true));
            h.slots.put(slot, idx);
        }
        if (vis.isEmpty()) {
            h.inv.setItem((rows / 2) * 9 + 4, named(Material.BARRIER, "&cTienda vacia"));
        }
        int base = (rows - 1) * 9;
        if (page > 0) h.inv.setItem(base + 3, named(Material.ARROW, "&ePagina anterior"));
        if (page < pages - 1) h.inv.setItem(base + 5, named(Material.ARROW, "&ePagina siguiente"));
        if (pages > 1) h.inv.setItem(base + 4, named(Material.PAPER, "&7Pagina " + (page + 1) + "/" + pages));
        p.openInventory(h.inv);
    }

    private void openChoice(Player p, Npc n, int page, int idx) {
        ShopItem si = item(n, idx);
        if (si == null) {
            openMain(p, n, page);
            return;
        }
        Holder h = holder(Type.CHOICE, n, page, idx, true, 1, 27, c("&8¿Comprar o vender?"));
        fill(h, n);
        h.inv.setItem(13, display(si, false));
        h.inv.setItem(11, si.buy >= 0
                ? button("LIME", "&a&lCOMPRAR", "&7Precio: &a" + money(si.buy) + " &7c/u", " ", "&eClic para continuar")
                : button("GRAY", "&7Compra no disponible"));
        h.inv.setItem(15, si.sell >= 0
                ? button("ORANGE", "&6&lVENDER", "&7Te pagan: &6" + money(si.sell) + " &7c/u", " ", "&eClic para continuar")
                : button("GRAY", "&7Venta no disponible"));
        h.inv.setItem(22, named(Material.ARROW, "&cVolver"));
        p.openInventory(h.inv);
    }

    private ItemStack qtyItem(ShopItem si, boolean buy, int qty) {
        double unit = buy ? si.buy : si.sell;
        ItemStack it = si.item.clone();
        it.setAmount(Math.max(1, Math.min(it.getMaxStackSize(), qty)));
        return withLore(it, " ",
                "&7Cantidad: &f" + qty,
                "&7Precio c/u: &e" + money(unit),
                "&7Total: &e" + money(round2(unit * qty)));
    }

    private void openQty(Player p, Npc n, int page, int idx, boolean buy, int qty) {
        ShopItem si = item(n, idx);
        if (si == null) {
            openMain(p, n, page);
            return;
        }
        Holder h = holder(Type.QTY, n, page, idx, buy, qty, 27,
                c(buy ? "&8Cuantos quieres comprar?" : "&8Cuantos quieres vender?"));
        fill(h, n);
        h.inv.setItem(9, button("RED", "&c&l-64"));
        h.inv.setItem(10, button("RED", "&c&l-10"));
        h.inv.setItem(11, button("RED", "&c&l-1"));
        h.inv.setItem(13, qtyItem(si, buy, qty));
        h.inv.setItem(15, button("LIME", "&a&l+1"));
        h.inv.setItem(16, button("LIME", "&a&l+10"));
        h.inv.setItem(17, button("LIME", "&a&l+64"));
        h.inv.setItem(18, named(Material.ARROW, "&cVolver"));
        h.inv.setItem(22, button("LIME", "&a&lCONTINUAR"));
        p.openInventory(h.inv);
    }

    private void openConfirm(Player p, Npc n, int page, int idx, boolean buy, int qty) {
        ShopItem si = item(n, idx);
        if (si == null) {
            openMain(p, n, page);
            return;
        }
        Holder h = holder(Type.CONFIRM, n, page, idx, buy, qty, 27,
                c(buy ? "&8¿Seguro que quieres comprar?" : "&8¿Seguro que quieres vender?"));
        fill(h, n);
        h.inv.setItem(13, qtyItem(si, buy, qty));
        h.inv.setItem(11, button("LIME", "&a&lSÍ, CONFIRMAR",
                "&7" + (buy ? "Compras" : "Vendes") + " &fx" + qty));
        h.inv.setItem(15, button("RED", "&c&lNO, CANCELAR"));
        p.openInventory(h.inv);
    }

    // =====================================================
    //  Clics del cliente
    // =====================================================

    @EventHandler
    public void onNpcClick(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        String id = e.getRightClicked().getPersistentDataContainer()
                .get(NpcManager.KEY, PersistentDataType.STRING);
        if (id == null) return;
        Npc n = mgr.get(id);
        if (n == null || !n.shop) return;
        e.setCancelled(true);

        Player p = e.getPlayer();
        long now = System.currentTimeMillis();
        Long last = npcDebounce.get(p.getUniqueId());
        if (last != null && now - last < 400) return;
        npcDebounce.put(p.getUniqueId(), now);
        openMain(p, n, 0);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof Holder h)) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Npc n = mgr.get(h.npcId);

        if (h.type == Type.EDIT) {
            editClick(e, h, n, p);
            return;
        }
        e.setCancelled(true);
        if (n == null || !n.shop) {
            h.locked = true;
            later(p::closeInventory);
            return;
        }
        if (h.locked || e.getClickedInventory() != e.getView().getTopInventory()) return;

        long now = System.currentTimeMillis();
        Long last = guiDebounce.get(p.getUniqueId());
        if (last != null && now - last < 150) return;
        guiDebounce.put(p.getUniqueId(), now);

        int slot = e.getRawSlot();
        switch (h.type) {
            case MAIN -> mainClick(p, n, h, slot);
            case CHOICE -> choiceClick(p, n, h, slot);
            case QTY -> qtyClick(p, n, h, slot);
            case CONFIRM -> confirmClick(p, n, h, slot);
            default -> { }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof Holder h)) return;
        if (h.type != Type.EDIT) {
            e.setCancelled(true);
            return;
        }
        if (h.saved) {
            e.setCancelled(true);
            return;
        }
        for (int s : e.getRawSlots()) {
            if (s >= EDIT_SLOTS && s < 54) {
                e.setCancelled(true);
                return;
            }
        }
    }

    private void mainClick(Player p, Npc n, Holder h, int slot) {
        Integer idx = h.slots.get(slot);
        if (idx != null) {
            sound(p, Sound.UI_BUTTON_CLICK);
            go(h, () -> openChoice(p, n, h.page, idx));
            return;
        }
        int base = (rowsOf(n) - 1) * 9;
        if (slot == base + 3 && h.page > 0) {
            go(h, () -> openMain(p, n, h.page - 1));
        } else if (slot == base + 5 && h.page < h.pages - 1) {
            go(h, () -> openMain(p, n, h.page + 1));
        }
    }

    private void choiceClick(Player p, Npc n, Holder h, int slot) {
        ShopItem si = item(n, h.index);
        if (si == null) {
            go(h, () -> openMain(p, n, h.page));
            return;
        }
        if (slot == 11 && si.buy >= 0) {
            go(h, () -> openQty(p, n, h.page, h.index, true, 1));
        } else if (slot == 15 && si.sell >= 0) {
            go(h, () -> openQty(p, n, h.page, h.index, false, 1));
        } else if (slot == 22) {
            go(h, () -> openMain(p, n, h.page));
        }
    }

    private void qtyClick(Player p, Npc n, Holder h, int slot) {
        ShopItem si = item(n, h.index);
        if (si == null) {
            go(h, () -> openMain(p, n, h.page));
            return;
        }
        int d = switch (slot) {
            case 9 -> -64;
            case 10 -> -10;
            case 11 -> -1;
            case 15 -> 1;
            case 16 -> 10;
            case 17 -> 64;
            default -> 0;
        };
        if (d != 0) {
            h.qty = Math.max(1, Math.min(MAXQ, h.qty + d));
            h.inv.setItem(13, qtyItem(si, h.buy, h.qty));
            return;
        }
        if (slot == 22) {
            go(h, () -> openConfirm(p, n, h.page, h.index, h.buy, h.qty));
        } else if (slot == 18) {
            go(h, () -> openChoice(p, n, h.page, h.index));
        }
    }

    private void confirmClick(Player p, Npc n, Holder h, int slot) {
        if (slot == 11) {
            trade(p, n, h);
        } else if (slot == 15) {
            go(h, () -> openChoice(p, n, h.page, h.index));
        }
    }

    // =====================================================
    //  Compra y venta
    // =====================================================

    private void trade(Player p, Npc n, Holder h) {
        h.locked = true;
        ShopItem si = item(n, h.index);
        final String err;
        if (si == null) err = "&cEse item ya no esta disponible.";
        else err = h.buy ? buy(p, si, h.qty) : sell(p, si, h.qty);

        if (err != null) {
            p.sendMessage(c(err));
            sound(p, Sound.ENTITY_VILLAGER_NO);
        } else {
            double total = round2((h.buy ? si.buy : si.sell) * h.qty);
            p.sendMessage(c(h.buy
                    ? "&aCompraste &fx" + h.qty + " " + itemName(si.item) + " &apor &e" + money(total) + "&a."
                    : "&aVendiste &fx" + h.qty + " " + itemName(si.item) + " &ay recibiste &e" + money(total) + "&a."));
            sound(p, Sound.ENTITY_PLAYER_LEVELUP);
        }
        later(() -> {
            if (si != null && err != null) openChoice(p, n, h.page, h.index);
            else openMain(p, n, h.page);
        });
    }

    private boolean hasSpace(Player p, ItemStack item, int qty) {
        int max = item.getMaxStackSize();
        int room = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) {
            if (s == null || s.getType().isAir()) room += max;
            else if (s.isSimilar(item)) room += Math.max(0, max - s.getAmount());
            if (room >= qty) return true;
        }
        return room >= qty;
    }

    private void give(Player p, ItemStack item, int qty) {
        int max = item.getMaxStackSize();
        int left = qty;
        while (left > 0) {
            ItemStack s = item.clone();
            int a = Math.min(max, left);
            s.setAmount(a);
            left -= a;
            for (ItemStack rest : p.getInventory().addItem(s).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), rest);
            }
        }
    }

    /** Devuelve un mensaje de error, o null si salio bien. */
    private String buy(Player p, ShopItem si, int qty) {
        if (!eco.available()) return "&cLa economia no esta disponible (falta Vault).";
        if (si.buy < 0) return "&cEste item no se puede comprar.";
        double total = round2(si.buy * qty);
        if (!eco.has(p, total)) return "&cNo tienes suficiente dinero. Necesitas &e" + money(total) + "&c.";
        if (!hasSpace(p, si.item, qty)) return "&cNo tienes espacio en el inventario.";
        if (!eco.withdraw(p, total)) return "&cNo se pudo cobrar. No se entrego nada.";
        give(p, si.item, qty);
        return null;
    }

    private String sell(Player p, ShopItem si, int qty) {
        if (!eco.available()) return "&cLa economia no esta disponible (falta Vault).";
        if (si.sell < 0) return "&cEste item no se puede vender.";
        int have = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) {
            if (s != null && s.isSimilar(si.item)) have += s.getAmount();
        }
        if (have < qty) return "&cNo tienes suficientes items (tienes &e" + have + "&c).";

        int left = qty;
        ItemStack[] cont = p.getInventory().getStorageContents();
        for (int i = 0; i < cont.length && left > 0; i++) {
            ItemStack s = cont[i];
            if (s == null || !s.isSimilar(si.item)) continue;
            int t = Math.min(left, s.getAmount());
            if (t == s.getAmount()) cont[i] = null;
            else s.setAmount(s.getAmount() - t);
            left -= t;
        }
        p.getInventory().setStorageContents(cont);

        double total = round2(si.sell * qty);
        if (!eco.deposit(p, total)) {
            give(p, si.item, qty);
            return "&cNo se pudo pagar. Se devolvieron tus items.";
        }
        return null;
    }

    // =====================================================
    //  Editor (arrastrar items)
    // =====================================================

    private boolean isTagged(ItemStack s) {
        return s != null && !s.getType().isAir() && s.hasItemMeta()
                && s.getItemMeta().getPersistentDataContainer().has(kExtra, PersistentDataType.INTEGER);
    }

    private ItemStack clean(ItemStack s, boolean one) {
        ItemStack it = s.clone();
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            PersistentDataContainer d = m.getPersistentDataContainer();
            Integer extra = d.get(kExtra, PersistentDataType.INTEGER);
            if (extra != null) {
                List<Component> lore = m.lore();
                if (lore != null) {
                    int keep = Math.max(0, lore.size() - extra);
                    List<Component> nl = new ArrayList<Component>(lore.subList(0, keep));
                    m.lore(nl.isEmpty() ? null : nl);
                }
                d.remove(kExtra);
                d.remove(kBuy);
                d.remove(kSell);
                it.setItemMeta(m);
            }
        }
        if (one) it.setAmount(1);
        return it;
    }

    private ItemStack editDisplay(ShopItem si) {
        String[] extra = {
                " ",
                si.buy >= 0 ? "&7Compra: &a" + money(si.buy) : "&7Compra: &8no",
                si.sell >= 0 ? "&7Venta: &6" + money(si.sell) : "&7Venta: &8no",
                "&eClic derecho: poner precios"
        };
        ItemStack it = withLore(si.item, extra);
        it.setAmount(1);
        ItemMeta m = it.getItemMeta();
        PersistentDataContainer d = m.getPersistentDataContainer();
        d.set(kBuy, PersistentDataType.DOUBLE, si.buy);
        d.set(kSell, PersistentDataType.DOUBLE, si.sell);
        d.set(kExtra, PersistentDataType.INTEGER, extra.length);
        it.setItemMeta(m);
        return it;
    }

    public void openEdit(Player p, Npc n, int page) {
        Holder h = holder(Type.EDIT, n, page, 0, true, 1, 54,
                c("&8Editor: &6" + n.id + " &8(pag " + (page + 1) + ")"));
        int start = page * EDIT_SLOTS;
        for (int i = 0; i < EDIT_SLOTS && start + i < n.shopItems.size(); i++) {
            h.inv.setItem(i, editDisplay(n.shopItems.get(start + i)));
        }
        for (int s = EDIT_SLOTS; s < 54; s++) h.inv.setItem(s, button("GRAY", " "));
        if (page > 0) h.inv.setItem(48, named(Material.ARROW, "&ePagina anterior"));
        h.inv.setItem(49, named(Material.PAPER, "&6Editor de tienda",
                "&7Arrastra items aqui para venderlos.",
                "&7Clic derecho en un item: precios.",
                "&7Saca un item para quitarlo.",
                "&7Cerrar = guardar."));
        if ((page + 1) * EDIT_SLOTS <= n.shopItems.size()) {
            h.inv.setItem(50, named(Material.ARROW, "&ePagina siguiente"));
        }
        p.openInventory(h.inv);
    }

    private void saveEdit(Player p, Holder h, Npc n) {
        if (h.saved) return;
        h.saved = true;

        List<ShopItem> slice = new ArrayList<>();
        for (int i = 0; i < EDIT_SLOTS; i++) {
            ItemStack s = h.inv.getItem(i);
            if (s == null || s.getType().isAir()) continue;
            ShopItem si = new ShopItem();
            si.item = clean(s, true);
            if (isTagged(s)) {
                PersistentDataContainer d = s.getItemMeta().getPersistentDataContainer();
                Double b = d.get(kBuy, PersistentDataType.DOUBLE);
                Double sl = d.get(kSell, PersistentDataType.DOUBLE);
                si.buy = b == null ? -1 : b;
                si.sell = sl == null ? -1 : sl;
            } else {
                // item nuevo: la tienda guarda una copia y se te devuelve el stack original
                for (ItemStack rest : p.getInventory().addItem(s.clone()).values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), rest);
                }
            }
            slice.add(si);
        }

        int start = h.page * EDIT_SLOTS;
        List<ShopItem> all = n.shopItems;
        List<ShopItem> result = new ArrayList<>(all.subList(0, Math.min(start, all.size())));
        result.addAll(slice);
        if (start + EDIT_SLOTS < all.size()) result.addAll(all.subList(start + EDIT_SLOTS, all.size()));
        n.shopItems = result;
        mgr.save();
    }

    private void cleanPlayer(Player p) {
        ItemStack cur = p.getItemOnCursor();
        if (isTagged(cur)) p.setItemOnCursor(clean(cur, false));
        later(() -> {
            if (!p.isOnline()) return;
            ItemStack[] cont = p.getInventory().getContents();
            for (int i = 0; i < cont.length; i++) {
                if (isTagged(cont[i])) p.getInventory().setItem(i, clean(cont[i], false));
            }
        });
    }

    private void editClick(InventoryClickEvent e, Holder h, Npc n, Player p) {
        if (n == null || h.saved) {
            e.setCancelled(true);
            return;
        }
        int raw = e.getRawSlot();

        if (raw >= EDIT_SLOTS && raw < 54) {
            e.setCancelled(true);
            if (raw == 48 && h.page > 0) editNav(p, n, h, h.page - 1);
            else if (raw == 50 && (h.page + 1) * EDIT_SLOTS <= n.shopItems.size()) editNav(p, n, h, h.page + 1);
            return;
        }
        if (raw >= 0 && raw < EDIT_SLOTS && e.getClick() == ClickType.RIGHT
                && e.getCursor().getType().isAir()) {
            ItemStack cur = e.getCurrentItem();
            if (cur != null && !cur.getType().isAir()) {
                e.setCancelled(true);
                startPrice(p, n, h, raw);
            }
        }
    }

    private void editNav(Player p, Npc n, Holder h, int newPage) {
        saveEdit(p, h, n);
        later(() -> openEdit(p, n, newPage));
    }

    // ---------- Precios por chat ----------

    private void startPrice(Player p, Npc n, Holder h, int raw) {
        int cnt = 0;
        for (int i = 0; i < raw; i++) {
            ItemStack s = h.inv.getItem(i);
            if (s != null && !s.getType().isAir()) cnt++;
        }
        int idx = h.page * EDIT_SLOTS + cnt;
        saveEdit(p, h, n);
        if (idx >= n.shopItems.size()) return;

        Prompt pr = new Prompt(n.id, idx, h.page);
        prompts.put(p.getUniqueId(), pr);
        later(() -> {
            p.closeInventory();
            ask(p, n, pr);
        });
    }

    private void ask(Player p, Npc n, Prompt pr) {
        ShopItem si = item(n, pr.idx);
        String name = si == null ? "?" : itemName(si.item);
        if (pr.stage == 0) {
            p.sendMessage(c("&6Precio de COMPRA de &f" + name + " &6(lo que paga el jugador por 1)."));
        } else {
            p.sendMessage(c("&6Precio de VENTA de &f" + name + " &6(lo que le pagas al jugador por 1)."));
        }
        p.sendMessage(c("&7Escribe un numero, &eno &7para desactivarlo, o &ecancelar&7."));
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (!prompts.containsKey(p.getUniqueId())) return;
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message());
        later(() -> handlePrompt(p, text));
    }

    private void handlePrompt(Player p, String text) {
        Prompt pr = prompts.get(p.getUniqueId());
        if (pr == null) return;
        Npc n = mgr.get(pr.npcId);
        if (n == null || !p.isOnline()) {
            prompts.remove(p.getUniqueId());
            return;
        }
        if (text.trim().equalsIgnoreCase("cancelar")) {
            prompts.remove(p.getUniqueId());
            openEdit(p, n, pr.page);
            return;
        }
        Double v = parsePrice(text);
        if (v == null) {
            p.sendMessage(c("&cNumero invalido. Escribe un numero mayor que 0, &eno &co &ecancelar&c."));
            return;
        }
        if (pr.stage == 0) {
            pr.buy = v;
            pr.stage = 1;
            ask(p, n, pr);
            return;
        }
        prompts.remove(p.getUniqueId());
        ShopItem si = item(n, pr.idx);
        if (si != null) {
            si.buy = pr.buy;
            si.sell = v;
            mgr.save();
            p.sendMessage(c("&aPrecios guardados."));
        }
        openEdit(p, n, pr.page);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof Holder h)) return;
        if (h.type != Type.EDIT || !(e.getPlayer() instanceof Player p)) return;
        Npc n = mgr.get(h.npcId);
        if (n != null) saveEdit(p, h, n);
        cleanPlayer(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        prompts.remove(e.getPlayer().getUniqueId());
        guiDebounce.remove(e.getPlayer().getUniqueId());
        npcDebounce.remove(e.getPlayer().getUniqueId());
    }

    // =====================================================
    //  Comando /fnpc shop
    // =====================================================

    public void command(CommandSender s, String[] a) {
        if (a.length < 3) {
            s.sendMessage(c("&eUso: /fnpc shop <id> <" + String.join("|", SUBS) + ">"));
            return;
        }
        Npc n = mgr.get(a[1]);
        if (n == null) {
            s.sendMessage(c("&cNo existe ese NPC."));
            return;
        }
        switch (a[2].toLowerCase()) {
            case "crear" -> {
                n.shop = true;
                mgr.save();
                s.sendMessage(c("&aTienda activada en &e" + n.id + "&a. Pon items con &e/fnpc shop " + n.id + " editar"));
                if (!eco.available()) {
                    s.sendMessage(c("&eNo se detecta Vault o un plugin de economia: nadie podra comprar ni vender hasta instalarlos."));
                }
            }
            case "quitar" -> {
                n.shop = false;
                mgr.save();
                s.sendMessage(c("&aTienda quitada. Los items se conservan por si la vuelves a activar."));
            }
            case "editar" -> {
                if (!(s instanceof Player p)) {
                    s.sendMessage(c("&cSolo jugadores."));
                    return;
                }
                if (!n.shop) {
                    s.sendMessage(c("&cPrimero usa /fnpc shop " + n.id + " crear"));
                    return;
                }
                int page = 0;
                if (a.length > 3) {
                    try {
                        page = Math.max(0, Integer.parseInt(a[3]) - 1);
                    } catch (NumberFormatException ignored) { }
                }
                if (page * EDIT_SLOTS > n.shopItems.size()) page = n.shopItems.size() / EDIT_SLOTS;
                openEdit(p, n, page);
            }
            case "items" -> {
                if (n.shopItems.isEmpty()) {
                    s.sendMessage(c("&7Esa tienda no tiene items."));
                    return;
                }
                for (int i = 0; i < n.shopItems.size(); i++) {
                    ShopItem si = n.shopItems.get(i);
                    s.sendMessage(c("&e" + (i + 1) + "&7: &f" + itemName(si.item)
                            + " &7compra: &a" + (si.buy >= 0 ? money(si.buy) : "no")
                            + " &7venta: &6" + (si.sell >= 0 ? money(si.sell) : "no")));
                }
            }
            case "precio" -> {
                if (a.length < 6) {
                    s.sendMessage(c("&eUso: /fnpc shop " + n.id + " precio <numero> <compra|no> <venta|no>"));
                    return;
                }
                ShopItem si;
                try {
                    si = item(n, Integer.parseInt(a[3]) - 1);
                } catch (NumberFormatException e) {
                    si = null;
                }
                Double b = parsePrice(a[4]);
                Double v = parsePrice(a[5]);
                if (si == null || b == null || v == null) {
                    s.sendMessage(c("&cDatos invalidos. Mira los numeros con /fnpc shop " + n.id + " items"));
                    return;
                }
                si.buy = b;
                si.sell = v;
                mgr.save();
                s.sendMessage(c("&aPrecios actualizados."));
            }
            case "titulo" -> {
                if (a.length < 4) {
                    s.sendMessage(c("&eUso: /fnpc shop " + n.id + " titulo <texto>"));
                    return;
                }
                n.shopTitle = String.join(" ", Arrays.copyOfRange(a, 3, a.length));
                mgr.save();
                s.sendMessage(c("&aTitulo cambiado."));
            }
            case "filas" -> {
                try {
                    int v = Integer.parseInt(a[3]);
                    if (v < 3 || v > 6) throw new NumberFormatException();
                    n.shopRows = v;
                    mgr.save();
                    s.sendMessage(c("&aFilas: &e" + v));
                } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                    s.sendMessage(c("&eUso: /fnpc shop " + n.id + " filas <3-6>"));
                }
            }
            case "color" -> {
                String v = a.length > 3 ? a[3].toLowerCase() : "";
                if (!v.equals("arcoiris") && !COLORS.containsKey(v)) {
                    s.sendMessage(c("&eUso: /fnpc shop " + n.id + " color <color|arcoiris>"));
                    s.sendMessage(c("&7Colores: " + String.join(", ", COLORS.keySet())));
                    return;
                }
                n.shopColor = v;
                mgr.save();
                s.sendMessage(c("&aColor de los vidrios: &e" + v));
            }
            case "limpiar" -> {
                n.shopItems.clear();
                mgr.save();
                s.sendMessage(c("&aTodos los items de la tienda fueron borrados."));
            }
            default -> s.sendMessage(c("&eUso: /fnpc shop <id> <" + String.join("|", SUBS) + ">"));
        }
    }

    public List<String> tab(String[] a) {
        if (a.length == 2) return filter(mgr.all().stream().map(n -> n.id).toList(), a[1]);
        if (a.length == 3) return filter(SUBS, a[2]);
        if (a.length == 4 && a[2].equalsIgnoreCase("color")) {
            List<String> l = new ArrayList<>(COLORS.keySet());
            l.add("arcoiris");
            return filter(l, a[3]);
        }
        if (a.length == 4 && a[2].equalsIgnoreCase("filas")) return filter(List.of("3", "4", "5", "6"), a[3]);
        if (a.length >= 5 && a[2].equalsIgnoreCase("precio")) return filter(List.of("no"), a[a.length - 1]);
        return List.of();
    }

    private List<String> filter(List<String> list, String start) {
        String st = start.toLowerCase();
        return list.stream().filter(x -> x.toLowerCase().startsWith(st)).toList();
    }
}
