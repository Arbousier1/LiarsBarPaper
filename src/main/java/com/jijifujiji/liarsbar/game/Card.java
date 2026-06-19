package com.jijifujiji.liarsbar.game;

public enum Card {
    A("A", "liarsbar:card_a", 9999450),
    Q("Q", "liarsbar:card_q", 9999451),
    K("K", "liarsbar:card_k", 9999452),
    KUN("坤", "liarsbar:card_kun", 9999453),
    DEMON("恶魔", "liarsbar:card_demon", 9999454);

    private final String display;
    private final String itemModel;
    private final int customModelData;

    Card(String display, String itemModel, int customModelData) {
        this.display = display;
        this.itemModel = itemModel;
        this.customModelData = customModelData;
    }

    public String getDisplay() {
        return display;
    }

    public String getItemModel() {
        return itemModel;
    }

    public int getCustomModelData() {
        return customModelData;
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
