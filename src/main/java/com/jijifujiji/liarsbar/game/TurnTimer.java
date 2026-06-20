package com.jijifujiji.liarsbar.game;

import com.jijifujiji.liarsbar.LiarsBarPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public final class TurnTimer {

    private static final int TURN_SECONDS = 30;

    private final LiarsBarPlugin plugin;
    private BukkitTask task;
    private BossBar bossBar;
    private int secondsLeft;

    public TurnTimer(LiarsBarPlugin plugin) {
        this.plugin = plugin;
    }

    public void start(PlayerState current, Iterable<Player> participants, CardType mainCard, Runnable timeoutAction) {
        cancel();
        secondsLeft = TURN_SECONDS;
        bossBar = Bukkit.createBossBar("", BarColor.RED, BarStyle.SEGMENTED_10);
        for (Player player : participants) {
            bossBar.addPlayer(player);
        }
        update(current, mainCard);
        bossBar.setVisible(true);

        task = new BukkitRunnable() {
            @Override
            public void run() {
                secondsLeft--;
                update(current, mainCard);
                if (secondsLeft <= 0) {
                    TurnTimer.this.cancel();
                    timeoutAction.run();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void removePlayer(Player player) {
        if (bossBar != null) {
            bossBar.removePlayer(player);
        }
    }

    public void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
    }

    private void update(PlayerState current, CardType mainCard) {
        if (bossBar == null) return;
        double progress = Math.max(0.0, Math.min(1.0, secondsLeft / (double) TURN_SECONDS));
        bossBar.setProgress(progress);
        bossBar.setTitle(ChatColor.GOLD + current.getPlayer().getName()
                + ChatColor.YELLOW + " 的回合  "
                + ChatColor.RED + secondsLeft + "s"
                + ChatColor.GRAY + " | "
                + ChatColor.YELLOW + "主牌 " + ChatColor.GOLD + mainCard.getDisplay()
                + ChatColor.GRAY + " | "
                + ChatColor.YELLOW + "子弹 " + ChatColor.GOLD + current.getBullets() + "/6");
    }
}
