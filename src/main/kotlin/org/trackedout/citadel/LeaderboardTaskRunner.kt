package org.trackedout.citadel

import de.oliver.fancyholograms.api.FancyHologramsPlugin
import de.oliver.fancyholograms.api.data.TextHologramData
import de.oliver.fancyholograms.api.hologram.Hologram
import net.citizensnpcs.api.CitizensAPI
import net.citizensnpcs.trait.SkinTrait
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Sign
import org.bukkit.block.Skull
import org.bukkit.block.data.type.Snow
import org.bukkit.block.data.type.WallSign
import org.bukkit.block.sign.Side
import org.bukkit.entity.Display
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.command.CommandSender
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import org.joml.Vector3f
import org.trackedout.citadel.mongo.MongoDBManager
import org.trackedout.citadel.mongo.MongoPlayerStats
import org.trackedout.citadel.mongo.Stats
import org.trackedout.client.apis.ConfigApi
import java.time.Duration
import java.util.AbstractMap
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

// WARNING: This task is processed *asynchronously*, and thus most interactions with the main Minecraft thread
// must go through a sync task scheduler. The Scoreboard lib is thread-safe, so it doesn't require a sync task.
class LeaderboardTaskRunner(
    private val plugin: Citadel,
    private val configApi: ConfigApi,
) : BukkitRunnable() {

    companion object {
        private const val TICKS_PRE_ANIMATION_PAUSE = 60L
        private const val TICKS_PER_SIGN_GROUP = 20L
        private const val TICKS_PRE_PODIUM_PAUSE = 60L
        private const val TICKS_PARTICLE_TRAIL = 15
        private const val TICKS_POST_TRAIL_GAP = 5L
        private const val TICKS_BETWEEN_PODIUMS = 40L
        private const val TICKS_BOUNDARY_CHECK = 5L
        private const val HOLOGRAM_HEIGHT = 6.0
        private const val HOLOGRAM_VISIBILITY = 80
        private const val PARTICLE_TRAIL_SOURCES = 3
        private const val SIGN_REVEAL_VOLUME = 0.3f
    }

    data class LeaderboardSlot(val signBlock: Block, val headBlock: Block, val snowBlock: Block)
    data class PodiumRegion(val rank: Int, val signBlock: Block?, val npcLocation: Location?)
    data class SlotAssignment(val slot: LeaderboardSlot, val player: PlayerWithPoints, val rank: Int, val snowLayers: Int)

    private val previousGameModes = mutableMapOf<UUID, GameMode>()
    private val previousLocations = mutableMapOf<UUID, Location>()
    private var animationParticipants: Set<UUID>? = null // null = all online players
    val playerData = mutableMapOf<String, PlayerWithPoints>()
    var currentMaxPhase = 0

    override fun run() {
        doRun(false)
    }

    fun runWithAnimation(participants: Set<UUID>? = null) {
        animationParticipants = participants
        doRun(true)
    }

    private fun doRun(forceAnimate: Boolean) {
        if (this.isCancelled) return
        if (FirstRun.running) return
        FirstRun.running = true

        try {
            plugin.logger.info("[Async task ${this.taskId}] Fetching leaderboard from MongoDB")

            FirstRun.showLeaderboard = configApi.getBool("lobby", "show-leaderboard", default = false)
            if (!FirstRun.showLeaderboard) return

            val world = plugin.server.worlds.find { it.name == "world" } ?: return

            val activePlayers = mutableMapOf<String, PlayerWithPoints>()
            var maxPhase = 0
            val pointsForPosition = arrayOf(25, 21, 18, 16, 14, 12, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1)

            configApi.getInt("comp-season", "current-phase")?.let { currentPhase ->
                maxPhase = parsePhaseDataForAllPlayers(currentPhase, activePlayers, pointsForPosition)
                currentMaxPhase = maxPhase
            }

            // Deterministic sort: alphabetical by player name
            val sortedPlayers = activePlayers.values.sortedBy { it.player }
            playerData.clear()
            sortedPlayers.forEach { playerData[it.player] = it }

            val totalPointsRank = sortedPlayers.map { it.totalPoints }.distinct().sortedDescending()

            plugin.logger.info("[Leaderboard] ${sortedPlayers.size} players sorted")

            if (forceAnimate) FirstRun.animating = true

            plugin.runOnNextTick {
                // Discover leaderboard slots from WorldGuard regions (must be on main thread)
                val slots = discoverLeaderboardSlots(world)
                plugin.logger.info("[Leaderboard] Found ${slots.size} leaderboard slots")

                // Discover podium regions
                val podiums = discoverPodiums(world)
                plugin.logger.info("[Leaderboard] Found ${podiums.size} podium regions")

                val animate = forceAnimate

                // Build slot-to-player mapping
                val assignments = sortedPlayers.take(slots.size).mapIndexed { index, player ->
                    val rank = totalPointsRank.indexOf(player.totalPoints) + 1
                    SlotAssignment(slots[index], player, rank, player.totalPoints / 4)
                }

                if (animate) {
                    runAnimation(world, slots, podiums, assignments, sortedPlayers, totalPointsRank, maxPhase)
                } else {
                    // Instant update (no animation)
                    assignments.forEach { (slot, player, rank, snowLayers) ->
                        updateSlot(slot, player, rank, maxPhase, snowLayers)
                    }

                    // Clear unused slots
                    for (i in sortedPlayers.size until slots.size) {
                        clearSlot(slots[i])
                    }

                    // Update podiums for top 3 (by points, not alphabetical)
                    val pointsByRank = totalPointsRank
                    podiums.forEach { podium ->
                        val points = pointsByRank.getOrNull(podium.rank - 1)
                        val playersAtRank = if (points != null) sortedPlayers.filter { it.totalPoints == points } else emptyList()
                        updatePodium(podium, playersAtRank, podium.rank, maxPhase)
                    }

                    // Remove any leftover NPCs in podium regions that aren't expected
                    val expectedIds = podiums.flatMap { podium ->
                        listOf("leaderboard-podium-${podium.rank}", "leaderboard-podium-${podium.rank}-left", "leaderboard-podium-${podium.rank}-right")
                    }.filter { npcId ->
                        // Only keep IDs that actually have a player assigned
                        val rank = npcId.removePrefix("leaderboard-podium-").split("-").first().toIntOrNull() ?: return@filter false
                        val points = pointsByRank.getOrNull(rank - 1) ?: return@filter false
                        val playersAtRank = sortedPlayers.filter { it.totalPoints == points }
                        when {
                            npcId.endsWith("-left") -> playersAtRank.size > 1
                            npcId.endsWith("-right") -> playersAtRank.size > 2
                            else -> playersAtRank.isNotEmpty()
                        }
                    }.toSet()
                    removeStaleNpcs(world, expectedIds)

                    // Update title hologram above podium-1
                    updateTitleHologram(podiums.find { it.rank == 1 }, maxPhase)
                }
            }
        } finally {
            if (!FirstRun.animating) FirstRun.running = false
        }
    }

    private fun runAnimation(
        world: World,
        slots: List<LeaderboardSlot>,
        podiums: List<PodiumRegion>,
        assignments: List<SlotAssignment>,
        sortedPlayers: List<PlayerWithPoints>,
        totalPointsRank: List<Int>,
        maxPhase: Int,
    ) {
        plugin.logger.info("[Leaderboard] Starting animation with ${assignments.size} players across ${totalPointsRank.size} ranks")

        slots.forEach { clearSlot(it) }
        podiums.forEach { updatePodium(it, emptyList(), it.rank, maxPhase) }
        removeStaleNpcs(world, emptySet())

        val podium1 = podiums.find { it.rank == 1 }
        val viewLocation = computeViewLocation(podium1, slots)
        enterSpectatorMode(world, viewLocation)

        animationAudience().forEach { p ->
            p.showTitle(Title.title(
                Component.empty(),
                Component.text("The Leaderboard", NamedTextColor.GOLD),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500))
            ))
        }

        var tickDelay = TICKS_PRE_ANIMATION_PAUSE
        tickDelay = scheduleSignReveals(podium1, assignments, maxPhase, tickDelay)
        tickDelay += TICKS_PRE_PODIUM_PAUSE
        schedulePodiumReveals(world, slots, podiums, sortedPlayers, totalPointsRank, assignments, maxPhase, tickDelay)
    }

    private fun computeViewLocation(podium1: PodiumRegion?, slots: List<LeaderboardSlot>): Location? {
        return podium1?.signBlock?.let { sign ->
            val facing = (sign.blockData as? WallSign)?.facing ?: return@let null
            val podiumLoc = sign.location.clone().add(0.5, 1.0, 0.5)
            val furthestSlot = slots.maxByOrNull {
                it.signBlock.location.toVector().subtract(podiumLoc.toVector()).dot(facing.direction)
            } ?: return@let null
            val loc = podiumLoc.clone()
            val depthDiff = furthestSlot.signBlock.location.toVector().subtract(podiumLoc.toVector()).dot(facing.direction)
            loc.add(facing.direction.multiply(depthDiff))
            loc.add(facing.direction.multiply(1.0))
            loc.add(0.0, -2.0, 0.0)
            loc.setDirection(facing.direction.multiply(-1))
            loc.pitch = 30f
            loc
        }
    }

    private fun animationAudience(): List<Player> {
        val participants = animationParticipants
        return if (participants == null) plugin.server.onlinePlayers.toList()
        else plugin.server.onlinePlayers.filter { it.uniqueId in participants }
    }

    private fun enterSpectatorMode(world: World, viewLocation: Location?) {
        previousGameModes.clear()
        previousLocations.clear()
        animationAudience().forEach { p ->
            previousGameModes[p.uniqueId] = p.gameMode
            previousLocations[p.uniqueId] = p.location.clone()
            p.gameMode = GameMode.SPECTATOR
            p.velocity = org.bukkit.util.Vector(0, 0, 0)
            viewLocation?.let { p.teleport(it) }
        }

        val viewRegion = world.regions()?.find { it.id == "view-leaderboard" }
        if (viewRegion != null && viewLocation != null) {
            object : BukkitRunnable() {
                override fun run() {
                    if (!FirstRun.animating) { cancel(); return }
                    plugin.server.onlinePlayers.forEach { p ->
                        if (previousGameModes.containsKey(p.uniqueId) &&
                            !viewRegion.contains(p.location.blockX, p.location.blockY, p.location.blockZ)) {
                            p.teleport(viewLocation)
                        }
                    }
                }
            }.runTaskTimer(plugin, TICKS_BOUNDARY_CHECK, TICKS_BOUNDARY_CHECK)
        }
    }

    private fun scheduleSignReveals(
        podium1: PodiumRegion?,
        assignments: List<SlotAssignment>,
        maxPhase: Int,
        startDelay: Long,
    ): Long {
        var tickDelay = startDelay
        val podiumFacing = (podium1?.signBlock?.blockData as? WallSign)?.facing
        val podiumLoc = podium1?.signBlock?.location?.clone()?.add(0.5, 0.5, 0.5)

        val groupedByDepth = if (podiumFacing != null && podiumLoc != null) {
            assignments.groupBy { assignment ->
                val diff = assignment.slot.signBlock.location.toVector().subtract(podiumLoc.toVector())
                Math.round(diff.dot(podiumFacing.direction)).toInt()
            }.entries.sortedByDescending { it.key }
        } else {
            assignments.reversed().map { AbstractMap.SimpleEntry(0, listOf(it)) }
        }

        for (group in groupedByDepth) {
            val delay = tickDelay
            plugin.runLaterOnATick(delay) {
                if (!FirstRun.animating) return@runLaterOnATick
                group.value.forEach { a ->
                    updateSlot(a.slot, a.player, a.rank, maxPhase, a.snowLayers)
                }
                animationAudience().forEach { p ->
                    p.playSound(Sound.sound(Key.key("do2:events.card_reveal"), Sound.Source.MASTER, SIGN_REVEAL_VOLUME, 0f))
                }
            }
            tickDelay += TICKS_PER_SIGN_GROUP
        }
        return tickDelay
    }

    private fun schedulePodiumReveals(
        world: World,
        slots: List<LeaderboardSlot>,
        podiums: List<PodiumRegion>,
        sortedPlayers: List<PlayerWithPoints>,
        totalPointsRank: List<Int>,
        assignments: List<SlotAssignment>,
        maxPhase: Int,
        startDelay: Long,
    ) {
        var tickDelay = startDelay

        for (podiumRank in 3 downTo 1) {
            val podium = podiums.find { it.rank == podiumRank } ?: continue
            val targetLoc = podium.npcLocation?.clone()?.add(0.0, 1.0, 0.0) ?: continue

            val sourceLocs = slots.sortedBy {
                it.signBlock.location.distanceSquared(targetLoc)
            }.take(PARTICLE_TRAIL_SOURCES).map { it.signBlock.location.clone().add(0.5, 0.5, 0.5) }

            val trailDelay = tickDelay
            for (tick in 0..TICKS_PARTICLE_TRAIL) {
                val t = tick
                plugin.runLaterOnATick(trailDelay + t) {
                    if (!FirstRun.animating) return@runLaterOnATick
                    val progress = t.toDouble() / TICKS_PARTICLE_TRAIL
                    sourceLocs.forEach { src ->
                        val pos = src.clone().add(targetLoc.toVector().subtract(src.toVector()).multiply(progress))
                        world.spawnParticle(particleForRank(podiumRank), pos, 3, 0.05, 0.05, 0.05, 0.01)
                    }
                }
            }
            tickDelay += TICKS_PARTICLE_TRAIL + TICKS_POST_TRAIL_GAP

            val revealDelay = tickDelay
            plugin.runLaterOnATick(revealDelay) {
                if (!FirstRun.animating) return@runLaterOnATick
                podium.npcLocation?.let { npcLoc ->
                    val burstLoc = npcLoc.clone().add(0.0, 1.5, 0.0)
                    world.spawnParticle(particleForRank(podiumRank), burstLoc, 50, 0.3, 0.5, 0.3, 0.05)
                }

                val points = totalPointsRank.getOrNull(podium.rank - 1)
                val playersAtRank = if (points != null) sortedPlayers.filter { it.totalPoints == points } else emptyList()
                updatePodium(podium, playersAtRank, podium.rank, maxPhase)

                val sound = if (podiumRank == 1) "do2:events.artifact_retrived" else "do2:events.card_reveal"
                val pitch = when (podiumRank) { 1 -> 0f; 2 -> 0.5f; else -> 1f }
                animationAudience().forEach { p ->
                    p.playSound(Sound.sound(Key.key(sound), Sound.Source.MASTER, 1f, pitch))
                }

                if (podiumRank == 1) {
                    sendChatLeaderboard(assignments, maxPhase)
                    updateTitleHologram(podium, maxPhase)
                    restoreGameModes()
                    FirstRun.animating = false
                    FirstRun.running = false
                }
            }
            tickDelay += TICKS_BETWEEN_PODIUMS
        }
    }

    private fun particleForRank(rank: Int): Particle = when (rank) {
        1 -> Particle.SCULK_SOUL
        2 -> Particle.SOUL_FIRE_FLAME
        else -> Particle.SPELL_WITCH
    }

    private fun sendChatLeaderboard(assignments: List<SlotAssignment>, maxPhase: Int) {
        animationAudience().forEach { p ->
            sendLeaderboardTo(p, assignments.map { it.player }, maxPhase)
        }
    }

    fun sendLeaderboardTo(target: CommandSender, players: List<PlayerWithPoints> = playerData.values.toList(), maxPhase: Int = currentMaxPhase) {
        val sorted = players.sortedByDescending { it.totalPoints }
        if (sorted.isEmpty()) {
            target.sendMessage(Component.text("No leaderboard data available", NamedTextColor.RED))
            return
        }
        val targetName = (target as? Player)?.name
        val totalPointsRank = sorted.map { it.totalPoints }.distinct().sortedDescending()
        target.sendMessage(Component.text(""))
        target.sendMessage(Component.text("------- Leaderboard -------", NamedTextColor.GOLD))
        sorted.forEachIndexed { i, p ->
            val rank = totalPointsRank.indexOf(p.totalPoints) + 1
            val isSelf = p.player == targetName
            val color = if (isSelf) NamedTextColor.AQUA else when (rank) { 1 -> NamedTextColor.GOLD; 2 -> NamedTextColor.WHITE; 3 -> NamedTextColor.RED; else -> NamedTextColor.GRAY }
            val line = Component.text(" ${i + 1}. ${p.player} - ${p.totalPoints} pts (${p.stats[maxPhase]?.tomesSubmitted ?: 0} tomes)", color)
            target.sendMessage(if (isSelf) line.decorate(net.kyori.adventure.text.format.TextDecoration.BOLD) else line)
        }
        target.sendMessage(Component.text(""))
    }

    private fun restoreGameModes() {
        val world = plugin.server.worlds.find { it.name == "world" }
        val viewRegion = world?.regions()?.find { it.id == "view-leaderboard" }
        plugin.server.onlinePlayers.forEach { p ->
            previousGameModes[p.uniqueId]?.let { mode ->
                val inRegion = viewRegion != null && viewRegion.contains(p.location.blockX, p.location.blockY, p.location.blockZ)
                if (!inRegion) {
                    previousLocations[p.uniqueId]?.let { p.teleport(it) }
                } else {
                    val loc = p.location.clone()
                    while (loc.block.type.isSolid || loc.clone().add(0.0, 1.0, 0.0).block.type.isSolid) {
                        loc.add(0.0, 1.0, 0.0)
                    }
                    if (loc != p.location) p.teleport(loc)
                }
                p.gameMode = if (mode == GameMode.SPECTATOR) GameMode.SURVIVAL else mode
                p.addPotionEffect(PotionEffect(PotionEffectType.SLOW_FALLING, 200, 0, false, false))
            }
        }
        previousGameModes.clear()
        previousLocations.clear()
    }

    fun hideAll() {
        restoreGameModes()
        val world = plugin.server.worlds.find { it.name == "world" } ?: return
        discoverLeaderboardSlots(world).forEach { clearSlot(it) }
        discoverPodiums(world).forEach { updatePodium(it, emptyList(), it.rank, 0) }
        removeStaleNpcs(world, emptySet())
        val manager = FancyHologramsPlugin.get().hologramManager
        manager.getHologram("leaderboard-title").ifPresent { it.deleteHologram(); manager.removeHologram(it) }
    }

    private fun removeStaleNpcs(world: World, expectedNpcIds: Set<String>) {
        val registry = CitizensAPI.getNPCRegistry() ?: return
        val podiumRegions = (1..3).mapNotNull { world.regions()?.find { r -> r.id == "podium-$it" } }
        val podiumNpcLocations = discoverPodiums(world).mapNotNull { it.npcLocation }

        registry.toList().forEach { npc ->
            if (!npc.isSpawned) {
                if (npc.data().has("leaderboard-id")) npc.destroy()
                return@forEach
            }
            val loc = npc.storedLocation ?: return@forEach
            if (loc.world != world) return@forEach

            val isLeaderboardNpc = npc.data().has("leaderboard-id")
            val inPodium = podiumRegions.any { it.contains(loc.blockX, loc.blockY, loc.blockZ) }
            val nearPodium = podiumNpcLocations.any { it.distanceSquared(loc) < 9 }

            if (isLeaderboardNpc || inPodium || nearPodium) {
                val id = npc.data().get<String>("leaderboard-id") ?: ""
                if (id !in expectedNpcIds) {
                    npc.destroy()
                }
            }
        }
    }

    private fun discoverLeaderboardSlots(world: World): List<LeaderboardSlot> {
        val regions = world.regions()?.filter { it.id.startsWith("leaderboard-") } ?: return emptyList()

        val podiumRegion = world.regions()?.find { it.id == "podium-1" }
        val podiumCenter = podiumRegion?.let {
            val min = it.minimumPoint
            val max = it.maximumPoint
            Location(world, (min.x + max.x) / 2.0, (min.y + max.y) / 2.0, (min.z + max.z) / 2.0)
        }

        // Collect slots per region, sorted by distance from podium within each region
        val slotsPerRegion = regions.map { region ->
            val min = region.minimumPoint
            val max = region.maximumPoint
            val regionSlots = mutableListOf<LeaderboardSlot>()

            for (x in min.x..max.x) {
                for (y in min.y..max.y) {
                    for (z in min.z..max.z) {
                        val block = world.getBlockAt(x, y, z)
                        if (block.type != Material.WARPED_WALL_SIGN) continue

                        val signData = block.blockData as? WallSign ?: continue
                        val attachedBlock = block.getRelative(signData.facing.oppositeFace)
                        val headBlock = attachedBlock.getRelative(BlockFace.UP)
                        val snowBlock = headBlock.getRelative(signData.facing.oppositeFace)
                        regionSlots.add(LeaderboardSlot(block, headBlock, snowBlock))
                    }
                }
            }

            if (podiumCenter != null) {
                regionSlots.sortBy { it.signBlock.location.distanceSquared(podiumCenter) }
            }
            regionSlots
        }.filter { it.isNotEmpty() }.sortedBy { group ->
            // Sort region groups by their closest slot to podium
            if (podiumCenter != null) group.first().signBlock.location.distanceSquared(podiumCenter) else 0.0
        }

        // Interleave slots round-robin across regions
        val result = mutableListOf<LeaderboardSlot>()
        val iterators = slotsPerRegion.map { it.iterator() }.toMutableList()
        while (iterators.any { it.hasNext() }) {
            iterators.forEach { if (it.hasNext()) result.add(it.next()) }
        }
        return result
    }

    private fun discoverPodiums(world: World): List<PodiumRegion> {
        val podiums = mutableListOf<PodiumRegion>()

        for (rank in 1..3) {
            val region = world.regions()?.find { it.id == "podium-$rank" } ?: continue
            val min = region.minimumPoint
            val max = region.maximumPoint

            var signBlock: Block? = null

            for (x in min.x..max.x) {
                for (y in min.y..max.y) {
                    for (z in min.z..max.z) {
                        val block = world.getBlockAt(x, y, z)
                        if (block.type == Material.WARPED_WALL_SIGN && signBlock == null) {
                            signBlock = block
                        }
                    }
                }
            }

            // NPC location: 2 blocks back from sign and 1 block up
            val npcLocation = signBlock?.let { sign ->
                val signData = sign.blockData as WallSign
                val behind = signData.facing.oppositeFace
                sign.getRelative(behind, 2).getRelative(BlockFace.UP).location.add(0.5, 0.0, 0.5)
            }

            podiums.add(PodiumRegion(rank, signBlock, npcLocation))
        }

        return podiums
    }

    private fun updateSlot(slot: LeaderboardSlot, player: PlayerWithPoints, rank: Int, maxPhase: Int, snowLayers: Int) {
        val playerName = player.player
        val offlinePlayer = plugin.server.getOfflinePlayerIfCached(playerName) ?: plugin.server.getOfflinePlayer(playerName)

        // Update head
        val signData = slot.signBlock.blockData as WallSign
        val desiredRotation = signData.facing.oppositeFace
        if (slot.headBlock.type != Material.PLAYER_HEAD) {
            slot.headBlock.type = Material.PLAYER_HEAD
        }
        val skull = slot.headBlock.state as? Skull
        if (skull != null && (skull.owningPlayer?.uniqueId != offlinePlayer.uniqueId || skull.rotation != desiredRotation)) {
            skull.setOwningPlayer(offlinePlayer)
            skull.rotation = desiredRotation
            skull.update(false)
        }

        // Update sign
        val sign = slot.signBlock.state as Sign
        val signSide = sign.getSide(Side.FRONT)
        signSide.line(0, Component.text("#$rank (${player.totalPoints} points)").color(NamedTextColor.AQUA))
        signSide.line(1, Component.text(playerName).color(NamedTextColor.WHITE))
        signSide.line(2, Component.text(""))
        signSide.line(3, Component.text("Phase${maxPhase} tomes: ${player.stats.getOrDefault(maxPhase, null)?.tomesSubmitted ?: 0}").color(NamedTextColor.AQUA))
        sign.update()

        // Update snow layers behind head
        setSnowLayers(slot.snowBlock, snowLayers)
    }

    private fun clearSlot(slot: LeaderboardSlot) {
        // Clear sign text
        val sign = slot.signBlock.state as? Sign ?: return
        val signSide = sign.getSide(Side.FRONT)
        signSide.line(0, Component.text(""))
        signSide.line(1, Component.text(""))
        signSide.line(2, Component.text(""))
        signSide.line(3, Component.text(""))
        sign.update()

        // Clear head
        if (slot.headBlock.type == Material.PLAYER_HEAD || slot.headBlock.type == Material.PLAYER_WALL_HEAD) {
            slot.headBlock.type = Material.AIR
        }

        // Clear snow
        if (slot.snowBlock.type == Material.SNOW) {
            slot.snowBlock.type = Material.AIR
        }
    }

    private fun updatePodium(podium: PodiumRegion, players: List<PlayerWithPoints>, rank: Int, maxPhase: Int) {
        val signBlock = podium.signBlock ?: return
        val signData = signBlock.blockData as? WallSign ?: return
        val facing = signData.facing

        // Determine left/right relative to sign facing (perpendicular axis)
        val leftFace = when (facing) {
            BlockFace.NORTH -> BlockFace.WEST
            BlockFace.SOUTH -> BlockFace.EAST
            BlockFace.EAST -> BlockFace.NORTH
            BlockFace.WEST -> BlockFace.SOUTH
            else -> BlockFace.WEST
        }
        val rightFace = leftFace.oppositeFace

        // Main sign (always present)
        val mainPlayer = players.getOrNull(0)
        updatePodiumSign(signBlock, mainPlayer, rank, maxPhase)

        // Extra signs: 1 block below main sign for 2nd and 3rd tied players
        val extraSign1Block = signBlock.getRelative(BlockFace.DOWN)
        val extraSign2Block = extraSign1Block.getRelative(BlockFace.DOWN)

        if (players.size > 1) {
            placeWallSign(extraSign1Block, facing)
            updatePodiumSign(extraSign1Block, players[1], rank, maxPhase)
        } else {
            removeWallSign(extraSign1Block)
        }

        if (players.size > 2) {
            placeWallSign(extraSign2Block, facing)
            updatePodiumSign(extraSign2Block, players[2], rank, maxPhase)
        } else {
            removeWallSign(extraSign2Block)
        }

        // NPCs: main center, 2nd to left, 3rd to right
        val npcBaseLoc = podium.npcLocation ?: return
        val targetLoc = npcBaseLoc.clone().add(0.0, 1.0, 0.0)
        val leftLoc = targetLoc.clone().add(leftFace.modX.toDouble(), 0.0, leftFace.modZ.toDouble())
        val rightLoc = targetLoc.clone().add(rightFace.modX.toDouble(), 0.0, rightFace.modZ.toDouble())

        updatePodiumNpc("leaderboard-podium-${podium.rank}", mainPlayer, targetLoc)
        updatePodiumNpc("leaderboard-podium-${podium.rank}-left", players.getOrNull(1), leftLoc)
        updatePodiumNpc("leaderboard-podium-${podium.rank}-right", players.getOrNull(2), rightLoc)
    }

    private fun updatePodiumSign(signBlock: Block, player: PlayerWithPoints?, rank: Int, maxPhase: Int) {
        val sign = signBlock.state as? Sign ?: return
        val signSide = sign.getSide(Side.FRONT)
        if (player != null) {
            signSide.line(0, Component.text("#$rank").color(NamedTextColor.GOLD))
            signSide.line(1, Component.text(player.player).color(NamedTextColor.WHITE))
            signSide.line(2, Component.text("${player.totalPoints} points").color(NamedTextColor.AQUA))
            signSide.line(3, Component.text("Phase${maxPhase} tomes: ${player.stats.getOrDefault(maxPhase, null)?.tomesSubmitted ?: 0}").color(NamedTextColor.AQUA))
        } else {
            signSide.line(0, Component.text(""))
            signSide.line(1, Component.text(""))
            signSide.line(2, Component.text(""))
            signSide.line(3, Component.text(""))
        }
        sign.update()
    }

    private fun placeWallSign(block: Block, facing: BlockFace) {
        if (block.type != Material.WARPED_WALL_SIGN) {
            block.type = Material.WARPED_WALL_SIGN
            val data = block.blockData as WallSign
            data.facing = facing
            block.blockData = data
        }
    }

    private fun removeWallSign(block: Block) {
        if (block.type == Material.WARPED_WALL_SIGN) {
            block.type = Material.AIR
        }
    }

    private fun updatePodiumNpc(npcId: String, player: PlayerWithPoints?, location: Location) {
        val registry = CitizensAPI.getNPCRegistry() ?: return

        val existingNpc = registry.find { (it.data().has("leaderboard-id") && it.data().get<String>("leaderboard-id") == npcId) || it.name == npcId }
        registry.filter { it !== existingNpc && it.name == npcId }.forEach { it.destroy() }

        if (player != null) {
            val npc = existingNpc ?: registry.createNPC(EntityType.PLAYER, player.player).also {
                it.data().set("leaderboard-id", npcId)
            }

            if (npc.name != player.player) {
                npc.name = player.player
            }

            val skinTrait = npc.getOrAddTrait(SkinTrait::class.java)
            val currentSkin = npc.data().get<String>("leaderboard-skin")
            if (currentSkin != player.player) {
                skinTrait.setSkinName(player.player)
                npc.data().set("leaderboard-skin", player.player)
            }

            if (!npc.isSpawned) {
                npc.spawn(location)
            } else if (npc.storedLocation.distanceSquared(location) > 0.1) {
                npc.teleport(location, PlayerTeleportEvent.TeleportCause.PLUGIN)
            }
        } else {
            existingNpc?.destroy()
        }
    }

    private fun updateTitleHologram(podium: PodiumRegion?, maxPhase: Int) {
        val manager = FancyHologramsPlugin.get().hologramManager

        manager.holograms.filter { it.data.name.startsWith("leaderboard") && it.data.name != "leaderboard-title" }
            .forEach { it.deleteHologram(); manager.removeHologram(it) }

        if (podium?.signBlock == null) return
        val signData = podium.signBlock.blockData as? WallSign ?: return
        val facing = signData.facing

        val loc = podium.signBlock.location.add(0.5, HOLOGRAM_HEIGHT, 0.5)
            .add(facing.modX * 2.0, 0.0, facing.modZ * 2.0)

        val name = "leaderboard-title"
        val hologramData = TextHologramData(name, loc)
        hologramData.background = Hologram.TRANSPARENT
        hologramData.billboard = Display.Billboard.CENTER
        hologramData.scale = Vector3f(3f, 3f, 3f)
        hologramData.visibilityDistance = HOLOGRAM_VISIBILITY
        hologramData.text = listOf(
            "<gold><bold>Season 2 Leaderboard!</bold></gold>",
            "",
            "<aqua>Showing Phase <gold>${maxPhase}</gold> results</aqua>",
        )

        val existing = manager.getHologram(name).orElse(null)
        if (existing != null) {
            val data = existing.data
            if (data is TextHologramData && data.text == hologramData.text && data.location.distanceSquared(loc) < 0.1) {
                return // No change needed
            }
            existing.deleteHologram()
            manager.removeHologram(existing)
        }
        val hologram = manager.create(hologramData)
        manager.addHologram(hologram)
        hologram.queueUpdate()
    }

    private fun setSnowLayers(startBlock: Block, layers: Int) {
        for (blockIndex in 0 until 6) {
            val snowBlock = startBlock.getRelative(BlockFace.UP, blockIndex)
            val layersForBlock = min(8, layers - (blockIndex * 8))

            if (layersForBlock <= 0) {
                if (snowBlock.type != Material.AIR) {
                    snowBlock.type = Material.AIR
                }
            } else {
                if (snowBlock.type != Material.SNOW) {
                    snowBlock.type = Material.SNOW
                }
                val snow = snowBlock.blockData as Snow
                snow.layers = layersForBlock
                snowBlock.blockData = snow
            }
        }
    }

    private fun parsePhaseDataForAllPlayers(
        currentPhase: Int,
        activePlayers: MutableMap<String, PlayerWithPoints>,
        pointsForPosition: Array<Int>,
    ): Int {
        val database = MongoDBManager.getDatabase("dunga-dunga")
        var maxPhase = 0
        (1..currentPhase).forEach { phase ->
            val playerStatsCollection = database.getCollection("playerStatsPhase${phase}", MongoPlayerStats::class.java)

            val activePlayersInPhase = playerStatsCollection.find().toList().filter { it.stats.tomesSubmitted > 0 }.sortedBy { it.player }
            val tomesSubmitted = activePlayersInPhase.map { it.stats.tomesSubmitted }.distinct().sortedWith(
                compareByDescending { it }
            )
            plugin.logger.fine("[Leaderboard] Tomes submitted for Phase${phase}: $tomesSubmitted")

            if (activePlayersInPhase.isNotEmpty()) {
                maxPhase = max(maxPhase, phase)
            }

            activePlayersInPhase.forEach { player ->
                val playerWithPoints = activePlayers.getOrDefault(player.player, PlayerWithPoints(player.player, mapOf()))
                val phasePoints = pointsForPosition.getOrElse(tomesSubmitted.indexOf(player.stats.tomesSubmitted)) { 0 }

                activePlayers[player.player] = playerWithPoints.copy(
                    stats = playerWithPoints.stats + mapOf(phase to player.stats),
                    totalPoints = playerWithPoints.totalPoints + phasePoints,
                    pointsPerPhase = playerWithPoints.pointsPerPhase + mapOf(phase to phasePoints)
                )
            }
        }
        return maxPhase
    }

    data class PlayerWithPoints(
        val player: String,
        val stats: Map<Int, Stats>,
        val totalPoints: Int = 0,
        val pointsPerPhase: Map<Int, Int> = mapOf(),
    )
}

object LeaderboardConfig {
    var showLeaderboard = false
}

object LeaderboardState {
    @Volatile var running = false
    @Volatile var animating = false
}

// Backward-compatible alias
object FirstRun {
    var showLeaderboard: Boolean
        get() = LeaderboardConfig.showLeaderboard
        set(value) { LeaderboardConfig.showLeaderboard = value }
    var running: Boolean
        get() = LeaderboardState.running
        set(value) { LeaderboardState.running = value }
    var animating: Boolean
        get() = LeaderboardState.animating
        set(value) { LeaderboardState.animating = value }
}
