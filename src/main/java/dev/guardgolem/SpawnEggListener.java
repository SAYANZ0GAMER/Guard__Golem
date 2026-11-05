package dev.guardgolem;

import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.RayTraceResult;

/**
 * Intercepts the tagged egg so the vanilla (untagged, ownerless) Iron Golem is never
 * spawned by it, and spawns an owned Guard Golem instead.
 */
public final class SpawnEggListener implements Listener {

    private final GuardGolems golems;

    public SpawnEggListener(GuardGolems golems) {
        this.golems = golems;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        EquipmentSlot hand = event.getHand();
        if (hand == null || !golems.isEgg(event.getItem())) {
            return;
        }
        Action action = event.getAction();
        Player player = event.getPlayer();
        // IMPORTANT: read this BEFORE calling setCancelled(true). Cancelling the event
        // automatically sets useItemInHand to DENY, which would make every use look "denied".
        boolean denied = event.useItemInHand() == Event.Result.DENY;

        if (action == Action.RIGHT_CLICK_BLOCK) {
            Block clicked = event.getClickedBlock();
            if (clicked == null) {
                return;
            }
            // Let chests, doors, etc. handle the click like vanilla does.
            if (clicked.getType().isInteractable() && !player.isSneaking()
                    && clicked.getType() != Material.SPAWNER) {
                return;
            }
            event.setCancelled(true);
            if (clicked.getType() == Material.SPAWNER) {
                return; // never turn a spawner into an Iron Golem spawner
            }
            if (denied) {
                return; // another plugin (claims) denied it
            }
            Block target = clicked.isReplaceable() ? clicked : clicked.getRelative(event.getBlockFace());
            trySpawn(player, hand, target);

        } else if (action == Action.RIGHT_CLICK_AIR) {
            // Vanilla eggs also work on the surface of liquids. Cancel the vanilla action
            // (it would create an ownerless golem) and handle the liquid case ourselves.
            event.setCancelled(true);
            if (denied) {
                return;
            }
            RayTraceResult hit = player.rayTraceBlocks(5.0, FluidCollisionMode.SOURCE_ONLY);
            if (hit != null && hit.getHitBlock() != null && hit.getHitBlock().isLiquid()) {
                trySpawn(player, hand, hit.getHitBlock());
            }
        }
    }

    private void trySpawn(Player player, EquipmentSlot hand, Block target) {
        if (!target.isPassable() && !target.isLiquid()) {
            return;
        }
        Location loc = target.getLocation().add(0.5, 0.0, 0.5);
        golems.spawn(player, loc);

        if (player.getGameMode() != GameMode.CREATIVE) {
            PlayerInventory inv = player.getInventory();
            ItemStack stack = inv.getItem(hand);
            if (stack != null) {
                stack.setAmount(stack.getAmount() - 1);
                inv.setItem(hand, stack.getAmount() > 0 ? stack : null);
            }
        }
    }

    /** Stops the egg from doing vanilla things when used on an entity. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        ItemStack held = event.getPlayer().getInventory().getItem(event.getHand());
        if (golems.isEgg(held)) {
            event.setCancelled(true);
        }
    }

    /** A dispenser would spawn an ownerless vanilla golem, so it is blocked. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDispense(BlockDispenseEvent event) {
        if (golems.isEgg(event.getItem())) {
            event.setCancelled(true);
        }
    }
}
