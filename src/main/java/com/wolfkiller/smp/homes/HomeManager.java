package com.wolfkiller.smp.homes;

import com.wolfkiller.smp.WolfkillerSMP;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stores up to 5 personal homes + 1 "team home" per player in homes.yml.
 * Locations are saved as "world,x,y,z,yaw,pitch" strings.
 *
 * NOTE: Team Home is currently PER-PLAYER (not shared between team members)
 * because there is no Team/Party system in the plugin yet. Once that exists,
 * swap teamHomes to be keyed by team-id instead of player UUID.
 */
public class HomeManager {

    public static final int MAX_HOMES = 5;

    private final WolfkillerSMP plugin;
    private final File file;
    private FileConfiguration config;

    // uuid -> (home1..home5 -> Location)
    private final Map<UUID, Map<Integer, Location>> homes = new HashMap<>();
    // uuid -> team home Location
    private final Map<UUID, Location> teamHomes = new HashMap<>();
    // uuid -> (custom home name -> Location), set via /sethome <name>
    private final Map<UUID, Map<String, Location>> namedHomes = new HashMap<>();

    public HomeManager(WolfkillerSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "homes.yml");
        load();
    }

    private void load() {
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            return;
        }
        config = YamlConfiguration.loadConfiguration(file);
        if (!config.isConfigurationSection("players")) return;

        for (String uuidStr : config.getConfigurationSection("players").getKeys(false)) {
            UUID uuid = UUID.fromString(uuidStr);
            Map<Integer, Location> playerHomes = new HashMap<>();

            for (int i = 1; i <= MAX_HOMES; i++) {
                String raw = config.getString("players." + uuidStr + ".home" + i);
                Location loc = deserialize(raw);
                if (loc != null) playerHomes.put(i, loc);
            }
            homes.put(uuid, playerHomes);

            String teamRaw = config.getString("players." + uuidStr + ".teamhome");
            Location teamLoc = deserialize(teamRaw);
            if (teamLoc != null) teamHomes.put(uuid, teamLoc);

            if (config.isConfigurationSection("players." + uuidStr + ".named")) {
                Map<String, Location> named = new HashMap<>();
                for (String homeName : config.getConfigurationSection("players." + uuidStr + ".named").getKeys(false)) {
                    Location loc = deserialize(config.getString("players." + uuidStr + ".named." + homeName));
                    if (loc != null) named.put(homeName.toLowerCase(), loc);
                }
                namedHomes.put(uuid, named);
            }
        }
    }

    public void save() {
        config = new YamlConfiguration();

        for (Map.Entry<UUID, Map<Integer, Location>> entry : homes.entrySet()) {
            String base = "players." + entry.getKey() + ".";
            for (Map.Entry<Integer, Location> home : entry.getValue().entrySet()) {
                config.set(base + "home" + home.getKey(), serialize(home.getValue()));
            }
        }
        for (Map.Entry<UUID, Location> entry : teamHomes.entrySet()) {
            config.set("players." + entry.getKey() + ".teamhome", serialize(entry.getValue()));
        }
        for (Map.Entry<UUID, Map<String, Location>> entry : namedHomes.entrySet()) {
            String base = "players." + entry.getKey() + ".named.";
            for (Map.Entry<String, Location> home : entry.getValue().entrySet()) {
                config.set(base + home.getKey(), serialize(home.getValue()));
            }
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save homes.yml: " + e.getMessage());
        }
    }

    // ---------- personal homes (slots 1-5) ----------

    public Location getHome(Player player, int slot) {
        return homes.getOrDefault(player.getUniqueId(), Map.of()).get(slot);
    }

    public boolean hasHome(Player player, int slot) {
        return getHome(player, slot) != null;
    }

    public void setHome(Player player, int slot, Location location) {
        homes.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).put(slot, location.clone());
        save();
    }

    public void deleteHome(Player player, int slot) {
        Map<Integer, Location> playerHomes = homes.get(player.getUniqueId());
        if (playerHomes != null) {
            playerHomes.remove(slot);
            save();
        }
    }

    // ---------- team home ----------

    public Location getTeamHome(Player player) {
        return teamHomes.get(player.getUniqueId());
    }

    public boolean hasTeamHome(Player player) {
        return getTeamHome(player) != null;
    }

    public void setTeamHome(Player player, Location location) {
        teamHomes.put(player.getUniqueId(), location.clone());
        save();
    }

    public void deleteTeamHome(Player player) {
        teamHomes.remove(player.getUniqueId());
        save();
    }

    // ---------- named homes (via /sethome <name>) ----------

    public Location getNamedHome(Player player, String name) {
        return namedHomes.getOrDefault(player.getUniqueId(), Map.of()).get(name.toLowerCase());
    }

    public boolean hasNamedHome(Player player, String name) {
        return getNamedHome(player, name) != null;
    }

    public void setNamedHome(Player player, String name, Location location) {
        namedHomes.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).put(name.toLowerCase(), location.clone());
        save();
    }

    public void deleteNamedHome(Player player, String name) {
        Map<String, Location> map = namedHomes.get(player.getUniqueId());
        if (map != null) {
            map.remove(name.toLowerCase());
            save();
        }
    }

    // ---------- serialization ----------

    private String serialize(Location loc) {
        return loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ()
                + "," + loc.getYaw() + "," + loc.getPitch();
    }

    private Location deserialize(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String[] parts = raw.split(",");
        if (parts.length != 6) return null;

        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;

        double x = Double.parseDouble(parts[1]);
        double y = Double.parseDouble(parts[2]);
        double z = Double.parseDouble(parts[3]);
        float yaw = Float.parseFloat(parts[4]);
        float pitch = Float.parseFloat(parts[5]);

        return new Location(world, x, y, z, yaw, pitch);
    }
}
