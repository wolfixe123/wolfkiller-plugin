package com.wolfkiller.smp.shop;

import com.wolfkiller.smp.WolfkillerSMP;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Main /shop menu. Left side = combat gear (PVP / Gear / Tools),
 * middle = building materials, right side = enchants/spawners/misc.
 * A player-head "profile" icon in the top-middle shows name + balance.
 */
public class ShopGUI implements Listener {

    private static final String TITLE = ChatColor.GOLD + "" + ChatColor.BOLD + "Wolfkiller Shop";
    private static final int PROFILE_SLOT = 4;

    // slot -> {displayName, icon material, category key (null = "coming soon" placeholder)}
    private static final Map<Integer, MenuEntry> MENU = new LinkedHashMap<>();

    static {
        // left side: combat
        MENU.put(10, new MenuEntry("PVP Kits", Material.NETHERITE_SWORD, null));
        MENU.put(19, new MenuEntry("Gear (Armor)", Material.DIAMOND_CHESTPLATE, "Gear"));
        MENU.put(28, new MenuEntry("Tools", Material.DIAMOND_PICKAXE, "Gear"));

        // middle: blocks & building
        MENU.put(12, new MenuEntry("Ores", Material.DIAMOND_ORE, "Ores"));
        MENU.put(13, new MenuEntry("Blocks", Material.STONE, "Blocks"));
        MENU.put(14, new MenuEntry("Decoration", Material.FLOWER_POT, "Decoration"));
        MENU.put(21, new MenuEntry("Farming", Material.WHEAT, "Farming"));
        MENU.put(22, new MenuEntry("Food", Material.COOKED_BEEF, "Food"));
        MENU.put(23, new MenuEntry("Mob Drops", Material.BONE, "Mobs"));
        MENU.put(30, new MenuEntry("All Items", Material.CHEST, "Z_EverythingElse"));
        MENU.put(31, new MenuEntry("Redstone", Material.REDSTONE, "Redstone"));
        MENU.put(32, new MenuEntry("Workstations", Material.CRAFTING_TABLE, "Workstations"));

        // right side: enchants / spawners / misc
        MENU.put(15, new MenuEntry("Enchanting", Material.ENCHANTED_BOOK, "Enchanting"));
        MENU.put(16, new MenuEntry("Spawners", Material.SPAWNER, "Spawners"));
        MENU.put(24, new MenuEntry("Potions", Material.POTION, "Potions"));
        MENU.put(25, new MenuEntry("Music Discs", Material.MUSIC_DISC_CAT, "Music"));
        MENU.put(33, new MenuEntry("Misc", Material.NETHER_STAR, "Miscellaneous"));
    }

    private record MenuEntry(String displayName, Material icon, String categoryKey) {}

    private final WolfkillerSMP plugin;
    private final ShopManager shopManager;
    private final CategoryGUI categoryGUI;

    public ShopGUI(WolfkillerSMP plugin, ShopManager shopManager, CategoryGUI categoryGUI) {
        this.plugin = plugin;
        this.shopManager = shopManager;
        this.categoryGUI = categoryGUI;
    }

    public void open(Player player) {
        Inventory gui = Bukkit.createInventory(null, 45, TITLE);

        ItemStack filler = namedItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 45; i++) gui.setItem(i, filler);

        gui.setItem(PROFILE_SLOT, buildProfileHead(player));

        for (Map.Entry<Integer, MenuEntry> entry : MENU.entrySet()) {
            MenuEntry menuEntry = entry.getValue();
            List<String> lore = new ArrayList<>();
            if (menuEntry.categoryKey() == null) {
                lore.add(ChatColor.GRAY + "Coming soon!");
            } else {
                lore.add(ChatColor.GRAY + "Click to browse this category.");
                lore.add(ChatColor.DARK_GRAY + (shopManager.getCategory(menuEntry.categoryKey()).size()) + " items");
            }
            gui.setItem(entry.getKey(), namedItem(menuEntry.icon(), ChatColor.YELLOW + "" + ChatColor.BOLD + menuEntry.displayName(), lore));
        }

        player.openInventory(gui);
    }

    private ItemStack buildProfileHead(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(player);
        meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + player.getName());

        double balance = plugin.getEconomy() != null ? plugin.getEconomy().getBalance(player) : 0;
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Balance: " + ChatColor.GOLD + "$" + String.format("%.2f", balance));
        meta.setLore(lore);

        head.setItemMeta(meta);
        return head;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(TITLE)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        MenuEntry entry = MENU.get(event.getRawSlot());
        if (entry == null) return;

        if (entry.categoryKey() == null) {
            player.sendMessage(ChatColor.YELLOW + entry.displayName() + " isn't ready yet — coming soon!");
            return;
        }
        categoryGUI.open(player, entry.categoryKey(), 0);
    }

    private ItemStack namedItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null) meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
