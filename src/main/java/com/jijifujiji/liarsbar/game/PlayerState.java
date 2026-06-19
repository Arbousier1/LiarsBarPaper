package com.jijifujiji.liarsbar.game;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlayerState {

    private final Player player;
    private final int seatIndex;
    private final List<Card> hand = new ArrayList<>();
    private final List<Integer> selected = new ArrayList<>();
    private int bullets = 6;
    private boolean alive = true;

    public PlayerState(Player player, int seatIndex) {
        this.player = player;
        this.seatIndex = seatIndex;
    }

    public Player getPlayer() { return player; }
    public int getSeatIndex() { return seatIndex; }
    public List<Card> getHand() { return hand; }
    public List<Integer> getSelected() { return selected; }
    public int getBullets() { return bullets; }
    public void setBullets(int bullets) { this.bullets = Math.max(0, bullets); }
    public boolean isAlive() { return alive; }
    public void setAlive(boolean alive) { this.alive = alive; }
    public boolean hasCards() { return !hand.isEmpty(); }

    public void toggleSelection(int index) {
        if (selected.contains(index)) selected.remove((Integer) index);
        else selected.add(index);
    }

    public void clearSelection() { selected.clear(); }

    public List<Integer> getSortedSelection() {
        List<Integer> sorted = new ArrayList<>(selected);
        Collections.sort(sorted);
        return sorted;
    }

    public void removeCards(List<Integer> indices) {
        List<Integer> sorted = new ArrayList<>(indices);
        sorted.sort(Collections.reverseOrder());
        for (int i : sorted) {
            if (i >= 0 && i < hand.size()) {
                hand.remove(i);
            }
        }
    }

    public void resetForNewRound() {
        hand.clear();
        selected.clear();
        bullets = 6;
        alive = true;
    }
}