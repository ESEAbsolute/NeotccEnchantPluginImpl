package icu.eseabs0.ntccenchants;

import icu.eseabs0.ntccenchants.impl.SonicEnchantmentImpl;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class NeotccEnchantPluginImpl extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic
        Bukkit.getPluginManager().registerEvents(new SonicEnchantmentImpl(this), this);

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
