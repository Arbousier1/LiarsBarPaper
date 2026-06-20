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

            PlaceCall placeCall = findPlaceCall(manager.getClass(), furniture.get().getClass());
            if (placeCall == null) {
                logger.warning("CraftEngine furniture place API is not available. Supported CraftEngine versions use place(Location, furniture, FurnitureExtraData/PersistentData, boolean[, player]).");
                return false;
            }

            Object data = createFurnitureData(placeCall.dataType());
            if (data == null) {
                logger.warning("CraftEngine furniture data API is not available for " + placeCall.dataType().getName());
                return false;
            }

            Object placed = placeCall.invoke(manager, location, furniture.get(), data);
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
            Object furniture = loadedFurniture(manager, entity.getEntityId());
            if (furniture != null && destroyFurniture(furniture)) {
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
        Method method = findMethod(manager.getClass(), "furnitureById", 1);
        if (method == null || !method.getParameterTypes()[0].isAssignableFrom(key.getClass())) {
            return Optional.empty();
        }
        Object result = method.invoke(manager, key);
        return result instanceof Optional<?> optional ? optional : Optional.empty();
    }

    private PlaceCall findPlaceCall(Class<?> managerClass, Class<?> furnitureClass) {
        for (Method method : managerClass.getMethods()) {
            if (!method.getName().equals("place")) continue;
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 4 && params.length != 5) continue;
            if (!Location.class.isAssignableFrom(params[0])) continue;
            if (!params[1].isAssignableFrom(furnitureClass)) continue;
            if (params[3] != boolean.class) continue;
            if (!isSupportedFurnitureData(params[2])) continue;
            return new PlaceCall(method, params[2]);
        }
        return null;
    }

    private boolean isSupportedFurnitureData(Class<?> dataType) {
        String name = dataType.getName();
        return name.equals("net.momirealms.craftengine.core.entity.furniture.FurniturePersistentData")
                || name.equals("net.momirealms.craftengine.core.entity.furniture.FurnitureExtraData");
    }

    private Object createFurnitureData(Class<?> dataType) throws ReflectiveOperationException {
        String name = dataType.getName();
        if (name.equals("net.momirealms.craftengine.core.entity.furniture.FurniturePersistentData")) {
            return dataType.getMethod("ofVariant", String.class).invoke(null, "ground");
        }
        if (name.equals("net.momirealms.craftengine.core.entity.furniture.FurnitureExtraData")) {
            Object builder = dataType.getMethod("builder").invoke(null);
            return builder.getClass().getMethod("build").invoke(builder);
        }
        return null;
    }

    private Object loadedFurniture(Object manager, int entityId) throws ReflectiveOperationException {
        String[] methodNames = {
                "loadedFurnitureByMetaEntityId",
                "loadedFurnitureByRealEntityId",
                "loadedFurnitureByEntityId"
        };
        for (String methodName : methodNames) {
            try {
                Method method = manager.getClass().getMethod(methodName, int.class);
                Object furniture = method.invoke(manager, entityId);
                if (furniture != null) return furniture;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private boolean destroyFurniture(Object furniture) throws ReflectiveOperationException {
        try {
            Method destroy = furniture.getClass().getMethod("destroy");
            destroy.invoke(furniture);
            return true;
        } catch (NoSuchMethodException ignored) {
        }

        try {
            Method destroy = furniture.getClass().getMethod("destroy", craftEnginePlayerClass());
            destroy.invoke(furniture, new Object[] { null });
            return true;
        } catch (NoSuchMethodException ignored) {
        }
        return false;
    }

    private void tagPlacedFurniture(Object placed, String tableId) {
        Entity entity = placedFurnitureEntity(placed);
        if (entity == null) return;
        entity.addScoreboardTag(AUTO_FURNITURE_TAG);
        entity.addScoreboardTag(TableLayout.tableEntityTag(tableId));
    }

    private Entity placedFurnitureEntity(Object placed) {
        if (placed == null) return null;
        String[] entityMethods = { "baseEntity", "bukkitEntity", "getBukkitEntity" };
        for (String methodName : entityMethods) {
            try {
                Method method = placed.getClass().getMethod(methodName);
                Object entity = method.invoke(placed);
                if (entity instanceof Entity bukkitEntity) {
                    return bukkitEntity;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        if (placed instanceof ItemDisplay display) {
            return display;
        }
        return null;
    }

    private Method findMethod(Class<?> type, String name, int parameterCount) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterTypes().length == parameterCount) {
                return method;
            }
        }
        return null;
    }

    private Class<?> craftEnginePlayerClass() throws ClassNotFoundException {
        return Class.forName("net.momirealms.craftengine.core.entity.player.Player");
    }

    private record PlaceCall(Method method, Class<?> dataType) {
        Object invoke(Object manager, Location location, Object furniture, Object data) throws ReflectiveOperationException {
            if (method.getParameterTypes().length == 4) {
                return method.invoke(manager, location, furniture, data, true);
            }
            return method.invoke(manager, location, furniture, data, true, null);
        }
    }
}
