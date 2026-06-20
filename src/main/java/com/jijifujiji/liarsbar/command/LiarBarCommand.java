package com.jijifujiji.liarsbar.command;

import com.jijifujiji.liarsbar.LiarsBarPlugin;
import com.jijifujiji.liarsbar.game.BetMode;
import com.jijifujiji.liarsbar.game.Table;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LiarBarCommand implements CommandExecutor {

    private final LiarsBarPlugin plugin;

    public LiarBarCommand(LiarsBarPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("command.only-player"));
            return true;
        }
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "set" -> handleSet(player, args);
            case "build" -> handleBuild(player, args);
            case "create" -> handleCreate(player, args);
            case "delete" -> handleDelete(player, args);
            case "gambling" -> handleGambling(player, args);
            case "join" -> handleJoin(player, args);
            case "leave" -> handleLeave(player);
            case "start" -> handleStart(player, args);
            case "stop" -> handleStop(player, args);
            case "mode" -> handleMode(player, args);
            case "select" -> handleSelect(player, args);
            case "play" -> handlePlay(player);
            case "challenge" -> handleChallenge(player);
            case "info" -> handleInfo(player);
            case "help" -> sendHelp(player);
            default -> sendHelp(player);
        }
        return true;
    }

    private void handleSet(Player player, String[] args) {
        if (!player.hasPermission("liarsbar.admin")) {
            send(player, "command.no-permission");
            return;
        }
        if (args.length < 2) {
            send(player, "command.usage.set");
            return;
        }
        String id = args[1].toLowerCase();
        Location loc = player.getLocation();
        plugin.getConfigManager().setTableLocation(id, loc);
        Table table = plugin.getTableManager().createTable(id, loc);
        table.setLocation(loc);
        boolean furniturePlaced = table.syncCraftEngineFurniture();
        table.buildDisplay();
        send(player, "command.table.set",
                "table", id.toUpperCase(),
                "sync", msg(furniturePlaced ? "craftengine.sync-success" : "craftengine.sync-failure"));
    }

    private void handleBuild(Player player, String[] args) {
        if (!player.hasPermission("liarsbar.admin")) {
            send(player, "command.no-permission");
            return;
        }
        if (args.length < 2) {
            send(player, "command.usage.build");
            return;
        }
        Table table = plugin.getTableManager().getTable(args[1]);
        if (table == null) {
            send(player, "command.table.not-found-set-first");
            return;
        }
        boolean furniturePlaced = table.syncCraftEngineFurniture();
        table.buildDisplay();
        send(player, "command.table.built",
                "table", table.getId().toUpperCase(),
                "sync", msg(furniturePlaced ? "craftengine.sync-success" : "craftengine.sync-failure"));
    }

    private void handleCreate(Player player, String[] args) {
        if (!player.hasPermission("liarsbar.admin")) {
            send(player, "command.no-permission");
            return;
        }
        if (args.length < 2) {
            send(player, "command.usage.create");
            return;
        }
        String id = args[1].toLowerCase();
        if (plugin.getTableManager().getTable(id) != null) {
            send(player, "command.table.exists", "table", id);
            return;
        }
        Location loc = player.getLocation();
        plugin.getConfigManager().setTableLocation(id, loc);
        Table table = plugin.getTableManager().createTable(id, loc);
        boolean furniturePlaced = table.syncCraftEngineFurniture();
        table.buildDisplay();
        send(player, "command.table.created",
                "table", id,
                "sync", msg(furniturePlaced ? "craftengine.sync-create-success" : "craftengine.sync-create-failure"));
    }

    private void handleDelete(Player player, String[] args) {
        if (!player.hasPermission("liarsbar.admin")) {
            send(player, "command.no-permission");
            return;
        }
        if (args.length < 2) {
            send(player, "command.usage.delete");
            return;
        }
        if (plugin.getTableManager().deleteTable(args[1])) {
            plugin.getConfigManager().removeTableLocation(args[1]);
            send(player, "command.table.deleted", "table", args[1]);
        } else {
            send(player, "command.table.not-found");
        }
    }

    private void handleGambling(Player player, String[] args) {
        if (!player.hasPermission("liarsbar.admin")) {
            send(player, "command.no-permission");
            return;
        }
        if (args.length < 2) {
            boolean on = plugin.getConfigManager().isGamblingEnabled();
            send(player, "command.gambling.status", "state", msg(on ? "common.on" : "common.off"));
            return;
        }
        boolean enable = switch (args[1].toLowerCase()) {
            case "on", "true", "1", "开" -> true;
            default -> false;
        };
        plugin.getConfigManager().setGamblingEnabled(enable);
        send(player, "command.gambling.changed", "state", msg(enable ? "common.enabled" : "common.disabled"));
    }

    private void handleJoin(Player player, String[] args) {
        if (args.length < 2) {
            send(player, "command.usage.join");
            return;
        }
        Table table = plugin.getTableManager().getTable(args[1]);
        if (table == null) {
            send(player, "command.table.not-found");
            return;
        }
        // 自动分配一个空座位
        for (int i = 0; i < 4; i++) {
            if (!table.isSeatOccupied(i)) {
                table.joinSeat(player, i);
                return;
            }
        }
        send(player, "command.join.full");
    }

    private void handleLeave(Player player) {
        Table table = plugin.getTableManager().findTableByPlayer(player);
        if (table == null) {
            send(player, "command.leave.not-in-game");
            return;
        }
        table.removePlayer(player);
        send(player, "command.leave.left");
    }

    private void handleStart(Player player, String[] args) {
        Table table;
        if (args.length >= 2) {
            table = plugin.getTableManager().getTable(args[1]);
        } else {
            table = plugin.getTableManager().findTableByPlayer(player);
        }
        if (table == null) {
            send(player, "command.table.not-found-or-not-in-game");
            return;
        }
        table.startGame(player);
    }

    private void handleStop(Player player, String[] args) {
        if (!player.hasPermission("liarsbar.admin")) {
            send(player, "command.no-permission");
            return;
        }
        Table table;
        if (args.length >= 2) {
            table = plugin.getTableManager().getTable(args[1]);
        } else {
            table = plugin.getTableManager().findTableByPlayer(player);
        }
        if (table == null) {
            send(player, "command.table.not-found");
            return;
        }
        table.endGame(player);
    }

    private void handleMode(Player player, String[] args) {
        if (!player.hasPermission("liarsbar.admin")) {
            send(player, "command.no-permission");
            return;
        }
        if (args.length < 3) {
            send(player, "command.usage.mode");
            return;
        }
        Table table = plugin.getTableManager().getTable(args[1]);
        if (table == null) {
            send(player, "command.table.not-found");
            return;
        }
        BetMode mode = switch (args[2].toLowerCase()) {
            case "fantuan", "f", "饭团" -> BetMode.FANTUAN;
            case "kunkun", "k", "坤坤" -> BetMode.KUNKUN;
            default -> BetMode.LIFE;
        };
        if (!table.setBetMode(mode)) {
            send(player, "command.mode.running");
            return;
        }
        send(player, "command.mode.changed", "mode", plugin.messages().betMode(mode));
    }

    private void handleSelect(Player player, String[] args) {
        if (args.length < 2) {
            send(player, "command.usage.select");
            return;
        }
        Table table = plugin.getTableManager().findTableByPlayer(player);
        if (table == null) {
            send(player, "command.leave.not-in-game");
            return;
        }
        try {
            int index = Integer.parseInt(args[1]) - 1;
            table.selectCard(player, index);
        } catch (NumberFormatException e) {
            send(player, "command.select.invalid-number");
        }
    }

    private void handlePlay(Player player) {
        Table table = plugin.getTableManager().findTableByPlayer(player);
        if (table == null) {
            send(player, "command.leave.not-in-game");
            return;
        }
        table.playCards(player);
    }

    private void handleChallenge(Player player) {
        Table table = plugin.getTableManager().findTableByPlayer(player);
        if (table == null) {
            send(player, "command.leave.not-in-game");
            return;
        }
        table.challenge(player);
    }

    private void handleInfo(Player player) {
        send(player, "command.info.header");
        if (plugin.getTableManager().getTables().isEmpty()) {
            send(player, "command.info.empty");
        } else {
            for (Table table : plugin.getTableManager().getTables()) {
                player.sendMessage(table.getInfo());
            }
        }
        send(player, "command.info.hint");
    }

    private void sendHelp(Player player) {
        for (String line : plugin.messages().list("command.help")) {
            player.sendMessage(line);
        }
    }

    private void send(Player player, String key, Object... replacements) {
        player.sendMessage(msg(key, replacements));
    }

    private String msg(String key, Object... replacements) {
        return plugin.messages().get(key, replacements);
    }
}
