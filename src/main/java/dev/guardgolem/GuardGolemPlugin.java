package dev.guardgolem;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class GuardGolemPlugin extends JavaPlugin implements Listener {

    private GuardGolems golems;

    @Override
    public void onEnable() {
        golems = new GuardGolems(this);

        // Remove first so /reload never throws "duplicate recipe".
        Bukkit.removeRecipe(golems.recipeKey());
        Bukkit.addRecipe(golems.createRecipe());

        ProtectionListener protection = new ProtectionListener(golems);
        getServer().getPluginManager().registerEvents(new SpawnEggListener(golems), this);
        getServer().getPluginManager().registerEvents(protection, this);
        getCommand("guardgolem").setExecutor((sender, command, label, args) -> {
            if (args.length == 1 && args[0].equalsIgnoreCase("debug")) {
                boolean on = protection.toggleDebug();
                sender.sendMessage("GuardGolem debug: " + (on ? "ON" : "OFF"));
                return true;
            }
            return false;
        });
        getServer().getPluginManager().registerEvents(this, this);

        // Re-asserts golem targets every 10 ticks (0.5 s) and expires hostility.
        getServer().getScheduler().runTaskTimer(this, protection::tick, 10L, 10L);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.discoverRecipe(golems.recipeKey());
        }
    }

    @Override
    public void onDisable() {
        if (golems != null) {
            Bukkit.removeRecipe(golems.recipeKey());
        }
    }

    /** Unlock the recipe in the recipe book so it is easy to find in Survival. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().discoverRecipe(golems.recipeKey());
    }
}
