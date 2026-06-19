package com.jijifujiji.liarsbar;

import com.jijifujiji.liarsbar.command.LiarBarCommand;
import com.jijifujiji.liarsbar.command.LiarBarTabCompleter;
import com.jijifujiji.liarsbar.game.TableManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class LiarsBarPlugin extends JavaPlugin {

    private static LiarsBarPlugin instance;
    private TableManager tableManager;

    @Override
    public void onEnable() {
        instance = this;
        this.tableManager = new TableManager(this);

        getCommand("liarbar").setExecutor(new LiarBarCommand(this));
        getCommand("liarbar").setTabCompleter(new LiarBarTabCompleter(this));
        getLogger().info("骗子酒馆 Paper 插件已加载！");
    }

    @Override
    public void onDisable() {
        if (tableManager != null) {
            tableManager.shutdownAll();
        }
        getLogger().info("骗子酒馆 Paper 插件已卸载！");
    }

    public static LiarsBarPlugin getInstance() {
        return instance;
    }

    public TableManager getTableManager() {
        return tableManager;
    }
}
