package com.jijifujiji.liarsbar.game;

public enum Card {
    A("A"),
    Q("Q"),
    K("K"),
    KUN("坤"),
    DEMON("恶魔");

    private final String display;

    Card(String display) {
        this.display = display;
    }

    public String getDisplay() {
        return display;
    }

    public boolean isMainOrWild(CardType mainType) {
        return this == KUN || matchesMain(mainType);
    }

    public boolean matchesMain(CardType mainType) {
        return switch (mainType) {
            case A -> this == A;
            case Q -> this == Q;
            case K -> this == K;
        };
    }

    public static CardType randomMainType() {
        CardType[] types = {CardType.A, CardType.Q, CardType.K};
        return types[(int) (Math.random() * types.length)];
    }
}
