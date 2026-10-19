package me.sellaxe;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SellListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private record Entry(int slot, int take) {}

    private final SellAxePlugin plugin;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public SellListener(SellAxePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cooldowns.remove(event.getPlayer().getUniqueId());
    }

    // HIGH + ignoreCancelled: if a claim/protection plugin denied the interaction, we don't sell.
    // Folia: this event already runs on the region thread that owns the player and the clicked block.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!plugin.axe().isSellAxe(event.getItem())) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        BlockState state = block.getState(false);
        if (!(state instanceof Container container)) {
            return;
        }

        event.setCancelled(true); // don't open the container

        Player player = event.getPlayer();
        if (!player.hasPermission("sellaxe.use")) {
            send(player, "no-permission");
            return;
        }

        long now = System.currentTimeMillis();
        long cooldown = plugin.getConfig().getLong("cooldown-ms", 300);
        Long last = cooldowns.get(player.getUniqueId());
        if (last != null && now - last < cooldown) {
            return;
        }
        cooldowns.put(player.getUniqueId(), now);

        Economy eco = plugin.economy();
        if (eco == null) {
            send(player, "no-economy");
            return;
        }

        sell(player, container.getInventory(), eco);
    }

    private void sell(Player player, Inventory inv, Economy eco) {
        int max = Math.max(1, plugin.getConfig().getInt("max-items", 65000));
        double multiplier = plugin.getConfig().getDouble("price-multiplier", 1.0);

        // 1) Work out what would be sold, without touching the container yet.
        List<Entry> plan = new ArrayList<>();
        long items = 0;
        double total = 0;
        boolean limitHit = false;

        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (stack == null || stack.getType().isAir() || plugin.axe().isSellAxe(stack)) {
                continue;
            }

            double unit = plugin.prices().unitPrice(player, stack);
            if (unit <= 0) {
                continue;
            }

            long remaining = max - items;
            if (remaining <= 0) {
                limitHit = true;
                break;
            }

            int take = (int) Math.min(stack.getAmount(), remaining);
            if (take < stack.getAmount()) {
                limitHit = true;
            }

            plan.add(new Entry(slot, take));
            items += take;
            total += unit * take * multiplier;
        }

        if (plan.isEmpty() || total <= 0) {
            send(player, "nothing");
            return;
        }

        // 2) Pay first. If payment fails, the container is left untouched (no lost items).
        EconomyResponse response = eco.depositPlayer(player, total);
        if (!response.transactionSuccess()) {
            send(player, "pay-failed");
            return;
        }

        // 3) Remove the sold items.
        for (Entry entry : plan) {
            ItemStack stack = inv.getItem(entry.slot());
            if (stack == null) {
                continue;
            }
            int left = stack.getAmount() - entry.take();
            if (left <= 0) {
                inv.setItem(entry.slot(), null);
            } else {
                stack.setAmount(left);
                inv.setItem(entry.slot(), stack);
            }
        }

        send(player, "sold",
                Placeholder.unparsed("items", String.format(Locale.US, "%,d", items)),
                Placeholder.unparsed("money", String.format(Locale.US, "%,.2f", total)));
        if (limitHit) {
            send(player, "limit-reached", Placeholder.unparsed("max", String.format(Locale.US, "%,d", max)));
        }
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
    }

    private void send(Player player, String key, TagResolver... resolvers) {
        String raw = plugin.getConfig().getString("messages." + key, "");
        if (!raw.isEmpty()) {
            player.sendMessage(MM.deserialize(raw, resolvers));
        }
    }
}
