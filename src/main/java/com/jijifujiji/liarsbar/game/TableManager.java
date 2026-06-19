package com.jijifujiji.liarsbar.game;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class TableManager {

    private final JavaPlugin plugin;
    private final Map<String, Table> tables = new HashMap<>();

    public TableManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public Table createTable(String id, Location location) {
        if (tables.containsKey(id)) {
            return null;
        }
        Table table = new Table(plugin, id, location);
        tables.put(id, table);
        return table;
    }

    public Table getTable(String id) {
        return tables.get(id);
    }

    public boolean deleteTable(String id) {
        Table table = tables.remove(id);
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
            if (table.isInGame(player)) {
                return table;
            }
        }
        return null;
    }

    public void shutdownAll() {
        for (Table table : tables.values()) {
            table.destroy();
        }
        tables.clear();
    }
}
