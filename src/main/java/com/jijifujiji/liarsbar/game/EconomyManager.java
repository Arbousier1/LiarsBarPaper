package com.jijifujiji.liarsbar.game;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.logging.Level;
import java.util.logging.Logger;

public class EconomyManager {

    private Economy economy;
    private final Logger logger;

    public EconomyManager(Logger logger) {
        this.logger = logger;
    }

    public boolean setup() {
        if (Bukkit.getServer().getPluginManager().getPlugin("Vault") == null) {
            logger.warning("未检测到 Vault 插件，经济功能将不可用。");
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            logger.warning("未找到 Vault 经济服务提供者。");
            return false;
        }
        economy = rsp.getProvider();
        logger.info("Vault 经济系统已连接：" + (economy != null ? economy.getName() : "未知"));
        return economy != null;
    }

    public boolean isEnabled() {
        return economy != null;
    }

    public boolean has(Player player, double amount) {
        if (economy == null) return false;
        return economy.has(player, amount);
    }

    public boolean withdraw(Player player, double amount) {
        if (economy == null) return false;
        EconomyResponse resp = economy.withdrawPlayer(player, amount);
        if (!resp.transactionSuccess()) {
            logger.log(Level.WARNING, "Vault 扣费失败: {0} 从 {1} 扣 {2}: {3}",
                    new Object[]{player.getName(), amount, resp.errorMessage});
            return false;
        }
        return true;
    }

    public boolean deposit(Player player, double amount) {
        if (economy == null) return false;
        EconomyResponse resp = economy.depositPlayer(player, amount);
        if (!resp.transactionSuccess()) {
            logger.log(Level.WARNING, "Vault 存款失败: {0} 存 {1} 给 {2}: {3}",
                    new Object[]{amount, player.getName(), resp.errorMessage});
            return false;
        }
        return true;
    }

    public double getBalance(Player player) {
        if (economy == null) return 0;
        return economy.getBalance(player);
    }

    public String format(double amount) {
        if (economy == null) return String.format("%.2f", amount);
        return economy.format(amount);
    }
}