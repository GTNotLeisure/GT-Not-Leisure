package com.science.gtnl.common.wireless;

public class WirelessCardActions {

    public static final int TOGGLE_AUTO = 2;
    public static final int REFRESH = 3;
    public static final int CYCLE_VISUALISATION = 9;

    public static final int TOGGLE_PAUSE = 10;
    public static final int OPEN_DETAILS = 20;
    public static final int HIGHLIGHT = 40;
    public static final int TELEPORT = 50;
    public static final int TOGGLE_GROUPING = 61;
    public static final int SET_VISUALISATION = 62;
    public static final int RENAME_GROUP = 63;
    public static final int RENAME_DEVICE = 64;
    public static final int RENAME_CLUSTER = 65;
    public static final int OPEN_RENAME = 70;
    public static final int OPEN_DEVICE = 80;
    private static final int ROW_STRIDE = 10;

    public static int type(int id) {
        return isRowAction(id) ? id - rowIndex(id) : id;
    }

    public static int rowIndex(int id) {
        return id % ROW_STRIDE;
    }

    public static boolean isRowAction(int id) {
        return id >= TOGGLE_PAUSE && id < TELEPORT + ROW_STRIDE || id >= OPEN_RENAME && id < OPEN_DEVICE + ROW_STRIDE;
    }
}
