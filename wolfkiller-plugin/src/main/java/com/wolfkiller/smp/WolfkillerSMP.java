package com.wolfkiller.smp;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import com.wolfkiller.smp.sell.SellGUI;
import com.wolfkiller.smp.sell.SellManager;
import com.wolfkiller.smp.sell.BulkSellGUI;
import com.wolfkiller.smp.homes.HomeGUI;
import com.wolfkiller.smp.homes.HomeManager;
import com.wolfkiller.smp.shop.ShopManager;
import com.wolfkiller.smp.shop.ShopGUI;
import com.wolfkiller.smp.shop.CategoryGUI;

public class WolfkillerSMP extends JavaPlugin {

    private static WolfkillerSMP instance;
    private Economy economy;
    private SellManager sellManager;
    private HomeManager homeManager;
    private HomeGUI homeGUI;
    private ShopManager shopManager;
    private ShopGUI shopGUI;
    private CategoryGUI categoryGUI;
    private BulkSellGUI bulkSellGUI;

    @Override
    public void onEnable() {
        instance = this;

        if (!setupEconomy()) {
            getLogger().severe("Vault economy not found! Disabling WolfkillerSMP.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Loads sell-prices.yml and creates the sell GUI logic
        this.sellManager = new SellManager(this);

        // Register the GUI click listener
        getServer().getPluginManager().registerEvents(new SellGUI(this, sellManager), this);

        // Homes system
        this.homeManager = new HomeManager(this);
        this.homeGUI = new HomeGUI(this, homeManager);
        getServer().getPluginManager().registerEvents(homeGUI, this);

        // Shop system
        this.shopManager = new ShopManager(this);
        this.categoryGUI = new CategoryGUI(this, shopManager);
        this.shopGUI = new ShopGUI(this, shopManager, categoryGUI);
        this.categoryGUI.setShopGUI(shopGUI);
        getServer().getPluginManager().registerEvents(shopGUI, this);
        getServer().getPluginManager().registerEvents(categoryGUI, this);

        // Bulk quick-sell box (/sellgui)
        this.bulkSellGUI = new BulkSellGUI(this, shopManager);
        getServer().getPluginManager().registerEvents(bulkSellGUI, this);

        getLogger().info("WolfkillerSMP has started! Economy hooked: " + economy.getName());
    }

    @Override
    public void onDisable() {
        getLogger().info("WolfkillerSMP has stopped.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used in-game.");
            return true;
        }

        switch (command.getName().toLowerCase()) {
            case "sell":
                SellGUI.open(player, sellManager);
                return true;
            case "sellhand":
                sellManager.sellHand(player);
                return true;
            case "home":
                if (args.length == 0) {
                    homeGUI.open(player);
                } else {
                    String name = args[0];
                    if (!homeManager.hasNamedHome(player, name)) {
                        player.sendMessage(org.bukkit.ChatColor.RED + "You don't have a home named '" + name + "'.");
                    } else {
                        homeGUI.startTeleportCountdown(player, homeManager.getNamedHome(player, name), name);
                    }
                }
                return true;
            case "sethome":
                if (args.length == 0) {
                    player.sendMessage(org.bukkit.ChatColor.RED + "Usage: /sethome <name>");
                } else {
                    homeManager.setNamedHome(player, args[0], player.getLocation());
                    player.sendMessage(org.bukkit.ChatColor.GREEN + "Home '" + args[0] + "' set!");
                }
                return true;
            case "delhome":
                if (args.length == 0) {
                    player.sendMessage(org.bukkit.ChatColor.RED + "Usage: /delhome <name>");
                } else if (!homeManager.hasNamedHome(player, args[0])) {
                    player.sendMessage(org.bukkit.ChatColor.RED + "You don't have a home named '" + args[0] + "'.");
                } else {
                    homeManager.deleteNamedHome(player, args[0]);
                    player.sendMessage(org.bukkit.ChatColor.RED + "Home '" + args[0] + "' deleted.");
                }
                return true;
            case "shop":
                shopGUI.open(player);
                return true;
            case "sellgui":
                bulkSellGUI.open(player);
                return true;
            default:
                return false;
        }
    }

    public static WolfkillerSMP getInstance() {
        return instance;
    }

    public Economy getEconomy() {
        return economy;
    }

    public SellManager getSellManager() {
        return sellManager;
    }
}
