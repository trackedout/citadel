package org.trackedout.citadel

import de.oliver.fancyholograms.api.FancyHologramsPlugin
import de.oliver.fancyholograms.api.data.TextHologramData
import de.oliver.fancyholograms.api.hologram.Hologram
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.data.type.WallSign
import org.bukkit.entity.Display
import org.bukkit.scheduler.BukkitRunnable
import org.trackedout.citadel.mongo.MongoDBManager
import org.trackedout.citadel.mongo.MongoTrophy

// WARNING: This task is processed *asynchronously*, and thus most interactions with the main Minecraft thread
// must go through a sync task scheduler.
class TrophyTaskRunner(
    private val plugin: Citadel,
) : BukkitRunnable() {
    override fun run() {
        applyTOTs(plugin)
    }
}

fun applyTOTs(plugin: Citadel) {
    plugin.logger.info("Running TOT sign updater")

    val database = MongoDBManager.getDatabase("dunga-dunga");
    val trophyCollection = database.getCollection("trophies", MongoTrophy::class.java)

    val trophies = trophyCollection.find().toList()

    plugin.runOnNextTick {
        trophies.forEach { trophy ->
            val sign = trophy.sign
//            updateSign(plugin, sign.x, sign.y, sign.z, listOf()) // Clear the sign. In the future we want this to have the ToT description
            duplicateSignText(plugin, sign.x, sign.y, sign.z)
            updateSignHead(plugin, sign.x, sign.y, sign.z, trophy.player)
            updateHologram(plugin, sign.x, sign.y, sign.z, trophy.player, sign.text, trophy.totKey)
        }
    }
}

// Find the sign 7 blocks above the given coordinates and copy its text to the sign at the given coordinates
fun duplicateSignText(plugin: Citadel, x: Int, y: Int, z: Int) {
    val sourceSignBlock = plugin.server.worlds.find { it.name == "world" }?.getBlockAt(x, y + 7, z)
    if (sourceSignBlock == null || sourceSignBlock.type == Material.AIR) {
        plugin.logger.fine("Source sign at ${x}, ${y + 7}, $z does not exist or is not a wall sign")
        return
    }

    val state = sourceSignBlock.state
    if (state !is org.bukkit.block.Sign) {
        plugin.logger.fine("Source sign at ${x}, ${y + 7}, $z is not a sign state")
        return
    }

//    val lines = state.getSide(Side.FRONT).lines().toList()
    val lines = state.lines.toList()
    updateSign(plugin, x, y, z, lines)
}

// Updates the player head (if any) located on the block above the block the sign is attached to.
// If playerName is null or blank, the head's owner will be cleared.
fun updateSignHead(plugin: Citadel, x: Int, y: Int, z: Int, playerName: String?) {
    plugin.logger.fine("Updating sign head for sign at ${x}, ${y}, $z with player: $playerName")

    val signBlock = getSignBlock(plugin, x, y, z)
    if (signBlock == null) {
        plugin.logger.warning("Sign at ${x}, ${y}, $z does not exist")
        return
    }

    val signBlockData = signBlock.blockData
    if (signBlockData !is WallSign) {
        plugin.logger.warning("Sign at ${x}, ${y}, $z does not have WallSign block data, cannot determine attached block")
        return
    }

    val attachedBlock = signBlock.getRelative(signBlockData.facing.oppositeFace) // The attached block
    val headBlock = attachedBlock.getRelative(BlockFace.UP) // The block above the attached block

    // If it's air, set to player head first
    if (headBlock.type == Material.AIR) {
        headBlock.type = Material.PLAYER_HEAD
    }

    if (headBlock.type != Material.PLAYER_HEAD && headBlock.type != Material.PLAYER_WALL_HEAD) {
        plugin.logger.fine("No player head present above at ${headBlock.x}, ${headBlock.y}, ${headBlock.z}; found ${headBlock.type}")
        return
    }

    // Normalize to a standing player head for consistent Skull state handling
    if (headBlock.type != Material.PLAYER_HEAD) {
        headBlock.type = Material.PLAYER_HEAD
    }

    val state = headBlock.state
    if (state !is org.bukkit.block.Skull) {
        plugin.logger.warning("Block at ${headBlock.x}, ${headBlock.y}, ${headBlock.z} is a player head type but state was not a Skull instance")
        return
    }

    try {
        val desiredRotation = signBlockData.facing.oppositeFace

        val offlinePlayer = getOfflinePlayer(plugin, playerName)
        if (offlinePlayer != null) {
            // Only update if owner or rotation differs
            if (state.owningPlayer?.uniqueId != offlinePlayer.uniqueId || state.rotation != desiredRotation) {
                state.setOwningPlayer(offlinePlayer)
                state.rotation = desiredRotation
                state.update(false)
                plugin.logger.fine("Set head at ${headBlock.x}, ${headBlock.y}, ${headBlock.z} to owner $playerName (UUID: ${offlinePlayer.player?.uniqueId}) with rotation $desiredRotation")
            } else {
                plugin.logger.fine("Head at ${headBlock.x}, ${headBlock.y}, ${headBlock.z} is already set to $playerName and facing $desiredRotation")
            }

        } else {
            headBlock.type = Material.AIR
            plugin.logger.fine("Removed head at ${x}, ${y}, $z as player $playerName was not found")
        }
    } catch (ex: Exception) {
        plugin.logger.warning("Failed to update skull at ${headBlock.x}, ${headBlock.y}, ${headBlock.z} for player $playerName: ${ex.stackTraceToString()}")
        ex.printStackTrace()
    }
}

private fun getSignBlock(plugin: Citadel, x: Int, y: Int, z: Int): Block? {
    val world = plugin.server.worlds.find { it.name == "world" }
    if (world == null) {
        plugin.logger.warning("World 'world' not found, cannot update sign head at ${x}, ${y}, $z")
        return null
    }

    val signBlock = world.getBlockAt(x, y, z)
    if (signBlock.type != Material.WARPED_WALL_SIGN) {
        plugin.logger.warning("Block at ${x}, ${y}, $z is not a wall sign, cannot locate attached block for head")
        return null
    }

    return signBlock
}

// Creates or updates a hologram located above the player's head sign
fun updateHologram(plugin: Citadel, x: Int, y: Int, z: Int, playerName: String?, lines: List<String>, totKey: String) {
    val signBlock = getSignBlock(plugin, x, y, z)
    if (signBlock == null) {
        plugin.logger.warning("Sign at ${x}, ${y}, $z does not exist")
        return
    }

    val signBlockData = signBlock.blockData
    if (signBlockData !is WallSign) {
        plugin.logger.warning("Sign at ${x}, ${y}, $z does not have WallSign block data, cannot determine attached block")
        return
    }

    val attachedBlock = signBlock.getRelative(signBlockData.facing.oppositeFace) // The attached block
    val hologramLocation = attachedBlock.getRelative(BlockFace.UP).location.add(0.5, 1.0, 0.5) // The block above the player skull
    hologramLocation.direction = signBlockData.facing.direction

    try {
        val name = "tot-${x}-${y}-${z}"
        val manager = FancyHologramsPlugin.get().hologramManager

        if (lines.size >= 4 && playerName != null) {
            val hologramData = TextHologramData(name, hologramLocation)

            // Adjust the Hologram Data
            hologramData.background = Hologram.TRANSPARENT
            hologramData.billboard = Display.Billboard.FIXED
            hologramData.isSeeThrough = false
            hologramData.text = listOf(
                "<red>Trophy:</red> $totKey",
                "",
                "<gold>${playerName}",
                "",
                "<light_purple>${lines[2]}",
            )
            hologramData.visibilityDistance = 8

            var hologram = manager.getHologram(name).orElse(manager.create(hologramData).apply { manager.addHologram(this) })
            val data = hologram.data
            if (data is TextHologramData && data.text != hologramData.text || data.location != hologramLocation) {
                hologram.deleteHologram()
                manager.removeHologram(hologram)
                hologram = manager.create(hologramData).apply { manager.addHologram(this) }
                hologram.queueUpdate()
            }

        } else {
            // Remove hologram if player name is null
            manager.getHologram(name).ifPresent {
                it.deleteHologram()
                manager.removeHologram(it)
            }
        }
    } catch (ex: Exception) {
        plugin.logger.warning("Failed to update hologram at ${hologramLocation.x}, ${hologramLocation.y}, ${hologramLocation.z} for player $playerName: ${ex.stackTraceToString()}")
        ex.printStackTrace()
    }
}

private fun getOfflinePlayer(plugin: Citadel, playerName: String?): OfflinePlayer? {
    return playerName?.takeIf { it.matches(playerNameRegex) }?.let {
        try {
            return@let plugin.server.getOfflinePlayerIfCached(it) ?: plugin.server.getOfflinePlayer(it)
        } catch (e: Exception) {
            plugin.logger.warning("Failed to get desired player $it: ${e.stackTraceToString()}")
            null
        }
    }
}
