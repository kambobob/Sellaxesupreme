package me.sellaxe;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SellAxeCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final SellAxePlugin plugin;

    public SellAxeCommand(SellAxePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(MM.deserialize("<yellow>/" + label + " give [player] [amount] <gray>| <yellow>/" + label + " reload"));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> give(sender, args);
            case "reload" -> {
                if (!sender.hasPermission("sellaxe.reload")) {
                    msg(sender, "no-permission");
                    return true;
                }
                plugin.reload();
                msg(sender, "reloaded");
            }
            default -> sender.sendMessage(MM.deserialize("<red>Unknown subcommand."));
        }
        return true;
    }

    private void give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("sellaxe.give")) {
            msg(sender, "no-permission");
            return;
        }

        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
        } else {
            target = sender instanceof Player p ? p : null;
        }
        if (target == null) {
            msg(sender, "player-not-found");
            return;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Math.min(36, Integer.parseInt(args[2])));
            } catch (NumberFormatException ignored) {
                // keep 1
            }
        }

        final int count = amount;
        final Player recipient = target;

        // Folia: touch a player's inventory / world only from that player's own scheduler.
        recipient.getScheduler().run(plugin, task -> {
            for (int i = 0; i < count; i++) {
                Map<Integer, ItemStack> leftover = recipient.getInventory().addItem(plugin.axe().create());
                for (ItemStack item : leftover.values()) {
                    recipient.getWorld().dropItemNaturally(recipient.getLocation(), item);
                }
            }
            msg(recipient, "received");
        }, null);

        msg(sender, "gave",
                Placeholder.unparsed("amount", String.valueOf(count)),
                Placeholder.unparsed("player", recipient.getName()));
    }

    private void msg(CommandSender to, String key, TagResolver... resolvers) {
        String raw = plugin.getConfig().getString("messages." + key, "");
        if (!raw.isEmpty()) {
            to.sendMessage(MM.deserialize(raw, resolvers));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("sellaxe.give")) out.add("give");
            if (sender.hasPermission("sellaxe.reload")) out.add("reload");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give") && sender.hasPermission("sellaxe.give")) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            out.addAll(List.of("1", "2", "5", "10"));
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
