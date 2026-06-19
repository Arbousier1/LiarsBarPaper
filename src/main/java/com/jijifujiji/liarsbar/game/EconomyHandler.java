package com.jijifujiji.liarsbar.game;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class EconomyHandler {

    public static boolean canAfford(Player player, BetMode mode) {
        return switch (mode) {
            case LIFE -> true;
            case FANTUAN -> hasFantuan(player);
            case KUNKUN -> hasKunkun(player);
        };
    }

    public static boolean pay(Player player, BetMode mode) {
        return switch (mode) {
            case LIFE -> true;
            case FANTUAN -> takeFantuan(player, 1);
            case KUNKUN -> takeKunkun(player, 1);
        };
    }

    public static void rewardWinner(Player player, BetMode mode, int amount) {
        switch (mode) {
            case LIFE -> player.getInventory().addItem(createVictoryBread(amount));
            case FANTUAN -> player.getInventory().addItem(createFantuan(amount));
            case KUNKUN -> player.getInventory().addItem(createKunkun(amount));
        }
    }

    private static boolean hasFantuan(Player player) {
        return findMatching(player, Material.PRIZE_POTTERY_SHERD, "\u00A74饭团币") != null;
    }

    private static boolean takeFantuan(Player player, int amount) {
        ItemStack item = findMatching(player, Material.PRIZE_POTTERY_SHERD, "\u00A74饭团币");
        if (item == null) return false;
        item.setAmount(item.getAmount() - amount);
        return true;
    }

    private static boolean hasKunkun(Player player) {
        return findMatching(player, Material.HEART_OF_THE_SEA, "\u00A7c坤坤币") != null;
    }

    private static boolean takeKunkun(Player player, int amount) {
        ItemStack item = findMatching(player, Material.HEART_OF_THE_SEA, "\u00A7c坤坤币");
        if (item == null) return false;
        item.setAmount(item.getAmount() - amount);
        return true;
    }

    private static ItemStack findMatching(Player player, Material type, String name) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() != type) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta == null || !meta.hasDisplayName()) continue;
            if (ChatColor.stripColor(meta.getDisplayName()).equals(ChatColor.stripColor(name))) {
                return item;
            }
        }
        return null;
    }

    public static ItemStack createFantuan(int amount) {
        ItemStack item = new ItemStack(Material.PRIZE_POTTERY_SHERD, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("\u00A74饭团币");
            meta.setCustomModelData(9999301);
            List<String> lore = new ArrayList<>();
            lore.add("\u00A7f搏一搏，\u00A7e单车变");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createKunkun(int amount) {
        ItemStack item = new ItemStack(Material.HEART_OF_THE_SEA, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("\u00A7c坤坤币");
            List<String> lore = new ArrayList<>();
            lore.add("用于兑换服务器特供物品的货币。");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createVictoryBread(int amount) {
        ItemStack item = new ItemStack(Material.BREAD, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("\u00A7c胜利者的面包");
            List<String> lore = new ArrayList<>();
            lore.add("赢得了命和面包。");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
