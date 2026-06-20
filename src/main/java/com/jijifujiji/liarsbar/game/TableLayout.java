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

    // Chair visual/collision is provided by CraftEngine furniture. This value only keeps
    // gameplay seat anchors aligned with the top of the CE chair red cushion model.
    private static final double CE_CHAIR_VISUAL_Y = 0.42;
    private static final double CE_CHAIR_MODEL_CENTER_Y = 8.0 / 16.0;
    private static final double CE_CHAIR_RED_CUSHION_TOP_Y = CE_CHAIR_VISUAL_Y + ((7.65 / 16.0) - CE_CHAIR_MODEL_CENTER_Y);

    public static final double SEAT_CLICK_Y = 0.10;
    public static final double SEAT_RIDE_Y = CE_CHAIR_RED_CUSHION_TOP_Y;
    public static final double SEAT_CARD_BASE_Y = 0.44;

    public static final String MANAGED_ENTITY_TAG = "liarsbar_managed";

    private TableLayout() {}

    public static Location seatLocation(Location tableLocation, int seatIndex) {
        Location seatLoc = tableLocation.clone().add(seatX(seatIndex), SEAT_RIDE_Y, seatZ(seatIndex));
        seatLoc.setYaw(SEAT_YAWS[seatIndex]);
        seatLoc.setPitch(0f);
        return seatLoc;
    }

    public static Location seatClickLocation(Location tableLocation, int seatIndex) {
        return tableLocation.clone().add(seatX(seatIndex), SEAT_CLICK_Y, seatZ(seatIndex));
    }

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

    public static String seatVehicleTag(int seatIndex) {
        return "liarsbar_seat_vehicle_" + seatIndex;
    }

    private static float chairYaw(int seatIndex) {
        float yaw = SEAT_YAWS[seatIndex] + 180f;
        return yaw > 180f ? yaw - 360f : yaw;
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
