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
            sender.sendMessage(ChatColor.RED + "此命令仅限玩家执行。");
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
            player.sendMessage(ChatColor.RED + "无权限。");
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "用法：/liarbar set <桌子ID>");
            return;
        }
        String id = args[1].toLowerCase();
        Location loc = player.getLocation();
        plugin.getConfigManager().setTableLocation(id, loc);
        Table table = plugin.getTableManager().createTable(id, loc);
        table.setLocation(loc);
        table.buildDisplay();
        player.sendMessage(ChatColor.GREEN + "已设置 " + ChatColor.GOLD + id.toUpperCase() + ChatColor.GREEN + " 桌坐标。");
    }

    private void handleBuild(Player player, String[] args) {
        if (!player.hasPermission("liarsbar.admin")) {
            player.sendMessage(ChatColor.RED + "无权限。");
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "用法：/liarbar build <桌子ID>");
            return;
        }
        Table table = plugin.getTableManager().getTable(args[1]);
        if (table == null) {
            player.sendMessage(ChatColor.RED + "桌子不存在，请先用 /liarbar set 设置坐标。");
            return;
        }
        table.buildDisplay();
        player.sendMessage(ChatColor.GREEN + "已为 " + ChatColor.GOLD + table.getId().toUpperCase() + ChatColor.GREEN + " 桌生成 Display Entity 座位。");
    }

    private void handleCreate(Player player, String[] args) {
        if (!player.hasPermission("liarsbar.admin")) {
            player.sendMessage(ChatColor.RED + "无权限。");
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "用法：/liarbar create <桌子ID>");
            return;
        }
        String id = args[1].toLowerCase();
        if (plugin.getTableManager().getTable(id) != null) {
            player.sendMessage(ChatColor.RED + "桌子 " + id + " 已存在。");
            return;
        }
        Location loc = player.getLocation();
        plugin.getConfigManager().setTableLocation(id, loc);
        plugin.getTableManager().createTable(id, loc);
        player.sendMessage(ChatColor.GREEN + "成功创建桌子 " + ChatColor.GOLD + id);
    }

    private void handleDelete(Player player, String[] args) {
        if (!player.hasPermission("liarsbar.admin")) {
            player.sendMessage(ChatColor.RED + "无权限。");
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "用法：/liarbar delete <桌子ID>");
            return;
        }
        if (plugin.getTableManager().deleteTable(args[1])) {
            plugin.getConfigManager().removeTableLocation(args[1]);
            player.sendMessage(ChatColor.GREEN + "已删除桌子 " + args[1]);
        } else {
            player.sendMessage(ChatColor.RED + "桌子不存在。");
        }
    }

    private void handleGambling(Player player, String[] args) {
        if (!player.hasPermission("liarsbar.admin")) {
            player.sendMessage(ChatColor.RED + "无权限。");
            return;
        }
        if (args.length < 2) {
            boolean on = plugin.getConfigManager().isGamblingEnabled();
            player.sendMessage(ChatColor.YELLOW + "赌博模式：" + (on ? ChatColor.GREEN + "开" : ChatColor.RED + "关"));
            return;
        }
        boolean enable = switch (args[1].toLowerCase()) {
            case "on", "true", "1", "开" -> true;
            default -> false;
        };
        plugin.getConfigManager().setGamblingEnabled(enable);
        player.sendMessage(ChatColor.GREEN + "赌博模式已" + (enable ? ChatColor.GOLD + "开启" : ChatColor.RED + "关闭"));
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
        // 自动分配一个空座位
        for (int i = 0; i < 4; i++) {
            if (!table.isSeatOccupied(i)) {
                table.joinSeat(player, i);
                return;
            }
        }
        player.sendMessage(ChatColor.RED + "无法加入，座位已满。");
    }

    private void handleLeave(Player player) {
        Table table = plugin.getTableManager().findTableByPlayer(player);
        if (table == null) {
            player.sendMessage(ChatColor.RED + "你不在任何游戏中。");
            return;
        }
        table.removePlayer(player);
        player.sendMessage(ChatColor.YELLOW + "已离开游戏。");
    }

    private void handleStart(Player player, String[] args) {
        Table table;
        if (args.length >= 2) {
            table = plugin.getTableManager().getTable(args[1]);
        } else {
            table = plugin.getTableManager().findTableByPlayer(player);
        }
        if (table == null) {
            player.sendMessage(ChatColor.RED + "桌子不存在或你不在任何游戏中。");
            return;
        }
        table.startGame(player);
    }

    private void handleStop(Player player, String[] args) {
        if (!player.hasPermission("liarsbar.admin")) {
            player.sendMessage(ChatColor.RED + "无权限。");
            return;
        }
        Table table;
        if (args.length >= 2) {
            table = plugin.getTableManager().getTable(args[1]);
        } else {
            table = plugin.getTableManager().findTableByPlayer(player);
        }
        if (table == null) {
            player.sendMessage(ChatColor.RED + "桌子不存在。");
            return;
        }
        table.endGame(player);
    }

    private void handleMode(Player player, String[] args) {
        if (!player.hasPermission("liarsbar.admin")) {
            player.sendMessage(ChatColor.RED + "无权限。");
            return;
        }
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
            case "fantuan", "f", "饭团" -> BetMode.FANTUAN;
            case "kunkun", "k", "坤坤" -> BetMode.KUNKUN;
            default -> BetMode.LIFE;
        };
        if (!table.setBetMode(mode)) {
            player.sendMessage(ChatColor.RED + "游戏进行中，不能切换模式。");
            return;
        }
        player.sendMessage(ChatColor.GREEN + "已将桌子模式设为：" + ChatColor.GOLD + mode.getDisplay());
    }

    private void handleSelect(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "用法：/liarbar select <序号>");
            return;
        }
        Table table = plugin.getTableManager().findTableByPlayer(player);
        if (table == null) {
            player.sendMessage(ChatColor.RED + "你不在任何游戏中。");
            return;
        }
        try {
            int index = Integer.parseInt(args[1]) - 1;
            table.selectCard(player, index);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "请输入有效的序号（1-5）。");
        }
    }

    private void handlePlay(Player player) {
        Table table = plugin.getTableManager().findTableByPlayer(player);
        if (table == null) {
            player.sendMessage(ChatColor.RED + "你不在任何游戏中。");
            return;
        }
        table.playCards(player);
    }

    private void handleChallenge(Player player) {
        Table table = plugin.getTableManager().findTableByPlayer(player);
        if (table == null) {
            player.sendMessage(ChatColor.RED + "你不在任何游戏中。");
            return;
        }
        table.challenge(player);
    }

    private void handleInfo(Player player) {
        player.sendMessage(ChatColor.GOLD + "===== 骗子酒馆 桌子列表 =====");
        if (plugin.getTableManager().getTables().isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "暂无桌子。");
        } else {
            for (Table table : plugin.getTableManager().getTables()) {
                player.sendMessage(ChatColor.YELLOW + table.getInfo());
            }
        }
        player.sendMessage(ChatColor.GRAY + "点击座位实体即可入座，满4人自动开始。");
    }

    private void sendHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "===== 骗子酒馆 Paper 插件 =====");
        player.sendMessage(ChatColor.YELLOW + "/liarbar set <ID>" + ChatColor.WHITE + "  设置桌子坐标");
        player.sendMessage(ChatColor.YELLOW + "/liarbar build <ID>" + ChatColor.WHITE + "  生成 Display Entity 座位");
        player.sendMessage(ChatColor.YELLOW + "/liarbar create <ID>" + ChatColor.WHITE + "  快速创建并保存");
        player.sendMessage(ChatColor.YELLOW + "/liarbar delete <ID>" + ChatColor.WHITE + "  删除桌子");
        player.sendMessage(ChatColor.GRAY + "桌子 ID 不限 A-E，例如 lobby_1、vip_2、table_10。");
        player.sendMessage(ChatColor.YELLOW + "/liarbar gambling [on|off]" + ChatColor.WHITE + "  开关赌博");
        player.sendMessage(ChatColor.YELLOW + "/liarbar join <ID>" + ChatColor.WHITE + "  加入桌子");
        player.sendMessage(ChatColor.YELLOW + "/liarbar leave" + ChatColor.WHITE + "  离开游戏");
        player.sendMessage(ChatColor.YELLOW + "/liarbar mode <ID> <life|fantuan|kunkun>" + ChatColor.WHITE + "  设置模式");
        player.sendMessage(ChatColor.YELLOW + "/liarbar select <1-5>" + ChatColor.WHITE + "  选择手牌");
        player.sendMessage(ChatColor.YELLOW + "/liarbar play" + ChatColor.WHITE + "  出牌");
        player.sendMessage(ChatColor.YELLOW + "/liarbar challenge" + ChatColor.WHITE + "  质疑上家");
        player.sendMessage(ChatColor.YELLOW + "/liarbar start [ID]" + ChatColor.WHITE + "  开始游戏");
        player.sendMessage(ChatColor.YELLOW + "/liarbar stop [ID]" + ChatColor.WHITE + "  结束游戏");
        player.sendMessage(ChatColor.YELLOW + "/liarbar info" + ChatColor.WHITE + "  查看桌子列表");
        player.sendMessage(ChatColor.GRAY + "也可以直接点击座位实体入座，点击手牌选牌出牌！");
    }
}
