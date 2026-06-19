package com.jijifujiji.liarsbar.config;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class ConfigManager {

    private final JavaPlugin plugin;
    private final Map<String, Location> tableLocations = new HashMap<>();
    private boolean gamblingEnabled = true;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        load();
    }

    public void load() {
        FileConfiguration config = plugin.getConfig();
        gamblingEnabled = config.getBoolean("gambling-mode", true);
        tableLocations.clear();
        ConfigurationSection tables = config.getConfigurationSection("tables");
        if (tables != null) {
            for (String key : tables.getKeys(false)) {
                ConfigurationSection sec = tables.getConfigurationSection(key);
                if (sec == null) continue;
                String worldName = sec.getString("world");
                World world = worldName == null ? null : Bukkit.getWorld(worldName);
                if (world == null) continue;
                double x = sec.getDouble("x");
                double y = sec.getDouble("y");
                double z = sec.getDouble("z");
                tableLocations.put(key.toLowerCase(), new Location(world, x, y, z));
            }
        }
    }

    public void save() {
        FileConfiguration config = plugin.getConfig();
        config.set("gambling-mode", gamblingEnabled);
        for (Map.Entry<String, Location> entry : tableLocations.entrySet()) {
            Location loc = entry.getValue();
            String path = "tables." + entry.getKey();
            config.set(path + ".world", loc.getWorld().getName());
            config.set(path + ".x", loc.getX());
            config.set(path + ".y", loc.getY());
            config.set(path + ".z", loc.getZ());
        }
        plugin.saveConfig();
    }

    public Location getTableLocation(String id) {
        return tableLocations.get(id.toLowerCase());
    }

    public void setTableLocation(String id, Location location) {
        tableLocations.put(id.toLowerCase(), location.clone());
        save();
    }

    public Map<String, Location> getTableLocations() {
        return new HashMap<>(tableLocations);
    }

    public boolean isGamblingEnabled() {
        return gamblingEnabled;
    }

    public void setGamblingEnabled(boolean enabled) {
        this.gamblingEnabled = enabled;
        save();
    }
}
