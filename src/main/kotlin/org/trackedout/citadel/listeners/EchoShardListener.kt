package org.trackedout.citadel.listeners

import co.aikar.commands.BaseCommand
import me.devnatan.inventoryframework.ViewFrame
import net.kyori.adventure.text.TextComponent
import org.bukkit.block.Barrel
import org.bukkit.entity.HumanEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.async
import org.trackedout.citadel.inventory.DeckManagementView.Companion.JOIN_QUEUE_FUNC
import org.trackedout.citadel.inventory.DeckManagementView.Companion.PLUGIN
import org.trackedout.citadel.inventory.EnterQueueView
import org.trackedout.citadel.sendGreenMessage
import org.trackedout.citadel.sendGreyMessage
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
                barrel.customName()?.let { customName ->
                    val title = (customName as TextComponent).content()

                    // Should be at -538 112 1980
                    if (title == "Enter Queue") {
                        event.isCancelled = true
                        showQueueBarrel(event.player as Player)
                    }
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

    @EventHandler(ignoreCancelled = true)
    fun onDropItem(event: PlayerDropItemEvent) {
        val player = event.player
        player.debug(
            "Drop item event: ${event.eventName} - inventoryType=${event.itemDrop.type}, " +
                "cursorItem=${event.itemDrop.type.name}"
        )
        if (isRestrictedItem(event.itemDrop.itemStack)) {
            player.debug("Blocking item drop event")
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onDragItem(event: InventoryDragEvent) {
        val player = event.view.player
        player.debug(
            "Drag event: ${event.type.name} - inventoryType=${event.inventory.type.name}, " +
                "inventorySize=${event.inventory.size}, " +
                "view=${event.view.type.name}, " +
                "cursorItem=${event.cursor?.type?.name}, " +
                "oldItem=${event.oldCursor.type.name}"
        )
        val slotTypes = event.rawSlots.map { it to event.view.getSlotType(it).name }
        player.debug("Slot types: $slotTypes")

        // Allow all slots if the target inventory is the player's inventory.
        // Otherwise, block any of the non-player inventory slots from being involved.
        val blockSlotsBelow = if (event.inventory.type == InventoryType.PLAYER) 0 else event.inventory.size
        player.debug("Blocking slots < $blockSlotsBelow")

        if ((!isPlayerInventory(event.inventory)) && isRestrictedItem(event.oldCursor) && event.rawSlots.any { it < blockSlotsBelow }) {
            player.debug("Blocking drag event")
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onInventoryInteract(event: InventoryClickEvent) {
        val player = event.whoClicked as Player

        player.debug(
            "Click event: { " +
                "eventType=${event.action}, " +
                "hotbarButton=${event.hotbarButton}, " +
                "inventoryType=${event.inventory.type}, " +
                "openInventoryType=${player.openInventory.type}, " +
                "clickedInventoryType=${event.clickedInventory?.type}, " +
                "cursor=${event.cursor.type.name}, " +
                "currentItem=${event.currentItem?.type?.name}, " +
                "offHandItem=${player.inventory.itemInOffHand.type.name} }"
        )

        if (player.scoreboardTags.contains("nocheck")) {
            player.debug("Skipping inventory checks as player has 'nocheck' tag")
            return
        }

        /* Actions to prevent:
        Press Q:
        { eventType=DROP_ONE_SLOT, inventoryType=CRAFTING, openInventoryType=CRAFTING, clickedInventoryType=PLAYER, topInventoryType=CRAFTING, bottomInventoryType=PLAYER, cursor=AIR, currentItem=IRON_NUGGET }

        Press F (hotbar swap):
        { eventType=HOTBAR_SWAP, inventoryType=CHEST, openInventoryType=CHEST, clickedInventoryType=CHEST, topInventoryType=CHEST, bottomInventoryType=PLAYER, cursor=AIR, currentItem=AIR }

        Press F (hotbar swap) but with item already in hotbar:
        { eventType=HOTBAR_MOVE_AND_READD, inventoryType=BARREL, openInventoryType=BARREL, clickedInventoryType=BARREL, topInventoryType=BARREL, bottomInventoryType=PLAYER, cursor=AIR, currentItem=IRON_NUGGET }

        Click to pickup, then this is the place event:
        { eventType=PLACE_ALL, inventoryType=CHEST, openInventoryType=CHEST, clickedInventoryType=PLAYER, topInventoryType=CHEST, bottomInventoryType=PLAYER, cursor=IRON_NUGGET, currentItem=AIR }

        Same, but with right click to place:
        { eventType=PLACE_ONE, inventoryType=CHEST, openInventoryType=CHEST, clickedInventoryType=CHEST, topInventoryType=CHEST, bottomInventoryType=PLAYER, cursor=IRON_NUGGET, currentItem=AIR }

        Shift click:
        { eventType=MOVE_TO_OTHER_INVENTORY, inventoryType=CHEST, openInventoryType=CHEST, clickedInventoryType=PLAYER, topInventoryType=CHEST, bottomInventoryType=PLAYER, cursor=AIR, currentItem=IRON_NUGGET }

        Right click with item in hand:
        { eventType=PLACE_ONE, inventoryType=CHEST, openInventoryType=CHEST, clickedInventoryType=CHEST, topInventoryType=CHEST, bottomInventoryType=PLAYER, cursor=IRON_NUGGET, currentItem=AIR }

        Right click on stack:
        { eventType=PICKUP_HALF, inventoryType=CHEST, openInventoryType=CHEST, clickedInventoryType=PLAYER, topInventoryType=CHEST, bottomInventoryType=PLAYER, cursor=AIR, currentItem=IRON_NUGGET }

        Drag place:
        ???
        */

        val actionsToBlockRegardlessOfInventoryType = listOf(
            InventoryAction.DROP_ONE_SLOT,
            InventoryAction.DROP_ALL_SLOT,
            InventoryAction.DROP_ONE_CURSOR,
            InventoryAction.DROP_ALL_CURSOR,
        )
        if (event.action !in actionsToBlockRegardlessOfInventoryType
            && isPlayerInventory(event.clickedInventory)
            && isPlayerInventory(event.inventory)
        ) {
            return
        }

        val itemsToCheck = mutableListOf<ItemStack>()

        val allowForPlayerInventory = listOf(
            InventoryAction.PICKUP_ALL,
            InventoryAction.PICKUP_ONE,
            InventoryAction.PICKUP_HALF,
            InventoryAction.PICKUP_SOME,
            InventoryAction.SWAP_WITH_CURSOR,
            InventoryAction.PLACE_ALL,
            InventoryAction.PLACE_SOME,
            InventoryAction.PLACE_ONE,
        )
        if (!(event.action in allowForPlayerInventory && isPlayerInventory(event.clickedInventory))) {
            event.currentItem?.let { itemsToCheck.add(it) }
        }
        if (!(event.action in allowForPlayerInventory && isPlayerInventory(event.clickedInventory))) {
            itemsToCheck.add(event.cursor)
        }

        if (event.action === InventoryAction.HOTBAR_SWAP || event.action === InventoryAction.HOTBAR_MOVE_AND_READD) {
            if (event.hotbarButton >= 0) {
                player.debug("Hotbar item: ${player.inventory.getItem(event.hotbarButton)?.type?.name}")
                player.inventory.getItem(event.hotbarButton)?.let { itemsToCheck.add(it) }
            } else {
                itemsToCheck.add(player.inventory.itemInOffHand)
            }
        }

        val anyItemMatches = itemsToCheck.any { isRestrictedItem(it) }
        if (anyItemMatches) {
            player.debug("Blocking click action ${event.action}")
            event.isCancelled = true
            return
        }
    }

    private fun isPlayerInventory(inventory: Inventory?) = inventory?.type == InventoryType.PLAYER || inventory?.type == InventoryType.CRAFTING

    private fun isRestrictedItem(it: ItemStack) = it.itemMeta != null && it.itemMeta.hasCustomModelData()
}

private fun HumanEntity.debug(message: String) {
    if (this.scoreboardTags.contains("debug.click")) {
        this.sendGreyMessage(message)
    }
}
