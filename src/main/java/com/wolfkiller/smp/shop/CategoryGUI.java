package com.wolfkiller.smp.shop;

import com.wolfkiller.smp.WolfkillerSMP;
import net.milkbowl.vault.economy.EconomyResponse;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shows a paginated list of ShopItems for one category.
 * Left click  -> buy 1
 * Right click -> sell 1 (must have it in your inventory)
 */
public class CategoryGUI implements Listener {

    private static final int ITEMS_PER_PAGE = 45; // slots 0-44, row 6 (45-53) is the nav bar
    private static final int BACK_SLOT = 45;
    private static final int PREV_SLOT = 48;
    private static final int NEXT_SLOT = 50;
    private static final int CLOSE_SLOT = 53;

    private final WolfkillerSMP plugin;
    private final ShopManager shopManager;
    private ShopGUI shopGUI; // set after construction to avoid a circular constructor dependency

    private final Map<UUID, String> openCategory = new HashMap<>();
    private final Map<UUID, Integer> openPage = new HashMap<>();

    public CategoryGUI(WolfkillerSMP plugin, ShopManager shopManager) {
        this.plugin = plugin;
        this.shopManager = shopManager;
    }

    public void setShopGUI(ShopGUI shopGUI) {
        this.shopGUI = shopGUI;
    }

    public void open(Player player, String categoryKey, int page) {
        List<ShopItem> items = shopManager.getCategory(categoryKey);
        int maxPage = Math.max(0, (items.size() - 1) / ITEMS_PER_PAGE);
        page = Math.max(0, Math.min(page, maxPage));

        openCategory.put(player.getUniqueId(), categoryKey);
        openPage.put(player.getUniqueId(), page);

        String title = ChatColor.DARK_GREEN + categoryKey.replace("_", " ") + ChatColor.GRAY + " (" + (page + 1) + "/" + (maxPage + 1) + ")";
        Inventory gui = Bukkit.createInventory(null, 54, title);

        ItemStack filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 45; i < 54; i++) gui.setItem(i, filler);

        int start = page * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            int index = start + i;
            if (index >= items.size()) break;
            gui.setItem(i, buildItemDisplay(items.get(index)));
        }

        gui.setItem(BACK_SLOT, namedItem(Material.ARROW, ChatColor.YELLOW + "Back to Shop", null));
        if (page > 0) {
            gui.setItem(PREV_SLOT, namedItem(Material.PAPER, ChatColor.YELLOW + "< Previous Page", null));
        }
        if (page < maxPage) {
            gui.setItem(NEXT_SLOT, namedItem(Material.PAPER, ChatColor.YELLOW + "Next Page >", null));
        }
        gui.setItem(CLOSE_SLOT, namedItem(Material.BARRIER, ChatColor.RED + "Close", null));

        player.openInventory(gui);
    }

    private ItemStack buildItemDisplay(ShopItem shopItem) {
        List<String> lore = new ArrayList<>();
        lore.add(shopItem.isBuyable()
                ? ChatColor.GREEN + "Buy: " + ChatColor.GOLD + "$" + String.format("%.2f", shopItem.buy) + ChatColor.GRAY + " (left-click)"
                : ChatColor.DARK_GRAY + "Not purchasable");
        lore.add(shopItem.isSellable()
                ? ChatColor.AQUA + "Sell: " + ChatColor.GOLD + "$" + String.format("%.2f", shopItem.sell) + ChatColor.GRAY + " (right-click)"
                : ChatColor.DARK_GRAY + "Not sellable");

        ItemStack display = buildRealStack(shopItem, 1);
        ItemMeta meta = display.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE + displayName(shopItem));
        meta.setLore(lore);
        display.setItemMeta(meta);
        return display;
    }

    /**
     * Builds the actual obtainable ItemStack for a shop entry - for a plain
     * material this is just `new ItemStack(material)`, but for a SPAWNER or
     * POTION-family entry it also sets the real spawner mob type / potion
     * type so the item you receive (and the icon shown) is correct, not a
     * generic unlabelled spawner or a colourless water bottle.
     */
    private ItemStack buildRealStack(ShopItem shopItem, int amount) {
        ItemStack stack = new ItemStack(shopItem.material, amount);
        if (shopItem.variant == null) return stack;

        if (shopItem.material == Material.SPAWNER) {
            try {
                org.bukkit.inventory.meta.BlockStateMeta bsm = (org.bukkit.inventory.meta.BlockStateMeta) stack.getItemMeta();
                org.bukkit.block.CreatureSpawner cs = (org.bukkit.block.CreatureSpawner) bsm.getBlockState();
                cs.setSpawnedType(org.bukkit.entity.EntityType.valueOf(shopItem.variant.toUpperCase()));
                bsm.setBlockState(cs);
                stack.setItemMeta(bsm);
            } catch (Exception ignored) {
                // unknown entity type - fall back to a plain spawner rather than crashing
            }
            return stack;
        }

        if (stack.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta pm) {
            try {
                pm.setBasePotionType(org.bukkit.potion.PotionType.valueOf(shopItem.variant.toUpperCase()));
                stack.setItemMeta(pm);
            } catch (Exception ignored) {
                // unknown potion type - fall back to a plain potion rather than crashing
            }
        }
        return stack;
    }

    private String displayName(ShopItem shopItem) {
        String base = formatMaterial(shopItem.material);
        if (shopItem.variant == null) return base;

        String variantLabel = formatWords(shopItem.variant);
        if (shopItem.material == Material.SPAWNER) {
            return variantLabel + " Spawner";
        }
        return base + " of " + variantLabel;
    }

    private String formatWords(String raw) {
        String cleaned = raw.replace("_", " ").toLowerCase();
        StringBuilder sb = new StringBuilder();
        for (String word : cleaned.split(" ")) {
            if (word.isEmpty()) continue;
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String categoryKey = openCategory.get(player.getUniqueId());
        if (categoryKey == null) return;
        if (!event.getView().getTitle().startsWith(ChatColor.DARK_GREEN + categoryKey.replace("_", " "))) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();
        int page = openPage.getOrDefault(player.getUniqueId(), 0);

        if (slot == BACK_SLOT) {
            if (shopGUI != null) shopGUI.open(player);
            return;
        }
        if (slot == PREV_SLOT) {
            open(player, categoryKey, page - 1);
            return;
        }
        if (slot == NEXT_SLOT) {
            open(player, categoryKey, page + 1);
            return;
        }
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot < 0 || slot >= ITEMS_PER_PAGE) return;

        List<ShopItem> items = shopManager.getCategory(categoryKey);
        int index = page * ITEMS_PER_PAGE + slot;
        if (index >= items.size()) return;
        ShopItem shopItem = items.get(index);

        if (event.isRightClick()) {
            handleSell(player, shopItem);
        } else if (event.isLeftClick()) {
            handleBuy(player, shopItem);
        }
        open(player, categoryKey, page); // refresh (e.g. balance in header, if we add it later)
    }

    private void handleBuy(Player player, ShopItem shopItem) {
        if (!shopItem.isBuyable()) {
            player.sendMessage(ChatColor.RED + "You can't buy that.");
            return;
        }
        if (plugin.getEconomy().getBalance(player) < shopItem.buy) {
            player.sendMessage(ChatColor.RED + "You don't have enough money for that.");
            return;
        }
        EconomyResponse resp = plugin.getEconomy().withdrawPlayer(player, shopItem.buy);
        if (!resp.transactionSuccess()) {
            player.sendMessage(ChatColor.RED + "Purchase failed: " + resp.errorMessage);
            return;
        }
        player.getInventory().addItem(buildRealStack(shopItem, 1));
        player.sendMessage(ChatColor.GREEN + "Bought 1x " + displayName(shopItem)
                + " for $" + String.format("%.2f", shopItem.buy));
    }

    private void handleSell(Player player, ShopItem shopItem) {
        if (!shopItem.isSellable()) {
            player.sendMessage(ChatColor.RED + "You can't sell that here.");
            return;
        }
        ItemStack toRemove = buildRealStack(shopItem, 1);
        if (!player.getInventory().containsAtLeast(toRemove, 1)) {
            player.sendMessage(ChatColor.RED + "You don't have any " + displayName(shopItem) + ".");
            return;
        }
        player.getInventory().removeItem(toRemove);
        plugin.getEconomy().depositPlayer(player, shopItem.sell);
        player.sendMessage(ChatColor.GREEN + "Sold 1x " + displayName(shopItem)
                + " for $" + String.format("%.2f", shopItem.sell));
    }

    private String formatMaterial(Material material) {
        String name = material.name().replace("_", " ").toLowerCase();
        StringBuilder sb = new StringBuilder();
        for (String word : name.split(" ")) {
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
        }
        return sb.toString().trim();
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
