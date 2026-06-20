package com.jijifujiji.liarsbar.game;

import com.jijifujiji.liarsbar.i18n.Messages;
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
    private final Messages messages;

    public EconomyManager(Logger logger, Messages messages) {
        this.logger = logger;
        this.messages = messages;
    }

    public boolean setup() {
        if (Bukkit.getServer().getPluginManager().getPlugin("Vault") == null) {
            logger.warning(messages.plain("log.vault-missing"));
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            logger.warning(messages.plain("log.vault-provider-missing"));
            return false;
        }
        economy = rsp.getProvider();
        logger.info(messages.plain("log.vault-connected", "provider",
                economy != null ? economy.getName() : messages.plain("log.vault-unknown-provider")));
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
            logger.log(Level.WARNING, messages.plain("log.vault-withdraw-failed",
                    "player", player.getName(), "amount", amount, "error", resp.errorMessage));
            return false;
        }
        return true;
    }

    public boolean deposit(Player player, double amount) {
        if (economy == null) return false;
        EconomyResponse resp = economy.depositPlayer(player, amount);
        if (!resp.transactionSuccess()) {
            logger.log(Level.WARNING, messages.plain("log.vault-deposit-failed",
                    "player", player.getName(), "amount", amount, "error", resp.errorMessage));
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
