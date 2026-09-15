package com.wolfkiller.smp.sell;

import com.wolfkiller.smp.WolfkillerSMP;
import com.wolfkiller.smp.shop.ShopManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * /sellgui - opens a big (54-slot, double-chest sized) empty box.
 * Drag whatever you want to sell into it, then just close the GUI:
 * anything sellable is sold automatically, anything that isn't
 * sellable is handed straight back to you.
 */
public class BulkSellGUI implements Listener {

    private static final String TITLE = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Quick Sell - drop items, then close";

    /** Marks an inventory as one of our bulk-sell boxes so onClose can find it reliably. */
    private static class BulkSellHolder implements InventoryHolder {
        private Inventory inventory;
        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final WolfkillerSMP plugin;
    private final ShopManager shopManager;

    public BulkSellGUI(WolfkillerSMP plugin, ShopManager shopManager) {
        this.plugin = plugin;
        this.shopManager = shopManager;
    }

    public void open(Player player) {
        BulkSellHolder holder = new BulkSellHolder();
        Inventory gui = Bukkit.createInventory(holder, 54, TITLE);
        holder.inventory = gui;
        player.openInventory(gui);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof BulkSellHolder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        double total = 0.0;
        int itemsReturned = 0;

        for (ItemStack item : event.getInventory().getContents()) {
            if (item == null) continue;

            double price = shopManager.getSellPrice(item);
            if (price > 0) {
                total += price * item.getAmount();
            } else {
                // not sellable - give it back
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                for (ItemStack drop : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
                itemsReturned++;
            }
        }

        if (total > 0) {
            plugin.getEconomy().depositPlayer(player, total);
            player.sendMessage(ChatColor.GREEN + "Sold your items for " + ChatColor.GOLD
                    + "$" + String.format("%.2f", total) + ChatColor.GREEN + "!");
        }
        if (itemsReturned > 0) {
            player.sendMessage(ChatColor.YELLOW + "Some items weren't sellable, so they were given back to you.");
        }
        if (total <= 0 && itemsReturned == 0) {
            player.sendMessage(ChatColor.GRAY + "No items to sell.");
        }
    }
}
