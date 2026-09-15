package com.wolfkiller.smp.sell;

import com.wolfkiller.smp.WolfkillerSMP;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads sell-prices.yml (material -> price per item) and handles
 * the actual "give money, remove items" logic used by both the
 * /sell GUI and /sellhand command.
 */
public class SellManager {

    private final WolfkillerSMP plugin;
    private final Map<Material, Double> prices = new HashMap<>();
    private File file;

    public SellManager(WolfkillerSMP plugin) {
        this.plugin = plugin;
        loadPrices();
    }

    private void loadPrices() {
        file = new File(plugin.getDataFolder(), "sell-prices.yml");

        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            try (InputStream in = plugin.getResource("sell-prices.yml")) {
                if (in != null) {
                    java.nio.file.Files.copy(in, file.toPath());
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Could not create default sell-prices.yml: " + e.getMessage());
            }
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        prices.clear();
        if (config.isConfigurationSection("prices")) {
            for (String key : config.getConfigurationSection("prices").getKeys(false)) {
                try {
                    Material mat = Material.valueOf(key.toUpperCase());
                    double price = config.getDouble("prices." + key);
                    prices.put(mat, price);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Unknown material in sell-prices.yml: " + key);
                }
            }
        }
        plugin.getLogger().info("Loaded " + prices.size() + " sellable items.");
    }

    public void reload() {
        loadPrices();
    }

    public boolean isSellable(Material material) {
        return prices.containsKey(material);
    }

    public double getPrice(Material material) {
        return prices.getOrDefault(material, 0.0);
    }

    /**
     * Sells every sellable item currently in the player's main inventory
     * (used by the GUI's "sell all" button).
     */
    public double sellAllInInventory(Player player) {
        double total = 0.0;
        ItemStack[] contents = player.getInventory().getStorageContents();

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null) continue;
            if (!isSellable(item.getType())) continue;

            double pricePerItem = getPrice(item.getType());
            total += pricePerItem * item.getAmount();
            contents[i] = null;
        }

        player.getInventory().setStorageContents(contents);

        if (total > 0) {
            plugin.getEconomy().depositPlayer(player, total);
        }
        return total;
    }

    /**
     * Sells only the item currently held in the player's main hand.
     */
    public void sellHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (hand == null || hand.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "You are not holding anything to sell.");
            return;
        }
        if (!isSellable(hand.getType())) {
            player.sendMessage(ChatColor.RED + "You can't sell " + formatMaterial(hand.getType()) + ".");
            return;
        }

        double pricePerItem = getPrice(hand.getType());
        double total = pricePerItem * hand.getAmount();

        player.getInventory().setItemInMainHand(null);
        plugin.getEconomy().depositPlayer(player, total);

        player.sendMessage(ChatColor.GREEN + "Sold " + hand.getAmount() + "x "
                + formatMaterial(hand.getType()) + " for $" + String.format("%.2f", total));
    }

    private String formatMaterial(Material material) {
        String name = material.name().replace("_", " ").toLowerCase();
        StringBuilder sb = new StringBuilder();
        for (String word : name.split(" ")) {
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    public Map<Material, Double> getAllPrices() {
        return prices;
    }
}
