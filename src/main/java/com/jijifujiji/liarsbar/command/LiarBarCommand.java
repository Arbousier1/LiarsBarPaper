package com.jijifujiji.liarsbar.command;

import com.jijifujiji.liarsbar.LiarsBarPlugin;
import com.jijifujiji.liarsbar.game.BetMode;
import com.jijifujiji.liarsbar.game.Table;
import org.bukkit.ChatColor;
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
            sender.sendMessage("该命令只能由玩家执行。");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "create" -> handleCreate(player, args);
            case "delete" -> handleDelete(player, args);
            case "join" -> handleJoin(player, args);
            case "leave" -> handleLeave(player);
            case "start" -> handleStart(player, args);
            case "stop" -> handleStop(player, args);
            case "mode" -> handleMode(player, args);
            case "select" -> handleSelect(player, args);
            case "play" -> handlePlay(player, args);
            case "challenge" -> handleChallenge(player, args);
            case "info" -> handleInfo(player);
            case "help", "?" -> sendHelp(player);
            default -> sendHelp(player);
        }
        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (!player.hasPermission("liarsbar.admin")) {
            player.sendMessage(ChatColor.RED + "你没有权限创建桌子。");
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "用法：/liarbar create <桌子ID>");
            return;
        }
        String id = args[1];
        Location loc = player.getLocation();
        Table table = plugin.getTableManager().createTable(id, loc);
        if (table == null) {
            player.sendMessage(ChatColor.RED + "桌子 " + id + " 已存在。");
            return;
        }
        player.sendMessage(ChatColor.GREEN + "成功创建桌子 " + ChatColor.GOLD + id);
    }

    private void handleDelete(Player player, String[] args) {
        if (!player.hasPermission("liarsbar.admin")) {
            player.sendMessage(ChatColor.RED + "你没有权限删除桌子。");
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "用法：/liarbar delete <桌子ID>");
            return;
        }
        boolean ok = plugin.getTableManager().deleteTable(args[1]);
        player.sendMessage((ok ? ChatColor.GREEN + "已删除桌子。" : ChatColor.RED + "桌子不存在。"));
    }

    private void handleJoin(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "用法：/liarbar join <桌子ID>");
            return;
        }
        Table table = plugin.getTableManager().getTable(args[1]);
        if (table == null) {
            player.sendMessage(ChatColor.RED + "桌子不存在。");
            return;
        }
        Table current = plugin.getTableManager().findTableByPlayer(player);
        if (current != null && current != table) {
            player.sendMessage(ChatColor.RED + "你已经在其他桌子了，请先离开。");
            return;
        }
        table.addWaitingPlayer(player);
    }

    private void handleLeave(Player player) {
        Table table = plugin.getTableManager().findTableByPlayer(player);
        if (table == null) {
            player.sendMessage(ChatColor.RED + "你不在任何桌子中。");
            return;
        }
        table.removePlayer(player);
        player.sendMessage(ChatColor.YELLOW + "你已离开桌子。");
    }

    private void handleStart(Player player, String[] args) {
        Table table = getTargetTable(player, args, 1);
        if (table == null) return;
        table.startGame(player);
    }

    private void handleStop(Player player, String[] args) {
        Table table = getTargetTable(player, args, 1);
        if (table == null) return;
        if (!player.hasPermission("liarsbar.admin") && !table.isInGame(player)) {
            player.sendMessage(ChatColor.RED + "你没有权限结束这桌游戏。");
            return;
        }
        table.endGame(player);
    }

    private void handleMode(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "用法：/liarbar mode <桌子ID> <life|fantuan|kunkun>");
            return;
        }
        Table table = plugin.getTableManager().getTable(args[1]);
        if (table == null) {
            player.sendMessage(ChatColor.RED + "桌子不存在。");
            return;
        }
        BetMode mode = switch (args[2].toLowerCase()) {
            case "fantuan", "f", "饭团", "饭团币" -> BetMode.FANTUAN;
            case "kunkun", "k", "坤坤", "坤坤币" -> BetMode.KUNKUN;
            default -> BetMode.LIFE;
        };
        table.setBetMode(mode);
        player.sendMessage(ChatColor.GREEN + "已将桌子模式设为：" + ChatColor.GOLD + mode.getDisplay());
    }

    private void handleSelect(Player player, String[] args) {
        Table table = getTargetTable(player, args, 2);
        if (table == null) return;
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "用法：/liarbar select <桌子ID> <牌序号>");
            return;
        }
        try {
            int idx = Integer.parseInt(args[args.length - 1]);
            table.toggleSelection(player, idx);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "请输入数字。");
        }
    }

    private void handlePlay(Player player, String[] args) {
        Table table = getTargetTable(player, args, 1);
        if (table == null) return;
        table.playCards(player);
    }

    private void handleChallenge(Player player, String[] args) {
        Table table = getTargetTable(player, args, 1);
        if (table == null) return;
        table.challenge(player);
    }

    private void handleInfo(Player player) {
        var tables = plugin.getTableManager().getTables();
        if (tables.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "当前没有任何桌子。");
            return;
        }
        player.sendMessage(ChatColor.GOLD + "===== 骗子酒馆桌子列表 =====");
        for (Table table : tables) {
            player.sendMessage(ChatColor.YELLOW + table.getInfo());
        }
    }

    private Table getTargetTable(Player player, String[] args, int idIndex) {
        Table table = null;
        if (args.length > idIndex) {
            table = plugin.getTableManager().getTable(args[idIndex]);
        } else {
            table = plugin.getTableManager().findTableByPlayer(player);
        }
        if (table == null) {
            player.sendMessage(ChatColor.RED + "请指定桌子ID或先加入一桌。");
            return null;
        }
        return table;
    }

    private void sendHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "===== 骗子酒馆 Paper 插件 =====");
        player.sendMessage(ChatColor.YELLOW + "/liarbar create <ID>" + ChatColor.WHITE + " 创建桌子");
        player.sendMessage(ChatColor.YELLOW + "/liarbar delete <ID>" + ChatColor.WHITE + " 删除桌子");
        player.sendMessage(ChatColor.YELLOW + "/liarbar join <ID>" + ChatColor.WHITE + " 加入桌子");
        player.sendMessage(ChatColor.YELLOW + "/liarbar leave" + ChatColor.WHITE + " 离开桌子");
        player.sendMessage(ChatColor.YELLOW + "/liarbar mode <ID> <life|fantuan|kunkun>" + ChatColor.WHITE + " 设置模式");
        player.sendMessage(ChatColor.YELLOW + "/liarbar start [ID]" + ChatColor.WHITE + " 开始游戏");
        player.sendMessage(ChatColor.YELLOW + "/liarbar stop [ID]" + ChatColor.WHITE + " 结束游戏");
        player.sendMessage(ChatColor.YELLOW + "/liarbar info" + ChatColor.WHITE + " 查看桌子列表");
    }
}
