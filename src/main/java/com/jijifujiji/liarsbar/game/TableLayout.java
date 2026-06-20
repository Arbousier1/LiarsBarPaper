package com.jijifujiji.liarsbar.game;

import org.bukkit.Location;

public final class TableLayout {

    public static final int SEAT_COUNT = 4;

    private static final double[][] SEAT_OFFSETS = {
            {2.0, 0},      // 0: east
            {0, 1.75},     // 1: south
            {-2.0, 0},     // 2: west
            {0, -1.75}     // 3: north
    };

    private static final double[][] CARD_OFFSETS = {
            {0.5, 0},      // east: cards go outward from seat
            {0, -0.5},
            {-0.5, 0},
            {0, 0.5}
    };

    public static final float[] SEAT_YAWS = {90f, 180f, -90f, 0f};

    public static final double SEAT_CARD_BASE_Y = 0.44;

    public static final String MANAGED_ENTITY_TAG = "liarsbar_managed";

    private TableLayout() {}

    public static Location playerCardLocation(Location tableLocation, int seatIndex, int cardIndex) {
        return tableLocation.clone().add(
                seatX(seatIndex) + CARD_OFFSETS[seatIndex][0] * (cardIndex + 1),
                SEAT_CARD_BASE_Y + 0.2,
                seatZ(seatIndex) + CARD_OFFSETS[seatIndex][1] * (cardIndex + 1));
    }

    public static Location centerCardLocation(Location tableLocation, int cardIndex, int totalCards) {
        return tableLocation.clone().add((cardIndex - totalCards / 2.0) * 0.5, 1.29, 0);
    }

    public static Location actionButtonLocation(Location tableLocation, int seatIndex, double zOffset) {
        return tableLocation.clone().add(seatX(seatIndex), SEAT_CARD_BASE_Y - 0.3, seatZ(seatIndex) + zOffset);
    }

    public static Location tableFurnitureLocation(Location tableLocation) {
        Location furniture = tableLocation.clone();
        furniture.setYaw(0f);
        furniture.setPitch(0f);
        return furniture;
    }

    public static Location chairFurnitureLocation(Location tableLocation, int seatIndex) {
        Location furniture = tableLocation.clone().add(seatX(seatIndex), 0, seatZ(seatIndex));
        furniture.setYaw(chairYaw(seatIndex));
        furniture.setPitch(0f);
        return furniture;
    }

    public static String tableEntityTag(String tableId) {
        return "liarsbar_table_" + normalizeTableId(tableId);
    }

    public static int chairSeatIndex(Location tableLocation, Location furnitureLocation) {
        if (tableLocation == null || furnitureLocation == null) return -1;
        if (tableLocation.getWorld() == null || !tableLocation.getWorld().equals(furnitureLocation.getWorld())) return -1;
        for (int i = 0; i < SEAT_COUNT; i++) {
            Location chair = chairFurnitureLocation(tableLocation, i);
            if (Math.abs(chair.getX() - furnitureLocation.getX()) <= 0.25
                    && Math.abs(chair.getY() - furnitureLocation.getY()) <= 0.75
                    && Math.abs(chair.getZ() - furnitureLocation.getZ()) <= 0.25) {
                return i;
            }
        }
        return -1;
    }

    private static float chairYaw(int seatIndex) {
        return SEAT_YAWS[seatIndex];
    }

    private static double seatX(int seatIndex) {
        return SEAT_OFFSETS[seatIndex][0];
    }

    private static double seatZ(int seatIndex) {
        return SEAT_OFFSETS[seatIndex][1];
    }

    private static String normalizeTableId(String tableId) {
        return tableId.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
