package com.jijifujiji.liarsbar.listener;

import com.jijifujiji.liarsbar.LiarsBarPlugin;
import com.jijifujiji.liarsbar.game.TableManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import java.lang.reflect.Method;

public final class CraftEngineFurnitureInteractListener implements Listener {

    private static final String EVENT_CLASS = "net.momirealms.craftengine.bukkit.api.event.FurnitureInteractEvent";

    private final LiarsBarPlugin plugin;

    private CraftEngineFurnitureInteractListener(LiarsBarPlugin plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("unchecked")
    public static void register(LiarsBarPlugin plugin) {
        try {
            Class<? extends Event> eventClass = (Class<? extends Event>) Class.forName(EVENT_CLASS).asSubclass(Event.class);
            CraftEngineFurnitureInteractListener listener = new CraftEngineFurnitureInteractListener(plugin);
            EventExecutor executor = (ignored, event) -> listener.handle(event);
            plugin.getServer().getPluginManager().registerEvent(
                    eventClass, listener, EventPriority.NORMAL, executor, plugin, true);
            plugin.getLogger().info("CraftEngine furniture seat listener registered.");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("CraftEngine furniture event API was not found; chair clicks cannot join tables.");
        }
    }

    private void handle(Event event) {
        try {
            Player player = (Player) event.getClass().getMethod("getPlayer").invoke(event);
            Location furnitureLocation = (Location) event.getClass().getMethod("location").invoke(event);
            if (!isMainHand(event)) return;

            TableManager.ChairSeat chairSeat = plugin.getTableManager().findChairSeat(furnitureLocation);
            if (chairSeat == null) return;

            boolean joined = chairSeat.table().joinCraftEngineSeat(player, chairSeat.seatIndex());
            if (!joined && event instanceof Cancellable cancellable) {
                cancellable.setCancelled(true);
            }
        } catch (ReflectiveOperationException | ClassCastException e) {
            plugin.getLogger().warning("Failed to handle CraftEngine furniture interaction: " + e.getMessage());
        }
    }

    private boolean isMainHand(Event event) throws ReflectiveOperationException {
        Method hand = event.getClass().getMethod("hand");
        Object value = hand.invoke(event);
        return value == null || value.toString().equalsIgnoreCase("MAIN_HAND");
    }
}
