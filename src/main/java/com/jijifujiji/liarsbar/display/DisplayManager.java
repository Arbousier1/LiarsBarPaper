package com.jijifujiji.liarsbar.display;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class DisplayManager {

    private static final Map<Integer, ClickAction> CLICK_ACTIONS = new ConcurrentHashMap<>();
    private static final Map<JavaPlugin, NamespacedKey> KEY_CACHE = new ConcurrentHashMap<>();

    private static final NamespacedKey ACTION_KEY = new NamespacedKey("liarsbar", "action");
    private static final NamespacedKey TABLE_KEY = new NamespacedKey("liarsbar", "table");
    private static final NamespacedKey SEAT_KEY = new NamespacedKey("liarsbar", "seat");
    private static final NamespacedKey INDEX_KEY = new NamespacedKey("liarsbar", "index");
    private static final NamespacedKey MANAGED_KEY = new NamespacedKey("liarsbar", "managed");

    private DisplayManager() {}

    public static void registerClickAction(int entityId, ClickAction action) {
        if (action != null) {
            CLICK_ACTIONS.put(entityId, action);
        }
    }

    public static ClickAction getClickAction(int entityId) {
        return CLICK_ACTIONS.get(entityId);
    }

    public static void unregisterClickAction(int entityId) {
        CLICK_ACTIONS.remove(entityId);
    }

    public static void clearAll() {
        CLICK_ACTIONS.clear();
    }

    public record ClickAction(ActionType type, String tableId, int seatIndex, int cardIndex) {
        public enum ActionType {
            JOIN_SEAT, PLAY_CARD, PLAY_BUTTON, CHALLENGE_BUTTON, START_BUTTON
        }
    }

    public static ItemDisplay spawnCard(JavaPlugin plugin, Location location, String cardName, int modelData,
                                         String tableId, int seatIndex, int cardIndex) {
        World world = location.getWorld();
        if (world == null) return null;
        ItemDisplay display = world.spawn(location, ItemDisplay.class);
        display.setPersistent(false);
        markManaged(plugin, display);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.HEAD);
        display.setViewRange(32f);
        display.setBrightness(new Display.Brightness(15, 15));

        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(modelData);
            meta.setDisplayName(cardName);
            item.setItemMeta(meta);
        }
        display.setItemStack(item);
        display.setDisplayWidth(0.4f);
        display.setDisplayHeight(0.6f);

        var pdc = display.getPersistentDataContainer();
        pdc.set(ACTION_KEY, PersistentDataType.STRING, "play_card");
        pdc.set(TABLE_KEY, PersistentDataType.STRING, tableId);
        pdc.set(SEAT_KEY, PersistentDataType.INTEGER, seatIndex);
        pdc.set(INDEX_KEY, PersistentDataType.INTEGER, cardIndex);

        registerClickAction(display.getEntityId(),
                new ClickAction(ClickAction.ActionType.PLAY_CARD, tableId, seatIndex, cardIndex));
        return display;
    }

    public static Interaction spawnInteraction(JavaPlugin plugin, Location location, float width, float height,
                                                ClickAction action) {
        World world = location.getWorld();
        if (world == null) return null;
        Interaction interaction = world.spawn(location, Interaction.class);
        interaction.setPersistent(false);
        markManaged(plugin, interaction);
        interaction.setResponsive(true);
        interaction.setInteractionWidth(width);
        interaction.setInteractionHeight(height);
        if (action != null) {
            registerClickAction(interaction.getEntityId(), action);
        }
        return interaction;
    }

    public static TextDisplay spawnLabel(JavaPlugin plugin, Location location, String text,
                                          Color bgColor, boolean seeThrough) {
        World world = location.getWorld();
        if (world == null) return null;
        TextDisplay display = world.spawn(location, TextDisplay.class);
        display.setPersistent(false);
        markManaged(plugin, display);
        display.setText(text);
        display.setSeeThrough(seeThrough);
        display.setShadowed(true);
        display.setDefaultBackground(false);
        display.setBillboard(Display.Billboard.CENTER);
        display.setLineWidth(200);
        display.setViewRange(32f);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setBackgroundColor(bgColor);
        return display;
    }

    public static void applyCardTransform(ItemDisplay display, float yaw, float scale) {
        display.setRotation(yaw, 0f);
        display.setTransformation(new Transformation(
                new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
    }

    public static void removeManagedEntities(JavaPlugin plugin, Collection<Entity> entities) {
        for (Entity entity : entities) {
            if (entity != null && entity.isValid()) {
                unregisterClickAction(entity.getEntityId());
                entity.remove();
            }
        }
    }

    private static void markManaged(JavaPlugin plugin, Entity entity) {
        entity.getPersistentDataContainer().set(MANAGED_KEY, PersistentDataType.BYTE, (byte) 1);
    }

    public static boolean isManaged(Entity entity) {
        return entity.getPersistentDataContainer().has(MANAGED_KEY, PersistentDataType.BYTE);
    }

    public static NamespacedKey getActionKey() { return ACTION_KEY; }
    public static NamespacedKey getTableKey() { return TABLE_KEY; }
    public static NamespacedKey getSeatKey() { return SEAT_KEY; }
    public static NamespacedKey getIndexKey() { return INDEX_KEY; }
}