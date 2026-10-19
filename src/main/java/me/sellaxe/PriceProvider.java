package me.sellaxe;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;

/**
 * Gets sell prices from EconomyShopGUI through its public API (EconomyShopGUIHook).
 * It is done with reflection so SellAxe compiles without the EconomyShopGUI jar
 * and keeps working across EconomyShopGUI / EconomyShopGUI-Premium.
 */
public final class PriceProvider {

    private static final String HOOK_CLASS = "me.gypopo.economyshopgui.api.EconomyShopGUIHook";

    private final SellAxePlugin plugin;

    private volatile boolean ready;
    private volatile boolean failed;
    private volatile boolean warnedInvoke;

    private volatile Method getShopItemPlayer; // getShopItem(Player, ItemStack)
    private volatile Method getShopItem;       // getShopItem(ItemStack)
    private volatile Method sellPrice;         // getItemSellPrice(ShopItem, ItemStack[, Player])

    public PriceProvider(SellAxePlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void reset() {
        ready = false;
        failed = false;
        warnedInvoke = false;
        getShopItemPlayer = null;
        getShopItem = null;
        sellPrice = null;
    }

    /**
     * Sell price of ONE item of this type, or -1 if it can't be sold.
     * Always asked per single item, then multiplied by the amount, so it doesn't matter
     * whether the API takes the stack amount into account.
     */
    public double unitPrice(Player player, ItemStack stack) {
        if (!ready) {
            tryInit();
        }

        if (ready) {
            try {
                ItemStack one = stack.clone();
                one.setAmount(1);

                Object shopItem = getShopItemPlayer != null
                        ? getShopItemPlayer.invoke(null, player, one)
                        : getShopItem.invoke(null, one);

                if (shopItem != null) {
                    Method m = sellPrice;
                    Object result = m.getParameterCount() == 3
                            ? m.invoke(null, shopItem, one, player)
                            : m.invoke(null, shopItem, one);
                    double price = toDouble(result);
                    if (price > 0) {
                        return price;
                    }
                }
            } catch (Throwable t) {
                if (!warnedInvoke) {
                    warnedInvoke = true;
                    plugin.getLogger().warning("EconomyShopGUI price lookup failed: " + t);
                }
            }
        }

        return fallback(stack);
    }

    private double fallback(ItemStack stack) {
        if (stack.hasItemMeta()) {
            return -1;
        }
        return plugin.getConfig().getDouble("fallback-prices." + stack.getType().name(), -1);
    }

    private static double toDouble(Object o) {
        if (o instanceof Optional<?> opt) {
            o = opt.orElse(null);
        }
        return o instanceof Number n ? n.doubleValue() : -1;
    }

    private synchronized void tryInit() {
        if (ready || failed) {
            return;
        }

        Plugin esg = null;
        for (String name : new String[]{"EconomyShopGUI-Premium", "EconomyShopGUI"}) {
            Plugin p = Bukkit.getPluginManager().getPlugin(name);
            if (p != null && p.isEnabled()) {
                esg = p;
                break;
            }
        }
        if (esg == null) {
            return; // not loaded (yet) - fallback prices only
        }

        try {
            Class<?> hook = Class.forName(HOOK_CLASS, true, esg.getClass().getClassLoader());

            for (Method m : hook.getMethods()) {
                if (!Modifier.isStatic(m.getModifiers())) {
                    continue;
                }
                Class<?>[] p = m.getParameterTypes();

                if (m.getName().equals("getShopItem")) {
                    if (p.length == 2 && p[0] == Player.class && p[1] == ItemStack.class) {
                        getShopItemPlayer = m;
                    } else if (p.length == 1 && p[0] == ItemStack.class) {
                        getShopItem = m;
                    }
                } else if (m.getName().equals("getItemSellPrice")) {
                    boolean fits = p.length >= 2 && p[1] == ItemStack.class
                            && (p.length == 2 || (p.length == 3 && p[2] == Player.class));
                    if (fits && (sellPrice == null || p.length > sellPrice.getParameterCount())) {
                        sellPrice = m;
                    }
                }
            }

            if (sellPrice == null || (getShopItem == null && getShopItemPlayer == null)) {
                failed = true;
                plugin.getLogger().warning("Found " + esg.getName() + " but its API doesn't have the expected "
                        + "getShopItem/getItemSellPrice methods. Using fallback-prices only.");
                return;
            }

            ready = true;
            plugin.getLogger().info("Hooked into " + esg.getName() + " for sell prices.");
        } catch (Throwable t) {
            failed = true;
            plugin.getLogger().warning("Could not hook into " + esg.getName() + ": " + t);
        }
    }
}
