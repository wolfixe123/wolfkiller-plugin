package com.wolfkiller.smp.shop;

import com.wolfkiller.smp.WolfkillerSMP;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;

/**
 * Loads every category YAML in the plugin's /shop folder (copied from the
 * bundled defaults on first run) into memory. Each file uses the same
 * "pages -> items -> {material, buy, sell}" layout as the EconomyShopGUI
 * files this server was already using, so prices stay consistent.
 *
 * Note: SPAWNER and POTION-family rows are told apart using their
 * "spawnertype" / "potiontypes" field so a Zombie Spawner and a Wolf
 * Spawner don't collapse into a single generic spawner entry.
 */
public class ShopManager {

    public static final String[] CATEGORY_FILES = {
            "Ores", "Gear", "Enchanting", "Blocks", "Decoration", "Farming",
            "Food", "Miscellaneous", "Mobs", "Music", "Potions", "Redstone",
            "Spawners", "Workstations", "Z_EverythingElse"
    };

    private final WolfkillerSMP plugin;
    private final Map<String, List<ShopItem>> categories = new LinkedHashMap<>();

    public ShopManager(WolfkillerSMP plugin) {
        this.plugin = plugin;
        copyDefaults();
        loadAll();
    }

    private void copyDefaults() {
        File shopFolder = new File(plugin.getDataFolder(), "shop");
        if (!shopFolder.exists()) shopFolder.mkdirs();

        for (String name : CATEGORY_FILES) {
            File target = new File(shopFolder, name + ".yml");
            if (target.exists()) continue;
            try (InputStream in = plugin.getResource("shop/" + name + ".yml")) {
                if (in != null) {
                    Files.copy(in, target.toPath());
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Could not copy default shop file " + name + ": " + e.getMessage());
            }
        }
    }

    public void loadAll() {
        categories.clear();
        File shopFolder = new File(plugin.getDataFolder(), "shop");

        for (String name : CATEGORY_FILES) {
            File file = new File(shopFolder, name + ".yml");
            if (!file.exists()) continue;

            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            List<ShopItem> items = new ArrayList<>();
            Set<String> seen = new HashSet<>();

            ConfigurationSection pages = config.getConfigurationSection("pages");
            if (pages != null) {
                for (String pageKey : pages.getKeys(false)) {
                    ConfigurationSection itemsSection = pages.getConfigurationSection(pageKey + ".items");
                    if (itemsSection == null) continue;

                    for (String itemKey : itemsSection.getKeys(false)) {
                        String matStr = itemsSection.getString(itemKey + ".material");
                        if (matStr == null) continue;

                        Material mat;
                        try {
                            mat = Material.valueOf(matStr.toUpperCase().trim());
                        } catch (IllegalArgumentException ex) {
                            continue;
                        }

                        // SPAWNER uses "spawnertype", potions use a "potiontypes" list -
                        // these are what make otherwise-identical materials distinct.
                        String variant = itemsSection.getString(itemKey + ".spawnertype");
                        if (variant == null && itemsSection.isList(itemKey + ".potiontypes")) {
                            List<String> types = itemsSection.getStringList(itemKey + ".potiontypes");
                            if (!types.isEmpty()) variant = types.get(0);
                        }

                        double buy = itemsSection.getDouble(itemKey + ".buy", -1);
                        double sell = itemsSection.getDouble(itemKey + ".sell", -1);
                        ShopItem shopItem = new ShopItem(mat, buy, sell, variant);

                        if (seen.contains(shopItem.dedupeKey())) continue;
                        seen.add(shopItem.dedupeKey());
                        items.add(shopItem);
                    }
                }
            }
            categories.put(name, items);
        }

        int total = categories.values().stream().mapToInt(List::size).sum();
        plugin.getLogger().info("Shop loaded: " + categories.size() + " categories, " + total + " items.");
    }

    public List<ShopItem> getCategory(String name) {
        return categories.getOrDefault(name, List.of());
    }

    public Set<String> getCategoryNames() {
        return categories.keySet();
    }

    /**
     * Searches every category for the given material and returns the
     * first positive sell price found, or -1 if it isn't sellable anywhere.
     * NOTE: for SPAWNER/POTION-type materials this ignores the variant -
     * prefer getSellPrice(ItemStack) when you have the actual item, since
     * it can tell a Zombie Spawner apart from a Wolf Spawner correctly.
     */
    public double getSellPrice(Material material) {
        for (List<ShopItem> items : categories.values()) {
            for (ShopItem item : items) {
                if (item.material == material && item.isSellable()) {
                    return item.sell;
                }
            }
        }
        return -1;
    }

    /**
     * Variant-aware lookup: reads the spawner mob type or potion type off the
     * actual ItemStack so spawners/potions are priced correctly instead of
     * all collapsing to whichever one happens to be listed first.
     */
    public double getSellPrice(org.bukkit.inventory.ItemStack stack) {
        if (stack == null) return -1;
        String variant = extractVariant(stack);

        List<ShopItem> candidates = new ArrayList<>();
        for (List<ShopItem> items : categories.values()) {
            for (ShopItem item : items) {
                if (item.material == stack.getType()) candidates.add(item);
            }
        }

        if (variant != null) {
            for (ShopItem item : candidates) {
                if (variant.equalsIgnoreCase(item.variant) && item.isSellable()) {
                    return item.sell;
                }
            }
        }
        // no variant on the item (or no exact match) - fall back to a plain material match
        for (ShopItem item : candidates) {
            if (item.variant == null && item.isSellable()) return item.sell;
        }
        return candidates.isEmpty() ? -1 : (candidates.get(0).isSellable() ? candidates.get(0).sell : -1);
    }

    private String extractVariant(org.bukkit.inventory.ItemStack stack) {
        if (stack.getType() == Material.SPAWNER && stack.getItemMeta() instanceof org.bukkit.inventory.meta.BlockStateMeta bsm) {
            if (bsm.getBlockState() instanceof org.bukkit.block.CreatureSpawner cs && cs.getSpawnedType() != null) {
                return cs.getSpawnedType().name();
            }
        }
        if (stack.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta pm) {
            try {
                return pm.getBasePotionType() != null ? pm.getBasePotionType().name().toLowerCase() : null;
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    public boolean isSellableAnywhere(Material material) {
        return getSellPrice(material) > 0;
    }
}
