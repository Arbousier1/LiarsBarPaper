package com.jijifujiji.liarsbar.listener;

import com.jijifujiji.liarsbar.LiarsBarPlugin;
import com.jijifujiji.liarsbar.game.TableManager;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

public final class CraftEngineFurnitureInteractListener implements Listener {

    private static final String EVENT_CLASS = "net.momirealms.craftengine.bukkit.api.event.FurnitureInteractEvent";
    private static final String HIT_EVENT_CLASS = "net.momirealms.craftengine.bukkit.api.event.FurnitureHitEvent";
    private static final String BREAK_EVENT_CLASS = "net.momirealms.craftengine.bukkit.api.event.FurnitureBreakEvent";

    private final LiarsBarPlugin plugin;

    private CraftEngineFurnitureInteractListener(LiarsBarPlugin plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("unchecked")
    public static void register(LiarsBarPlugin plugin) {
        CraftEngineFurnitureInteractListener listener = new CraftEngineFurnitureInteractListener(plugin);
        listener.registerFurnitureInteract();
        listener.registerFurnitureProtection();
        listener.registerSeatVehicleEvent("org.bukkit.event.entity.EntityMountEvent", EventPriority.NORMAL, listener::handleSeatMount);
        listener.registerSeatVehicleEvent("org.spigotmc.event.entity.EntityMountEvent", EventPriority.NORMAL, listener::handleSeatMount);
        listener.registerSeatVehicleEvent("org.bukkit.event.entity.EntityDismountEvent", EventPriority.LOWEST, listener::handleSeatDismount);
        listener.registerSeatVehicleEvent("org.spigotmc.event.entity.EntityDismountEvent", EventPriority.LOWEST, listener::handleSeatDismount);
    }

    @SuppressWarnings("unchecked")
    private void registerFurnitureInteract() {
        try {
            Class<? extends Event> eventClass = craftEngineEventClass(EVENT_CLASS);
            EventExecutor executor = (ignored, event) -> handleFurnitureInteract(event);
            plugin.getServer().getPluginManager().registerEvent(
                    eventClass, this, EventPriority.NORMAL, executor, plugin, true);
            plugin.getLogger().info("CraftEngine furniture seat listener registered.");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("CraftEngine furniture event API was not found; chair clicks cannot join tables.");
        }
    }

    @SuppressWarnings("unchecked")
    private void registerFurnitureProtection() {
        registerFurnitureProtectionEvent(HIT_EVENT_CLASS, EventPriority.LOWEST);
        registerFurnitureProtectionEvent(BREAK_EVENT_CLASS, EventPriority.HIGHEST);
    }

    @SuppressWarnings("unchecked")
    private void registerFurnitureProtectionEvent(String className, EventPriority priority) {
        try {
            Class<? extends Event> eventClass = craftEngineEventClass(className);
            EventExecutor executor = (ignored, event) -> protectLiarsBarFurniture(event);
            plugin.getServer().getPluginManager().registerEvent(
                    eventClass, this, priority, executor, plugin, true);
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("CraftEngine furniture protection event API was not found: " + className);
        }
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Event> craftEngineEventClass(String className) throws ClassNotFoundException {
        Plugin craftEngine = plugin.getServer().getPluginManager().getPlugin("CraftEngine");
        if (craftEngine != null) {
            try {
                return (Class<? extends Event>) Class.forName(className, true, craftEngine.getClass().getClassLoader())
                        .asSubclass(Event.class);
            } catch (ClassNotFoundException ignored) {
            }
        }
        return (Class<? extends Event>) Class.forName(className).asSubclass(Event.class);
    }

    @SuppressWarnings("unchecked")
    private void registerSeatVehicleEvent(String className, EventPriority priority, EventExecutor executor) {
        try {
            Class<? extends Event> eventClass = (Class<? extends Event>) Class.forName(className).asSubclass(Event.class);
            plugin.getServer().getPluginManager().registerEvent(eventClass, this, priority, executor, plugin, true);
        } catch (ClassNotFoundException ignored) {
            // Paper/Spigot have used different packages for mount events across versions.
        }
    }

    private void protectLiarsBarFurniture(Event event) {
        if (!(event instanceof Cancellable cancellable)) return;
        try {
            Object furniture = event.getClass().getMethod("furniture").invoke(event);
            if (plugin.getCraftEngineFurnitureBridge().isLiarsBarFurniture(furniture)) {
                cancellable.setCancelled(true);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("Failed to protect CraftEngine furniture: " + e.getMessage());
        }
    }

    private void handleFurnitureInteract(Event event) {
        try {
            Player player = (Player) event.getClass().getMethod("getPlayer").invoke(event);
            Location furnitureLocation = (Location) event.getClass().getMethod("location").invoke(event);
            if (!isMainHand(event)) return;

            TableManager.ChairSeat chairSeat = plugin.getTableManager().findChairSeat(furnitureLocation);
            if (chairSeat == null) return;

            if (!chairSeat.table().canJoinCraftEngineSeat(player, chairSeat.seatIndex()) && event instanceof Cancellable cancellable) {
                cancellable.setCancelled(true);
            }
        } catch (ReflectiveOperationException | ClassCastException e) {
            plugin.getLogger().warning("Failed to handle CraftEngine furniture interaction: " + e.getMessage());
        }
    }

    private void handleSeatMount(Listener ignored, Event event) {
        Entity entity = eventEntity(event, "getEntity");
        if (!(entity instanceof Player player)) return;

        TableManager.ChairSeat chairSeat = chairSeatForSeatEntity(eventEntity(event, "getMount"));
        if (chairSeat == null) return;

        boolean joined = chairSeat.table().joinCraftEngineSeat(player, chairSeat.seatIndex());
        if (!joined) {
            cancel(event);
            plugin.getServer().getScheduler().runTask(plugin, player::leaveVehicle);
        }
    }

    private void handleSeatDismount(Listener ignored, Event event) {
        Entity entity = eventEntity(event, "getEntity");
        if (!(entity instanceof Player player)) return;

        TableManager.ChairSeat chairSeat = chairSeatForSeatEntity(eventEntity(event, "getDismounted"));
        if (chairSeat == null) return;

        boolean allowed = chairSeat.table().handleCraftEngineSeatDismount(player);
        if (!allowed) {
            cancel(event);
        }
    }

    private TableManager.ChairSeat chairSeatForSeatEntity(Entity seatEntity) {
        Entity furnitureEntity = plugin.getCraftEngineFurnitureBridge().furnitureEntityForSeat(seatEntity);
        if (furnitureEntity == null) return null;
        return plugin.getTableManager().findChairSeat(furnitureEntity.getLocation());
    }

    private Entity eventEntity(Event event, String methodName) {
        if (event == null) return null;
        try {
            Object value = event.getClass().getMethod(methodName).invoke(event);
            return value instanceof Entity entity ? entity : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private void cancel(Event event) {
        if (event instanceof Cancellable cancellable) {
            cancellable.setCancelled(true);
        }
    }

    private boolean isMainHand(Event event) throws ReflectiveOperationException {
        Method hand = event.getClass().getMethod("hand");
        Object value = hand.invoke(event);
        return value == null || value.toString().equalsIgnoreCase("MAIN_HAND");
    }
}
