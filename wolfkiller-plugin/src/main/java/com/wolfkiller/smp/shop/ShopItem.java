package com.wolfkiller.smp.shop;

import org.bukkit.Material;

public class ShopItem {
    public final Material material;
    public final double buy;
    public final double sell;
    /** Distinguishes items that share a Material but aren't the same thing,
     *  e.g. SPAWNER (spawnertype=ZOMBIE) or POTION (potiontype=healing). Null if none. */
    public final String variant;

    public ShopItem(Material material, double buy, double sell, String variant) {
        this.material = material;
        this.buy = buy;
        this.sell = sell;
        this.variant = variant;
    }

    public boolean isBuyable() {
        return buy > 0;
    }

    public boolean isSellable() {
        return sell > 0;
    }

    /** Unique key so items sharing a Material (spawners, potions) don't collide. */
    public String dedupeKey() {
        return material.name() + "|" + (variant == null ? "" : variant.toLowerCase());
    }
}
