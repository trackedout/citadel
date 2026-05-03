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
    plugin.debug("Running TOT sign updater")

    val database = MongoDBManager.getDatabase("dunga-dunga");
    val trophyCollection = database.getCollection("trophies", MongoTrophy::class.java)

    val trophies = trophyCollection.find().toList()
    plugin.debug("Found ${trophies.size} trophies")

    // Collect the set of valid hologram names from current trophies
    val validHologramNames = trophies.map { "tot-${it.sign.x}-${it.sign.y}-${it.sign.z}" }.toSet()

    plugin.runOnNextTick {
        // Remove stale holograms/heads that no longer correspond to a trophy in the DB
        try {
            val manager = FancyHologramsPlugin.get().hologramManager
            val staleHolograms = manager.holograms
                .filter { it.data.name.startsWith("tot-") && it.data.name !in validHologramNames }

            staleHolograms.forEach { hologram ->
                plugin.debug("Removing stale hologram: ${hologram.data.name}")
                hologram.deleteHologram()
                manager.removeHologram(hologram)
            }
        } catch (ex: Exception) {
            plugin.logger.warning("Failed to clean up stale holograms: ${ex.message}")
        }

        var warnings = 0
        trophies.forEach { trophy ->
            plugin.debug("Applying trophy $trophy")
            val sign = trophy.sign
            val signBlock = getSignBlock(plugin, sign.x, sign.y, sign.z)
            if (signBlock == null) {
                warnings++
                return@forEach
            }
            updateSignBlock(plugin, sign.x, sign.y, sign.z, trophy.player != null)
            val signDesc = trophy.signDescription
            if (signDesc != null && signDesc.size >= 4) {
                updateSign(plugin, sign.x, sign.y, sign.z, signDesc)
            } else {
                duplicateSignText(plugin, sign.x, sign.y, sign.z)
            }
            updateSignHead(plugin, signBlock, trophy.player)
            updateHologram(plugin, signBlock, trophy.player, sign.text, trophy.totKey)
        }
        if (warnings > 0) {
            plugin.logger.warning("$warnings trophies had invalid sign blocks and were skipped")
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
fun updateSignHead(plugin: Citadel, signBlock: Block, playerName: String?) {
    val signBlockData = signBlock.blockData as WallSign

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
            plugin.logger.fine("Removed head at ${headBlock.x}, ${headBlock.y}, ${headBlock.z} as player $playerName was not found")
        }
    } catch (ex: Exception) {
        plugin.logger.warning("Failed to update skull at ${headBlock.x}, ${headBlock.y}, ${headBlock.z} for player $playerName: ${ex.stackTraceToString()}")
        ex.printStackTrace()
    }
}

// Sets the block the sign is attached to and the sign type based on trophy availability
fun updateSignBlock(plugin: Citadel, x: Int, y: Int, z: Int, isActive: Boolean) {
    val world = plugin.server.worlds.find { it.name == "world" } ?: return
    val signBlock = world.getBlockAt(x, y, z)
    val signBlockData = signBlock.blockData
    if (signBlockData !is WallSign) return

    // Change sign type
    val desiredSignType = if (isActive) Material.WARPED_WALL_SIGN else Material.CRIMSON_WALL_SIGN
    if (signBlock.type != desiredSignType) {
        val facing = signBlockData.facing
        signBlock.type = desiredSignType
        val newData = signBlock.blockData as WallSign
        newData.facing = facing
        signBlock.blockData = newData
    }

    // Change attached block (stem)
    val attachedBlock = signBlock.getRelative(signBlockData.facing.oppositeFace)
    val desiredStemType = if (isActive) Material.STRIPPED_WARPED_STEM else Material.STRIPPED_CRIMSON_STEM
    if (attachedBlock.type != desiredStemType) {
        attachedBlock.type = desiredStemType
    }

    // Change stairs on either side of the stem
    val desiredStairType = if (isActive) Material.WARPED_STAIRS else Material.CRIMSON_STAIRS
    val currentStairType = if (isActive) Material.CRIMSON_STAIRS else Material.WARPED_STAIRS
    val stairOffsets = if (signBlockData.facing.modX != 0) {
        // Sign faces east/west, stairs are at z±1
        listOf(attachedBlock.getRelative(0, 0, 1), attachedBlock.getRelative(0, 0, -1))
    } else {
        // Sign faces north/south, stairs are at x±1
        listOf(attachedBlock.getRelative(1, 0, 0), attachedBlock.getRelative(-1, 0, 0))
    }
    stairOffsets.forEach { stairBlock ->
        if (stairBlock.type == currentStairType) {
            val stairData = stairBlock.blockData as org.bukkit.block.data.type.Stairs
            stairBlock.type = desiredStairType
            val newStairData = stairBlock.blockData as org.bukkit.block.data.type.Stairs
            newStairData.facing = stairData.facing
            newStairData.half = stairData.half
            newStairData.shape = stairData.shape
            stairBlock.blockData = newStairData
        }
    }

    // Change trapdoor behind the stem
    val trapdoorBlock = attachedBlock.getRelative(signBlockData.facing.oppositeFace)
    val desiredTrapdoorType = if (isActive) Material.WARPED_TRAPDOOR else Material.CRIMSON_TRAPDOOR
    val currentTrapdoorType = if (isActive) Material.CRIMSON_TRAPDOOR else Material.WARPED_TRAPDOOR
    if (trapdoorBlock.type == currentTrapdoorType) {
        val trapdoorData = trapdoorBlock.blockData as org.bukkit.block.data.type.TrapDoor
        trapdoorBlock.type = desiredTrapdoorType
        val newTrapdoorData = trapdoorBlock.blockData as org.bukkit.block.data.type.TrapDoor
        newTrapdoorData.facing = trapdoorData.facing
        newTrapdoorData.half = trapdoorData.half
        newTrapdoorData.isOpen = trapdoorData.isOpen
        trapdoorBlock.blockData = newTrapdoorData
    }
}

fun getSignBlock(plugin: Citadel, x: Int, y: Int, z: Int): Block? {
    val world = plugin.server.worlds.find { it.name == "world" }
    if (world == null) {
        plugin.logger.warning("World 'world' not found, cannot update sign head at ${x}, ${y}, $z")
        return null
    }

    val signBlock = world.getBlockAt(x, y, z)
    if (signBlock.blockData !is WallSign) {
        plugin.debug("Block at ${x}, ${y}, $z is not a wall sign, skipping trophy")
        return null
    }

    return signBlock
}

// Creates or updates a hologram located above the player's head sign
fun updateHologram(plugin: Citadel, signBlock: Block, playerName: String?, lines: List<String>, totKey: String) {
    val x = signBlock.x
    val y = signBlock.y
    val z = signBlock.z
    val signBlockData = signBlock.blockData as WallSign

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
