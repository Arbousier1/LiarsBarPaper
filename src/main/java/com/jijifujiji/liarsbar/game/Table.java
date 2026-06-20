package com.jijifujiji.liarsbar.game;

import com.jijifujiji.liarsbar.LiarsBarPlugin;
import com.jijifujiji.liarsbar.display.TableDisplay;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public class Table {

    private static final List<Card> BASE_DECK = createBaseDeck();

    private final LiarsBarPlugin plugin;
    private final String id;
    private final TableDisplay display;
    private final TurnTimer turnTimer;
    private Location location;

    private final List<PlayerState> players = new ArrayList<>();
    private final List<Player> waitingPlayers = new ArrayList<>();
    private final Map<Integer, Player> seatMap = new java.util.HashMap<>();
    private final List<Card> centerCards = new ArrayList<>();

    private GameState state = GameState.IDLE;
    private BetMode betMode = BetMode.LIFE;
    private CardType mainCard;
    private int currentPlayerIndex = -1;
    private PlayerState lastPlayer;
    private int pendingShots;
    private int joinedCount;
    private boolean isFirstRound = true;

    public Table(LiarsBarPlugin plugin, String id, Location location) {
        this.plugin = plugin;
        this.id = id;
        this.location = location == null ? null : location.clone();
        this.display = new TableDisplay(id);
        this.turnTimer = new TurnTimer(plugin);
    }

    public String getId() { return id; }
    public Location getLocation() { return location == null ? null : location.clone(); }
    public void setLocation(Location location) { this.location = location.clone(); }
    public GameState getState() { return state; }
    public BetMode getBetMode() { return betMode; }

    public boolean isInGame(Player player) {
        UUID uuid = player.getUniqueId();
        for (PlayerState ps : players) {
            if (ps.getPlayer().getUniqueId().equals(uuid)) return true;
        }
        for (Player waiting : waitingPlayers) {
            if (waiting.getUniqueId().equals(uuid)) return true;
        }
        for (Player seated : seatMap.values()) {
            if (seated != null && seated.getUniqueId().equals(uuid)) return true;
        }
        return false;
    }

    public boolean setBetMode(BetMode mode) {
        if (state != GameState.IDLE && state != GameState.WAITING) {
            return false;
        }
        this.betMode = mode;
        buildDisplay();
        return true;
    }

    public boolean isSeatOccupied(int seatIndex) {
        Player occupant = seatMap.get(seatIndex);
        return occupant != null && occupant.isOnline();
    }

    private void movePlayerToSeat(Player player, int seatIndex) {
        if (location == null || location.getWorld() == null) return;
        player.leaveVehicle();
        player.teleport(TableLayout.seatLocation(location, seatIndex));
        Entity seat = display.findSeatVehicle(location, seatIndex);
        if (seat != null && seat.isValid()) {
            seat.addPassenger(player);
        }
    }

    public void buildDisplay() {
        display.build(location, betMode);
        if (location == null || location.getWorld() == null) return;
        for (Map.Entry<Integer, Player> entry : seatMap.entrySet()) {
            Player player = entry.getValue();
            if (player != null && player.isOnline()) {
                movePlayerToSeat(player, entry.getKey());
            }
        }
    }

    public void clearDisplay() {
        display.clear(location);
    }

    public boolean syncCraftEngineFurniture() {
        return plugin.getCraftEngineFurnitureBridge().placeTableSet(id, location);
    }

    public void clearCraftEngineFurniture() {
        plugin.getCraftEngineFurnitureBridge().clearTableSet(id, location);
    }

    private void setStatus(String text) {
        display.setStatus(text);
    }

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

        if (!chargeEntryCost(player)) return;

        seatMap.put(seatIndex, player);
        waitingPlayers.add(player);
        registerPlayer(player);
        movePlayerToSeat(player, seatIndex);
        joinedCount = participantCount();
        setStatus("玩家: " + joinedCount + "/4");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_XYLOPHONE, 1f, 1.34f);

        if (state == GameState.IDLE) state = GameState.WAITING;
        if (joinedCount >= TableLayout.SEAT_COUNT) {
            startGame(player);
        }
    }

    public void removePlayer(Player player) {
        player.leaveVehicle();
        boolean wasActiveGame = state == GameState.PLAYING || state == GameState.RESOLVING || state == GameState.DEALING;
        int removedIndex = indexOfState(player);
        boolean wasCurrent = currentPlayerIndex >= 0 && removedIndex == currentPlayerIndex;
        PlayerState removedState = removedIndex >= 0 ? players.get(removedIndex) : null;
        if (removedState != null) {
            display.clearPlayerDisplay(removedState.getSeatIndex());
        }

        waitingPlayers.removeIf(p -> p.getUniqueId().equals(player.getUniqueId()));
        turnTimer.removePlayer(player);
        players.removeIf(p -> p.getPlayer().getUniqueId().equals(player.getUniqueId()));
        seatMap.values().removeIf(p -> p != null && p.getUniqueId().equals(player.getUniqueId()));
        unregisterPlayer(player);

        if (removedIndex >= 0 && removedIndex < currentPlayerIndex) {
            currentPlayerIndex--;
        }
        if (currentPlayerIndex >= players.size()) {
            currentPlayerIndex = players.isEmpty() ? -1 : 0;
        }
        if (players.isEmpty() && waitingPlayers.isEmpty()) {
            turnTimer.cancel();
            state = GameState.IDLE;
            clearDisplay();
            buildDisplay();
        } else if (wasActiveGame && state == GameState.PLAYING) {
            if (!checkWin() && wasCurrent) {
                startTurn(true);
            }
        }
        broadcast(ChatColor.GOLD + player.getName() + ChatColor.YELLOW + " 离开了游戏。");
        joinedCount = participantCount();
        setStatus("玩家: " + joinedCount + "/4");
    }

    public List<Player> getAllParticipants() {
        List<Player> all = new ArrayList<>(TableLayout.SEAT_COUNT);
        visitParticipants(all::add);
        return all;
    }

    public void startGame(Player starter) {
        if (state != GameState.WAITING && state != GameState.IDLE) {
            if (starter != null) starter.sendMessage(ChatColor.RED + "游戏不处于可开始状态。");
            return;
        }
        if (starter != null && !seatMap.containsValue(starter) && !starter.hasPermission("liarsbar.admin")) {
            starter.sendMessage(ChatColor.RED + "只有本桌玩家可以开始游戏。");
            return;
        }

        List<Map.Entry<Integer, Player>> seatedPlayers = seatedPlayersInOrder();
        if (seatedPlayers.size() < 2) {
            if (starter != null) starter.sendMessage(ChatColor.RED + "至少需要 2 名玩家。");
            return;
        }

        players.clear();
        waitingPlayers.clear();
        seatMap.clear();
        for (Map.Entry<Integer, Player> entry : seatedPlayers) {
            Player player = entry.getValue();
            seatMap.put(entry.getKey(), player);
            registerPlayer(player);
            movePlayerToSeat(player, entry.getKey());
            players.add(new PlayerState(player, entry.getKey()));
        }
        joinedCount = players.size();
        state = GameState.DEALING;
        broadcast(ChatColor.YELLOW + "游戏开始！模式：" + ChatColor.GOLD + ChatColor.BOLD + betMode.getDisplay());
        isFirstRound = true;
        startRound();
    }

    private void startRound() {
        centerCards.clear();
        mainCard = Card.randomMainType();
        List<Card> deck = new ArrayList<>(BASE_DECK);
        java.util.Collections.shuffle(deck, ThreadLocalRandom.current());
        replaceOneMainCardWithDemon(deck);

        for (PlayerState ps : players) ps.resetForNewRound();

        int cardIndex = 0;
        for (PlayerState ps : players) {
            for (int i = 0; i < 5 && cardIndex < deck.size(); i++) {
                ps.getHand().add(deck.get(cardIndex++));
            }
        }

        broadcast(ChatColor.YELLOW + "新的一轮！主牌：" + ChatColor.GOLD + ChatColor.BOLD + "[{ " + mainCard.getDisplay() + " }]");
        if (isFirstRound) {
            currentPlayerIndex = ThreadLocalRandom.current().nextInt(players.size());
            isFirstRound = false;
        }
        lastPlayer = null;
        state = GameState.PLAYING;
        startTurn(true);
    }

    private void startTurn(boolean standard) {
        if (checkWin()) return;
        if (players.isEmpty()) {
            state = GameState.IDLE;
            buildDisplay();
            return;
        }
        if (currentPlayerIndex < 0 || currentPlayerIndex >= players.size()) {
            currentPlayerIndex = 0;
        }

        PlayerState current = players.get(currentPlayerIndex);
        while (!current.isAlive() || !current.hasCards()) {
            currentPlayerIndex = nextIndex(currentPlayerIndex);
            current = players.get(currentPlayerIndex);
        }

        PlayerState finalCurrent = current;
        turnTimer.start(finalCurrent, getAllParticipants(), mainCard, () -> handleTimeout(finalCurrent));
        finalCurrent.getPlayer().sendTitle(ChatColor.GREEN + ">>>你的回合<<<", "", 10, 70, 20);
        finalCurrent.getPlayer().playSound(finalCurrent.getPlayer().getLocation(), Sound.BLOCK_ANVIL_PLACE, 1f, 1f);

        display.renderPlayerCards(location, finalCurrent);
        display.renderActionButtons(location, finalCurrent, standard && lastPlayer != null && lastPlayer != finalCurrent);
        if (!centerCards.isEmpty()) display.renderCenterCards(location, centerCards);
        setStatus(standard ? (finalCurrent.getPlayer().getName() + " 的回合") : "先手回合");

        for (PlayerState ps : players) {
            if (ps == finalCurrent) continue;
            ps.getPlayer().sendMessage(ChatColor.YELLOW + "当前是 " + ChatColor.GOLD + finalCurrent.getPlayer().getName() + ChatColor.YELLOW + " 的回合");
        }
    }

    public void selectCard(Player player, int cardIndex) {
        PlayerState ps = findState(player);
        if (!isCurrentPlayer(ps)) {
            player.sendMessage(ChatColor.RED + "现在不是你的回合。");
            return;
        }
        if (cardIndex < 0 || cardIndex >= ps.getHand().size()) {
            player.sendMessage(ChatColor.RED + "无效的手牌序号。");
            return;
        }
        ps.toggleSelection(cardIndex);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.2f);
        if (ps.getSelected().contains(cardIndex)) {
            player.sendMessage(ChatColor.GREEN + "已选择第 " + (cardIndex + 1) + " 张牌 [" + ps.getHand().get(cardIndex).getDisplay() + "]");
        } else {
            player.sendMessage(ChatColor.RED + "已取消第 " + (cardIndex + 1) + " 张牌");
        }
    }

    public void playCards(Player player) {
        PlayerState ps = findState(player);
        if (state != GameState.PLAYING || !isCurrentPlayer(ps)) {
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
        if (!isValidSelection(ps, sel)) {
            ps.clearSelection();
            player.sendMessage(ChatColor.RED + "选择已过期，请重新选择手牌。");
            return;
        }

        List<Card> played = selectedCards(ps, sel);
        if (played.contains(Card.DEMON) && played.size() > 1) {
            player.sendMessage(ChatColor.RED + "恶魔牌只能单出！");
            return;
        }

        ps.removeCards(sel);
        centerCards.clear();
        centerCards.addAll(played);
        lastPlayer = ps;
        ps.clearSelection();

        broadcast(ChatColor.GOLD + player.getName() + ChatColor.YELLOW + " 出了 " + ChatColor.GOLD + played.size() + ChatColor.YELLOW + " 张牌。");
        display.clearPlayerDisplay(ps.getSeatIndex());
        display.clearCenterDisplay();
        display.renderCenterCards(location, centerCards);

        currentPlayerIndex = nextIndex(currentPlayerIndex);
        startTurn(true);
    }

    public void challenge(Player player) {
        PlayerState ps = findState(player);
        if (state != GameState.PLAYING || !isCurrentPlayer(ps)) {
            player.sendMessage(ChatColor.RED + "现在不是你的回合。");
            return;
        }
        if (lastPlayer == null || lastPlayer == ps) {
            player.sendMessage(ChatColor.RED + "没有可质疑的牌。");
            return;
        }
        turnTimer.cancel();
        state = GameState.RESOLVING;
        ps.clearSelection();

        broadcast(ChatColor.GOLD + player.getName() + ChatColor.YELLOW + " 质疑了 " + ChatColor.RED + ChatColor.BOLD + lastPlayer.getPlayer().getName());
        display.renderCenterCards(location, centerCards);

        boolean hasDemon = false;
        boolean allMainOrWild = true;
        for (Card card : centerCards) {
            if (card == Card.DEMON) hasDemon = true;
            if (!card.isMainOrWild(mainCard)) allMainOrWild = false;
        }

        StringBuilder reveal = new StringBuilder(ChatColor.YELLOW + "翻开的牌： ");
        for (Card c : centerCards) reveal.append("[").append(c.getDisplay()).append("] ");
        broadcast(reveal.toString());

        if (hasDemon) {
            broadcast(ChatColor.YELLOW + "哦！是" + ChatColor.RED + ChatColor.BOLD + "恶魔牌" + ChatColor.YELLOW + "，除了出牌者所有人都要挨枪！");
            List<PlayerState> targets = new ArrayList<>();
            for (PlayerState target : players) {
                if (target != lastPlayer && target.isAlive()) targets.add(target);
            }
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

    public void endGame(Player sender) {
        turnTimer.cancel();
        state = GameState.ENDED;
        leaveVehicles();
        clearDisplay();
        broadcast(ChatColor.YELLOW + "游戏被 " + sender.getName() + " 强制结束。");

        if (plugin.getConfigManager().isGamblingEnabled() && betMode != BetMode.LIFE && !players.isEmpty()) {
            PlayerState randomWinner = randomAliveOrAnyPlayer();
            if (randomWinner != null) {
                broadcast(ChatColor.YELLOW + "有人强制结束，随机胜者：" + ChatColor.GOLD + randomWinner.getPlayer().getName());
                EconomyManager eco = plugin.getEconomyManager();
                if (eco.isEnabled()) {
                    eco.deposit(randomWinner.getPlayer(), joinedCount * 1.0);
                }
            }
        }

        unregisterAllParticipants();
        players.clear();
        waitingPlayers.clear();
        seatMap.clear();
        joinedCount = 0;
        state = GameState.IDLE;
        buildDisplay();
    }

    public void destroy() {
        turnTimer.cancel();
        leaveVehicles();
        clearDisplay();
        clearCraftEngineFurniture();
        broadcast(ChatColor.YELLOW + "本桌已被删除。");
        unregisterAllParticipants();
        players.clear();
        waitingPlayers.clear();
        seatMap.clear();
        joinedCount = 0;
    }

    public String getInfo() {
        return "桌子 " + id + " 状态：" + state + " 人数：" + participantCount() + "/4 模式：" + betMode.getDisplay();
    }

    private boolean chargeEntryCost(Player player) {
        if (!plugin.getConfigManager().isGamblingEnabled() || betMode == BetMode.LIFE) {
            broadcast(ChatColor.GOLD + player.getName() + ChatColor.GREEN + " 加入对局！");
            return true;
        }

        double cost = 1.0;
        EconomyManager eco = plugin.getEconomyManager();
        if (!eco.isEnabled()) {
            player.sendMessage(ChatColor.RED + "经济系统未启用，无法加入赌局。");
            return false;
        }
        if (!eco.has(player, cost)) {
            player.sendMessage(ChatColor.RED + "你的余额不足，需要 " + eco.format(cost) + "。");
            return false;
        }
        if (!eco.withdraw(player, cost)) {
            player.sendMessage(ChatColor.RED + "扣费失败。");
            return false;
        }
        broadcast(ChatColor.GOLD + player.getName() + ChatColor.GREEN + " 支付了 " + eco.format(cost) + "，加入赌局！");
        return true;
    }

    private void shoot(PlayerState ps, Runnable callback) {
        Player p = ps.getPlayer();
        int bullets = ps.getBullets();
        int roll = ThreadLocalRandom.current().nextInt(bullets) + 1;
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
            centerCards.clear();
            lastPlayer = null;
            display.clearCenterDisplay();
            startTurn(true);
        });
    }

    private void handleTimeout(PlayerState ps) {
        if (lastPlayer != null && lastPlayer != ps) {
            broadcast(ChatColor.GOLD + ps.getPlayer().getName() + ChatColor.YELLOW + " 回合超时，自动质疑。");
            challenge(ps.getPlayer());
        } else {
            broadcast(ChatColor.GOLD + ps.getPlayer().getName() + ChatColor.YELLOW + " 回合超时，自动出牌。");
            if (!ps.getHand().isEmpty()) {
                ps.clearSelection();
                ps.toggleSelection(0);
                playCards(ps.getPlayer());
            }
        }
    }

    private boolean checkWin() {
        int aliveWithCards = 0;
        PlayerState winner = null;
        for (PlayerState ps : players) {
            if (ps.isAlive() && ps.hasCards()) {
                aliveWithCards++;
                winner = ps;
            }
        }

        if (aliveWithCards > 1) return false;

        state = GameState.ENDED;
        turnTimer.cancel();
        leaveVehicles();
        clearDisplay();

        if (winner != null) {
            broadcast(ChatColor.GOLD + "" + ChatColor.BOLD + "=== 游戏结束 ===");
            broadcast(ChatColor.YELLOW + "恭喜 " + ChatColor.GOLD + ChatColor.BOLD + winner.getPlayer().getName() +
                    ChatColor.YELLOW + " 获得了胜利！");
            rewardWinner(winner);
            winner.getPlayer().getWorld().spawnParticle(Particle.FIREWORKS_SPARK,
                    winner.getPlayer().getLocation().add(0, 2, 0), 30, 0.5, 0.5, 0.5, 0.1);
            broadcast(ChatColor.GREEN + "本局获胜：下注模式 " + betMode.getDisplay() + " > " + ChatColor.GOLD + winner.getPlayer().getName());
        } else {
            broadcast(ChatColor.YELLOW + "所有玩家都出局了，本局没有胜者。");
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            unregisterAllParticipants();
            players.clear();
            waitingPlayers.clear();
            seatMap.clear();
            joinedCount = 0;
            state = GameState.IDLE;
            buildDisplay();
        }, 100L);
        return true;
    }

    private void rewardWinner(PlayerState winner) {
        if (!plugin.getConfigManager().isGamblingEnabled() || betMode == BetMode.LIFE) return;
        EconomyManager eco = plugin.getEconomyManager();
        double reward = joinedCount * 1.0;
        if (eco.isEnabled()) {
            eco.deposit(winner.getPlayer(), reward);
            broadcast(ChatColor.YELLOW + "赢得 " + ChatColor.GOLD + eco.format(reward) + ChatColor.YELLOW + "！");
        }
    }

    private PlayerState randomAliveOrAnyPlayer() {
        List<PlayerState> alive = new ArrayList<>();
        for (PlayerState ps : players) {
            if (ps.isAlive()) alive.add(ps);
        }
        List<PlayerState> pool = alive.isEmpty() ? players : alive;
        if (pool.isEmpty()) return null;
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    private List<Map.Entry<Integer, Player>> seatedPlayersInOrder() {
        List<Map.Entry<Integer, Player>> seatedPlayers = new ArrayList<>(TableLayout.SEAT_COUNT);
        for (int i = 0; i < TableLayout.SEAT_COUNT; i++) {
            Player player = seatMap.get(i);
            if (player != null && player.isOnline()) {
                seatedPlayers.add(Map.entry(i, player));
            }
        }
        return seatedPlayers;
    }

    private void replaceOneMainCardWithDemon(List<Card> deck) {
        List<Integer> mainIndices = new ArrayList<>();
        for (int i = 0; i < deck.size(); i++) {
            if (deck.get(i).matchesMain(mainCard)) mainIndices.add(i);
        }
        if (!mainIndices.isEmpty()) {
            int selected = mainIndices.get(ThreadLocalRandom.current().nextInt(mainIndices.size()));
            deck.set(selected, Card.DEMON);
        }
    }

    private boolean isCurrentPlayer(PlayerState ps) {
        return currentPlayerIndex >= 0 && currentPlayerIndex < players.size()
                && ps != null && ps == players.get(currentPlayerIndex);
    }

    private boolean isValidSelection(PlayerState ps, List<Integer> selected) {
        for (int index : selected) {
            if (index < 0 || index >= ps.getHand().size()) return false;
        }
        return true;
    }

    private List<Card> selectedCards(PlayerState ps, List<Integer> selected) {
        List<Card> cards = new ArrayList<>(selected.size());
        for (int index : selected) {
            cards.add(ps.getHand().get(index));
        }
        return cards;
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
        int index = indexOfState(player);
        return index >= 0 ? players.get(index) : null;
    }

    private int indexOfState(Player player) {
        UUID uuid = player.getUniqueId();
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).getPlayer().getUniqueId().equals(uuid)) return i;
        }
        return -1;
    }

    private void leaveVehicles() {
        visitParticipants(Player::leaveVehicle);
    }

    private void broadcast(String message) {
        visitParticipants(player -> player.sendMessage(message));
    }

    private int participantCount() {
        return visitParticipants(null);
    }

    private int visitParticipants(Consumer<Player> visitor) {
        Set<UUID> seen = new HashSet<>(TableLayout.SEAT_COUNT);
        int count = 0;
        for (Player player : waitingPlayers) {
            if (visitParticipant(player, seen, visitor)) count++;
        }
        for (PlayerState ps : players) {
            if (visitParticipant(ps.getPlayer(), seen, visitor)) count++;
        }
        for (Player player : seatMap.values()) {
            if (visitParticipant(player, seen, visitor)) count++;
        }
        return count;
    }

    private boolean visitParticipant(Player player, Set<UUID> seen, Consumer<Player> visitor) {
        if (player == null || !seen.add(player.getUniqueId())) return false;
        if (visitor != null) visitor.accept(player);
        return true;
    }

    private void registerPlayer(Player player) {
        plugin.getTableManager().registerPlayer(player, this);
    }

    private void unregisterPlayer(Player player) {
        plugin.getTableManager().unregisterPlayer(player);
    }

    private void unregisterAllParticipants() {
        visitParticipants(player -> plugin.getTableManager().unregisterPlayer(player));
    }

    private static List<Card> createBaseDeck() {
        List<Card> deck = new ArrayList<>(20);
        for (int i = 0; i < 6; i++) deck.add(Card.A);
        for (int i = 0; i < 6; i++) deck.add(Card.Q);
        for (int i = 0; i < 6; i++) deck.add(Card.K);
        deck.add(Card.KUN);
        deck.add(Card.KUN);
        return java.util.Collections.unmodifiableList(deck);
    }
}
