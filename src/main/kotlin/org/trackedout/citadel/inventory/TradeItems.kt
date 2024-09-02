package org.trackedout.citadel.inventory

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.trackedout.data.Cards

interface ScoreboardDescriber {
    fun sourceScoreboardName(runType: String): String
    fun sourceInversionScoreboardName(runType: String): String = sourceScoreboardName(runType)
    fun targetScoreboardName(runType: String): String = sourceScoreboardName(runType)
    fun itemStack(runType: String, count: Int): ItemStack
}

val baseTradeItems: Map<String, ScoreboardDescriber> = mapOf(
    "DUMMY" to object : ScoreboardDescriber {
        override fun sourceScoreboardName(runType: String): String = ""

        override fun itemStack(runType: String, count: Int): ItemStack = ItemStack(Material.STICK)
    },

    "QUEUE" to object : ScoreboardDescriber {
        override fun sourceScoreboardName(runType: String): String = "queue"

        override fun itemStack(runType: String, count: Int): ItemStack = ItemStack(Material.STICK)
    },

    "CROWN" to object : ScoreboardDescriber {
        override fun sourceScoreboardName(runType: String): String {
            return "${runType}-do2.lifetime.escaped.crowns"
        }

        override fun sourceInversionScoreboardName(runType: String): String {
            return "${runType}-do2.lifetime.spent.crowns"
        }

        override fun itemStack(runType: String, count: Int): ItemStack {
            return when (runType) {
                "competitive" -> competitiveCrown(count)
                else -> practiceCrown(count)
            }
        }
    },

    "TOME" to object : ScoreboardDescriber {
        override fun sourceScoreboardName(runType: String): String {
            return "${runType}-do2.lifetime.escaped.tomes"
        }

        override fun sourceInversionScoreboardName(runType: String): String {
            return "${runType}-do2.lifetime.spent.tomes"
        }

        override fun itemStack(runType: String, count: Int): ItemStack {
            return when (runType) {
                "competitive" -> competitiveTome(count)
                else -> practiceTome(count)
            }
        }
    },

    "SHARD" to object : ScoreboardDescriber {
        override fun sourceScoreboardName(runType: String): String {
            return "do2.inventory.shards.${runType}"
        }

        override fun itemStack(runType: String, count: Int): ItemStack {
            return when (runType) {
                "competitive" -> competitiveShard(count)
                else -> practiceShard(count)
            }
        }
    },

    "SLOWNESS_POTION" to object : ScoreboardDescriber {
        override fun sourceScoreboardName(runType: String): String {
            return "$runType-do2.lifetime.escaped.slowness_potion"
        }

        override fun itemStack(runType: String, count: Int): ItemStack {
            return when (runType) {
                "competitive" -> competitiveSlownessPotion(count)
                else -> practiceSlownessPotion(count)
            }
        }
    }

)

val tradeItems = baseTradeItems.plus(cardDescribers())


fun cardDescribers(): Map<String, ScoreboardDescriber> {
    return Cards.Companion.Card.entries.associate {
        it.key.uppercase() to object : ScoreboardDescriber {
            override fun sourceScoreboardName(runType: String): String {
                return ""
            }

            override fun itemStack(runType: String, count: Int): ItemStack {
                TODO("Not yet implemented")
            }
        }
    }
}
