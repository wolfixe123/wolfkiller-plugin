package com.wolfkiller.smp.sell;

import com.wolfkiller.smp.WolfkillerSMP;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple GUI: shows how much the player's current inventory is worth
 * and lets them sell everything sellable with one click.
 * Later this can be expanded into a full DonutSMP-style category shop.
 */
public class SellGUI implements Listener {

    private static final String TITLE = ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "Sell Items";
    private static final int SELL_ALL_SLOT = 22;

    private final WolfkillerSMP plugin;
    private final SellManager sellManager;

    public SellGUI(WolfkillerSMP plugin, SellManager sellManager) {
        this.plugin = plugin;
        this.sellManager = sellManager;
    }

    public static void open(Player player, SellManager sellManager) {
        Inventory gui = org.bukkit.Bukkit.createInventory(null, 27, TITLE);

        ItemStack filler = namedItem(Material.GREEN_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            gui.setItem(i, filler);
        }

        double worth = calculateInventoryWorth(player, sellManager);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Sells every item in your inventory");
        lore.add(ChatColor.GRAY + "that is on the sell list.");
        lore.add("");
        lore.add(ChatColor.YELLOW + "Current value: " + ChatColor.GOLD + "$" + String.format("%.2f", worth));
        lore.add("");
        lore.add(ChatColor.GREEN + "Click to sell all!");

        ItemStack sellAll = namedItem(Material.EMERALD_BLOCK, ChatColor.GREEN + "" + ChatColor.BOLD + "SELL ALL", lore);
        gui.setItem(SELL_ALL_SLOT, sellAll);

        player.openInventory(gui);
    }

    private static double calculateInventoryWorth(Player player, SellManager sellManager) {
        double total = 0.0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null) continue;
            if (!sellManager.isSellable(item.getType())) continue;
            total += sellManager.getPrice(item.getType()) * item.getAmount();
        }
        return total;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(TITLE)) return;
        event.setCancelled(true);

        if (event.getRawSlot() != SELL_ALL_SLOT) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        double earned = sellManager.sellAllInInventory(player);

        if (earned <= 0) {
            player.sendMessage(ChatColor.RED + "You don't have anything sellable.");
        } else {
            player.sendMessage(ChatColor.GREEN + "Sold your items for " + ChatColor.GOLD
                    + "$" + String.format("%.2f", earned) + ChatColor.GREEN + "!");
        }
        player.closeInventory();
    }

    private static ItemStack namedItem(Material material, String name) {
        return namedItem(material, name, null);
    }

    private static ItemStack namedItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null) {
            meta.setLore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }
}
