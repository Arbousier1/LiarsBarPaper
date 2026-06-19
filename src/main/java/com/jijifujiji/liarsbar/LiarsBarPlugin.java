package com.jijifujiji.liarsbar;

import com.jijifujiji.liarsbar.command.LiarBarCommand;
import com.jijifujiji.liarsbar.command.LiarBarTabCompleter;
import com.jijifujiji.liarsbar.config.ConfigManager;
import com.jijifujiji.liarsbar.game.ChairManager;
import com.jijifujiji.liarsbar.game.TableManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class LiarsBarPlugin extends JavaPlugin {

    private static LiarsBarPlugin instance;
    private ConfigManager configManager;
    private TableManager tableManager;
    private ChairManager chairManager;

    @Override
    public void onEnable() {
        instance = this;
        this.configManager = new ConfigManager(this);
        this.tableManager = new TableManager(this, configManager);
        this.chairManager = new ChairManager(this);

        getCommand("liarbar").setExecutor(new LiarBarCommand(this));
        getCommand("liarbar").setTabCompleter(new LiarBarTabCompleter(this));
        getLogger().info("骗子酒馆 Paper 插件已加载！");
    }

    @Override
    public void onDisable() {
        if (tableManager != null) {
            tableManager.shutdownAll();
        }
        if (chairManager != null) {
            chairManager.removeAllChairs();
        }
        getLogger().info("骗子酒馆 Paper 插件已卸载！");
    }

    public static LiarsBarPlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public TableManager getTableManager() {
        return tableManager;
    }

    public ChairManager getChairManager() {
        return chairManager;
    }
}
