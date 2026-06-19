package com.jijifujiji.liarsbar.game;

import com.jijifujiji.liarsbar.LiarsBarPlugin;
import com.jijifujiji.liarsbar.display.DisplayManager;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.stream.Collectors;

public class Table {

    private static final double[][] SEAT_OFFSETS = {
            {2.5, 0.3, 0},    // 0: east
            {0, 0.3, 2.5},    // 1: south
            {-2.5, 0.3, 0},   // 2: west
            {0, 0.3, -2.5}    // 3: north
    };

    private static final double[][] CARD_OFFSETS = {
            {0.5, 0, 0},      // east: cards go north of seat
            {0, 0, -0.5},     // south: cards go west of seat
            {-0.5, 0, 0},     // west: cards go south of seat
            {0, 0, 0.5}       // north: cards go east of seat
    };

    private static final float[] SEAT_YAWS = {90f, 180f, -90f, 0f};

    private final LiarsBarPlugin plugin;
    private final String id;
    private Location location;
    private final List<PlayerState> players = new ArrayList<>();
    private final List<Player> waitingPlayers = new ArrayList<>();
    private final Map<Integer, Player> seatMap = new HashMap<>();

    private GameState state = GameState.IDLE;
    private BetMode betMode = BetMode.LIFE;
    private CardType mainCard;
    private int currentPlayerIndex = -1;
    private PlayerState lastPlayer;
    private List<Card> centerCards = new ArrayList<>();
    private BukkitTask turnTimer;
    private int turnSecondsLeft;
    private int pendingShots;
    private int joinedCount;
    private boolean isFirstRound = true;

    // Display entities
    private final List<Entity> displayEntities = new ArrayList<>();
    private final List<Entity> seatInteractions = new ArrayList<>();
    private TextDisplay modeLabel;
    private TextDisplay statusLabel;
    private TextDisplay turnLabel;
    private final Map<Integer, List<ItemDisplay>> playerCardDisplays = new HashMap<>();
    private final Map<Integer, List<Interaction>> playerCardInteractions = new HashMap<>();
    private final List<ItemDisplay> centerCardDisplays = new ArrayList<>();
    private Interaction playButton;
    private Interaction challengeButton;
    private Interaction startButton;

    public Table(LiarsBarPlugin plugin, String id, Location location) {
        this.plugin = plugin;
        this.id = id;
        this.location = location == null ? null : location.clone();
    }

    public String getId() { return id; }
    public Location getLocation() { return location == null ? null : location.clone(); }
    public void setLocation(Location location) { this.location = location.clone(); }
    public GameState getState() { return state; }
    public BetMode getBetMode() { return betMode; }
    public boolean isInGame(Player player) {
        return players.stream().anyMatch(p -> p.getPlayer().equals(player))
                || waitingPlayers.contains(player)
                || seatMap.containsValue(player);
    }

    public void setBetMode(BetMode mode) {
        this.betMode = mode;
        buildDisplay();
    }

    // ========== Seat & Display ==========

    public void buildDisplay() {
        clearDisplay();
        if (location == null || location.getWorld() == null) return;

        // Mode label above table
        modeLabel = DisplayManager.spawnLabel(plugin, location.clone().add(0, 2.2, 0),
                betMode.getDisplay(), Color.fromRGB(0x402020), false);

        // Status label
        statusLabel = DisplayManager.spawnLabel(plugin, location.clone().add(0, 1.6, 0),
                "等待玩家加入...", Color.fromRGB(0x004400), false);

        // Seat interactions
        for (int i = 0; i < 4; i++) {
            Location seatLoc = location.clone().add(SEAT_OFFSETS[i][0], SEAT_OFFSETS[i][1], SEAT_OFFSETS[i][2]);
            Interaction seat = DisplayManager.spawnInteraction(plugin, seatLoc, 0.8f, 1.6f,
                    new DisplayManager.ClickAction(DisplayManager.ClickAction.ActionType.JOIN_SEAT, id, i, -1));
            if (seat != null) {
                seatInteractions.add(seat);
                displayEntities.add(seat);
            }
        }

        // Start button
        Location btnLoc = location.clone().add(0, 1.0, 0);
        startButton = DisplayManager.spawnInteraction(plugin, btnLoc, 0.6f, 0.6f,
                new DisplayManager.ClickAction(DisplayManager.ClickAction.ActionType.START_BUTTON, id, -1, -1));
        if (startButton != null) displayEntities.add(startButton);

        if (modeLabel != null) displayEntities.add(modeLabel);
        if (statusLabel != null) displayEntities.add(statusLabel);
    }

    public void renderPlayerCards(PlayerState ps) {
        clearPlayerDisplay(ps.getSeatIndex());
        if (location == null || location.getWorld() == null) return;

        Location seatLoc = location.clone().add(SEAT_OFFSETS[ps.getSeatIndex()][0],
                SEAT_OFFSETS[ps.getSeatIndex()][1], SEAT_OFFSETS[ps.getSeatIndex()][2]);
        double[] cardOff = CARD_OFFSETS[ps.getSeatIndex()];
        float yaw = SEAT_YAWS[ps.getSeatIndex()];

        List<ItemDisplay> cardDisplays = new ArrayList<>();
        List<Interaction> cardInteractions = new ArrayList<>();

        List<Card> hand = ps.getHand();
        for (int i = 0; i < hand.size(); i++) {
            Location cardLoc = seatLoc.clone().add(cardOff[0] * (i + 1), 0.2, cardOff[2] * (i + 1));
            int modelData = getCardModelData(hand.get(i));
            ItemDisplay cardDisplay = DisplayManager.spawnCard(plugin, cardLoc,
                    hand.get(i).getDisplay(), modelData, id, ps.getSeatIndex(), i);
            if (cardDisplay != null) {
                DisplayManager.applyCardTransform(cardDisplay, yaw, 0.8f);
                cardDisplays.add(cardDisplay);
                displayEntities.add(cardDisplay);
            }

            Interaction cardInteract = DisplayManager.spawnInteraction(plugin, cardLoc, 0.5f, 0.7f,
                    new DisplayManager.ClickAction(DisplayManager.ClickAction.ActionType.PLAY_CARD,
                            id, ps.getSeatIndex(), i));
            if (cardInteract != null) {
                cardInteractions.add(cardInteract);
                displayEntities.add(cardInteract);
            }
        }
        playerCardDisplays.put(ps.getSeatIndex(), cardDisplays);
        playerCardInteractions.put(ps.getSeatIndex(), cardInteractions);
    }

    public void renderCenterCards() {
        clearCenterDisplay();
        if (location == null || location.getWorld() == null) return;

        for (int i = 0; i < centerCards.size(); i++) {
            Location cardLoc = location.clone().add((i - centerCards.size() / 2.0) * 0.5, 0.8, 0);
            int modelData = getCardModelData(centerCards.get(i));
            ItemDisplay cardDisplay = DisplayManager.spawnCard(plugin, cardLoc,
                    centerCards.get(i).getDisplay(), modelData, id, -1, -1);
            if (cardDisplay != null) {
                DisplayManager.applyCardTransform(cardDisplay, 0f, 0.6f);
                centerCardDisplays.add(cardDisplay);
                displayEntities.add(cardDisplay);
            }
        }
    }

    public void renderActionButtons(PlayerState current, boolean canChallenge) {
        if (location == null || location.getWorld() == null) return;
        Location seatLoc = location.clone().add(SEAT_OFFSETS[current.getSeatIndex()][0],
                SEAT_OFFSETS[current.getSeatIndex()][1], SEAT_OFFSETS[current.getSeatIndex()][2]);

        if (playButton != null) { playButton.remove(); displayEntities.remove(playButton); }
        if (challengeButton != null) { challengeButton.remove(); displayEntities.remove(challengeButton); }

        playButton = DisplayManager.spawnInteraction(plugin, seatLoc.clone().add(0, -0.3, 0), 0.5f, 0.5f,
                new DisplayManager.ClickAction(DisplayManager.ClickAction.ActionType.PLAY_BUTTON, id, -1, -1));
        if (playButton != null) displayEntities.add(playButton);

        if (canChallenge && lastPlayer != null && lastPlayer != current) {
            challengeButton = DisplayManager.spawnInteraction(plugin, seatLoc.clone().add(0, -0.3, 0.5), 0.5f, 0.5f,
                    new DisplayManager.ClickAction(DisplayManager.ClickAction.ActionType.CHALLENGE_BUTTON, id, -1, -1));
            if (challengeButton != null) displayEntities.add(challengeButton);
        }
    }

    private void setStatus(String text) {
        if (statusLabel != null && statusLabel.isValid()) {
            statusLabel.setText(text);
        }
    }

    private void clearPlayerDisplay(int seatIndex) {
        List<ItemDisplay> cards = playerCardDisplays.remove(seatIndex);
        if (cards != null) DisplayManager.removeManagedEntities(plugin, new ArrayList<>(cards));
        List<Interaction> intersects = playerCardInteractions.remove(seatIndex);
        if (intersects != null) DisplayManager.removeManagedEntities(plugin, new ArrayList<>(intersects));
    }

    private void clearCenterDisplay() {
        DisplayManager.removeManagedEntities(plugin, new ArrayList<>(centerCardDisplays));
        centerCardDisplays.clear();
    }

    public void clearDisplay() {
        clearCenterDisplay();
        for (int i = 0; i < 4; i++) clearPlayerDisplay(i);
        DisplayManager.removeManagedEntities(plugin, new ArrayList<>(displayEntities));
        displayEntities.clear();
        seatInteractions.clear();
        modeLabel = null;
        statusLabel = null;
        turnLabel = null;
        playButton = null;
        challengeButton = null;
        startButton = null;
    }

    private int getCardModelData(Card card) {
        return switch (card) {
            case A -> 1;
            case Q -> 2;
            case K -> 3;
            case KUN -> 4;
            case DEMON -> 5;
        };
    }

    // ========== Game Flow ==========

    public void joinSeat(Player player, int seatIndex) {
        if (state != GameState.IDLE && state != GameState.WAITING) {
            player.sendMessage(ChatColor.RED + "游戏进行中，请等待下一局。");
            return;
        }
        if (seatMap.containsKey(seatIndex)) {
            Player occupant = seatMap.get(seatIndex);
            if (occupant != null && occupant.isOnline() && !occupant.equals(player)) {
                player.sendMessage(ChatColor.RED + "这个座位已经有人了。");
                return;
            }
        }
        if (isInGame(player)) {
            player.sendMessage(ChatColor.RED + "你已经在本桌了！");
            return;
        }

        // 赌博模式扣费
        if (plugin.getConfigManager().isGamblingEnabled() && betMode != BetMode.LIFE) {
            double cost = betMode == BetMode.FANTUAN ? 1.0 : 1.0;
            EconomyManager eco = plugin.getEconomyManager();
            if (!eco.isEnabled()) {
                player.sendMessage(ChatColor.RED + "经济系统未启用，无法加入赌局。");
                return;
            }
            if (!eco.has(player, cost)) {
                player.sendMessage(ChatColor.RED + "你的余额不足，需要 " + eco.format(cost) + "。");
                return;
            }
            if (!eco.withdraw(player, cost)) {
                player.sendMessage(ChatColor.RED + "扣费失败。");
                return;
            }
            broadcast(ChatColor.GOLD + player.getName() + ChatColor.GREEN + " 支付了 " + eco.format(cost) + "，加入赌局！");
        } else {
            broadcast(ChatColor.GOLD + player.getName() + ChatColor.GREEN + " 加入对局！");
        }

        seatMap.put(seatIndex, player);
        waitingPlayers.add(player);
        joinedCount = getAllParticipants().size();
        setStatus("玩家: " + joinedCount + "/4");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_XYLOPHONE, 1f, 1.34f);

        if (state == GameState.IDLE) state = GameState.WAITING;

        if (getAllParticipants().size() >= 4) {
            startGame(player);
        }
    }

    public void removePlayer(Player player) {
        waitingPlayers.remove(player);
        players.removeIf(p -> p.getPlayer().equals(player));
        seatMap.values().removeIf(p -> p.equals(player));
        if (players.isEmpty() && waitingPlayers.isEmpty()) {
            state = GameState.IDLE;
            clearDisplay();
            buildDisplay();
        }
        broadcast(ChatColor.GOLD + player.getName() + ChatColor.YELLOW + " 离开了游戏。");
        joinedCount = getAllParticipants().size();
        setStatus("玩家: " + joinedCount + "/4");
    }

    public List<Player> getAllParticipants() {
        List<Player> all = new ArrayList<>(waitingPlayers);
        for (PlayerState ps : players) {
            if (!all.contains(ps.getPlayer())) all.add(ps.getPlayer());
        }
        return all;
    }

    public void startGame(Player starter) {
        if (state != GameState.WAITING && state != GameState.IDLE) {
            if (starter != null) starter.sendMessage(ChatColor.RED + "游戏不处于可开始状态。");
            return;
        }
        if (waitingPlayers.size() < 2) {
            if (starter != null) starter.sendMessage(ChatColor.RED + "至少需要 2 名玩家。");
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
        isFirstRound = true;
        startRound();
    }

    private void startRound() {
        centerCards.clear();
        mainCard = Card.randomMainType();

        // 构建牌堆: 6A + 6Q + 6K + 2坤 + 1恶魔
        List<Card> deck = new ArrayList<>();
        for (int i = 0; i < 6; i++) deck.add(Card.A);
        for (int i = 0; i < 6; i++) deck.add(Card.Q);
        for (int i = 0; i < 6; i++) deck.add(Card.K);
        deck.add(Card.KUN);
        deck.add(Card.KUN);
        Collections.shuffle(deck);

        // 从主牌中随机选一张替换为恶魔牌
        List<Integer> mainIndices = new ArrayList<>();
        for (int i = 0; i < deck.size(); i++) {
            if (deck.get(i).matchesMain(mainCard)) mainIndices.add(i);
        }
        if (!mainIndices.isEmpty()) {
            deck.set(mainIndices.get((int) (Math.random() * mainIndices.size())), Card.DEMON);
        }

        for (PlayerState ps : players) ps.resetForNewRound();

        int cardIndex = 0;
        for (PlayerState ps : players) {
            for (int i = 0; i < 5 && cardIndex < deck.size(); i++) {
                ps.getHand().add(deck.get(cardIndex++));
            }
        }

        broadcast(ChatColor.YELLOW + "新的一轮！主牌：" + ChatColor.GOLD + ChatColor.BOLD + "[{ " + mainCard.getDisplay() + " }]");

        // 随机先手
        if (isFirstRound) {
            currentPlayerIndex = (int) (Math.random() * players.size());
            isFirstRound = false;
        }
        lastPlayer = null;
        state = GameState.PLAYING;
        startTurn(true);
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

        current.getPlayer().sendTitle(ChatColor.GREEN + ">>>你的回合<<<", "", 10, 70, 20);
        current.getPlayer().playSound(current.getPlayer().getLocation(), Sound.BLOCK_ANVIL_PLACE, 1f, 1f);

        // 渲染当前玩家的手牌
        renderPlayerCards(current);
        renderActionButtons(current, standard && lastPlayer != null && lastPlayer != current);

        // 渲染中心牌
        if (!centerCards.isEmpty()) renderCenterCards();

        // 更新状态标签
        setStatus(standard ? (current.getPlayer().getName() + " 的回合") : "先手回合");

        for (PlayerState ps : players) {
            if (ps == current) continue;
            ps.getPlayer().sendMessage(ChatColor.YELLOW + "当前是 " + ChatColor.GOLD + current.getPlayer().getName() + ChatColor.YELLOW + " 的回合");
        }

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

    public void selectCard(Player player, int cardIndex) {
        PlayerState ps = findState(player);
        if (ps == null || ps != players.get(currentPlayerIndex)) {
            player.sendMessage(ChatColor.RED + "现在不是你的回合。");
            return;
        }
        ps.toggleSelection(cardIndex);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.2f);
        if (ps.getSelected().contains(cardIndex)) {
            player.sendActionBar(Component.text(ChatColor.GREEN + "已选择第 " + (cardIndex + 1) + " 张牌 [" + ps.getHand().get(cardIndex).getDisplay() + "]"));
        } else {
            player.sendActionBar(Component.text(ChatColor.RED + "已取消第 " + (cardIndex + 1) + " 张牌"));
        }
    }

    public void playCards(Player player) {
        PlayerState ps = findState(player);
        if (ps == null || ps != players.get(currentPlayerIndex)) {
            player.sendMessage(ChatColor.RED + "现在不是你的回合。");
            return;
        }
        List<Integer> sel = ps.getSortedSelection();
        if (sel.isEmpty()) {
            player.sendMessage(ChatColor.RED + "请先点击选择要出的牌。");
            return;
        }
        if (sel.size() > 3) {
            player.sendMessage(ChatColor.RED + "一次最多出 3 张牌。");
            return;
        }

        List<Card> played = sel.stream().map(i -> ps.getHand().get(i)).collect(Collectors.toList());
        boolean hasDemon = played.contains(Card.DEMON);
        if (hasDemon && played.size() > 1) {
            player.sendMessage(ChatColor.RED + "恶魔牌只能单出！");
            return;
        }

        ps.removeCards(sel);
        centerCards = new ArrayList<>(played);
        lastPlayer = ps;
        ps.clearSelection();

        broadcast(ChatColor.GOLD + player.getName() + ChatColor.YELLOW + " 出了 " + ChatColor.GOLD + played.size() + ChatColor.YELLOW + " 张牌。");

        clearPlayerDisplay(ps.getSeatIndex());
        clearCenterDisplay();
        renderCenterCards();

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
            player.sendMessage(ChatColor.RED + "没有可质疑的牌。");
            return;
        }
        cancelTurnTimer();
        state = GameState.RESOLVING;
        ps.clearSelection();

        broadcast(ChatColor.GOLD + player.getName() + ChatColor.YELLOW + " 质疑了 " + ChatColor.RED + ChatColor.BOLD + lastPlayer.getPlayer().getName());

        renderCenterCards();

        boolean hasDemon = centerCards.stream().anyMatch(c -> c == Card.DEMON);
        boolean allMainOrWild = centerCards.stream().allMatch(c -> c.isMainOrWild(mainCard));

        StringBuilder reveal = new StringBuilder(ChatColor.YELLOW + "翻开的牌： ");
        for (Card c : centerCards) reveal.append("[").append(c.getDisplay()).append("] ");
        broadcast(reveal.toString());

        if (hasDemon) {
            broadcast(ChatColor.YELLOW + "哦！是" + ChatColor.RED + ChatColor.BOLD + "恶魔牌" + ChatColor.YELLOW + "，除了出牌者所有人都要挨枪！");
            List<PlayerState> targets = players.stream()
                    .filter(p -> p != lastPlayer && p.isAlive()).collect(Collectors.toList());
            pendingShots = targets.size();
            if (pendingShots == 0) {
                afterResolution();
            } else {
                for (PlayerState target : targets) {
                    shoot(target, () -> {
                        pendingShots--;
                        if (pendingShots <= 0) afterResolution();
                    });
                }
            }
            return;
        }

        PlayerState loser;
        if (!allMainOrWild) {
            broadcast(ChatColor.YELLOW + "不是" + ChatColor.RED + ChatColor.BOLD + "主牌" + ChatColor.YELLOW + "！" +
                    ChatColor.GOLD + lastPlayer.getPlayer().getName() + ChatColor.YELLOW + " 冲着自己来一枪吧！");
            loser = lastPlayer;
        } else {
            broadcast(ChatColor.YELLOW + "是" + ChatColor.RED + ChatColor.BOLD + "主牌" + ChatColor.YELLOW + "！" +
                    ChatColor.GOLD + player.getName() + ChatColor.YELLOW + " 按下扳机吧！");
            loser = ps;
        }
        shoot(loser, this::afterResolution);
    }

    private void shoot(PlayerState ps, Runnable callback) {
        Player p = ps.getPlayer();
        int bullets = ps.getBullets();
        int roll = (int) (Math.random() * bullets) + 1;
        boolean real = roll == 1;

        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 0.5f, 1.5f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (real) {
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.51f);
                p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1.7, 0), 20, 0.2, 0.2, 0.2, 0.05);
                p.getWorld().spawnParticle(Particle.SMOKE_LARGE, p.getLocation().add(0, 1.7, 0), 10, 0.2, 0.2, 0.2, 0.02);
                broadcast(ChatColor.GOLD + p.getName() + ChatColor.DARK_RED + " 的脑袋被开了瓢。");
                ps.setAlive(false);
                ps.setBullets(0);
                p.setHealth(0);
            } else {
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_OFF, 1f, 0.97f);
                broadcast(ChatColor.GOLD + p.getName() + ChatColor.GREEN + " 运气不错，空弹！子弹剩余 " + (bullets - 1) + " 发。");
                ps.setBullets(bullets - 1);
            }
            callback.run();
        }, 40L);
    }

    private void afterResolution() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (checkWin()) return;
            state = GameState.PLAYING;
            currentPlayerIndex = nextAliveIndex(currentPlayerIndex);
            clearCenterDisplay();
            startTurn(true);
        });
    }

    private void handleTimeout(PlayerState ps) {
        broadcast(ChatColor.GOLD + ps.getPlayer().getName() + ChatColor.YELLOW + " 回合超时，自动质疑。");
        challenge(ps.getPlayer());
    }

    private boolean checkWin() {
        List<PlayerState> aliveWithCards = players.stream()
                .filter(p -> p.isAlive() && p.hasCards()).collect(Collectors.toList());

        if (aliveWithCards.size() <= 1) {
            state = GameState.ENDED;
            cancelTurnTimer();
            clearDisplay();

            PlayerState winner = aliveWithCards.isEmpty() ? null : aliveWithCards.get(0);
            if (winner != null) {
                broadcast(ChatColor.GOLD + "" + ChatColor.BOLD + "=== 游戏结束 ===");
                broadcast(ChatColor.YELLOW + "恭喜 " + ChatColor.GOLD + ChatColor.BOLD + winner.getPlayer().getName() +
                        ChatColor.YELLOW + " 获得了胜利！");

                if (plugin.getConfigManager().isGamblingEnabled() && betMode != BetMode.LIFE) {
                    EconomyManager eco = plugin.getEconomyManager();
                    double reward = joinedCount * 1.0;
                    if (eco.isEnabled()) {
                        eco.deposit(winner.getPlayer(), reward);
                        broadcast(ChatColor.YELLOW + "赢得 " + ChatColor.GOLD + eco.format(reward) + ChatColor.YELLOW + "！");
                    }
                }
                winner.getPlayer().getWorld().spawnParticle(Particle.FIREWORKS_SPARK,
                        winner.getPlayer().getLocation().add(0, 2, 0), 30, 0.5, 0.5, 0.5, 0.1);
                broadcast(ChatColor.GREEN + "本局获胜：下注模式 " + betMode.getDisplay() + " > " + ChatColor.GOLD + winner.getPlayer().getName());
            } else {
                broadcast(ChatColor.YELLOW + "所有玩家都出局了，本局没有胜者。");
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                players.clear();
                waitingPlayers.clear();
                seatMap.clear();
                state = GameState.IDLE;
                buildDisplay();
            }, 100L);
            return true;
        }
        return false;
    }

    public void endGame(Player sender) {
        cancelTurnTimer();
        state = GameState.ENDED;
        clearDisplay();
        broadcast(ChatColor.YELLOW + "游戏被 " + sender.getName() + " 强制结束。");

        if (plugin.getConfigManager().isGamblingEnabled() && betMode != BetMode.LIFE && !players.isEmpty()) {
            List<PlayerState> alive = players.stream().filter(PlayerState::isAlive).collect(Collectors.toList());
            PlayerState randomWinner = alive.isEmpty() ? players.get((int) (Math.random() * players.size()))
                    : alive.get((int) (Math.random() * alive.size()));
            broadcast(ChatColor.YELLOW + "有人强制结束，随机胜者：" + ChatColor.GOLD + randomWinner.getPlayer().getName());
            EconomyManager eco = plugin.getEconomyManager();
            if (eco.isEnabled()) {
                eco.deposit(randomWinner.getPlayer(), joinedCount * 1.0);
            }
        }

        players.clear();
        waitingPlayers.clear();
        seatMap.clear();
        state = GameState.IDLE;
        buildDisplay();
    }

    private int nextIndex(int current) { return (current + 1) % players.size(); }

    private int nextAliveIndex(int current) {
        for (int i = 0; i < players.size(); i++) {
            current = (current + 1) % players.size();
            PlayerState ps = players.get(current);
            if (ps.isAlive() && ps.hasCards()) return current;
        }
        return current;
    }

    private PlayerState findState(Player player) {
        return players.stream().filter(p -> p.getPlayer().equals(player)).findFirst().orElse(null);
    }

    private void cancelTurnTimer() {
        if (turnTimer != null) { turnTimer.cancel(); turnTimer = null; }
    }

    private void broadcast(String message) {
        for (Player p : getAllParticipants()) p.sendMessage(message);
    }

    public void destroy() {
        cancelTurnTimer();
        clearDisplay();
        for (Player p : getAllParticipants()) p.sendMessage(ChatColor.YELLOW + "本桌已被删除。");
        players.clear();
        waitingPlayers.clear();
        seatMap.clear();
    }

    public String getInfo() {
        return "桌子 " + id + " 状态：" + state + " 人数：" + getAllParticipants().size() + "/4 模式：" + betMode.getDisplay();
    }
}