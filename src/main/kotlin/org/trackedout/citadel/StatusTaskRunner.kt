package org.trackedout.citadel

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.megavex.scoreboardlibrary.api.sidebar.Sidebar
import org.bukkit.scheduler.BukkitRunnable
import org.trackedout.client.apis.StatusApi

// WARNING: This task is processed *asynchronously*, and thus most interactions with the main Minecraft thread
// must go through a sync task scheduler. The Scoreboard lib is thread-safe, so it doesn't require a sync task.
class StatusTaskRunner(
    private val plugin: Citadel,
    private val statusApi: StatusApi,
    private val sidebar: Sidebar,
) : BukkitRunnable() {
    override fun run() {
        plugin.debug("[Async task ${this.taskId}] Fetching network status from Dunga Dunga")

        val statusSections = statusApi.getStatus()
        val mm = MiniMessage.miniMessage();

        sidebar.clearLines()
        sidebar.title(Component.text("Network Status"))

        var lines = 0
        for (statusSection in statusSections) {
            if (lines > 0) {
                sidebar.line(lines++, Component.empty())
            }
            sidebar.line(lines++, mm.deserialize(statusSection.header!!).asComponent())

            statusSection.lines?.forEach {
                val parsed = mm.deserialize(it.key!!)

                val textComponent: TextComponent = Component.text()
                    .append(parsed)
                    .append(Component.text(": "))
                    .append(Component.text("${it.value!!}").colorIfAbsent(NamedTextColor.AQUA))
                    .build()
                sidebar.line(lines++, textComponent)
            }
        }

        plugin.server.onlinePlayers.forEach {
            if (it.scoreboardTags.contains(debugTag)) {
                sidebar.addPlayer(it)
            } else {
                sidebar.removePlayer(it)
            }
        }
    }
}
