package com.jijifujiji.liarsbar.display;

import com.jijifujiji.liarsbar.game.TableLayout;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.logging.Logger;

public final class CraftEngineFurnitureBridge {

    private static final String AUTO_FURNITURE_TAG = "liarsbar_ce_furniture";
    private static final String TABLE_FURNITURE_ID = "liarsbar:table_visual";
    private static final String CHAIR_FURNITURE_ID = "liarsbar:seat_chair";

    private final Logger logger;

    public CraftEngineFurnitureBridge(Plugin plugin) {
        this.logger = plugin.getLogger();
    }

    public boolean placeTableSet(String tableId, Location tableLocation) {
        if (tableLocation == null || tableLocation.getWorld() == null) return false;

        clearTableSet(tableId, tableLocation);
        int placed = 0;
        if (placeFurniture(TABLE_FURNITURE_ID, tableId, TableLayout.tableFurnitureLocation(tableLocation))) {
            placed++;
        }
        for (int i = 0; i < TableLayout.SEAT_COUNT; i++) {
            if (placeFurniture(CHAIR_FURNITURE_ID, tableId, TableLayout.chairFurnitureLocation(tableLocation, i))) {
                placed++;
            }
        }
        return placed == TableLayout.SEAT_COUNT + 1;
    }

    public void clearTableSet(String tableId, Location tableLocation) {
        if (tableLocation == null || tableLocation.getWorld() == null) return;
        String tableTag = TableLayout.tableEntityTag(tableId);
        for (Entity entity : tableLocation.getWorld().getNearbyEntities(tableLocation, 4.0, 3.0, 4.0)) {
            if (!entity.getScoreboardTags().contains(AUTO_FURNITURE_TAG)
                    || !entity.getScoreboardTags().contains(tableTag)) {
                continue;
            }
            destroyCraftEngineFurniture(entity);
        }
    }

    private boolean placeFurniture(String furnitureId, String tableId, Location location) {
        try {
            Object manager = craftEngineFurnitureManager();
            Object key = key(furnitureId);
            Optional<?> furniture = furnitureById(manager, key);
            if (furniture.isEmpty()) {
                logger.warning("CraftEngine furniture not found: " + furnitureId);
                return false;
            }

            Object data = furniturePersistentData("ground");
            Method place = findPlaceMethod(manager.getClass(), data.getClass(), furniture.get().getClass());
            if (place == null) {
                logger.warning("CraftEngine furniture place API is not available.");
                return false;
            }

            Object placed = place.invoke(manager, location, furniture.get(), data, true, null);
            tagPlacedFurniture(placed, tableId);
            return placed != null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            logger.warning("Failed to place CraftEngine furniture " + furnitureId + ": " + e.getMessage());
            return false;
        }
    }

    private void destroyCraftEngineFurniture(Entity entity) {
        try {
            Object manager = craftEngineFurnitureManager();
            Method loaded = manager.getClass().getMethod("loadedFurnitureByMetaEntityId", int.class);
            Object furniture = loaded.invoke(manager, entity.getEntityId());
            if (furniture != null) {
                Method destroy = furniture.getClass().getMethod("destroy", craftEnginePlayerClass());
                destroy.invoke(furniture, new Object[] { null });
                return;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        if (entity.isValid()) {
            entity.remove();
        }
    }

    private Object craftEngineFurnitureManager() throws ReflectiveOperationException {
        Class<?> managerClass = Class.forName("net.momirealms.craftengine.bukkit.entity.furniture.BukkitFurnitureManager");
        return managerClass.getMethod("instance").invoke(null);
    }

    private Object key(String id) throws ReflectiveOperationException {
        Class<?> keyClass = Class.forName("net.momirealms.craftengine.core.util.Key");
        return keyClass.getMethod("of", String.class).invoke(null, id);
    }

    private Optional<?> furnitureById(Object manager, Object key) throws ReflectiveOperationException {
        Method method = manager.getClass().getMethod("furnitureById", key.getClass());
        Object result = method.invoke(manager, key);
        return result instanceof Optional<?> optional ? optional : Optional.empty();
    }

    private Object furniturePersistentData(String variant) throws ReflectiveOperationException {
        Class<?> dataClass = Class.forName("net.momirealms.craftengine.core.entity.furniture.FurniturePersistentData");
        return dataClass.getMethod("ofVariant", String.class).invoke(null, variant);
    }

    private Method findPlaceMethod(Class<?> managerClass, Class<?> dataClass, Class<?> furnitureClass) {
        for (Method method : managerClass.getMethods()) {
            if (!method.getName().equals("place")) continue;
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 5) continue;
            if (!Location.class.isAssignableFrom(params[0])) continue;
            if (!params[1].isAssignableFrom(furnitureClass)) continue;
            if (!params[2].isAssignableFrom(dataClass)) continue;
            if (params[3] != boolean.class) continue;
            return method;
        }
        return null;
    }

    private void tagPlacedFurniture(Object placed, String tableId) {
        if (placed == null) return;
        try {
            Method entityMethod;
            try {
                entityMethod = placed.getClass().getMethod("bukkitEntity");
            } catch (NoSuchMethodException ignored) {
                entityMethod = placed.getClass().getMethod("getBukkitEntity");
            }
            Object entity = entityMethod.invoke(placed);
            if (entity instanceof ItemDisplay display) {
                display.addScoreboardTag(AUTO_FURNITURE_TAG);
                display.addScoreboardTag(TableLayout.tableEntityTag(tableId));
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private Class<?> craftEnginePlayerClass() throws ClassNotFoundException {
        return Class.forName("net.momirealms.craftengine.core.entity.player.Player");
    }
}
