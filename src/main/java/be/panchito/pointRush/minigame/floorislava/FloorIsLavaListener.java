package be.panchito.pointRush.minigame.floorislava;

import be.panchito.pointRush.util.Messages;
import be.panchito.pointRush.util.SmallText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Egg;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Bukkit events voor Floor is Lava (bouw + knock + lava).
 */
public final class FloorIsLavaListener implements Listener {

    private final FloorIsLavaGame game;

    public FloorIsLavaListener(FloorIsLavaGame game) {
        this.game = game;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!game.isParticipant(player.getUniqueId())) return;
        if (event.getTo() == null) return;

        if (game.getState() == FloorIsLavaGame.State.STARTING) {
            // Alleen levende deelnemers bevriezen tijdens de countdown; geëlimineerde spectators
            // (tijdens het overwinningsscherm) moeten vrij kunnen rondvliegen.
            FloorIsLavaPlayerState ps = game.getPlayerState(player.getUniqueId());
            if (ps != null && ps.isAlive()
                    && (event.getFrom().getX() != event.getTo().getX()
                    || event.getFrom().getY() != event.getTo().getY()
                    || event.getFrom().getZ() != event.getTo().getZ())) {
                event.setTo(event.getFrom().clone());
            }
            return;
        }

        if (game.getState() == FloorIsLavaGame.State.RUNNING) {
            game.checkLava(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!game.isParticipant(victim.getUniqueId())) return;
        if (game.getState() != FloorIsLavaGame.State.RUNNING) {
            event.setCancelled(true);
            return;
        }

        FloorIsLavaPlayerState vState = game.getPlayerState(victim.getUniqueId());
        if (vState == null || !vState.isAlive()) {
            event.setCancelled(true);
            return;
        }

        Player attacker = resolveAttacker(event);
        if (attacker != null && game.isParticipant(attacker.getUniqueId())) {
            FloorIsLavaPlayerState aState = game.getPlayerState(attacker.getUniqueId());
            if (aState != null && aState.isAlive()) {
                event.setDamage(Math.min(event.getDamage(), 1.0));
                return;
            }
        }
        event.setCancelled(true);
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p) {
            return p;
        }
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!game.isParticipant(player.getUniqueId())) return;

        if (event.getCause() == EntityDamageEvent.DamageCause.LAVA
                || event.getCause() == EntityDamageEvent.DamageCause.HOT_FLOOR
                || event.getCause() == EntityDamageEvent.DamageCause.FIRE
                || event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK
                || event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
            if (game.getState() == FloorIsLavaGame.State.RUNNING) {
                game.eliminate(player);
            }
            return;
        }
        if (game.getState() == FloorIsLavaGame.State.RUNNING) {
            event.setCancelled(true);
        }
        player.setFireTicks(0);
        player.setFallDistance(0f);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!game.isParticipant(player.getUniqueId())) return;
        if (game.getState() != FloorIsLavaGame.State.RUNNING) {
            event.setCancelled(true);
            return;
        }
        FloorIsLavaPlayerState ps = game.getPlayerState(player.getUniqueId());
        if (ps == null || !ps.isAlive()) {
            event.setCancelled(true);
            return;
        }
        if (!game.canBuildAt(event.getBlock().getLocation())) {
            event.setCancelled(true);
            player.sendActionBar(Component.text(
                    SmallText.of("Bouw alleen binnen de arena!"), NamedTextColor.RED));
            return;
        }
        game.trackPlacedBlock(event.getBlock());
    }

    /**
     * Sneeuwballen/eieren ("knock-items") duwen geraakte spelers weg — vanilla doet dit niet voor
     * spelers, dus we passen het zelf toe. Sterkte komt uit de config (instelbaar via /floorislava setknock).
     */
    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (game.getState() != FloorIsLavaGame.State.RUNNING) return;
        Projectile projectile = event.getEntity();
        if (projectile instanceof EnderPearl) {
            handleSwitchBall(event, projectile);
            return;
        }
        if (!(projectile instanceof Snowball) && !(projectile instanceof Egg)) return;
        if (!(event.getHitEntity() instanceof Player victim)) return;

        FloorIsLavaPlayerState vState = game.getPlayerState(victim.getUniqueId());
        if (vState == null || !vState.isAlive()) return;

        // Alleen knock van een levende deelnemer; geen zelf-knock.
        if (!(projectile.getShooter() instanceof Player shooter)) return;
        if (shooter.getUniqueId().equals(victim.getUniqueId())) return;
        FloorIsLavaPlayerState aState = game.getPlayerState(shooter.getUniqueId());
        if (aState == null || !aState.isAlive()) return;

        FloorIsLavaConfig config = game.getConfig();

        // Duwrichting = horizontale vliegrichting van het projectiel; val terug op schutter→slachtoffer.
        Vector vel = projectile.getVelocity();
        Vector horiz = new Vector(vel.getX(), 0.0, vel.getZ());
        if (horiz.lengthSquared() < 1.0e-6) {
            horiz = victim.getLocation().toVector()
                    .subtract(shooter.getLocation().toVector()).setY(0.0);
        }
        if (horiz.lengthSquared() < 1.0e-6) return;
        horiz.normalize().multiply(config.getKnockHorizontal());

        Vector current = victim.getVelocity();
        victim.setVelocity(new Vector(
                current.getX() * 0.4 + horiz.getX(),
                Math.max(current.getY() * 0.4, 0.0) + config.getKnockVertical(),
                current.getZ() * 0.4 + horiz.getZ()));
        victim.setFallDistance(0f);
        victim.getWorld().playSound(victim.getLocation(),
                Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.8f, 1.2f);
    }

    /** Voorkomt dat weggegooide eieren kippen in de arena spawnen. */
    @EventHandler
    public void onEggThrow(PlayerEggThrowEvent event) {
        if (game.isParticipant(event.getPlayer().getUniqueId())) {
            event.setHatching(false);
        }
    }

    /** Wisselbal: gooi een ender pearl op iemand om van plek te wisselen. */
    private void handleSwitchBall(ProjectileHitEvent event, Projectile pearl) {
        if (!(pearl.getShooter() instanceof Player thrower)) return;
        FloorIsLavaPlayerState aState = game.getPlayerState(thrower.getUniqueId());
        if (aState == null || !aState.isAlive()) return;

        if (!(event.getHitEntity() instanceof Player target)
                || target.getUniqueId().equals(thrower.getUniqueId())) {
            thrower.sendActionBar(Messages.warn("Wisselbal miste — geen speler geraakt."));
            return;
        }
        FloorIsLavaPlayerState tState = game.getPlayerState(target.getUniqueId());
        if (tState == null || !tState.isAlive()) {
            thrower.sendActionBar(Messages.warn("Wisselbal miste — geen speler geraakt."));
            return;
        }

        Location throwerLoc = thrower.getLocation().clone();
        Location targetLoc = target.getLocation().clone();
        thrower.teleport(targetLoc);
        target.teleport(throwerLoc);
        thrower.setFallDistance(0f);
        target.setFallDistance(0f);
        targetLoc.getWorld().playSound(targetLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        throwerLoc.getWorld().playSound(throwerLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        thrower.sendActionBar(Messages.success("Gewisseld!"));
        target.sendActionBar(Messages.warn("Je bent van plek gewisseld!"));
    }

    /** Annuleert de standaard ender-pearl teleport; de wisselbal regelt de plekwissel zelf. */
    @EventHandler
    public void onPearlTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                && game.isParticipant(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Grijphaak: trekt de speler naar waar de haak landt; 1 gebruik. */
    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (game.getState() != FloorIsLavaGame.State.RUNNING) return;
        if (!game.isParticipant(player.getUniqueId())) return;
        FloorIsLavaPlayerState ps = game.getPlayerState(player.getUniqueId());
        if (ps == null || !ps.isAlive()) return;

        PlayerFishEvent.State state = event.getState();
        if (state != PlayerFishEvent.State.IN_GROUND
                && state != PlayerFishEvent.State.CAUGHT_ENTITY
                && state != PlayerFishEvent.State.REEL_IN) {
            return;
        }

        EquipmentSlot hand = event.getHand() != null ? event.getHand() : EquipmentSlot.HAND;
        ItemStack rod = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        if (rod == null || rod.getType() != Material.FISHING_ROD) return;

        Location hookLoc = event.getHook().getLocation();
        Location playerLoc = player.getLocation();
        if (hookLoc.getWorld() != playerLoc.getWorld()) return;

        Vector dir = hookLoc.toVector().subtract(playerLoc.toVector());
        double dist = dir.length();
        if (dist < 1.0) return;
        Vector vel = dir.normalize().multiply(Math.min(0.85 + dist * 0.07, 2.2));
        vel.setY(Math.max(vel.getY(), 0.0) + 0.42);
        player.setVelocity(vel);
        player.setFallDistance(0f);
        player.playSound(playerLoc, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0f, 1.4f);

        // 1 gebruik: verwijder de gebruikte hengel.
        if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(null);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }

    /** Kikkersprong-drankje: jump boost voor enkele seconden. */
    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (game.getState() != FloorIsLavaGame.State.RUNNING) return;
        if (!game.isParticipant(player.getUniqueId())) return;
        FloorIsLavaPlayerState ps = game.getPlayerState(player.getUniqueId());
        if (ps == null || !ps.isAlive()) return;
        if (event.getItem().getType() != Material.POTION) return;

        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,
                FloorIsLavaKit.FROG_JUMP_SECONDS * 20, FloorIsLavaKit.FROG_JUMP_AMPLIFIER,
                false, true, true));
        player.playSound(player.getLocation(), Sound.ENTITY_FROG_LONG_JUMP, 1.0f, 1.0f);
        player.sendActionBar(Messages.success("Kikkersprong! Jump boost voor "
                + FloorIsLavaKit.FROG_JUMP_SECONDS + "s."));

        // Verwijder de lege glazen fles die na het drinken overblijft.
        game.getPlugin().getServer().getScheduler().runTask(game.getPlugin(),
                () -> player.getInventory().removeItem(new ItemStack(Material.GLASS_BOTTLE)));
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (game.isParticipant(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (game.isParticipant(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        if (game.isParticipant(p.getUniqueId()) && p.getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player p && game.isParticipant(p.getUniqueId())) {
            event.setCancelled(true);
            p.setFoodLevel(20);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (game.isParticipant(player.getUniqueId())) {
            game.removeParticipant(player, false);
        }
    }
}
