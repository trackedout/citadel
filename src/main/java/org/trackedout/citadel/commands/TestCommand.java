package org.trackedout.citadel.commands;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.trackedout.citadel.Citadel;

public class TestCommand implements CommandExecutor {

    private final Citadel plugin;

    public TestCommand(Citadel plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;

            player.sendMessage(ChatColor.GREEN + "Test!");
        } else
            this.plugin.logger.info("This command can only be run by players");

        return true;
    }
}