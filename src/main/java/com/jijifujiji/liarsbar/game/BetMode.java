package com.jijifujiji.liarsbar.game;

public enum BetMode {
    LIFE("赌命"),
    FANTUAN("饭团币"),
    KUNKUN("坤坤币");

    private final String display;

    BetMode(String display) {
        this.display = display;
    }

    public String getDisplay() {
        return display;
    }
}
