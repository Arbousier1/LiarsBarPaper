package com.jijifujiji.liarsbar.game;

import com.jijifujiji.liarsbar.LiarsBarPlugin;
import com.jijifujiji.liarsbar.config.ConfigManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class TableManager {

    private final LiarsBarPlugin plugin;
    private final Map<String, Table> tables = new LinkedHashMap<>();
    private final Map<UUID, Table> playerTableIndex = new HashMap<>();

    public TableManager(LiarsBarPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        for (Map.Entry<String, Location> entry : configManager.getTableLocations().entrySet()) {
            Table table = new Table(plugin, entry.getKey(), entry.getValue());
            tables.put(entry.getKey().toLowerCase(), table);
            table.syncCraftEngineFurniture();
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

    public ChairSeat findChairSeat(Location furnitureLocation) {
        for (Table table : tables.values()) {
            int seatIndex = table.findSeatByCraftEngineChair(furnitureLocation);
            if (seatIndex >= 0) {
                return new ChairSeat(table, seatIndex);
            }
        }
        return null;
    }

    public void registerPlayer(Player player, Table table) {
        if (player != null && table != null) {
            playerTableIndex.put(player.getUniqueId(), table);
        }
    }

    public void unregisterPlayer(Player player) {
        if (player != null) {
            playerTableIndex.remove(player.getUniqueId());
        }
    }

    public Table findTableByPlayer(Player player) {
        Table indexed = playerTableIndex.get(player.getUniqueId());
        if (indexed != null && indexed.isInGame(player)) {
            return indexed;
        }
        if (indexed != null) {
            playerTableIndex.remove(player.getUniqueId());
        }

        // Fallback for players already seated before an index was populated.
        for (Table table : tables.values()) {
            if (table.isInGame(player)) {
                registerPlayer(player, table);
                return table;
            }
        }
        return null;
    }

    public void shutdownAll() {
        for (Table table : tables.values()) table.destroy();
        tables.clear();
        playerTableIndex.clear();
    }

    public record ChairSeat(Table table, int seatIndex) {}
}
