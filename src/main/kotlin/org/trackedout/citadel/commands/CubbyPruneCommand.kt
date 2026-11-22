package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Description
import co.aikar.commands.annotation.Optional
import co.aikar.commands.annotation.Subcommand
import java.time.Duration
import java.time.Instant
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.cubby.CubbyFlags
import org.trackedout.citadel.cubby.CubbyReset
import org.trackedout.citadel.cubby.CubbyWorld
import org.trackedout.citadel.cubby.InactiveCubbyOwners
import org.trackedout.citadel.cubby.KnownPlayer
import org.trackedout.citadel.cubby.ownerNames
import org.trackedout.citadel.mongo.MongoDBManager
import org.trackedout.citadel.mongo.MongoPlayer
import org.trackedout.citadel.sendGreenMessage
import org.trackedout.citadel.sendMessage
import org.trackedout.citadel.sendRedMessage

private const val STAFF_NAMES_KEY = "cubby.staff-names"
private const val RESETS_PER_TICK = 2
private const val TICKS_BETWEEN_BATCHES = 1L

/** Finds cubbies whose owners have not played for a while, and resets them when confirmed. */
@CommandAlias("decked-out|do")
@CommandPermission("decked-out.cubby.admin")
class CubbyPruneCommand(
    private val plugin: Citadel,
    private val reset: CubbyReset,
) : BaseCommand() {

    @Subcommand("cubby prune")
    @Description("List cubbies whose owners were last seen over <days> days ago, and reset them with --confirm")
    fun pruneCubbies(source: CommandSender, days: Int, @Optional args: Array<String>?) {
        val flags = CubbyFlags.of(args)
        val cubbyWorld = (if (source is Player) source.world else plugin.server.worlds.find { it.name == "world" })
            ?.let { CubbyWorld(it) }
        if (cubbyWorld == null) {
            source.sendRedMessage("World not found")
            return
        }
        if (days < 1) {
            source.sendRedMessage("Days must be at least 1")
            return
        }

        val cubbiesByOwner = cubbiesByOwner(cubbyWorld)
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val knownPlayers = loadKnownPlayers()
            plugin.server.scheduler.runTask(plugin, Runnable { report(source, cubbyWorld, cubbiesByOwner, knownPlayers, days, flags) })
        })
    }

    private fun report(
        source: CommandSender,
        cubbyWorld: CubbyWorld,
        cubbiesByOwner: Map<String, List<String>>,
        knownPlayers: Map<String, KnownPlayer>,
        days: Int,
        flags: CubbyFlags,
    ) {
        val now = Instant.now()
        val staff = plugin.config.getStringList(STAFF_NAMES_KEY).toSet()
        val candidates = InactiveCubbyOwners.find(cubbiesByOwner, staff, knownPlayers, now.minus(Duration.ofDays(days.toLong())))

        val table = InactiveCubbyOwners.table(candidates.inactive, now)
        source.sendGreenMessage("--- Inactive cubby owners (${candidates.inactive.size}), not seen for $days days ---")
        table.forEach { source.sendMessage(it, NamedTextColor.WHITE) }
        plugin.logger.info("${source.name} listed ${candidates.inactive.size} inactive cubby owner(s) (>$days days):\n${table.joinToString("\n")}")
        source.sendMessage("Skipped ${candidates.staffSkipped} staff owner(s)", NamedTextColor.GRAY)
        if (candidates.unmatched.isNotEmpty()) {
            source.sendMessage("--- Owners with no player record (renamed?), never reset automatically ---", NamedTextColor.YELLOW)
            candidates.unmatched.forEach { (owner, cubbies) -> source.sendMessage("$owner  ${cubbies.joinToString(",")}", NamedTextColor.YELLOW) }
        }

        val cubbyIds = candidates.inactive.flatMap { it.cubbyIds }
        if (!flags.confirmed) {
            source.sendMessage("Nothing was changed. Add --confirm to reset ${cubbyIds.size} cubby(ies), archiving each first.", NamedTextColor.GRAY)
            return
        }
        resetInBatches(source, cubbyWorld, ArrayDeque(cubbyIds))
    }

    private fun resetInBatches(source: CommandSender, cubbyWorld: CubbyWorld, queue: ArrayDeque<String>) {
        val total = queue.size
        val failures = mutableListOf<String>()

        object : BukkitRunnable() {
            override fun run() {
                repeat(RESETS_PER_TICK) {
                    val cubbyId = queue.removeFirstOrNull() ?: return@repeat
                    resetOne(cubbyWorld, cubbyId)?.let { failures += it }
                }
                if (queue.isEmpty()) {
                    cancel()
                    reset.saveRegions(cubbyWorld.world)
                    source.sendGreenMessage("Reset ${total - failures.size} of $total cubby(ies)")
                    failures.forEach { source.sendRedMessage(it) }
                }
            }
        }.runTaskTimer(plugin, 0L, TICKS_BETWEEN_BATCHES)
    }

    /** Returns why the cubby was skipped, or null once it has been reset. */
    private fun resetOne(cubbyWorld: CubbyWorld, cubbyId: String): String? {
        val cubby = cubbyWorld.find(cubbyId) ?: return "$cubbyId: region no longer exists"
        val plan = reset.planReset(cubbyWorld, cubby)
        if (!plan.isValid) return "$cubbyId skipped: ${plan.problems.joinToString("; ")}"

        reset.execute(cubbyWorld, plan)
        plugin.logger.info("Pruned $cubbyId, previously owned by ${plan.owners.joinToString()}")
        return null
    }

    private fun cubbiesByOwner(cubbyWorld: CubbyWorld): Map<String, List<String>> =
        cubbyWorld.cubbies
            .flatMap { cubby -> cubby.ownerNames().map { it to cubby.id } }
            .groupBy({ it.first }, { it.second })

    /** Blocking: call from an async task */
    private fun loadKnownPlayers(): Map<String, KnownPlayer> {
        val players = MongoDBManager.getDatabase().getCollection("players", MongoPlayer::class.java)
        return players.find().toList().associate { it.playerName.lowercase() to KnownPlayer(it.playerName, it.lastSeen) }
    }
}
