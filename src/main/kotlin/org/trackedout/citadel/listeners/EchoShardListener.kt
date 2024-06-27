package org.trackedout.citadel.listeners

import co.aikar.commands.BaseCommand
import me.devnatan.inventoryframework.ViewFrame
import net.kyori.adventure.text.TextComponent
import org.bukkit.block.Barrel
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryOpenEvent
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.async
import org.trackedout.citadel.inventory.DeckManagementView.Companion.JOIN_QUEUE_FUNC
import org.trackedout.citadel.inventory.DeckManagementView.Companion.PLUGIN
import org.trackedout.citadel.inventory.EnterQueueView
import org.trackedout.citadel.sendGreenMessage
import org.trackedout.client.apis.EventsApi
import org.trackedout.client.apis.InventoryApi
import org.trackedout.client.models.Event
import java.util.function.Consumer

class EchoShardListener(
    private val plugin: Citadel,
    private val inventoryApi: InventoryApi,
    private val eventsApi: EventsApi,
    private val viewFrame: ViewFrame,
) : BaseCommand(), Listener {

    @EventHandler(ignoreCancelled = true)
    fun onInventoryOpen(event: InventoryOpenEvent) {
        if (event.player !is Player) {
            plugin.logger.info("Not a player")
            return
        }

        // TODO: Check if player is already queued
        // TODO: Get available shards from backend

        event.inventory.location?.let { location ->
            if (location.block.state is Barrel) {
                val barrel = (location.block.state as Barrel)
                val title = (barrel.customName() as TextComponent).content()

                // Should be at -538 112 1980
                if (title == "Enter Queue") {
                    event.isCancelled = true
                    showQueueBarrel(event.player as Player)
                }
            }
        }
    }

    private fun showQueueBarrel(player: Player) {
        val joinQueueFunc = Consumer<String> { deckId ->
            plugin.async(player) {
                eventsApi.eventsPost(
                    Event(
                        name = "joined-queue",
                        server = plugin.serverName,
                        player = player.name,
                        count = deckId.toInt(),
                        x = player.x,
                        y = player.y,
                        z = player.z,
                    )
                )

                player.sendGreenMessage("Joining dungeon queue with Deck #${deckId}")
            }
        }

        val context = mutableMapOf(
            PLUGIN to plugin,
            JOIN_QUEUE_FUNC to joinQueueFunc,
        )
        viewFrame.open(EnterQueueView::class.java, player, context)
    }
}
