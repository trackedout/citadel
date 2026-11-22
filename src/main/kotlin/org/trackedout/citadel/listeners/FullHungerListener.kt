package org.trackedout.citadel.listeners

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.player.PlayerJoinEvent

private const val MAX_FOOD_LEVEL = 20
private const val MAX_SATURATION = 20f

// The lobby has no food, so players are kept fed: they join with a full hunger
// bar and it never drops
class FullHungerListener : Listener {
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        feed(event.player)
    }

    @EventHandler(ignoreCancelled = true)
    fun onFoodLevelChange(event: FoodLevelChangeEvent) {
        val player = event.entity as? Player ?: return
        if (event.foodLevel >= player.foodLevel) return
        event.isCancelled = true
        feed(player)
    }

    private fun feed(player: Player) {
        player.foodLevel = MAX_FOOD_LEVEL
        player.saturation = MAX_SATURATION
    }
}
