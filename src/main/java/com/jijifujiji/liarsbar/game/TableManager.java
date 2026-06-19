package com.jijifujiji.liarsbar.game;

import com.jijifujiji.liarsbar.LiarsBarPlugin;
import com.jijifujiji.liarsbar.config.ConfigManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class TableManager {

    private final LiarsBarPlugin plugin;
    private final ConfigManager configManager;
    private final Map<String, Table> tables = new HashMap<>();

    public TableManager(LiarsBarPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        for (Map.Entry<String, Location> entry : configManager.getTableLocations().entrySet()) {
            createTable(entry.getKey(), entry.getValue());
        }
    }

    public Table createTable(String id, Location location) {
        if (tables.containsKey(id.toLowerCase())) {
            return tables.get(id.toLowerCase());
        }
        Table table = new Table(plugin, id, location);
        tables.put(id.toLowerCase(), table);
        return table;
    }

    public Table getTable(String id) {
        return tables.get(id.toLowerCase());
    }

    public boolean deleteTable(String id) {
        Table table = tables.remove(id.toLowerCase());
        if (table != null) {
            plugin.getChairManager().removeChairs(table);
            table.destroy();
            return true;
        }
        return false;
    }

    public Collection<Table> getTables() {
        return tables.values();
    }

    public Table findTableByPlayer(Player player) {
        for (Table table : tables.values()) {
            if (table.isInGame(player)) {
                return table;
            }
        }
        return null;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public void shutdownAll() {
        for (Table table : tables.values()) {
            plugin.getChairManager().removeChairs(table);
            table.destroy();
        }
        tables.clear();
    }
}
