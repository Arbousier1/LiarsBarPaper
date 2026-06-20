package com.jijifujiji.liarsbar.display;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class DisplayManager {

    private static final Map<Integer, ClickAction> CLICK_ACTIONS = new ConcurrentHashMap<>();
    private static final String CLICK_ACTION_TAG_PREFIX = "liarsbar_action_";
    private static final String CLICKABLE_TAG = "liarsbar_clickable";

    private DisplayManager() {}

    public static void registerClickAction(int entityId, ClickAction action) {
        if (action != null) CLICK_ACTIONS.put(entityId, action);
    }

    public static void registerClickAction(Entity entity, ClickAction action) {
        if (entity == null || action == null) return;
        registerClickAction(entity.getEntityId(), action);
        clearActionTags(entity);
        entity.addScoreboardTag(CLICKABLE_TAG);
        entity.addScoreboardTag(CLICK_ACTION_TAG_PREFIX + encodeActionTag(serializeClickAction(action)));
    }

    public static ClickAction getClickAction(int entityId) {
        return CLICK_ACTIONS.get(entityId);
    }

    public static ClickAction getClickAction(Entity entity) {
        if (entity == null) return null;
        ClickAction action = getClickAction(entity.getEntityId());
        if (action != null) return action;

        action = deserializeClickAction(readActionTag(entity));
        if (action != null) {
            registerClickAction(entity.getEntityId(), action);
        }
        return action;
    }

    public static void unregisterClickAction(int entityId) {
        CLICK_ACTIONS.remove(entityId);
    }

    public static boolean hasClickActionMarker(Entity entity) {
        return entity != null && (CLICK_ACTIONS.containsKey(entity.getEntityId())
                || entity.getScoreboardTags().contains(CLICKABLE_TAG));
    }

    public static void clearAll() {
        CLICK_ACTIONS.clear();
    }

    public record ClickAction(ActionType type, String tableId, int seatIndex, int cardIndex) {
        public enum ActionType {
            JOIN_SEAT, PLAY_CARD, PLAY_BUTTON, CHALLENGE_BUTTON, START_BUTTON
        }
    }

    private static String serializeClickAction(ClickAction action) {
        return action.type().name() + "|" + action.tableId() + "|" + action.seatIndex() + "|" + action.cardIndex();
    }

    private static String encodeActionTag(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeActionTag(String encoded) {
        try {
            return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void clearActionTags(Entity entity) {
        for (String tag : new ArrayList<>(entity.getScoreboardTags())) {
            if (tag.startsWith(CLICK_ACTION_TAG_PREFIX)) {
                entity.removeScoreboardTag(tag);
            }
        }
    }

    private static String readActionTag(Entity entity) {
        for (String tag : entity.getScoreboardTags()) {
            if (tag.startsWith(CLICK_ACTION_TAG_PREFIX)) {
                return decodeActionTag(tag.substring(CLICK_ACTION_TAG_PREFIX.length()));
            }
        }
        return null;
    }

    private static ClickAction deserializeClickAction(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.split("\\|", -1);
        if (parts.length != 4) return null;
        try {
            return new ClickAction(ClickAction.ActionType.valueOf(parts[0]), parts[1],
                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static ItemDisplay spawnCard(Location location, String cardName, String itemModel, int customModelData,
                                         String tableId, int seatIndex, int cardIndex) {
        World world = location.getWorld();
        if (world == null) return null;
        ItemDisplay display = world.spawn(location, ItemDisplay.class);
        display.setPersistent(false);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.HEAD);
        display.setViewRange(32f);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setDisplayWidth(0.4f);
        display.setDisplayHeight(0.6f);

        Material cardMaterial = Material.matchMaterial("MUSIC_DISC_RELIC");
        if (cardMaterial == null) cardMaterial = Material.PAPER;
        ItemStack item = new ItemStack(cardMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            applyCustomModelData(meta, customModelData);
            applyItemModel(meta, itemModel);
            meta.setDisplayName(cardName);
            item.setItemMeta(meta);
        }
        display.setItemStack(item);
        return display;
    }

    private static void applyCustomModelData(ItemMeta meta, int customModelData) {
        try {
            meta.getClass().getMethod("setCustomModelData", int.class).invoke(meta, customModelData);
        } catch (Exception ignored) {
            // Custom model data is a legacy fallback; item_model handles newer clients.
        }
    }

    private static void applyItemModel(ItemMeta meta, String itemModel) {
        NamespacedKey key = parseItemModelKey(itemModel);
        if (key == null) return;
        try {
            meta.getClass().getMethod("setItemModel", NamespacedKey.class).invoke(meta, key);
        } catch (Exception ignored) {
            // Item model components are available on 1.21.2+ APIs.
        }
    }

    private static NamespacedKey parseItemModelKey(String itemModel) {
        if (itemModel == null || itemModel.isBlank()) return null;
        String[] parts = itemModel.split(":", 2);
        if (parts.length == 1) {
            return new NamespacedKey("minecraft", parts[0]);
        }
        if (parts[0].isBlank() || parts[1].isBlank()) return null;
        return new NamespacedKey(parts[0], parts[1]);
    }

    public static Interaction spawnInteraction(Location location, float width, float height,
                                                ClickAction action) {
        return spawnInteraction(location, width, height, action, false, true);
    }

    public static Interaction spawnInteraction(Location location, float width, float height,
                                                ClickAction action, boolean persistent, boolean responsive) {
        World world = location.getWorld();
        if (world == null) return null;
        Interaction interaction = world.spawn(location, Interaction.class);
        interaction.setPersistent(persistent);
        interaction.setResponsive(responsive);
        interaction.setInteractionWidth(width);
        interaction.setInteractionHeight(height);
        if (action != null) {
            registerClickAction(interaction, action);
        }
        return interaction;
    }

    public static TextDisplay spawnLabel(Location location, String text,
                                          Color bgColor, boolean seeThrough) {
        World world = location.getWorld();
        if (world == null) return null;
        TextDisplay display = world.spawn(location, TextDisplay.class);
        display.setPersistent(false);
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

    public static BlockDisplay spawnBlock(Location location, Material material, Vector3f scale) {
        World world = location.getWorld();
        if (world == null) return null;
        BlockDisplay display = world.spawn(location, BlockDisplay.class);
        display.setPersistent(false);
        display.setViewRange(32f);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setBlock(material.createBlockData());
        display.setTransformation(new Transformation(
                new Vector3f(),
                new AxisAngle4f(),
                scale,
                new AxisAngle4f()));
        return display;
    }

    public static void applyCardTransform(ItemDisplay display, float yaw, float scale) {
        try {
            display.getClass().getMethod("setRotation", float.class, float.class).invoke(display, yaw, 0f);
        } catch (Exception ignored) {
        }
        display.setTransformation(new Transformation(
                new Vector3f(),
                new AxisAngle4f(),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f()));
    }

    public static void removeManagedEntities(Collection<Entity> entities) {
        for (Entity entity : entities) {
            if (entity != null) {
                int entityId = entity.getEntityId();
                unregisterClickAction(entityId);
                if (entity.isValid()) {
                    entity.remove();
                }
            }
        }
    }
}
