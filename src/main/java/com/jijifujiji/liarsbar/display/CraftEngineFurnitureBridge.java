package com.jijifujiji.liarsbar.display;

import com.jijifujiji.liarsbar.game.TableLayout;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.logging.Logger;

public final class CraftEngineFurnitureBridge {

    private static final String AUTO_FURNITURE_TAG = "liarsbar_ce_furniture";
    private static final String TABLE_FURNITURE_ID = "liarsbar:table_visual";
    private static final String CHAIR_FURNITURE_ID = "liarsbar:seat_chair";
    private static final NamespacedKey CE_FURNITURE_ID_KEY = new NamespacedKey("craftengine", "furniture_id");

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
            boolean taggedForTable = entity.getScoreboardTags().contains(AUTO_FURNITURE_TAG)
                    && entity.getScoreboardTags().contains(tableTag);
            boolean legacyLiarsBarFurniture = isLegacyTableSetFurniture(entity, tableLocation);
            if (!taggedForTable && !legacyLiarsBarFurniture) {
                continue;
            }
            destroyCraftEngineFurniture(entity);
        }
    }

    public Entity furnitureEntityForSeat(Entity seatEntity) {
        if (seatEntity == null) return null;
        try {
            Class<?> api = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineFurniture");
            Method getLoadedFurnitureBySeat = api.getMethod("getLoadedFurnitureBySeat", Entity.class);
            Object furniture = getLoadedFurnitureBySeat.invoke(null, seatEntity);
            if (furniture == null) return null;

            Method metaDataEntity = furniture.getClass().getMethod("metaDataEntity");
            Object wrappedEntity = metaDataEntity.invoke(furniture);
            if (wrappedEntity == null) return null;

            Method platformEntity = wrappedEntity.getClass().getMethod("platformEntity");
            Object entity = platformEntity.invoke(wrappedEntity);
            return entity instanceof Entity bukkitEntity ? bukkitEntity : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            logger.fine("Failed to resolve CraftEngine furniture from seat: " + e.getMessage());
            return null;
        }
    }

    public boolean isLiarsBarFurniture(Object furniture) {
        if (furniture == null) return false;
        try {
            if (isLiarsBarFurnitureId(furnitureId(furniture))) {
                return true;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return isLiarsBarFurnitureEntity(placedFurnitureEntity(furniture));
    }

    public boolean isLiarsBarFurnitureEntity(Entity entity) {
        if (entity == null) return false;
        if (entity.getScoreboardTags().contains(AUTO_FURNITURE_TAG)) {
            return true;
        }
        return isLiarsBarFurnitureId(persistentFurnitureId(entity));
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

    private boolean isLegacyTableSetFurniture(Entity entity, Location tableLocation) {
        try {
            Object manager = craftEngineFurnitureManager();
            Object furniture = loadedFurniture(manager, entity.getEntityId());
            if (furniture == null) {
                String persistentId = persistentFurnitureId(entity);
                return isLiarsBarFurnitureId(persistentId)
                        && isExpectedTableSetLocation(persistentId, tableLocation, entity.getLocation());
            }

            String id = furnitureId(furniture);
            if (!isLiarsBarFurnitureId(id)) {
                return false;
            }

            Entity baseEntity = placedFurnitureEntity(furniture);
            Location location = baseEntity != null ? baseEntity.getLocation() : entity.getLocation();
            return isExpectedTableSetLocation(id, tableLocation, location);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private boolean isLiarsBarFurnitureId(String id) {
        return TABLE_FURNITURE_ID.equals(id) || CHAIR_FURNITURE_ID.equals(id);
    }

    private String persistentFurnitureId(Entity entity) {
        try {
            Object container = entity.getClass().getMethod("getPersistentDataContainer").invoke(entity);
            Object id = container.getClass()
                    .getMethod("get", NamespacedKey.class, PersistentDataType.class)
                    .invoke(container, CE_FURNITURE_ID_KEY, PersistentDataType.STRING);
            return id instanceof String value ? value : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private boolean isExpectedTableSetLocation(String furnitureId, Location tableLocation, Location furnitureLocation) {
        if (furnitureLocation == null || furnitureLocation.getWorld() == null) return false;
        if (!furnitureLocation.getWorld().equals(tableLocation.getWorld())) return false;
        if (TABLE_FURNITURE_ID.equals(furnitureId)) {
            return furnitureLocation.distanceSquared(TableLayout.tableFurnitureLocation(tableLocation)) <= 0.36;
        }
        for (int i = 0; i < TableLayout.SEAT_COUNT; i++) {
            if (furnitureLocation.distanceSquared(TableLayout.chairFurnitureLocation(tableLocation, i)) <= 0.36) {
                return true;
            }
        }
        return false;
    }

    private String furnitureId(Object furniture) throws ReflectiveOperationException {
        Object config;
        try {
            config = furniture.getClass().getField("config").get(furniture);
        } catch (NoSuchFieldException ignored) {
            config = furniture.getClass().getMethod("config").invoke(furniture);
        }
        Object id = config.getClass().getMethod("id").invoke(config);
        return id == null ? "" : id.toString();
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
                "loadedFurnitureByInteractableEntityId",
                "loadedFurnitureByColliderEntityId",
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
        try {
            Method method = placed.getClass().getMethod("metaDataEntity");
            Object wrappedEntity = method.invoke(placed);
            Entity entity = platformEntity(wrappedEntity);
            if (entity != null) {
                return entity;
            }
        } catch (ReflectiveOperationException ignored) {
        }

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

    private Entity platformEntity(Object wrappedEntity) {
        if (wrappedEntity == null) return null;
        if (wrappedEntity instanceof Entity entity) return entity;
        try {
            Method method = wrappedEntity.getClass().getMethod("platformEntity");
            Object entity = method.invoke(wrappedEntity);
            return entity instanceof Entity bukkitEntity ? bukkitEntity : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
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
