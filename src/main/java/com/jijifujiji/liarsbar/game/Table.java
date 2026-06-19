package com.jijifujiji.liarsbar.game;

import com.jijifujiji.liarsbar.LiarsBarPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class Table {

    private final LiarsBarPlugin plugin;
    private final String id;
    private Location location;
    private final List<PlayerState> players = new ArrayList<>();
    private final List<Player> waitingPlayers = new ArrayList<>();

    private GameState state = GameState.IDLE;
    private BetMode betMode = BetMode.LIFE;
    private CardType mainCard;
    private int currentPlayerIndex = -1;
    private PlayerState lastPlayer;
    private List<Card> centerCards = new ArrayList<>();
    private BukkitTask turnTimer;
    private int turnSecondsLeft;
    private int pendingShots = 0;
    private int joinedCount = 0;

    private final Map<Player, Set<Integer>> selections = new HashMap<>();

    public Table(LiarsBarPlugin plugin, String id, Location location) {
        this.plugin = plugin;
        this.id = id;
        this.location = location == null ? null : location.clone();
    }

    public String getId() {
        return id;
    }

    public Location getLocation() {
        return location == null ? null : location.clone();
    }

    public void setLocation(Location location) {
        this.location = location.clone();
    }

    public GameState getState() {
        return state;
    }

    public BetMode getBetMode() {
        return betMode;
    }

    public List<PlayerState> getPlayers() {
        return new ArrayList<>(players);
    }

    public boolean isInGame(Player player) {
        return players.stream().anyMatch(p -> p.getPlayer().equals(player)) || waitingPlayers.contains(player);
    }

    public void setBetMode(BetMode mode) {
        this.betMode = mode;
    }

    public void addWaitingPlayer(Player player) {
        if (waitingPlayers.contains(player) || isInGame(player)) {
            player.sendMessage(ChatColor.RED + "你已经在本桌了！");
            return;
        }
        if (state != GameState.IDLE && state != GameState.WAITING) {
            player.sendMessage(ChatColor.RED + "本桌游戏进行中，无法加入。");
            return;
        }
        if (waitingPlayers.size() + players.size() >= 4) {
            player.sendMessage(ChatColor.RED + "本桌已满（最多4人）。");
            return;
        }
        // 赌博模式下，饭团币/坤坤币模式需要扣费
        if (plugin.getConfigManager().isGamblingEnabled() && betMode != BetMode.LIFE) {
            if (!EconomyHandler.canAfford(player, betMode)) {
                player.sendMessage(ChatColor.RED + "你的" + betMode.getDisplay() + "不足，请拿到" + betMode.getDisplay() + "后重新加入！");
                return;
            }
            if (!EconomyHandler.pay(player, betMode)) {
                player.sendMessage(ChatColor.RED + "扣费失败，请稍后重试。");
                return;
            }
            broadcast(ChatColor.GOLD + player.getName() + ChatColor.GREEN + " 支付了一个" + betMode.getDisplay() + "，加入对局！");
        } else {
            broadcast(ChatColor.GOLD + player.getName() + ChatColor.GREEN + " 加入对局！");
        }
        waitingPlayers.add(player);
        joinedCount = getAllParticipants().size();
        broadcast(ChatColor.YELLOW + "当前 " + ChatColor.GOLD + joinedCount + ChatColor.YELLOW + " 人加入游戏。");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_XYLOPHONE, 1f, 1.34f);
        if (state == GameState.IDLE) {
            state = GameState.WAITING;
        }
        // 满4人自动开始
        if (getAllParticipants().size() >= 4) {
            startGame(player);
        }
    }

    public void removePlayer(Player player) {
        waitingPlayers.remove(player);
        players.removeIf(p -> p.getPlayer().equals(player));
        if (players.isEmpty() && waitingPlayers.isEmpty()) {
            state = GameState.IDLE;
        }
        broadcast(ChatColor.GOLD + player.getName() + ChatColor.YELLOW + " 离开了游戏。");
    }

    public List<Player> getAllParticipants() {
        List<Player> all = new ArrayList<>(waitingPlayers);
        for (PlayerState ps : players) {
            if (!all.contains(ps.getPlayer())) {
                all.add(ps.getPlayer());
            }
        }
        return all;
    }

    public void startGame(Player starter) {
        if (state != GameState.WAITING && state != GameState.IDLE) {
            starter.sendMessage(ChatColor.RED + "游戏不处于可开始状态。");
            return;
        }
        if (waitingPlayers.size() < 2) {
            starter.sendMessage(ChatColor.RED + "至少需要 2 名玩家才能开始。");
            return;
        }
        players.clear();
        for (int i = 0; i < waitingPlayers.size(); i++) {
            players.add(new PlayerState(waitingPlayers.get(i), i));
        }
        waitingPlayers.clear();
        joinedCount = players.size();
        state = GameState.DEALING;
        broadcast(ChatColor.YELLOW + "游戏开始！模式：" + ChatColor.GOLD + ChatColor.BOLD + betMode.getDisplay());
        startRound();
    }

    private void startRound() {
        centerCards.clear();
        mainCard = Card.randomMainType();

        List<Card> deck = new ArrayList<>();
        for (int i = 0; i < 6; i++) deck.add(Card.A);
        for (int i = 0; i < 6; i++) deck.add(Card.Q);
        for (int i = 0; i < 6; i++) deck.add(Card.K);
        deck.add(Card.KUN);
        deck.add(Card.KUN);
        Collections.shuffle(deck);

        List<Integer> mainIndices = new ArrayList<>();
        for (int i = 0; i < deck.size(); i++) {
            if (deck.get(i).matchesMain(mainCard)) {
                mainIndices.add(i);
            }
        }
        if (!mainIndices.isEmpty()) {
            int demonIndex = mainIndices.get((int) (Math.random() * mainIndices.size()));
            deck.set(demonIndex, Card.DEMON);
        }

        for (PlayerState ps : players) {
            ps.resetForNewRound();
        }

        int cardIndex = 0;
        for (PlayerState ps : players) {
            for (int i = 0; i < 5 && cardIndex < deck.size(); i++) {
                ps.getHand().add(deck.get(cardIndex++));
            }
        }

        broadcast(ChatColor.YELLOW + "新的一轮开始了！本局主牌是：" + ChatColor.GOLD + ChatColor.BOLD + "[{ " + mainCard.getDisplay() + " }]");

        currentPlayerIndex = (int) (Math.random() * players.size());
        lastPlayer = null;
        state = GameState.PLAYING;
        startTurn(false);
    }

    private void startTurn(boolean standard) {
        if (checkWin()) return;

        PlayerState current = players.get(currentPlayerIndex);
        while (!current.isAlive() || !current.hasCards()) {
            currentPlayerIndex = nextIndex(currentPlayerIndex);
            current = players.get(currentPlayerIndex);
        }

        cancelTurnTimer();
        turnSecondsLeft = 30;

        current.getPlayer().sendTitle(ChatColor.GREEN + ChatColor.BOLD.toString() + ">>>你的回合<<<", "", 10, 70, 20);
        current.getPlayer().playSound(current.getPlayer().getLocation(), Sound.BLOCK_ANVIL_PLACE, 1f, 1f);

        sendHand(current);
        sendActionButtons(current, standard);

        PlayerState finalCurrent = current;
        turnTimer = new BukkitRunnable() {
            @Override
            public void run() {
                turnSecondsLeft--;
                if (turnSecondsLeft <= 0) {
                    cancel();
                    handleTimeout(finalCurrent);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void sendHand(PlayerState ps) {
        StringBuilder msg = new StringBuilder(ChatColor.YELLOW + "你的手牌： ");
        List<Card> hand = ps.getHand();
        for (int i = 0; i < hand.size(); i++) {
            Card c = hand.get(i);
            msg.append(ChatColor.WHITE).append("[").append(i + 1).append("]").append(c.getDisplay()).append(" ");
        }
        msg.append(ChatColor.GOLD).append("  主牌：").append(mainCard.getDisplay());
        ps.getPlayer().sendMessage(msg.toString());
    }

    private void sendActionButtons(PlayerState current, boolean canChallenge) {
        Player p = current.getPlayer();
        p.sendMessage(ChatColor.YELLOW + "请选择要出的手牌（点击数字切换选择，然后点出牌）：");

        StringBuilder selectRow = new StringBuilder(ChatColor.YELLOW + "选择： ");
        int max = Math.min(5, current.getHand().size());
        for (int i = 1; i <= max; i++) {
            selectRow.append(ChatColor.RED).append("[").append(i).append("]").append(ChatColor.RESET).append(" ");
        }
        p.sendMessage(selectRow.toString());
        p.sendMessage(ChatColor.GRAY + "（请使用命令：/liarbar select " + id + " <序号>）");

        StringBuilder actionRow = new StringBuilder(ChatColor.YELLOW + "操作： ");
        actionRow.append(ChatColor.GREEN).append(ChatColor.BOLD).append("[出牌]").append(ChatColor.RESET);
        if (canChallenge && lastPlayer != null && lastPlayer != current) {
            actionRow.append("  ").append(ChatColor.RED).append(ChatColor.BOLD).append("[质疑/开牌]");
        }
        p.sendMessage(actionRow.toString());
        p.sendMessage(ChatColor.GRAY + "出牌：/liarbar play " + id + "    质疑：/liarbar challenge " + id);
    }

    public void toggleSelection(Player player, int index) {
        Set<Integer> sel = selections.computeIfAbsent(player, k -> new HashSet<>());
        if (sel.contains(index)) {
            sel.remove(index);
            player.sendMessage(ChatColor.RED + "取消选择第 " + index + " 张牌");
        } else {
            sel.add(index);
            player.sendMessage(ChatColor.GREEN + "选择第 " + index + " 张牌");
        }
    }

    public void playCards(Player player) {
        PlayerState ps = findState(player);
        if (ps == null || ps != players.get(currentPlayerIndex)) {
            player.sendMessage(ChatColor.RED + "现在不是你的回合。");
            return;
        }
        Set<Integer> sel = selections.getOrDefault(player, Collections.emptySet());
        if (sel.isEmpty()) {
            player.sendMessage(ChatColor.RED + "请先选择要出的牌。");
            return;
        }
        List<Integer> indices = new ArrayList<>(sel);
        Collections.sort(indices);
        if (indices.size() > 3) {
            player.sendMessage(ChatColor.RED + "一次最多出 3 张牌。");
            return;
        }

        List<Card> played = new ArrayList<>();
        boolean hasDemon = false;
        for (int idx : indices) {
            Card c = ps.getHand().get(idx - 1);
            played.add(c);
            if (c == Card.DEMON) hasDemon = true;
        }
        if (hasDemon && played.size() > 1) {
            player.sendMessage(ChatColor.RED + "恶魔牌只能单出！");
            return;
        }

        ps.removeCards(indices.stream().map(i -> i - 1).toList());
        centerCards = new ArrayList<>(played);
        lastPlayer = ps;
        selections.remove(player);

        broadcast(ChatColor.GOLD + player.getName() + ChatColor.YELLOW + " 出了 " + ChatColor.GOLD + played.size() + ChatColor.YELLOW + " 张牌。");

        currentPlayerIndex = nextIndex(currentPlayerIndex);
        startTurn(true);
    }

    public void challenge(Player player) {
        PlayerState ps = findState(player);
        if (ps == null || ps != players.get(currentPlayerIndex)) {
            player.sendMessage(ChatColor.RED + "现在不是你的回合。");
            return;
        }
        if (lastPlayer == null || lastPlayer == ps) {
            player.sendMessage(ChatColor.RED + "现在没有可质疑的牌。");
            return;
        }
        cancelTurnTimer();
        state = GameState.RESOLVING;
        selections.remove(player);

        broadcast(ChatColor.GOLD + player.getName() + ChatColor.YELLOW + " 质疑了 " + ChatColor.RED + ChatColor.BOLD + lastPlayer.getPlayer().getName());

        boolean hasDemon = centerCards.stream().anyMatch(c -> c == Card.DEMON);
        boolean allMainOrWild = centerCards.stream().allMatch(c -> c.isMainOrWild(mainCard));

        StringBuilder reveal = new StringBuilder(ChatColor.YELLOW + "翻开的牌： ");
        for (Card c : centerCards) {
            reveal.append("[").append(c.getDisplay()).append("] ");
        }
        broadcast(reveal.toString());

        if (hasDemon) {
            broadcast(ChatColor.YELLOW + "哦！是" + ChatColor.RED + ChatColor.BOLD + "恶魔牌" + ChatColor.YELLOW + "，除了出牌者所有人都要遭殃！");
            List<PlayerState> targets = new ArrayList<>();
            for (PlayerState other : players) {
                if (other != lastPlayer && other.isAlive()) {
                    targets.add(other);
                }
            }
            pendingShots = targets.size();
            if (pendingShots == 0) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!checkWin()) {
                        state = GameState.PLAYING;
                        currentPlayerIndex = nextAliveIndex(currentPlayerIndex);
                        startTurn(true);
                    }
                });
            } else {
                for (PlayerState target : targets) {
                    shoot(target, () -> onDemonShotComplete(target));
                }
            }
            return;
        }

        PlayerState loser;
        if (!allMainOrWild) {
            broadcast(ChatColor.YELLOW + "哦！不是" + ChatColor.RED + ChatColor.BOLD + "主牌" + ChatColor.YELLOW + "，" + ChatColor.GOLD + lastPlayer.getPlayer().getName() + ChatColor.YELLOW + " 冲着自己来一枪吧！");
            loser = lastPlayer;
        } else {
            broadcast(ChatColor.YELLOW + "哇！是" + ChatColor.RED + ChatColor.BOLD + "主牌" + ChatColor.YELLOW + "，" + ChatColor.GOLD + player.getName() + ChatColor.YELLOW + " 按下扳机吧！");
            loser = ps;
        }
        shoot(loser, () -> afterShoot(loser));
    }

    private void afterShoot(PlayerState victim) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (checkWin()) return;
            state = GameState.PLAYING;
            currentPlayerIndex = nextAliveIndex(currentPlayerIndex);
            startTurn(true);
        });
    }

    private void onDemonShotComplete(PlayerState victim) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            pendingShots--;
            if (pendingShots <= 0) {
                if (!checkWin()) {
                    state = GameState.PLAYING;
                    currentPlayerIndex = nextAliveIndex(currentPlayerIndex);
                    startTurn(true);
                }
            }
        });
    }

    private void handleTimeout(PlayerState ps) {
        broadcast(ChatColor.GOLD + ps.getPlayer().getName() + ChatColor.YELLOW + " 回合超时，自动质疑上家。");
        challenge(ps.getPlayer());
    }

    private void shoot(PlayerState ps, Runnable callback) {
        Player p = ps.getPlayer();
        int bullets = ps.getBullets();
        int roll = (int) (Math.random() * bullets) + 1;
        boolean real = roll == 1;

        if (real) {
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.51f);
            broadcast(ChatColor.GOLD + p.getName() + ChatColor.DARK_RED + " 的脑袋被开了瓢。");
            ps.setAlive(false);
            ps.setBullets(0);
        } else {
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_OFF, 1f, 0.97f);
            broadcast(ChatColor.GOLD + p.getName() + ChatColor.GREEN + " 运气不错，是一发空弹。");
            ps.setBullets(bullets - 1);
        }

        Bukkit.getScheduler().runTaskLater(plugin, callback, 40L);
    }

    private boolean checkWin() {
        List<PlayerState> aliveWithCards = players.stream()
                .filter(p -> p.isAlive() && p.hasCards()).toList();
        if (aliveWithCards.size() <= 1) {
            state = GameState.ENDED;
            PlayerState winner = aliveWithCards.isEmpty() ? null : aliveWithCards.get(0);
            if (winner != null) {
                broadcast(ChatColor.YELLOW + "恭喜 " + ChatColor.GOLD + ChatColor.BOLD + winner.getPlayer().getName() + ChatColor.YELLOW + " 获得了本局骗子酒馆的胜利！赢得了 " + ChatColor.GOLD + joinedCount + ChatColor.YELLOW + " 个" + betMode.getDisplay() + "！");
                winner.getPlayer().getWorld().spawnParticle(org.bukkit.Particle.FIREWORKS_SPARK, winner.getPlayer().getLocation().add(0, 2, 0), 30);
                EconomyHandler.rewardWinner(winner.getPlayer(), betMode, joinedCount);
            } else {
                broadcast(ChatColor.YELLOW + "所有玩家都出局了，本局没有胜者。");
            }
            cleanup();
            return true;
        }
        return false;
    }

    private void cleanup() {
        cancelTurnTimer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            players.clear();
            waitingPlayers.clear();
            state = GameState.IDLE;
        }, 100L);
    }

    public void endGame(Player sender) {
        cancelTurnTimer();
        state = GameState.ENDED;
        broadcast(ChatColor.YELLOW + "游戏被 " + sender.getName() + " 强制结束。");
        // 赌博模式下强制结束会随机选一个胜者
        if (plugin.getConfigManager().isGamblingEnabled() && betMode != BetMode.LIFE && !players.isEmpty()) {
            List<PlayerState> alive = players.stream().filter(PlayerState::isAlive).toList();
            if (!alive.isEmpty()) {
                PlayerState randomWinner = alive.get((int) (Math.random() * alive.size()));
                broadcast(ChatColor.YELLOW + "有人拉了结束按钮，随机玩家 " + ChatColor.GOLD + randomWinner.getPlayer().getName() + ChatColor.YELLOW + " 成为胜者！");
                EconomyHandler.rewardWinner(randomWinner.getPlayer(), betMode, joinedCount);
            }
        }
        players.clear();
        waitingPlayers.clear();
        state = GameState.IDLE;
    }

    private int nextIndex(int current) {
        return (current + 1) % players.size();
    }

    private int nextAliveIndex(int current) {
        int idx = (current + 1) % players.size();
        int attempts = 0;
        while (attempts < players.size()) {
            PlayerState ps = players.get(idx);
            if (ps.isAlive() && ps.hasCards()) {
                return idx;
            }
            idx = (idx + 1) % players.size();
            attempts++;
        }
        return idx;
    }

    private PlayerState findState(Player player) {
        return players.stream().filter(p -> p.getPlayer().equals(player)).findFirst().orElse(null);
    }

    private void cancelTurnTimer() {
        if (turnTimer != null) {
            turnTimer.cancel();
            turnTimer = null;
        }
    }

    private void broadcast(String message) {
        for (Player p : getAllParticipants()) {
            p.sendMessage(message);
        }
    }

    public void destroy() {
        cancelTurnTimer();
        broadcast(ChatColor.YELLOW + "本桌已被删除。");
        players.clear();
        waitingPlayers.clear();
    }

    public String getInfo() {
        return "桌子 " + id + " 状态：" + state + " 人数：" + getAllParticipants().size() + "/4 模式：" + betMode.getDisplay();
    }
}
