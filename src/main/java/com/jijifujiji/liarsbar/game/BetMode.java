package com.jijifujiji.liarsbar.game;

public enum BetMode {
    LIFE("Life"),
    FANTUAN("Fantuan Coins"),
    KUNKUN("Kunkun Coins");

    private final String display;

    BetMode(String display) {
        this.display = display;
    }

    public String getDisplay() {
        return display;
    }
}
