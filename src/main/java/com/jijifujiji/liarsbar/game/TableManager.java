package com.jijifujiji.liarsbar.game;

import com.jijifujiji.liarsbar.LiarsBarPlugin;
import com.jijifujiji.liarsbar.config.ConfigManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;

public class TableManager {

    private final LiarsBarPlugin plugin;
    private final Map<String, Table> tables = new LinkedHashMap<>();

    public TableManager(LiarsBarPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        for (Map.Entry<String, Location> entry : configManager.getTableLocations().entrySet()) {
            Table table = new Table(plugin, entry.getKey(), entry.getValue());
            tables.put(entry.getKey().toLowerCase(), table);
            table.buildDisplay();
        }
    }

    public Table createTable(String id, Location location) {
        String key = id.toLowerCase();
        if (tables.containsKey(key)) return tables.get(key);
        Table table = new Table(plugin, key, location);
        tables.put(key, table);
        table.buildDisplay();
        return table;
    }

    public Table getTable(String id) {
        return tables.get(id.toLowerCase());
    }

    public boolean deleteTable(String id) {
        Table table = tables.remove(id.toLowerCase());
        if (table != null) {
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
            if (table.isInGame(player)) return table;
        }
        return null;
    }

    public void shutdownAll() {
        for (Table table : tables.values()) table.destroy();
        tables.clear();
    }
}