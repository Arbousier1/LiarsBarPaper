package com.jijifujiji.liarsbar.game;

public enum Card {
    A("A", "liarsbar:card_a"),
    Q("Q", "liarsbar:card_q"),
    K("K", "liarsbar:card_k"),
    KUN("坤", "liarsbar:card_kun"),
    DEMON("恶魔", "liarsbar:card_demon");

    private final String display;
    private final String itemModel;

    Card(String display, String itemModel) {
        this.display = display;
        this.itemModel = itemModel;
    }

    public String getDisplay() {
        return display;
    }

    public String getItemModel() {
        return itemModel;
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
