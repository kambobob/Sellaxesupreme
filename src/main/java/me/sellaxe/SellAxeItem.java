package me.sellaxe;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds and identifies the Sell Axe item.
 * Settings are read from config.yml under the "sellaxe" section:
 *
 * sellaxe:
 *   material: GOLDEN_AXE
 *   name: "&6&lSell Axe"
 *   lore:
 *     - "&7Hit a container to sell its contents."
 */
public class SellAxeItem {

    private final JavaPlugin plugin;
    private final NamespacedKey key;

    private Material material = Material.GOLDEN_AXE;
    private String name = "&6&lSell Axe";
    private List<String> lore = new ArrayList<>(List.of("&7Hit a container to sell its contents."));

    public SellAxeItem(JavaPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "sellaxe");
        reload();
    }

    /** Re-reads the item settings from config.yml. */
    public void reload() {
        FileConfiguration config = plugin.getConfig();

        Material parsed = Material.matchMaterial(config.getString("sellaxe.material", "GOLDEN_AXE"));
        this.material = parsed != null ? parsed : Material.GOLDEN_AXE;

        this.name = config.getString("sellaxe.name", "&6&lSell Axe");

        List<String> configLore = config.getStringList("sellaxe.lore");
        this.lore = configLore.isEmpty()
                ? new ArrayList<>(List.of("&7Hit a container to sell its contents."))
                : configLore;
    }

    /** Creates a Sell Axe stack with the given amount. */
    public ItemStack create(int amount) {
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(toComponent(name));

            List<Component> loreComponents = new ArrayList<>();
            for (String line : lore) {
                loreComponents.add(toComponent(line));
            }
            meta.lore(loreComponents);

            // Marker so we can tell this item apart from a normal axe
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);

            item.setItemMeta(meta);
        }

        return item;
    }

    /** Creates a single Sell Axe. */
    public ItemStack create() {
        return create(1);
    }

    /** Returns true if the given item is a Sell Axe. */
    public boolean isSellAxe(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    public NamespacedKey getKey() {
        return key;
    }

    private Component toComponent(String text) {
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(text)
                .decoration(TextDecoration.ITALIC, false);
    }
}
