package me.sellaxe;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class SellAxePlugin extends JavaPlugin {

    private PriceProvider prices;
    private SellAxeItem axe;
    private volatile Economy economy;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.prices = new PriceProvider(this);
        this.axe = new SellAxeItem(this);

        getServer().getPluginManager().registerEvents(new SellListener(this), this);

        SellAxeCommand command = new SellAxeCommand(this);
        PluginCommand pc = getCommand("sellaxe");
        if (pc != null) {
            pc.setExecutor(command);
            pc.setTabCompleter(command);
        }

        getLogger().info("SellAxe enabled.");
    }

    /** Looked up lazily, because the Vault economy provider can register after us. */
    public Economy economy() {
        Economy eco = this.economy;
        if (eco == null) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                eco = rsp.getProvider();
                this.economy = eco;
            }
        }
        return eco;
    }

    public void reload() {
        reloadConfig();
        this.economy = null;
        this.prices.reset();
    }

    public PriceProvider prices() {
        return prices;
    }

    public SellAxeItem axe() {
        return axe;
    }
}
