package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.CommandHelp
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Description
import co.aikar.commands.annotation.HelpCommand
import co.aikar.commands.annotation.Subcommand
import co.aikar.commands.annotation.Syntax
import org.bukkit.Location
import org.bukkit.block.BlockFace
import org.bukkit.block.data.type.WallSign
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.async
import org.trackedout.citadel.mongo.MongoDBManager
import org.trackedout.citadel.mongo.MongoTrophy
import org.trackedout.citadel.runOnNextTick
import org.trackedout.citadel.sendGreenMessage
import org.trackedout.citadel.sendMiniMessage
import org.trackedout.citadel.sendRedMessage

val trophySections = mapOf(
    "Adventuring" to listOf(
        "Addicted", "Respiration III", "Eggselent Eggsplorer", "Deep Diver", "Sneak Master",
        "Citadel Conquerer", "Dungeon Master's Master", "Rustiest Ally", "Clink Clank Clunk", "Halloween Enthusiast"
    ),
    "Surviving" to listOf(
        "Technically the Winner", "Momentum Master", "The Real Dungeon Master", "Mummy", "Twinkletoes",
        "Elite Entrance", "Back for Seconds", "Vex the Vex", "Spelunker", "Tryhard"
    ),
    "Artifacts" to listOf(
        "Death Loop Loop", "Omen of Success", "Sanctioned Forgery", "#NeverForget", "Compass School Valedictorian",
        "Key to Success", "Well, Aren't You Popular?", "Omen of Defeat", "Sparse-ifake™", "Homing Pigeon"
    ),
    "Embers" to listOf(
        "Frost Ember Farmer", "I Don't Like Getting Shards", "Hyperfocus", "Hunter Gatherer",
        "Optimized Spending, >1750", "Optimized Spending, <1750"
    ),
    "Dying" to listOf(
        "Humble Beginnings", "Glutton for the Grave", "Participation Trophy", "Momentum Disaster",
        "Ravager Cuddler", "Vexing Vex", "Warden Hugger", "Ravager Almost-Wrangler",
        "Selective Dying", "Vex Fanatic", "Dungeon Dinner", "Unfocused Sleeping",
        "Dungeon Delicacy", "Free Samples", "Mummified", "Caved In", "Fly, You Fools!", "Tryharder"
    ),
    "Cards and Deck" to listOf(
        "Card Collector", "Collecting 'Em All", "Variety Pack", "Thorougly Complete",
        "Ember Extravagence", "Sumptuous Sidelines", "Overpowered",
        "Penny Pincher: Caves", "Penny Pincher: Mines", "Penny Pincher: Dark",
        "It's how you use it: Caves", "It's how you use it: Mines", "It's how you use it: Dark",
        "Retail Therapy", "Bulk Buyer", "Window Shopper", "High Roller", "Ethereal Extraordinaire",
        "Clear as Day", "Planned In Advance", "Porkchop Powered", "Premium Member Bonus",
        "The True Ninja", "Keeping Your Options Open", "Greed is Good", "Frosted Tips",
        "Got the Zoomies", "Infinity Mending", "Fool of a Took!", "Harrowing Hazards", "Tripping", "Going Green"
    ),
    "Crowns" to listOf(
        "Reigning Monarch", "Sticky Fingers", "Crowning Achievement", "Tunnel Vision"
    ),
    "Misc" to listOf(
        "First In Line", "Is It Over?", "Dungeon Hogger", "Waiting Room Camper",
        "Polarizing Pizza Master", "One at a Time", "Taking Turns", "Bug Magnet", "Dungeon, You Feeling Old Yet?"
    ),
    "Phases" to listOf(
        "Shard Farmer", "Maximum Efficiency", "Rising Star", "Falling Star",
        "Minimum Efficiency", "The Infinity Shard", "Feed the Need"
    ),
)

val trophySectionNames = trophySections.keys.toList()

@CommandAlias("tots")
class TrophyCommand(
    private val plugin: Citadel,
) : BaseCommand() {

    @HelpCommand
    fun doHelp(help: CommandHelp) {
        help.showHelp()
    }

    private fun getTrophies(): List<MongoTrophy> {
        val database = MongoDBManager.getDatabase("dunga-dunga")
        val collection = database.getCollection("trophies", MongoTrophy::class.java)
        return collection.find().toList()
    }

    @Subcommand("tp")
    @CommandCompletion("@trophyNames")
    @CommandPermission("decked-out.tots.tp")
    @Description("Teleport to a trophy")
    fun teleportToTrophy(player: Player, name: String) {
        plugin.async(player) {
            val trophies = getTrophies()
            val trophy = trophies.find { it.totKey.equals(name, ignoreCase = true) }
            if (trophy == null) {
                val knownTrophy = trophySections.values.flatten().find { it.equals(name, ignoreCase = true) }
                if (knownTrophy != null) {
                    player.sendRedMessage("Trophy '$knownTrophy' exists but has no data yet (unclaimed)")
                } else {
                    player.sendRedMessage("Trophy '$name' not found")
                }
                return@async
            }

            val world = plugin.server.worlds.find { it.name == "world" } ?: return@async

            plugin.runOnNextTick {
                val signBlock = world.getBlockAt(trophy.sign.x, trophy.sign.y, trophy.sign.z)
                val signData = signBlock.blockData as? WallSign
                val facing = signData?.facing ?: BlockFace.NORTH

                // Stand two blocks in front of the sign (in the direction the sign faces)
                val loc = Location(
                    world,
                    trophy.sign.x + facing.modX * 2 + 0.5,
                    trophy.sign.y.toDouble(),
                    trophy.sign.z + facing.modZ * 2 + 0.5,
                )
                // Face toward the sign (opposite of sign's facing direction)
                loc.yaw = when (facing.oppositeFace) {
                    BlockFace.SOUTH -> 0f
                    BlockFace.WEST -> 90f
                    BlockFace.NORTH -> 180f
                    BlockFace.EAST -> -90f
                    else -> 0f
                }

                player.teleport(loc)
                player.sendGreenMessage("Teleported to trophy: ${trophy.totKey}")
            }
        }
    }

    @Subcommand("list")
    @CommandCompletion("@trophySections")
    @CommandPermission("decked-out.tots.list")
    @Description("List all trophies in a section")
    fun listSection(source: CommandSender, section: String) {
        plugin.async(source) {
            val trophies = getTrophies()
            val sectionEntry = trophySections.entries.find { it.key.equals(section, ignoreCase = true) }
            if (sectionEntry == null) {
                source.sendRedMessage("Unknown section '$section'. Valid: ${trophySectionNames.joinToString(", ")}")
                return@async
            }

            source.sendMiniMessage("<gold>--- ${sectionEntry.key} ---</gold>")
            sectionEntry.value.forEach { totKey ->
                val trophy = trophies.find { it.totKey == totKey }
                if (trophy?.player != null) {
                    source.sendMiniMessage("<yellow>$totKey</yellow> - <green>${trophy.player}</green> <gray>${trophy.value ?: ""}</gray>")
                } else {
                    source.sendMiniMessage("<yellow>$totKey</yellow> - <gray>unclaimed</gray>")
                }
            }
        }
    }

    @Subcommand("search")
    @Syntax("<trophy name | player name | section | keyword>")
    @CommandPermission("decked-out.tots.search")
    @Description("Search trophies - matches trophy name, player name, section, or description")
    fun search(source: CommandSender, query: String) {
        plugin.async(source) {
            val trophies = getTrophies()
            val q = query.lowercase()

            val matches = trophies.filter { trophy ->
                trophy.totKey.lowercase().contains(q) ||
                    trophy.player?.lowercase()?.contains(q) == true ||
                    trophy.description?.lowercase()?.contains(q) == true ||
                    trophy.value?.lowercase()?.contains(q) == true ||
                    getSectionForTrophy(trophy.totKey)?.lowercase()?.contains(q) == true
            }

            if (matches.isEmpty()) {
                source.sendRedMessage("No trophies matching '$query'")
                return@async
            }

            source.sendMiniMessage("<gold>--- Search: $query (${matches.size} results) ---</gold>")
            matches.forEach { trophy ->
                val section = getSectionForTrophy(trophy.totKey) ?: "Unknown"
                val player = trophy.player ?: "unclaimed"
                val value = trophy.value ?: ""
                source.sendMiniMessage("<gray>[$section]</gray> <yellow>${trophy.totKey}</yellow> - <green>$player</green> <gray>$value</gray>")
            }
        }
    }

    @Subcommand("info")
    @CommandPermission("decked-out.tots.info")
    @Description("Get info about the trophy you're looking at")
    fun info(player: Player) {
        plugin.async(player) {
            val trophies = getTrophies()

            // Find nearest trophy to player
            val world = plugin.server.worlds.find { it.name == "world" } ?: return@async
            val playerLoc = player.location

            val nearest = trophies.minByOrNull { trophy ->
                val tLoc = Location(world, trophy.sign.x.toDouble(), trophy.sign.y.toDouble(), trophy.sign.z.toDouble())
                tLoc.distanceSquared(playerLoc)
            }

            if (nearest == null) {
                player.sendRedMessage("No trophies found")
                return@async
            }

            val dist = Location(world, nearest.sign.x.toDouble(), nearest.sign.y.toDouble(), nearest.sign.z.toDouble())
                .distance(playerLoc)

            if (dist > 5) {
                player.sendRedMessage("No trophy nearby (nearest is ${nearest.totKey} at ${String.format("%.1f", dist)} blocks away)")
                return@async
            }

            val section = getSectionForTrophy(nearest.totKey) ?: "Unknown"
            player.sendMiniMessage("<gold>--- Trophy Info ---</gold>")
            player.sendMiniMessage("<yellow>Name:</yellow> ${nearest.totKey}")
            player.sendMiniMessage("<yellow>Section:</yellow> $section")
            player.sendMiniMessage("<yellow>Player:</yellow> ${nearest.player ?: "<gray>unclaimed</gray>"}")
            player.sendMiniMessage("<yellow>Value:</yellow> ${nearest.value ?: "N/A"}")
            player.sendMiniMessage("<yellow>Description:</yellow> ${nearest.description ?: "N/A"}")
            player.sendMiniMessage("<yellow>Location:</yellow> ${nearest.sign.x}, ${nearest.sign.y}, ${nearest.sign.z}")
        }
    }

    private fun getSectionForTrophy(totKey: String): String? {
        return trophySections.entries.find { it.value.contains(totKey) }?.key
    }
}
