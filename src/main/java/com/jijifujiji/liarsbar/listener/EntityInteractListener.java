package com.jijifujiji.liarsbar.listener;

import com.jijifujiji.liarsbar.LiarsBarPlugin;
import com.jijifujiji.liarsbar.display.DisplayManager;
import com.jijifujiji.liarsbar.game.Table;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

public class EntityInteractListener implements Listener {

    private final LiarsBarPlugin plugin;

    public EntityInteractListener(LiarsBarPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Entity entity = event.getRightClicked();
        DisplayManager.ClickAction action = DisplayManager.getClickAction(entity.getEntityId());
        if (action == null) return;
        event.setCancelled(true);
        handleAction(event.getPlayer(), action);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Interaction target = findTargetedInteraction(event.getPlayer());
        if (target == null) return;

        DisplayManager.ClickAction clickAction = DisplayManager.getClickAction(target.getEntityId());
        if (clickAction == null) return;

        event.setCancelled(true);
        handleAction(event.getPlayer(), clickAction);
    }

    private Interaction findTargetedInteraction(Player player) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();
        double maxDist = 5.0;
        Interaction best = null;
        double bestProj = Double.MAX_VALUE;

        for (Entity entity : player.getNearbyEntities(maxDist, maxDist, maxDist)) {
            if (!(entity instanceof Interaction interaction)) continue;
            if (DisplayManager.getClickAction(interaction.getEntityId()) == null) continue;

            Location center = interaction.getLocation().clone()
                    .add(0, interaction.getInteractionHeight() / 2.0, 0);
            Vector to = center.subtract(eye).toVector();
            double proj = to.dot(dir);
            if (proj < 0 || proj > maxDist) continue;

            double radius = Math.max(interaction.getInteractionWidth() / 2.0, 0.1);
            double perpSq = to.lengthSquared() - proj * proj;
            if (perpSq <= radius * radius && proj < bestProj) {
                bestProj = proj;
                best = interaction;
            }
        }
        return best;
    }

    private void handleAction(Player player, DisplayManager.ClickAction action) {
        Table table = plugin.getTableManager().getTable(action.tableId());
        if (table == null) return;

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
