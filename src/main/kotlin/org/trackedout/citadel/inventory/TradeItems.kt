package org.trackedout.citadel.inventory

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.trackedout.data.Cards

interface ScoreboardDescriber {
    fun sourceScoreboardName(trade: Trade): String
    fun sourceInversionScoreboardName(trade: Trade): String = sourceScoreboardName(trade)
    fun targetScoreboardName(trade: Trade): String = sourceScoreboardName(trade)
    fun itemStack(runType: String, count: Int): ItemStack
}

val tradeItems: Map<String, ScoreboardDescriber> = mapOf(
    "DUMMY" to object : ScoreboardDescriber {
        override fun sourceScoreboardName(trade: Trade): String = ""

        override fun itemStack(runType: String, count: Int): ItemStack = ItemStack(Material.STICK)
    },

    "CROWN" to object : ScoreboardDescriber {
        override fun sourceScoreboardName(trade: Trade): String {
            return "${trade.runType}-do2.lifetime.escaped.crowns"
        }

        override fun sourceInversionScoreboardName(trade: Trade): String {
            return "${trade.runType}-do2.lifetime.spent.crowns"
        }

        override fun itemStack(runType: String, count: Int): ItemStack = dungeonCrown(runType[0].uppercase() + runType.substring(1), itemCount = count)
    },

    "SHARD" to object : ScoreboardDescriber {
        override fun sourceScoreboardName(trade: Trade): String {
            return "do2.inventory.shards.${trade.runType}"
        }

        override fun itemStack(runType: String, count: Int): ItemStack {
            return when (runType) {
                "competitive" -> dungeonShard(runType[0].uppercase() + runType.substring(1), itemCount = count)
                else -> dungeonShard("Practice runs (infinite!?)", itemCount = count)
            }
        }
    }

).plus(cardDescribers())

fun cardDescribers(): Map<String, ScoreboardDescriber> {
    return Cards.Companion.Card.entries.associate {
        it.key.uppercase() to object : ScoreboardDescriber {
            override fun sourceScoreboardName(trade: Trade): String {
                TODO("Not yet implemented")
            }

            override fun itemStack(runType: String, count: Int): ItemStack {
                TODO("Not yet implemented")
            }
        }
    }
}
