package icu.eseabs0.ntccenchants.listeners;

import icu.eseabs0.ntccenchants.NeotccEnchantPluginImpl;
import icu.eseabs0.ntccenchants.impl.SonicEnchantmentImpl;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;

public class EntityShootBowEventListener implements Listener {
    private final SonicEnchantmentImpl sonicEnchantment;

    public EntityShootBowEventListener(NeotccEnchantPluginImpl plugin) {
        this.sonicEnchantment = new SonicEnchantmentImpl(plugin);
    }


    @EventHandler(priority = EventPriority.LOW)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        ItemStack bow = event.getBow();
        if (bow == null || bow.getType() != Material.CROSSBOW) {
            return;
        }

        boolean triggered = sonicEnchantment.tryTrigger(player, bow);

        if (triggered) {
            event.setCancelled(true);
        }
    }
}
