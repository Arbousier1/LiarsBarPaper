package com.jijifujiji.liarsbar;

import com.jijifujiji.liarsbar.command.LiarBarCommand;
import com.jijifujiji.liarsbar.command.LiarBarTabCompleter;
import com.jijifujiji.liarsbar.config.ConfigManager;
import com.jijifujiji.liarsbar.display.DisplayManager;
import com.jijifujiji.liarsbar.game.EconomyManager;
import com.jijifujiji.liarsbar.game.TableManager;
import com.jijifujiji.liarsbar.listener.EntityInteractListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class LiarsBarPlugin extends JavaPlugin {

    private static LiarsBarPlugin instance;
    private ConfigManager configManager;
    private EconomyManager economyManager;
    private TableManager tableManager;

    @Override
    public void onEnable() {
        instance = this;
        this.configManager = new ConfigManager(this);
        this.economyManager = new EconomyManager(getLogger());
        this.economyManager.setup();
        this.tableManager = new TableManager(this, configManager);

        getCommand("liarbar").setExecutor(new LiarBarCommand(this));
        getCommand("liarbar").setTabCompleter(new LiarBarTabCompleter(this));
        getServer().getPluginManager().registerEvents(new EntityInteractListener(this), this);

        getLogger().info("骗子酒馆 Paper 插件已加载！");
    }

    @Override
    public void onDisable() {
        if (tableManager != null) tableManager.shutdownAll();
        DisplayManager.clearAll();
        getLogger().info("骗子酒馆 Paper 插件已卸载！");
    }

    public static LiarsBarPlugin getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public TableManager getTableManager() { return tableManager; }
}