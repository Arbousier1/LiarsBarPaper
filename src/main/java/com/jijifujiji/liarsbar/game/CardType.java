package com.jijifujiji.liarsbar.game;

public enum CardType {
    A("A"),
    Q("Q"),
    K("K");

    private final String display;

    CardType(String display) {
        this.display = display;
    }

    public String getDisplay() {
        return display;
    }
}
