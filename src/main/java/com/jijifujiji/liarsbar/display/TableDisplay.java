package com.jijifujiji.liarsbar.display;

import com.jijifujiji.liarsbar.LiarsBarPlugin;
import com.jijifujiji.liarsbar.game.BetMode;
import com.jijifujiji.liarsbar.game.Card;
import com.jijifujiji.liarsbar.game.PlayerState;
import com.jijifujiji.liarsbar.game.TableLayout;
import com.jijifujiji.liarsbar.i18n.Messages;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TableDisplay {

    private final String tableId;
    private final String tableEntityTag;
    private final Messages messages;
    private final List<Entity> managedEntities = new ArrayList<>();
    private final Map<Integer, List<ItemDisplay>> playerCardDisplays = new HashMap<>();
    private final Map<Integer, List<Interaction>> playerCardInteractions = new HashMap<>();
    private final List<ItemDisplay> centerCardDisplays = new ArrayList<>();

    private TextDisplay modeLabel;
    private TextDisplay statusLabel;
    private Interaction playButton;
    private Interaction challengeButton;
    private Interaction startButton;

    public TableDisplay(LiarsBarPlugin plugin, String tableId) {
        this.tableId = tableId;
        this.tableEntityTag = TableLayout.tableEntityTag(tableId);
        this.messages = plugin.messages();
    }

    public void build(Location location, BetMode betMode) {
        clear(location);
        if (location == null || location.getWorld() == null) return;

        modeLabel = DisplayManager.spawnLabel(location.clone().add(0, 2.2, 0),
                messages.betMode(betMode), Color.fromRGB(0x0B2A66), false);
        statusLabel = DisplayManager.spawnLabel(location.clone().add(0, 1.6, 0),
                messages.get("display.status.waiting"), Color.fromRGB(0xB22234), false);

        startButton = DisplayManager.spawnInteraction(location.clone().add(0, 1.28, 0), 0.6f, 0.6f,
                new DisplayManager.ClickAction(DisplayManager.ClickAction.ActionType.START_BUTTON, tableId, -1, -1),
                true, true);
        addManaged(startButton);
        addManaged(modeLabel);
        addManaged(statusLabel);
    }

    public void setStatus(String text) {
        if (statusLabel != null && statusLabel.isValid()) {
            statusLabel.setText(text);
        }
    }

    public void renderPlayerCards(Location location, PlayerState ps) {
        clearPlayerDisplay(ps.getSeatIndex());
        if (location == null || location.getWorld() == null) return;

        List<ItemDisplay> cardDisplays = new ArrayList<>();
        List<Interaction> cardInteractions = new ArrayList<>();
        List<Card> hand = ps.getHand();
        float yaw = TableLayout.SEAT_YAWS[ps.getSeatIndex()];

        for (int i = 0; i < hand.size(); i++) {
            Card card = hand.get(i);
            Location cardLoc = TableLayout.playerCardLocation(location, ps.getSeatIndex(), i);
            ItemDisplay cardDisplay = DisplayManager.spawnCard(cardLoc,
                    messages.card(card), card.getItemModel(), card.getCustomModelData(), tableId, ps.getSeatIndex(), i);
            if (cardDisplay != null) {
                DisplayManager.applyCardTransform(cardDisplay, yaw, 0.8f);
                cardDisplays.add(cardDisplay);
                addManaged(cardDisplay);
            }

            Interaction cardInteract = DisplayManager.spawnInteraction(cardLoc, 0.5f, 0.7f,
                    new DisplayManager.ClickAction(DisplayManager.ClickAction.ActionType.PLAY_CARD,
                            tableId, ps.getSeatIndex(), i));
            if (cardInteract != null) {
                cardInteractions.add(cardInteract);
                addManaged(cardInteract);
            }
        }
        playerCardDisplays.put(ps.getSeatIndex(), cardDisplays);
        playerCardInteractions.put(ps.getSeatIndex(), cardInteractions);
    }

    public void clearPlayerDisplay(int seatIndex) {
        List<ItemDisplay> cards = playerCardDisplays.remove(seatIndex);
        if (cards != null) {
            DisplayManager.removeManagedEntities(new ArrayList<>(cards));
            managedEntities.removeAll(cards);
        }
        List<Interaction> interactions = playerCardInteractions.remove(seatIndex);
        if (interactions != null) {
            DisplayManager.removeManagedEntities(new ArrayList<>(interactions));
            managedEntities.removeAll(interactions);
        }
    }

    public void renderCenterCards(Location location, List<Card> centerCards) {
        clearCenterDisplay();
        if (location == null || location.getWorld() == null) return;

        for (int i = 0; i < centerCards.size(); i++) {
            Card card = centerCards.get(i);
            Location cardLoc = TableLayout.centerCardLocation(location, i, centerCards.size());
            ItemDisplay cardDisplay = DisplayManager.spawnCard(cardLoc,
                    messages.card(card), card.getItemModel(), card.getCustomModelData(), tableId, -1, -1);
            if (cardDisplay != null) {
                DisplayManager.applyCardTransform(cardDisplay, 0f, 0.6f);
                centerCardDisplays.add(cardDisplay);
                addManaged(cardDisplay);
            }
        }
    }

    public void clearCenterDisplay() {
        DisplayManager.removeManagedEntities(new ArrayList<>(centerCardDisplays));
        managedEntities.removeAll(centerCardDisplays);
        centerCardDisplays.clear();
    }

    public void renderActionButtons(Location location, PlayerState current, boolean canChallenge) {
        if (location == null || location.getWorld() == null) return;
        removeManaged(playButton);
        removeManaged(challengeButton);
        playButton = null;
        challengeButton = null;

        playButton = DisplayManager.spawnInteraction(
                TableLayout.actionButtonLocation(location, current.getSeatIndex(), 0), 0.5f, 0.5f,
                new DisplayManager.ClickAction(DisplayManager.ClickAction.ActionType.PLAY_BUTTON, tableId, -1, -1));
        addManaged(playButton);

        if (canChallenge) {
            challengeButton = DisplayManager.spawnInteraction(
                    TableLayout.actionButtonLocation(location, current.getSeatIndex(), 0.5), 0.5f, 0.5f,
                    new DisplayManager.ClickAction(DisplayManager.ClickAction.ActionType.CHALLENGE_BUTTON, tableId, -1, -1));
            addManaged(challengeButton);
        }
    }

    public void clear(Location location) {
        clearCenterDisplay();
        for (int i = 0; i < TableLayout.SEAT_COUNT; i++) {
            clearPlayerDisplay(i);
        }
        DisplayManager.removeManagedEntities(new ArrayList<>(managedEntities));
        managedEntities.clear();
        playButton = null;
        challengeButton = null;
        startButton = null;
        modeLabel = null;
        statusLabel = null;

        if (location != null && location.getWorld() != null) {
            clearPersistedDisplayEntities(location);
        }
    }

    private void clearPersistedDisplayEntities(Location location) {
        for (Entity entity : location.getWorld().getNearbyEntities(location, 8.0, 4.0, 8.0)) {
            boolean managedForTable = entity.getScoreboardTags().contains(TableLayout.MANAGED_ENTITY_TAG)
                    && entity.getScoreboardTags().contains(tableEntityTag);
            boolean legacyCollision = entity.getScoreboardTags().contains("liarsbar_collision")
                    && entity.getLocation().distanceSquared(location) <= 4.0;
            if (managedForTable || legacyCollision) {
                DisplayManager.unregisterClickAction(entity.getEntityId());
                entity.remove();
            }
        }
    }

    private void addManaged(Entity entity) {
        if (entity == null) return;
        entity.addScoreboardTag(TableLayout.MANAGED_ENTITY_TAG);
        entity.addScoreboardTag(tableEntityTag);
        managedEntities.add(entity);
    }

    private void removeManaged(Entity entity) {
        if (entity == null) return;
        DisplayManager.unregisterClickAction(entity.getEntityId());
        if (entity.isValid()) {
            entity.remove();
        }
        managedEntities.remove(entity);
    }
}
