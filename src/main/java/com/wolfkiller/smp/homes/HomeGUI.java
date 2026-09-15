package com.wolfkiller.smp.homes;

import com.wolfkiller.smp.WolfkillerSMP;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * /home GUI.
 *
 * Row 1 (bed slots):     0=TeamHome, 1-2=gap, 3-7=Home1..Home5, 8=gap
 * Row 2 (delete slots):  9=TeamHome delete, 10-11=gap, 12-16=Home1..Home5 delete, 17=gap
 *
 * Bed is GRAY_BED when empty -> click sets home there -> becomes LIGHT_BLUE_BED.
 * Clicking a LIGHT_BLUE_BED starts a 5-second teleport countdown (action bar) then teleports.
 * Clicking the BARRIER under a set home deletes that home.
 */
public class HomeGUI implements Listener {

    private static final String TITLE = ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Homes";

    // row1 slot -> home id (0 = team home, 1-5 = personal home slot)
    private static final Map<Integer, Integer> BED_SLOTS = new HashMap<>();
    // row2 slot -> home id
    private static final Map<Integer, Integer> DELETE_SLOTS = new HashMap<>();

    static {
        BED_SLOTS.put(0, 0);
        BED_SLOTS.put(3, 1);
        BED_SLOTS.put(4, 2);
        BED_SLOTS.put(5, 3);
        BED_SLOTS.put(6, 4);
        BED_SLOTS.put(7, 5);

        DELETE_SLOTS.put(9, 0);
        DELETE_SLOTS.put(12, 1);
        DELETE_SLOTS.put(13, 2);
        DELETE_SLOTS.put(14, 3);
        DELETE_SLOTS.put(15, 4);
        DELETE_SLOTS.put(16, 5);
    }

    private final WolfkillerSMP plugin;
    private final HomeManager homeManager;

    public HomeGUI(WolfkillerSMP plugin, HomeManager homeManager) {
        this.plugin = plugin;
        this.homeManager = homeManager;
    }

    public void open(Player player) {
        Inventory gui = Bukkit.createInventory(null, 18, TITLE);

        ItemStack filler = namedItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 18; i++) {
            gui.setItem(i, filler);
        }

        // team home bed (slot 0) + delete (slot 9)
        boolean teamSet = homeManager.hasTeamHome(player);
        gui.setItem(0, bedItem("Team Home", teamSet));
        gui.setItem(9, teamSet ? deleteItem("Team Home") : filler);

        // 5 personal homes
        for (int slot = 1; slot <= HomeManager.MAX_HOMES; slot++) {
            int bedSlotIndex = slotForHome(slot);
            int deleteSlotIndex = deleteSlotForHome(slot);
            boolean set = homeManager.hasHome(player, slot);

            gui.setItem(bedSlotIndex, bedItem("Home " + slot, set));
            gui.setItem(deleteSlotIndex, set ? deleteItem("Home " + slot) : filler);
        }

        player.openInventory(gui);
    }

    private int slotForHome(int homeSlot) {
        // home1->3, home2->4, home3->5, home4->6, home5->7
        return 2 + homeSlot;
    }

    private int deleteSlotForHome(int homeSlot) {
        // home1->12, home2->13 ...
        return 11 + homeSlot;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(TITLE)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();

        if (BED_SLOTS.containsKey(slot)) {
            handleBedClick(player, BED_SLOTS.get(slot));
        } else if (DELETE_SLOTS.containsKey(slot)) {
            handleDeleteClick(player, DELETE_SLOTS.get(slot));
        }
    }

    private void handleBedClick(Player player, int homeId) {
        boolean isTeam = homeId == 0;
        boolean alreadySet = isTeam ? homeManager.hasTeamHome(player) : homeManager.hasHome(player, homeId);

        if (!alreadySet) {
            // set home at current location
            if (isTeam) {
                homeManager.setTeamHome(player, player.getLocation());
                player.sendMessage(ChatColor.GREEN + "Team Home set!");
            } else {
                homeManager.setHome(player, homeId, player.getLocation());
                player.sendMessage(ChatColor.GREEN + "Home " + homeId + " set!");
            }
            open(player); // refresh GUI so the bed turns blue
            return;
        }

        // already set -> teleport with countdown
        Location dest = isTeam ? homeManager.getTeamHome(player) : homeManager.getHome(player, homeId);
        player.closeInventory();
        startTeleportCountdown(player, dest, isTeam ? "Team Home" : "Home " + homeId);
    }

    private void handleDeleteClick(Player player, int homeId) {
        boolean isTeam = homeId == 0;
        boolean set = isTeam ? homeManager.hasTeamHome(player) : homeManager.hasHome(player, homeId);
        if (!set) return;

        if (isTeam) {
            homeManager.deleteTeamHome(player);
            player.sendMessage(ChatColor.RED + "Team Home deleted.");
        } else {
            homeManager.deleteHome(player, homeId);
            player.sendMessage(ChatColor.RED + "Home " + homeId + " deleted.");
        }
        open(player); // refresh GUI so the bed turns gray again
    }

    public void startTeleportCountdown(Player player, Location destination, String homeName) {
        new BukkitRunnable() {
            int secondsLeft = 5;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }

                if (secondsLeft <= 0) {
                    player.teleport(destination);
                    player.sendMessage(ChatColor.GREEN + "Teleported to " + homeName + "!");
                    sendActionBar(player, "");
                    cancel();
                    return;
                }

                sendActionBar(player, ChatColor.AQUA + "Teleporting to " + homeName
                        + ChatColor.AQUA + " in " + ChatColor.GOLD + secondsLeft + ChatColor.AQUA + "...");
                secondsLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
    }

    private ItemStack bedItem(String label, boolean set) {
        Material material = set ? Material.LIGHT_BLUE_BED : Material.GRAY_BED;
        List<String> lore = new ArrayList<>();
        if (set) {
            lore.add(ChatColor.GRAY + "Click to teleport here!");
        } else {
            lore.add(ChatColor.GRAY + "Click to set your home here.");
        }
        return namedItem(material, (set ? ChatColor.AQUA : ChatColor.GRAY) + "" + ChatColor.BOLD + label, lore);
    }

    private ItemStack deleteItem(String label) {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.RED + "Click to delete this home.");
        return namedItem(Material.BARRIER, ChatColor.RED + "" + ChatColor.BOLD + "Delete " + label, lore);
    }

    private ItemStack namedItem(Material material, String name) {
        return namedItem(material, name, null);
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
