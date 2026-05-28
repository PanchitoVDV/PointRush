package be.panchito.pointRush.shop;

import be.panchito.pointRush.PointRush;
import be.panchito.pointRush.minigame.parkour.ParkourPlayerState;
import be.panchito.pointRush.minigame.race.RacePlayerState;
import be.panchito.pointRush.minigame.tntrun.TntRunPlayerState;
import be.panchito.pointRush.minigame.tnttag.TntTagPlayerState;
import be.panchito.pointRush.storage.mongo.MongoPlayerCoinRepository;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Verbruikt gekochte boosts bij join van minigames (Mongo {@code shop_charges}). */
public final class MinigameShopHook {

    private MinigameShopHook() {
    }

    public static void applyParkourJoin(PointRush plugin, Player player, ParkourPlayerState ps) {
        MongoPlayerCoinRepository repo = repo(plugin);
        if (repo == null) {
            return;
        }
        UUID u = player.getUniqueId();
        if (repo.tryConsumeShopCharge(u, ShopPerks.PARKOUR_TAILWIND)) {
            ps.setShopParkourSpeed(true);
        }
        if (repo.tryConsumeShopCharge(u, ShopPerks.PARKOUR_SPRINGHEEL)) {
            ps.setShopParkourJump(true);
        }
        if (repo.tryConsumeShopCharge(u, ShopPerks.PARKOUR_CLOUDSTEP)) {
            ps.setShopParkourCloud(true);
        }
    }

    public static void applyTagJoin(PointRush plugin, Player player, TntTagPlayerState ps) {
        MongoPlayerCoinRepository repo = repo(plugin);
        if (repo == null) {
            return;
        }
        UUID u = player.getUniqueId();
        if (repo.tryConsumeShopCharge(u, ShopPerks.TAG_GHOST_PASS)) {
            ps.setShopTagGhostPasses(1);
        }
        if (repo.tryConsumeShopCharge(u, ShopPerks.TAG_IRON_RUSH)) {
            ps.setShopTagIronRush(true);
        }
        if (repo.tryConsumeShopCharge(u, ShopPerks.TAG_SECOND_WIND)) {
            ps.setShopTagSecondWind(true);
        }
    }

    public static void applyTntRunJoin(PointRush plugin, Player player, TntRunPlayerState ps) {
        MongoPlayerCoinRepository repo = repo(plugin);
        if (repo == null) {
            return;
        }
        UUID u = player.getUniqueId();
        if (repo.tryConsumeShopCharge(u, ShopPerks.RUN_GRIP_SOLES)) {
            ps.setShopRunDecayBonusTicks(6);
        }
        if (repo.tryConsumeShopCharge(u, ShopPerks.RUN_FEATHER_STEP)) {
            ps.setShopRunFeather(true);
        }
        if (repo.tryConsumeShopCharge(u, ShopPerks.RUN_STEADY_CORE)) {
            ps.setShopRunResist(true);
        }
    }

    public static void applyRaceJoin(PointRush plugin, Player player, RacePlayerState ps) {
        MongoPlayerCoinRepository repo = repo(plugin);
        if (repo == null) {
            return;
        }
        UUID u = player.getUniqueId();
        if (repo.tryConsumeShopCharge(u, ShopPerks.RACE_NITRO_GLUG)) {
            ps.setShopRaceNitro(true);
        }
        if (repo.tryConsumeShopCharge(u, ShopPerks.RACE_ROLL_CAGE)) {
            ps.setShopRaceCage(true);
        }
        if (repo.tryConsumeShopCharge(u, ShopPerks.RACE_PIT_CREW)) {
            ps.setShopRacePit(true);
        }
    }

    private static MongoPlayerCoinRepository repo(PointRush plugin) {
        return plugin.getDataManager().getPlayerCoinRepository();
    }
}
