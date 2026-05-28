package be.panchito.pointRush;

import be.panchito.pointRush.commands.RandomEventCommand;
import be.panchito.pointRush.commands.PointRushCommand;
import be.panchito.pointRush.commands.PointsCommand;
import be.panchito.pointRush.commands.CoinCommand;
import be.panchito.pointRush.commands.ShopCommand;
import be.panchito.pointRush.commands.TeamCommand;
import be.panchito.pointRush.config.UnifiedSettings;
import be.panchito.pointRush.coins.CoinCollectionMenu;
import be.panchito.pointRush.coins.CoinPickupListener;
import be.panchito.pointRush.coins.CoinSpawnConfig;
import be.panchito.pointRush.coins.NexoCoinSpawner;
import be.panchito.pointRush.shop.CoinCreditRegistry;
import be.panchito.pointRush.shop.CoinShopService;
import be.panchito.pointRush.shop.ShopMenus;
import be.panchito.pointRush.history.EventHistoryManager;
import be.panchito.pointRush.minigame.parkour.ParkourCommand;
import be.panchito.pointRush.minigame.parkour.ParkourConfig;
import be.panchito.pointRush.minigame.parkour.ParkourGame;
import be.panchito.pointRush.minigame.parkour.ParkourListener;
import be.panchito.pointRush.minigame.race.RaceCommand;
import be.panchito.pointRush.minigame.race.RaceConfig;
import be.panchito.pointRush.minigame.race.RaceGame;
import be.panchito.pointRush.minigame.race.RaceListener;
import be.panchito.pointRush.minigame.boatrace.BoatRaceCommand;
import be.panchito.pointRush.minigame.boatrace.BoatRaceConfig;
import be.panchito.pointRush.minigame.boatrace.BoatRaceGame;
import be.panchito.pointRush.minigame.boatrace.BoatRaceListener;
import be.panchito.pointRush.minigame.bingo.BingoCommand;
import be.panchito.pointRush.minigame.bingo.BingoConfig;
import be.panchito.pointRush.minigame.bingo.BingoGame;
import be.panchito.pointRush.minigame.bingo.BingoListener;
import be.panchito.pointRush.minigame.tntrun.TntRunCommand;
import be.panchito.pointRush.minigame.tntrun.TntRunConfig;
import be.panchito.pointRush.minigame.tntrun.TntRunGame;
import be.panchito.pointRush.minigame.tntrun.TntRunListener;
import be.panchito.pointRush.minigame.koth.KothCommand;
import be.panchito.pointRush.minigame.koth.KothConfig;
import be.panchito.pointRush.minigame.koth.KothGame;
import be.panchito.pointRush.minigame.koth.KothListener;
import be.panchito.pointRush.minigame.floorislava.FloorIsLavaCommand;
import be.panchito.pointRush.minigame.floorislava.FloorIsLavaConfig;
import be.panchito.pointRush.minigame.floorislava.FloorIsLavaGame;
import be.panchito.pointRush.minigame.floorislava.FloorIsLavaListener;
import be.panchito.pointRush.minigame.treasurehunt.TreasureHuntCommand;
import be.panchito.pointRush.minigame.treasurehunt.TreasureHuntConfig;
import be.panchito.pointRush.minigame.treasurehunt.TreasureHuntGame;
import be.panchito.pointRush.minigame.treasurehunt.TreasureHuntListener;
import be.panchito.pointRush.minigame.goldrush.GoldRushCommand;
import be.panchito.pointRush.minigame.goldrush.GoldRushConfig;
import be.panchito.pointRush.minigame.goldrush.GoldRushGame;
import be.panchito.pointRush.minigame.goldrush.GoldRushListener;
import be.panchito.pointRush.minigame.hiddentarget.HiddenTargetCommand;
import be.panchito.pointRush.minigame.hiddentarget.HiddenTargetConfig;
import be.panchito.pointRush.minigame.hiddentarget.HiddenTargetGame;
import be.panchito.pointRush.minigame.hiddentarget.HiddenTargetListener;
import be.panchito.pointRush.minigame.ctf.CtfCommand;
import be.panchito.pointRush.minigame.ctf.CtfConfig;
import be.panchito.pointRush.minigame.ctf.CtfGame;
import be.panchito.pointRush.minigame.ctf.CtfListener;
import be.panchito.pointRush.minigame.tnttag.TntTagCommand;
import be.panchito.pointRush.minigame.tnttag.TntTagConfig;
import be.panchito.pointRush.minigame.tnttag.TntTagGame;
import be.panchito.pointRush.minigame.tnttag.TntTagListener;
import be.panchito.pointRush.minigame.MinigameRegistry;
import be.panchito.pointRush.random.RandomEventService;
import be.panchito.pointRush.scoreboard.LobbyScoreboard;
import be.panchito.pointRush.scoreboard.LobbyScoreboardListener;
import be.panchito.pointRush.storage.DataManager;
import be.panchito.pointRush.team.LeaderboardCache;
import be.panchito.pointRush.team.TeamManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Level;

/**
 * Main entrypoint for the PointRush plugin.
 * Wires up the team manager, persistence, command handlers and active minigames.
 */
public final class PointRush extends JavaPlugin {

    private static PointRush instance;

    private UnifiedSettings unifiedSettings;
    private LeaderboardCache leaderboardCache;
    private TeamManager teamManager;
    private DataManager dataManager;
    private EventHistoryManager eventHistoryManager;

    private ParkourConfig parkourConfig;
    private ParkourGame parkourGame;

    private TntTagConfig tntTagConfig;
    private TntTagGame tntTagGame;

    private TntRunConfig tntRunConfig;
    private TntRunGame tntRunGame;

    private RaceConfig raceConfig;
    private RaceGame raceGame;

    private BoatRaceConfig boatRaceConfig;
    private BoatRaceGame boatRaceGame;

    private BingoConfig bingoConfig;
    private BingoGame bingoGame;

    private KothConfig kothConfig;
    private KothGame kothGame;

    private FloorIsLavaConfig floorIsLavaConfig;
    private FloorIsLavaGame floorIsLavaGame;

    private TreasureHuntConfig treasureHuntConfig;
    private TreasureHuntGame treasureHuntGame;

    private GoldRushConfig goldRushConfig;
    private GoldRushGame goldRushGame;

    private HiddenTargetConfig hiddenTargetConfig;
    private HiddenTargetGame hiddenTargetGame;

    private CtfConfig ctfConfig;
    private CtfGame ctfGame;

    private CoinSpawnConfig coinSpawnConfig;
    private NexoCoinSpawner nexoCoinSpawner;

    private CoinCreditRegistry coinCreditRegistry;
    private CoinShopService coinShopService;

    private LobbyScoreboard lobbyScoreboard;
    private RandomEventService randomEventService;

    @Override
    public void onEnable() {
        instance = this;

        this.unifiedSettings = new UnifiedSettings(this);
        try {
            unifiedSettings.load();
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Kon PointRush settings.yml niet laden (encoding of YAML-syntax).", ex);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.leaderboardCache = new LeaderboardCache();
        this.teamManager = new TeamManager();
        this.dataManager = new DataManager(this, teamManager, unifiedSettings, leaderboardCache);
        try {
            dataManager.load();
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "PointRush kan niet starten zonder werkende MongoDB.", ex);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.eventHistoryManager = new EventHistoryManager(this);
        eventHistoryManager.load();
        eventHistoryManager.setOnRecord(dataManager::syncEventHistoryEntry);
        dataManager.syncAllEventHistory(eventHistoryManager.all());

        registerCommand("team", new TeamCommand(teamManager, dataManager));
        registerCommand("points", new PointsCommand(teamManager, dataManager));
        registerCommand("pointrush", new PointRushCommand(eventHistoryManager));

        this.randomEventService = new RandomEventService(this);
        registerCommand("randomevent", new RandomEventCommand(randomEventService));

        this.parkourConfig = new ParkourConfig(this, unifiedSettings);
        parkourConfig.load();
        this.parkourGame = new ParkourGame(this, parkourConfig);
        registerCommand("parkour", new ParkourCommand(parkourGame, parkourConfig));
        getServer().getPluginManager().registerEvents(new ParkourListener(parkourGame), this);

        this.tntTagConfig = new TntTagConfig(this, unifiedSettings);
        tntTagConfig.load();
        this.tntTagGame = new TntTagGame(this, tntTagConfig);
        registerCommand("tnttag", new TntTagCommand(tntTagGame, tntTagConfig));
        getServer().getPluginManager().registerEvents(new TntTagListener(tntTagGame), this);

        this.tntRunConfig = new TntRunConfig(this, unifiedSettings);
        tntRunConfig.load();
        this.tntRunGame = new TntRunGame(this, tntRunConfig);
        registerCommand("tntrun", new TntRunCommand(tntRunGame, tntRunConfig));
        getServer().getPluginManager().registerEvents(new TntRunListener(tntRunGame), this);

        this.raceConfig = new RaceConfig(this, unifiedSettings);
        raceConfig.load();
        this.raceGame = new RaceGame(this, raceConfig);
        registerCommand("race", new RaceCommand(raceGame, raceConfig));
        getServer().getPluginManager().registerEvents(new RaceListener(raceGame), this);

        this.boatRaceConfig = new BoatRaceConfig(this, unifiedSettings);
        boatRaceConfig.load();
        this.boatRaceGame = new BoatRaceGame(this, boatRaceConfig);
        registerCommand("boatrace", new BoatRaceCommand(boatRaceGame, boatRaceConfig));
        getServer().getPluginManager().registerEvents(new BoatRaceListener(boatRaceGame), this);

        this.bingoConfig = new BingoConfig(this, unifiedSettings);
        bingoConfig.load();
        this.bingoGame = new BingoGame(this, bingoConfig);
        registerCommand("bingo", new BingoCommand(bingoGame, bingoConfig));
        getServer().getPluginManager().registerEvents(new BingoListener(bingoGame), this);

        this.kothConfig = new KothConfig(this, unifiedSettings);
        kothConfig.load();
        this.kothGame = new KothGame(this, kothConfig);
        registerCommand("koth", new KothCommand(kothGame, kothConfig));
        getServer().getPluginManager().registerEvents(new KothListener(kothGame), this);

        this.floorIsLavaConfig = new FloorIsLavaConfig(this, unifiedSettings);
        floorIsLavaConfig.load();
        this.floorIsLavaGame = new FloorIsLavaGame(this, floorIsLavaConfig);
        registerCommand("floorislava", new FloorIsLavaCommand(floorIsLavaGame, floorIsLavaConfig));
        getServer().getPluginManager().registerEvents(new FloorIsLavaListener(floorIsLavaGame), this);

        this.treasureHuntConfig = new TreasureHuntConfig(this, unifiedSettings);
        treasureHuntConfig.load();
        this.treasureHuntGame = new TreasureHuntGame(this, treasureHuntConfig);
        registerCommand("treasurehunt", new TreasureHuntCommand(treasureHuntGame, treasureHuntConfig));
        getServer().getPluginManager().registerEvents(new TreasureHuntListener(treasureHuntGame), this);

        this.goldRushConfig = new GoldRushConfig(this, unifiedSettings);
        goldRushConfig.load();
        this.goldRushGame = new GoldRushGame(this, goldRushConfig);
        registerCommand("goldrush", new GoldRushCommand(goldRushGame, goldRushConfig));
        getServer().getPluginManager().registerEvents(new GoldRushListener(goldRushGame), this);

        this.hiddenTargetConfig = new HiddenTargetConfig(this, unifiedSettings);
        hiddenTargetConfig.load();
        this.hiddenTargetGame = new HiddenTargetGame(this, hiddenTargetConfig);
        registerCommand("hiddentarget", new HiddenTargetCommand(hiddenTargetGame, hiddenTargetConfig));
        getServer().getPluginManager().registerEvents(new HiddenTargetListener(hiddenTargetGame), this);

        this.ctfConfig = new CtfConfig(this, unifiedSettings);
        ctfConfig.load();
        this.ctfGame = new CtfGame(this, ctfConfig);
        registerCommand("ctf", new CtfCommand(ctfGame, ctfConfig));
        getServer().getPluginManager().registerEvents(new CtfListener(ctfGame), this);

        this.coinSpawnConfig = new CoinSpawnConfig(this, unifiedSettings);
        coinSpawnConfig.load();
        this.coinCreditRegistry = CoinCreditRegistry.load(unifiedSettings);
        this.coinShopService = new CoinShopService(
                dataManager.getPlayerCoinRepository(),
                coinCreditRegistry,
                coinSpawnConfig.getResolvedPoolIds());
        this.nexoCoinSpawner = new NexoCoinSpawner(this, coinSpawnConfig, () -> MinigameRegistry.anyActive(this));
        getServer().getPluginManager().registerEvents(nexoCoinSpawner, this);
        getServer().getPluginManager().registerEvents(
                new CoinPickupListener(this, coinSpawnConfig, dataManager, nexoCoinSpawner), this);
        getServer().getPluginManager().registerEvents(
                new CoinCollectionMenu.CoinCollectionMenuListener(), this);
        getServer().getPluginManager().registerEvents(
                new ShopMenus.ShopGuiListener(this, coinShopService), this);
        nexoCoinSpawner.startScheduling();
        registerCommand("coins", new CoinCommand(coinSpawnConfig, nexoCoinSpawner, dataManager));
        registerCommand("shop", new ShopCommand(this, coinShopService));
        getServer().getScheduler().runTaskLater(this, () ->
                nexoCoinSpawner.tryAssumeNexoReadyAfterBoot(), 20L);

        this.lobbyScoreboard = new LobbyScoreboard(this);
        getServer().getPluginManager().registerEvents(
                new LobbyScoreboardListener(lobbyScoreboard), this);
        lobbyScoreboard.start();

        getLogger().info("PointRush ingeschakeld. Teams: " + teamManager.getTeams().size()
                + ", parkour klaar: " + parkourConfig.isReady()
                + ", tnttag klaar: " + tntTagConfig.isReady()
                + ", tntrun klaar: " + tntRunConfig.isReady()
                + ", race klaar: " + raceConfig.isReady()
                + ", bootrace klaar: " + boatRaceConfig.isReady()
                + ", bingo klaar: " + bingoConfig.isReady()
                + ", koth klaar: " + kothConfig.isReady()
                + ", floorislava klaar: " + floorIsLavaConfig.isReady()
                + ", treasurehunt klaar: " + treasureHuntConfig.isReady()
                + ", goldrush klaar: " + goldRushConfig.isReady()
                + ", hiddentarget klaar: " + hiddenTargetConfig.isReady()
                + ", ctf klaar: " + ctfConfig.isReady()
                + ", coins spawner klaar: " + coinSpawnConfig.isRunnable()
                + " (nexo-async event volgt mogelijk nog).");
    }

    @Override
    public void onDisable() {
        if (lobbyScoreboard != null) {
            lobbyScoreboard.stop();
        }
        if (randomEventService != null) {
            randomEventService.shutdown();
        }
        if (parkourGame != null && parkourGame.getState() != ParkourGame.State.IDLE) {
            parkourGame.stop();
        }
        if (tntTagGame != null && tntTagGame.getState() != TntTagGame.State.IDLE) {
            tntTagGame.stop();
        }
        if (tntRunGame != null && tntRunGame.getState() != TntRunGame.State.IDLE) {
            tntRunGame.stop();
        }
        if (raceGame != null && raceGame.getState() != RaceGame.State.IDLE) {
            raceGame.stop();
        }
        if (boatRaceGame != null && boatRaceGame.getState() != BoatRaceGame.State.IDLE) {
            boatRaceGame.stop();
        }
        if (bingoGame != null && bingoGame.getState() != BingoGame.State.IDLE) {
            bingoGame.stop();
        }
        if (kothGame != null && kothGame.getState() != KothGame.State.IDLE) {
            kothGame.stop();
        }
        if (floorIsLavaGame != null && floorIsLavaGame.getState() != FloorIsLavaGame.State.IDLE) {
            floorIsLavaGame.stop();
        }
        if (treasureHuntGame != null && treasureHuntGame.getState() != TreasureHuntGame.State.IDLE) {
            treasureHuntGame.stop();
        }
        if (goldRushGame != null && goldRushGame.getState() != GoldRushGame.State.IDLE) {
            goldRushGame.stop();
        }
        if (hiddenTargetGame != null && hiddenTargetGame.getState() != HiddenTargetGame.State.IDLE) {
            hiddenTargetGame.stop();
        }
        if (ctfGame != null && ctfGame.getState() != CtfGame.State.IDLE) {
            ctfGame.stop();
        }
        if (nexoCoinSpawner != null) {
            nexoCoinSpawner.shutdown();
        }
        if (dataManager != null) {
            dataManager.shutdown();
        }
        if (eventHistoryManager != null) {
            eventHistoryManager.save();
        }
        instance = null;
        getLogger().info("PointRush uitgeschakeld. Data opgeslagen.");
    }

    private void registerCommand(String name, Object handler) {
        PluginCommand command = Objects.requireNonNull(getCommand(name),
                "Command '" + name + "' staat niet in plugin.yml");
        if (handler instanceof org.bukkit.command.CommandExecutor exec) {
            command.setExecutor(exec);
        }
        if (handler instanceof org.bukkit.command.TabCompleter tab) {
            command.setTabCompleter(tab);
        }
    }

    public static PointRush getInstance() {
        return instance;
    }

    public UnifiedSettings getUnifiedSettings() {
        return unifiedSettings;
    }

    public LeaderboardCache getLeaderboardCache() {
        return leaderboardCache;
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public EventHistoryManager getEventHistoryManager() {
        return eventHistoryManager;
    }

    public LobbyScoreboard getLobbyScoreboard() {
        return lobbyScoreboard;
    }

    public ParkourGame getParkourGame() {
        return parkourGame;
    }

    public ParkourConfig getParkourConfig() {
        return parkourConfig;
    }

    public TntTagGame getTntTagGame() {
        return tntTagGame;
    }

    public TntTagConfig getTntTagConfig() {
        return tntTagConfig;
    }

    public TntRunGame getTntRunGame() {
        return tntRunGame;
    }

    public TntRunConfig getTntRunConfig() {
        return tntRunConfig;
    }

    public RaceGame getRaceGame() {
        return raceGame;
    }

    public RaceConfig getRaceConfig() {
        return raceConfig;
    }

    public BoatRaceGame getBoatRaceGame() {
        return boatRaceGame;
    }

    public BoatRaceConfig getBoatRaceConfig() {
        return boatRaceConfig;
    }

    public BingoGame getBingoGame() {
        return bingoGame;
    }

    public BingoConfig getBingoConfig() {
        return bingoConfig;
    }

    public KothGame getKothGame() {
        return kothGame;
    }

    public KothConfig getKothConfig() {
        return kothConfig;
    }

    public FloorIsLavaGame getFloorIsLavaGame() {
        return floorIsLavaGame;
    }

    public FloorIsLavaConfig getFloorIsLavaConfig() {
        return floorIsLavaConfig;
    }

    public TreasureHuntGame getTreasureHuntGame() {
        return treasureHuntGame;
    }

    public TreasureHuntConfig getTreasureHuntConfig() {
        return treasureHuntConfig;
    }

    public GoldRushGame getGoldRushGame() {
        return goldRushGame;
    }

    public GoldRushConfig getGoldRushConfig() {
        return goldRushConfig;
    }

    public HiddenTargetGame getHiddenTargetGame() {
        return hiddenTargetGame;
    }

    public HiddenTargetConfig getHiddenTargetConfig() {
        return hiddenTargetConfig;
    }

    public CtfGame getCtfGame() {
        return ctfGame;
    }

    public CtfConfig getCtfConfig() {
        return ctfConfig;
    }

    public RandomEventService getRandomEventService() {
        return randomEventService;
    }
}
