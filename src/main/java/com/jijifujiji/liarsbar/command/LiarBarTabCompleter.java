package com.jijifujiji.liarsbar.command;

import com.jijifujiji.liarsbar.LiarsBarPlugin;
import com.jijifujiji.liarsbar.game.Table;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LiarBarTabCompleter implements TabCompleter {

    private final LiarsBarPlugin plugin;
    private static final List<String> SUBS = Arrays.asList(
            "create", "delete", "join", "leave", "start", "stop", "mode", "select", "play", "challenge", "info", "help");
    private static final List<String> MODES = Arrays.asList("life", "fantuan", "kunkun");

    public LiarBarTabCompleter(LiarsBarPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return List.of();
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBS) {
                if (s.startsWith(args[0].toLowerCase())) result.add(s);
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("create")) return List.of("<桌子ID>");
            if (sub.equals("delete") || sub.equals("join") || sub.equals("start") || sub.equals("stop") || sub.equals("mode") || sub.equals("select") || sub.equals("play") || sub.equals("challenge")) {
                for (Table table : plugin.getTableManager().getTables()) {
                    if (table.getId().toLowerCase().startsWith(args[1].toLowerCase())) {
                        result.add(table.getId());
                    }
                }
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("mode")) {
                for (String m : MODES) {
                    if (m.startsWith(args[2].toLowerCase())) result.add(m);
                }
            } else if (args[0].equalsIgnoreCase("select")) {
                return Arrays.asList("1", "2", "3", "4", "5");
            }
        }
        return result;
    }
}
