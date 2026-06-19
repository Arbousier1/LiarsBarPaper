package com.jijifujiji.liarsbar.listener;

import com.jijifujiji.liarsbar.LiarsBarPlugin;
import com.jijifujiji.liarsbar.display.DisplayManager;
import com.jijifujiji.liarsbar.game.Table;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class EntityInteractListener implements Listener {

    private final LiarsBarPlugin plugin;

    public EntityInteractListener(LiarsBarPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        DisplayManager.ClickAction action = DisplayManager.getClickAction(entity.getEntityId());
        if (action == null) return;
        event.setCancelled(true);

        Table table = plugin.getTableManager().getTable(action.tableId());
        if (table == null) return;
        Player player = event.getPlayer();

        switch (action.type()) {
            case JOIN_SEAT -> table.joinSeat(player, action.seatIndex());
            case PLAY_CARD -> table.selectCard(player, action.cardIndex());
            case PLAY_BUTTON -> table.playCards(player);
            case CHALLENGE_BUTTON -> table.challenge(player);
            case START_BUTTON -> table.startGame(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        for (Table table : plugin.getTableManager().getTables()) {
            if (table.isInGame(player)) {
                table.removePlayer(player);
            }
        }
    }
}