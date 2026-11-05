package dev.guardgolem;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Bodyguard behaviour. Only entities tagged as Guard Golems are affected;
 * vanilla Iron Golems are ignored completely.
 */
public final class ProtectionListener implements Listener {

    private static final long HOSTILE_MS = 30_000L;  // how long the golem stays angry at its owner
    private static final long DEFENSE_MS = 10_000L;  // how long it keeps chasing an attacker
    private static final double SEARCH_RADIUS = 64.0; // golems this close to the owner react
    private static final double MAX_CHASE = 40.0;

    private record Defense(UUID target, long expiry) { }

    private final GuardGolems golems;
    /** golem UUID -> time until which it is hostile to its owner */
    private final Map<UUID, Long> hostile = new HashMap<>();
    /** golem UUID -> current attacker being punished */
    private final Map<UUID, Defense> defense = new HashMap<>();
    private boolean debug;

    public boolean toggleDebug() {
        debug = !debug;
        return debug;
    }

    private void dbg(Player p, String msg) {
        if (debug && p != null) {
            p.sendMessage(Component.text("[GuardGolem] " + msg));
        }
    }

    public ProtectionListener(GuardGolems golems) {
        this.golems = golems;
    }

    // ------------------------------------------------------------ damage events

    /** A golem must never hurt its owner unless the owner angered it. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGolemHurtsOwner(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player owner
                && golems.isOwnedBy(event.getDamager(), owner)
                && !isHostile(event.getDamager().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        LivingEntity attacker = resolveAttacker(event.getDamager());
        if (attacker == null) {
            return;
        }
        Entity victim = event.getEntity();

        if (victim instanceof Player owner) {
            defendOwner(owner, attacker);
        } else if (golems.isGuard(victim) && attacker instanceof Player p
                && golems.isOwnedBy(victim, p)) {
            // 5. Owner hostility: the owner hit their own golem.
            IronGolem golem = (IronGolem) victim;
            hostile.put(golem.getUniqueId(), System.currentTimeMillis() + HOSTILE_MS);
            defense.remove(golem.getUniqueId());
            if (canBeTargeted(p)) {
                golem.setTarget(p);
                dbg(p, "You hit your Guard Golem: it is hostile to you for 30s.");
            } else {
                dbg(p, "You hit your Guard Golem, but you are in Creative/Spectator: golems ignore you.");
            }
        }
    }

    private void defendOwner(Player owner, LivingEntity attacker) {
        if (attacker.getUniqueId().equals(owner.getUniqueId())) {
            return; // self damage
        }
        if (golems.isOwnedBy(attacker, owner)) {
            return; // an angry golem of this owner; hostility rules handle it
        }
        if (attacker instanceof Player p && !canBeTargeted(p)) {
            dbg(owner, "Attacker " + p.getName() + " is in Creative/Spectator: golems cannot target them.");
            return;
        }
        int ordered = 0;
        long expiry = System.currentTimeMillis() + DEFENSE_MS;
        for (IronGolem golem : owner.getWorld()
                .getNearbyEntitiesByType(IronGolem.class, owner.getLocation(), SEARCH_RADIUS)) {
            if (!golems.isOwnedBy(golem, owner) || isHostile(golem.getUniqueId())) {
                continue;
            }
            golem.setTarget(attacker);
            defense.put(golem.getUniqueId(), new Defense(attacker.getUniqueId(), expiry));
            ordered++;
        }
        dbg(owner, "Hit by " + attacker.getType() + ": " + ordered + " Guard Golem(s) ordered to attack.");
    }

    private LivingEntity resolveAttacker(Entity damager) {
        if (damager instanceof LivingEntity living) {
            return living;
        }
        if (damager instanceof Projectile projectile
                && projectile.getShooter() instanceof LivingEntity shooter) {
            return shooter;
        }
        if (damager instanceof EvokerFangs fangs) {
            return fangs.getOwner();
        }
        return null;
    }

    // ------------------------------------------------------------ targeting gate

    /** The golem may only target its owner while hostile. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        LivingEntity target = event.getTarget();
        if (target == null || !golems.isGuard(event.getEntity())) {
            return;
        }
        UUID owner = golems.ownerOf(event.getEntity());
        if (target.getUniqueId().equals(owner) && !isHostile(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ housekeeping

    @EventHandler
    public void onOwnerDeath(PlayerDeathEvent event) {
        calmGolemsOf(event.getPlayer());
    }

    @EventHandler
    public void onOwnerQuit(PlayerQuitEvent event) {
        calmGolemsOf(event.getPlayer());
    }

    private void calmGolemsOf(Player owner) {
        Iterator<UUID> it = hostile.keySet().iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            Entity e = Bukkit.getEntity(id);
            if (e instanceof IronGolem g && golems.isOwnedBy(g, owner)) {
                it.remove();
                if (g.getTarget() != null && g.getTarget().getUniqueId().equals(owner.getUniqueId())) {
                    g.setTarget(null);
                }
            }
        }
    }

    /** Runs every 10 ticks: keeps targets alive (vanilla AI can clear them) and expires them. */
    public void tick() {
        long now = System.currentTimeMillis();

        Iterator<Map.Entry<UUID, Long>> hit = hostile.entrySet().iterator();
        while (hit.hasNext()) {
            Map.Entry<UUID, Long> entry = hit.next();
            if (!(Bukkit.getEntity(entry.getKey()) instanceof IronGolem golem) || golem.isDead()) {
                hit.remove();
                continue;
            }
            UUID ownerId = golems.ownerOf(golem);
            Player owner = ownerId == null ? null : Bukkit.getPlayer(ownerId);
            if (owner == null || now > entry.getValue()) {
                hit.remove();
                LivingEntity t = golem.getTarget();
                if (t != null && t.getUniqueId().equals(ownerId)) {
                    golem.setTarget(null);
                }
                continue;
            }
            if (canChase(golem, owner) && !owner.equals(golem.getTarget())) {
                golem.setTarget(owner);
            }
        }

        Iterator<Map.Entry<UUID, Defense>> dit = defense.entrySet().iterator();
        while (dit.hasNext()) {
            Map.Entry<UUID, Defense> entry = dit.next();
            Defense d = entry.getValue();
            if (!(Bukkit.getEntity(entry.getKey()) instanceof IronGolem golem)
                    || golem.isDead() || isHostile(entry.getKey())) {
                dit.remove();
                continue;
            }
            Entity t = Bukkit.getEntity(d.target());
            boolean valid = t instanceof LivingEntity le && !le.isDead()
                    && le.getWorld().equals(golem.getWorld())
                    && le.getLocation().distance(golem.getLocation()) <= MAX_CHASE
                    && !(le instanceof Player p && !canBeTargeted(p));
            if (now > d.expiry() || !valid) {
                dit.remove();
                LivingEntity cur = golem.getTarget();
                if (cur != null && cur.getUniqueId().equals(d.target())) {
                    golem.setTarget(null);
                }
                continue;
            }
            if (!t.equals(golem.getTarget())) {
                golem.setTarget((LivingEntity) t);
            }
        }
    }

    // ------------------------------------------------------------ helpers

    private boolean isHostile(UUID golemId) {
        Long until = hostile.get(golemId);
        return until != null && until > System.currentTimeMillis();
    }

    private boolean canBeTargeted(Player p) {
        GameMode gm = p.getGameMode();
        return p.isOnline() && !p.isDead() && (gm == GameMode.SURVIVAL || gm == GameMode.ADVENTURE);
    }

    private boolean canChase(IronGolem golem, Player owner) {
        return canBeTargeted(owner)
                && owner.getWorld().equals(golem.getWorld())
                && owner.getLocation().distance(golem.getLocation()) <= MAX_CHASE;
    }
}
