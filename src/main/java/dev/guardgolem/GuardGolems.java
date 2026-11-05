package dev.guardgolem;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Central helper: item/entity tagging, recipe and spawning.
 *
 * A Guard Golem is a vanilla IronGolem carrying a persistent-data "owner" tag.
 * That is what makes it a distinct kind of mob for this plugin while the client
 * still renders the unmodified vanilla Iron Golem model, animations and textures.
 * Untagged (normal) Iron Golems are never touched.
 */
public final class GuardGolems {

    public static final String MOB_NAME = "Guard Golem";
    public static final String EGG_NAME = "Guard Golem Spawn Egg";

    private final NamespacedKey ownerKey;
    private final NamespacedKey eggKey;
    private final NamespacedKey recipeKey;

    public GuardGolems(Plugin plugin) {
        this.ownerKey = new NamespacedKey(plugin, "guard_golem_owner");
        this.eggKey = new NamespacedKey(plugin, "guard_golem_egg");
        this.recipeKey = new NamespacedKey(plugin, "guard_golem_spawn_egg");
    }

    public NamespacedKey recipeKey() {
        return recipeKey;
    }

    // ------------------------------------------------------------------ item

    /** Vanilla Iron Golem spawn egg (same item model/texture/colours), renamed and tagged. */
    public ItemStack createEgg() {
        ItemStack egg = new ItemStack(Material.IRON_GOLEM_SPAWN_EGG);
        egg.editMeta(meta -> {
            meta.itemName(Component.text(EGG_NAME));
            meta.getPersistentDataContainer().set(eggKey, PersistentDataType.BYTE, (byte) 1);
        });
        return egg;
    }

    public boolean isEgg(ItemStack item) {
        if (item == null || item.getType() != Material.IRON_GOLEM_SPAWN_EGG || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(eggKey, PersistentDataType.BYTE);
    }

    public CraftingRecipe createRecipe() {
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, createEgg());
        recipe.shape(
                " P ",
                "III",
                " I ");
        recipe.setIngredient('P', Material.CARVED_PUMPKIN);
        recipe.setIngredient('I', Material.IRON_BLOCK);
        return recipe;
    }

    // ---------------------------------------------------------------- entity

    public boolean isGuard(Entity entity) {
        return entity instanceof IronGolem
                && entity.getPersistentDataContainer().has(ownerKey, PersistentDataType.STRING);
    }

    /** UUID of the owner, or null if this is not a Guard Golem. */
    public UUID ownerOf(Entity entity) {
        if (!(entity instanceof IronGolem)) {
            return null;
        }
        String raw = entity.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public boolean isOwnedBy(Entity entity, Player player) {
        UUID owner = ownerOf(entity);
        return owner != null && owner.equals(player.getUniqueId());
    }

    public IronGolem spawn(Player owner, Location location) {
        return location.getWorld().spawn(location, IronGolem.class, golem -> {
            golem.customName(Component.text(MOB_NAME));
            golem.setCustomNameVisible(false); // like a vanilla name-tagged mob: shows when you look at it
            // false on purpose: vanilla "player created" golems refuse to target players,
            // which would break defending against other players / retaliating on the owner.
            // Targeting the owner is still blocked separately by ProtectionListener.
            golem.setPlayerCreated(false);
            golem.setPersistent(true);
            golem.setRemoveWhenFarAway(false);
            golem.getPersistentDataContainer()
                    .set(ownerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
        });
    }
}
