package be.panchito.pointRush.minigame.gadgets;

import be.panchito.pointRush.PointRush;
import be.panchito.pointRush.minigame.parkour.ParkourGame;
import be.panchito.pointRush.minigame.parkour.ParkourPlayerState;
import be.panchito.pointRush.minigame.koth.KothGame;
import be.panchito.pointRush.minigame.koth.KothPlayerState;
import be.panchito.pointRush.minigame.tntrun.TntRunGame;
import be.panchito.pointRush.minigame.tntrun.TntRunPlayerState;
import be.panchito.pointRush.minigame.tnttag.TntTagGame;
import be.panchito.pointRush.minigame.tnttag.TntTagPlayerState;
import be.panchito.pointRush.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rush-event items: cooldowns, effecten en verbruik voor parkour / Tag / Run.
 */
public final class MinigameGadgetEngine {

    public static final long GADGET_COOLDOWN_MS = 2500L;

    public enum Result {
        /** Item is not one of our gadgets; listener should apply normal rules. */
        NOT_OURS,
        /** Rush-item afgehandeld (succes of geweigerd): interact annuleren. */
        HANDLED
    }

    private MinigameGadgetEngine() {
    }

    private static EquipmentSlot handOrMain(EquipmentSlot hand) {
        return hand == null ? EquipmentSlot.HAND : hand;
    }

    public static Result tryParkour(PointRush plugin, ParkourGame game, Player player,
                                    ItemStack stack, EquipmentSlot hand) {
        MinigameGadgetType type = MinigameGadgetItems.parse(plugin, stack);
        if (type == null || !type.allowedIn(MinigameGadgetMode.PARKOUR)) {
            return Result.NOT_OURS;
        }
        EquipmentSlot slot = handOrMain(hand);
        ParkourPlayerState me = game.getPlayerState(player.getUniqueId());
        if (me == null) return Result.HANDLED;

        if (game.getState() == ParkourGame.State.STARTING) {
            player.sendMessage(Messages.warn("Items werken pas als de run begonnen is."));
            return Result.HANDLED;
        }
        if (game.getState() != ParkourGame.State.RUNNING || me.isFinished()) {
            player.sendMessage(Messages.warn("Zo kun je dit item nu niet gebruiken."));
            return Result.HANDLED;
        }

        List<Player> pool = new ArrayList<>();
        for (UUID id : game.getAllPlayerStates().stream().map(ParkourPlayerState::getUuid).toList()) {
            if (id.equals(player.getUniqueId())) continue;
            ParkourPlayerState ps = game.getPlayerState(id);
            Player p = Bukkit.getPlayer(id);
            if (p != null && ps != null && !ps.isFinished()) {
                pool.add(p);
            }
        }

        return applyGadget(plugin, game.getGadgetCooldownMap(), player, type, slot, pool);
    }

    public static Result tryTntTag(PointRush plugin, TntTagGame game, Player player,
                                   ItemStack stack, EquipmentSlot hand) {
        MinigameGadgetType type = MinigameGadgetItems.parse(plugin, stack);
        if (type == null || !type.allowedIn(MinigameGadgetMode.TNT_TAG)) {
            return Result.NOT_OURS;
        }
        EquipmentSlot slot = handOrMain(hand);
        TntTagPlayerState me = game.getPlayerState(player.getUniqueId());
        if (me == null) return Result.HANDLED;

        if (game.getState() != TntTagGame.State.RUNNING || !me.isAlive()) {
            player.sendMessage(Messages.warn("Alleen tijdens een actieve ronde."));
            return Result.HANDLED;
        }

        List<Player> pool = new ArrayList<>();
        for (TntTagPlayerState ps : game.getAllPlayerStates()) {
            if (!ps.isAlive() || ps.getUuid().equals(player.getUniqueId())) continue;
            Player p = Bukkit.getPlayer(ps.getUuid());
            if (p != null) pool.add(p);
        }

        return applyGadget(plugin, game.getGadgetCooldownMap(), player, type, slot, pool);
    }

    public static Result tryKoth(PointRush plugin, KothGame game, Player player,
                                 ItemStack stack, EquipmentSlot hand) {
        MinigameGadgetType type = MinigameGadgetItems.parse(plugin, stack);
        if (type == null || !type.allowedIn(MinigameGadgetMode.KOTH)) {
            return Result.NOT_OURS;
        }
        EquipmentSlot slot = handOrMain(hand);
        KothPlayerState me = game.getPlayerState(player.getUniqueId());
        if (me == null) return Result.HANDLED;

        if (game.getState() != KothGame.State.RUNNING || !me.isAlive()) {
            player.sendMessage(Messages.warn("Alleen tijdens een actieve ronde."));
            return Result.HANDLED;
        }

        List<Player> pool = new ArrayList<>();
        for (KothPlayerState ps : game.getAllPlayerStates()) {
            if (!ps.isAlive() || ps.getUuid().equals(player.getUniqueId())) continue;
            Player p = Bukkit.getPlayer(ps.getUuid());
            if (p != null) pool.add(p);
        }

        return applyGadget(plugin, game.getGadgetCooldownMap(), player, type, slot, pool);
    }

    public static Result tryTntRun(PointRush plugin, TntRunGame game, Player player,
                                   ItemStack stack, EquipmentSlot hand) {
        MinigameGadgetType type = MinigameGadgetItems.parse(plugin, stack);
        if (type == null || !type.allowedIn(MinigameGadgetMode.TNT_RUN)) {
            return Result.NOT_OURS;
        }
        EquipmentSlot slot = handOrMain(hand);
        TntRunPlayerState me = game.getPlayerState(player.getUniqueId());
        if (me == null) return Result.HANDLED;

        if (game.getState() == TntRunGame.State.STARTING) {
            player.sendMessage(Messages.warn("Items werken pas als de run begonnen is."));
            return Result.HANDLED;
        }
        if (game.getState() != TntRunGame.State.RUNNING || !me.isAlive()) {
            player.sendMessage(Messages.warn("Zo kun je dit item nu niet gebruiken."));
            return Result.HANDLED;
        }

        List<Player> pool = new ArrayList<>();
        for (TntRunPlayerState ps : game.getAllPlayerStates()) {
            if (!ps.isAlive() || ps.getUuid().equals(player.getUniqueId())) continue;
            Player p = Bukkit.getPlayer(ps.getUuid());
            if (p != null) pool.add(p);
        }

        return applyGadget(plugin, game.getGadgetCooldownMap(), player, type, slot, pool);
    }

    private static Result applyGadget(PointRush plugin, Map<UUID, Long> cooldownMap,
                                      Player user, MinigameGadgetType type, EquipmentSlot slot,
                                      List<Player> victimPool) {
        long now = System.currentTimeMillis();
        Long until = cooldownMap.get(user.getUniqueId());
        if (until != null && until > now) {
            user.sendMessage(Messages.warn("Nog even wachten voor je volgende Rush-item."));
            return Result.HANDLED;
        }

        if (type.isSelfOnly()) {
            applyTurbo(plugin, user);
            consumeOne(plugin, user, slot, type);
            cooldownMap.put(user.getUniqueId(), now + GADGET_COOLDOWN_MS);
            user.playSound(user.getLocation(), Sound.ENTITY_GENERIC_DRINK, 0.9f, 1.2f);
            return Result.HANDLED;
        }

        Player victim = GadgetTargetPicker.pickVictim(user, victimPool);
        if (victim == null) {
            user.sendMessage(Messages.warn("Niemand voor je neus."));
            user.playSound(user.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.1f);
            return Result.HANDLED;
        }

        switch (type) {
            case TWIST_ROD -> applyTwist(plugin, victim);
            case INK_BLOB -> victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 7, 0, false, true, true));
            case GOO_BALL -> victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 4, 3, false, true, true));
            case SPARK_ROD -> applySpark(plugin, victim);
            case CHAOS_FRUIT -> applyChaos(plugin, victim);
            default -> {
                return Result.HANDLED;
            }
        }

        consumeOne(plugin, user, slot, type);
        cooldownMap.put(user.getUniqueId(), now + GADGET_COOLDOWN_MS);

        user.playSound(user.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.4f);
        victim.playSound(victim.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.9f, 0.7f);

        user.sendActionBar(Messages.success("Getroffen: " + victim.getName()));
        victim.sendMessage(Messages.warn(user.getName() + " heeft een Rush-truc op je gebruikt."));

        return Result.HANDLED;
    }

    private static void applyTurbo(PointRush plugin, Player user) {
        user.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 5, 1, false, false, true));
    }

    private static void applyTwist(PointRush plugin, Player victim) {
        spinPlayer(plugin, victim, 14, 26f);
        victim.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 20 * 3, 0, false, true, true));
        Location l = victim.getLocation();
        victim.setVelocity(l.getDirection().multiply(-0.35).setY(0.15));
    }

    private static void applyChaos(PointRush plugin, Player victim) {
        spinPlayer(plugin, victim, 18, 20f);
        victim.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 20 * 6, 0, false, true, true));
    }

    private static void applySpark(PointRush plugin, Player victim) {
        Location strike = victim.getLocation().clone().add(0, 0.05, 0);
        victim.getWorld().strikeLightningEffect(strike);
        victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20 * 5, 1, false, true, true));
    }

    private static void spinPlayer(PointRush plugin, Player victim, int ticks, float yawStep) {
        new BukkitRunnable() {
            int n = 0;

            @Override
            public void run() {
                if (!victim.isOnline() || n >= ticks) {
                    cancel();
                    return;
                }
                Location loc = victim.getLocation();
                loc.setYaw(loc.getYaw() + yawStep);
                victim.teleport(loc);
                n++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private static void consumeOne(PointRush plugin, Player player, EquipmentSlot slot, MinigameGadgetType expected) {
        ItemStack current = player.getInventory().getItem(slot);
        MinigameGadgetType t = MinigameGadgetItems.parse(plugin, current);
        if (t != expected || current == null) return;
        int amt = current.getAmount();
        if (amt <= 1) {
            player.getInventory().setItem(slot, null);
        } else {
            current.setAmount(amt - 1);
        }
    }
}
