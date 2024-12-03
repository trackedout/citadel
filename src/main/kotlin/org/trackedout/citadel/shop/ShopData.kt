package org.trackedout.citadel.shop

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bukkit.block.TileState
import org.trackedout.citadel.Citadel

private val json = Json { ignoreUnknownKeys = true }

@Serializable
data class ShopData(
    var name: String = "",
    var trades: List<String> = mutableListOf(),
    var disabled: Boolean = false,
) {
    fun save(plugin: Citadel, tileState: TileState) {
        this.save(plugin, tileState.x, tileState.y, tileState.z)
    }

    fun save(plugin: Citadel, x: Int, y: Int, z: Int) {
        val shopDataJson = json.encodeToString(this)
        plugin.logger.info("Saving shop data: $shopDataJson")

        plugin.config.set("shops.${x}-${y}-${z}", shopDataJson)
        plugin.saveConfig()
    }
}

fun TileState.getShopData(plugin: Citadel): ShopData {
    return getShopDataAtLocation(plugin, this.x, this.y, this.z) ?: ShopData()
}

fun getShopDataAtLocation(plugin: Citadel, x: Int, y: Int, z: Int): ShopData? {
    return (plugin.config.get("shops.$x-$y-$z") as String?)?.let {
        json.decodeFromString(it)
    }
}
