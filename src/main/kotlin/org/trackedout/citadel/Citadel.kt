package org.trackedout.citadel

import co.aikar.commands.PaperCommandManager
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.trackedout.citadel.commands.GiveShulkerCommand
import org.trackedout.citadel.commands.PartyCommand
import org.trackedout.citadel.commands.TakeShulkerCommand
import org.trackedout.citadel.data.Party

class Citadel : JavaPlugin() {
    private val manager: PaperCommandManager by lazy { PaperCommandManager(this) }

    var parties: HashSet<Party> = HashSet()

    override fun onEnable() {
        saveDefaultConfig()

        // https://github.com/aikar/commands/wiki/Real-World-Examples
        manager.registerCommand(PartyCommand())
        manager.registerCommand(TakeShulkerCommand())
        manager.registerCommand(GiveShulkerCommand())
        logger.info("Citadel has been enabled")
    }

    override fun onDisable() {
        logger.info("Citadel has been disabled")
        Bukkit.getScheduler().cancelTasks(this)
    }

    fun debug(message: String?) {
        if (this.config.getBoolean("debug")) {
            logger.info(message)
        }
    }
}