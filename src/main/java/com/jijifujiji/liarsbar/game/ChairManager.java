package com.jijifujiji.liarsbar.game;

import com.jijifujiji.liarsbar.LiarsBarPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChairManager implements Listener {

    private static final double[][] OFFSETS = {
            {2, 0.5, 0},
            {0, 0.5, 2},
            {-2, 0.5, 0},
            {0, 0.5, -2}
    };

    private final LiarsBarPlugin plugin;
    private final NamespacedKey tableKey;
    private final NamespacedKey seatKey;
    private final Map<String, List<Interaction>> chairs = new HashMap<>();
    private final Map<Player, String> seatedTable = new HashMap<>();
    private final Map<Player, Integer> seatIndex = new HashMap<>();

    public ChairManager(LiarsBarPlugin plugin) {
        this.plugin = plugin;
        this.tableKey = new NamespacedKey(plugin, "liarbar_table");
        this.seatKey = new NamespacedKey(plugin, "liarbar_seat");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void spawnChairs(Table table) {
        removeChairs(table);
        Location center = table.getLocation();
        if (center == null || center.getWorld() == null) return;
        List<Interaction> list = new ArrayList<>();
        for (int i = 0; i < OFFSETS.length; i++) {
            final int seatNum = i;
            Location loc = center.clone().add(OFFSETS[i][0], OFFSETS[i][1], OFFSETS[i][2]);
            Interaction interaction = center.getWorld().spawn(loc, Interaction.class, e -> {
                e.setInteractionWidth(1.0f);
                e.setInteractionHeight(1.0f);
                e.setResponsive(false);
                e.setPersistent(true);
                e.setInvulnerable(true);
                e.setGravity(false);
                e.setSilent(true);
                PersistentDataContainer data = e.getPersistentDataContainer();
                data.set(tableKey, PersistentDataType.STRING, table.getId().toLowerCase());
                data.set(seatKey, PersistentDataType.INTEGER, seatNum);
            });
            list.add(interaction);
        }
        chairs.put(table.getId().toLowerCase(), list);
    }

    public void removeChairs(Table table) {
        List<Interaction> list = chairs.remove(table.getId().toLowerCase());
        if (list != null) {
            for (Interaction interaction : list) {
                interaction.remove();
            }
        }
    }

    public void removeAllChairs() {
        for (List<Interaction> list : chairs.values()) {
            for (Interaction interaction : list) {
                interaction.remove();
            }
        }
        chairs.clear();
    }

    public Integer getSeatIndex(Player player) {
        return seatIndex.get(player);
    }

    public String getSeatedTableId(Player player) {
        return seatedTable.get(player);
    }

    public void clearSeat(Player player) {
        seatedTable.remove(player);
        seatIndex.remove(player);
    }

    @EventHandler
    public void onChairInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!(entity instanceof Interaction interaction)) return;
        PersistentDataContainer data = interaction.getPersistentDataContainer();
        if (!data.has(tableKey, PersistentDataType.STRING)) return;
        event.setCancelled(true);
        String tableId = data.get(tableKey, PersistentDataType.STRING);
        int seat = data.get(seatKey, PersistentDataType.INTEGER);
        Table table = plugin.getTableManager().getTable(tableId);
        if (table == null) return;
        Player player = event.getPlayer();
        if (table.getState() != GameState.IDLE && table.getState() != GameState.WAITING) {
            player.sendMessage(ChatColor.RED + "游戏进行中，无法入座。");
            return;
        }
        Player occupant = getOccupant(tableId, seat);
        if (occupant != null && !occupant.equals(player)) {
            player.sendMessage(ChatColor.RED + "这个座位已经有人了。");
            return;
        }
        seatedTable.put(player, tableId);
        seatIndex.put(player, seat);
        interaction.addPassenger(player);
        player.sendMessage(ChatColor.YELLOW + "你已坐在 " + ChatColor.GOLD + tableId.toUpperCase() + ChatColor.YELLOW + " 桌的 " + (seat + 1) + " 号座位。");
    }

    private Player getOccupant(String tableId, int seat) {
        for (Map.Entry<Player, String> entry : seatedTable.entrySet()) {
            if (entry.getValue().equals(tableId) && seatIndex.get(entry.getKey()) != null && seatIndex.get(entry.getKey()) == seat) {
                if (entry.getKey().isOnline()) return entry.getKey();
            }
        }
        return null;
    }
}
