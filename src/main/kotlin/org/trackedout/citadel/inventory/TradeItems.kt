package org.trackedout.citadel.inventory

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.trackedout.citadel.config.cardConfig
import org.trackedout.data.getRunTypeById

interface ScoreboardDescriber {
    fun sourceScoreboardName(runType: String): String
    fun sourceInversionScoreboardName(runType: String): String = sourceScoreboardName(runType)
    fun targetScoreboardName(runType: String): String = sourceScoreboardName(runType)
    fun itemStack(runType: String, count: Int): ItemStack
}

interface ItemWithoutScoreboard : ScoreboardDescriber {
    override fun sourceScoreboardName(runType: String): String {
        return ""
    }
}

val queueItems: Map<String, ScoreboardDescriber> = mapOf(
    "DUMMY" to object : ScoreboardDescriber {
        override fun sourceScoreboardName(runType: String): String = ""

        override fun itemStack(runType: String, count: Int): ItemStack = ItemStack(Material.STICK)
    },

    "QUEUE" to object : ScoreboardDescriber {
        override fun sourceScoreboardName(runType: String): String = "queue"

        override fun itemStack(runType: String, count: Int): ItemStack = ItemStack(Material.STICK)
    }
)

val baseTradeItems: Map<String, ScoreboardDescriber> = mapOf(
    "CROWN" to object : ScoreboardDescriber {
        override fun sourceScoreboardName(runType: String): String {
            return "${runType}-do2.lifetime.escaped.crowns"
        }

        override fun sourceInversionScoreboardName(runType: String): String {
            return "${runType}-do2.lifetime.spent.crowns"
        }

        override fun itemStack(runType: String, count: Int): ItemStack {
            return dungeonCrown(getRunTypeById(runType), count)
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
            return dungeonTome(getRunTypeById(runType), count)
        }
    },

    "SHARD" to object : ScoreboardDescriber {
        override fun sourceScoreboardName(runType: String): String {
            return "do2.inventory.shards.${runType}"
        }

        override fun itemStack(runType: String, count: Int): ItemStack {
            return dungeonShard(getRunTypeById(runType), count)
        }
    },

    "SHARD_FRAGMENT" to object : ScoreboardDescriber {
        override fun sourceScoreboardName(runType: String): String {
            return "${runType}-do2.lifetime.escaped.shard_fragments"
        }

        override fun sourceInversionScoreboardName(runType: String): String {
            return "${runType}-do2.lifetime.spent.shard_fragments"
        }

        override fun itemStack(runType: String, count: Int): ItemStack {
            return dungeonShardFragment(getRunTypeById(runType), count)
        }
    },
)

val intoDungeonItems: Map<String, ScoreboardDescriber> = mapOf(
    "SLOWNESS_POTION" to object : ItemWithoutScoreboard {
        override fun itemStack(runType: String, count: Int): ItemStack {
            return dungeonSlownessPotion(getRunTypeById(runType), count)
        }
    },

    "CAVES_OF_CARNAGE_KEY" to object : ItemWithoutScoreboard {
        override fun itemStack(runType: String, count: Int): ItemStack {
            return dungeonKeyLevel1(getRunTypeById(runType), count)
        }
    },

    "BLACK_MINES_KEY" to object : ItemWithoutScoreboard {
        override fun itemStack(runType: String, count: Int): ItemStack {
            return dungeonKeyLevel2(getRunTypeById(runType), count)
        }
    },

    "BURNING_DARK_KEY" to object : ItemWithoutScoreboard {
        override fun itemStack(runType: String, count: Int): ItemStack {
            return dungeonKeyLevel3(getRunTypeById(runType), count)
        }
    },

    "COIN" to object : ItemWithoutScoreboard {
        override fun itemStack(runType: String, count: Int): ItemStack {
            return dungeonCoin(getRunTypeById(runType), count)
        }
    },

    "RUSTY_REPAIR_KIT" to object : ItemWithoutScoreboard {
        override fun itemStack(runType: String, count: Int): ItemStack {
            return repairKit(getRunTypeById(runType), count)
        }
    },

    "COPPER_BLOCK" to object : ItemWithoutScoreboard {
        override fun itemStack(runType: String, count: Int): ItemStack {
            return basicDungeonItem("Copper Block", Material.COPPER_BLOCK, getRunTypeById(runType), count)
        }
    },

    "EXPOSED_COPPER" to object : ItemWithoutScoreboard {
        override fun itemStack(runType: String, count: Int): ItemStack {
            return basicDungeonItem("Exposed Copper", Material.EXPOSED_COPPER, getRunTypeById(runType), count)
        }
    },

    "WEATHERED_COPPER" to object : ItemWithoutScoreboard {
        override fun itemStack(runType: String, count: Int): ItemStack {
            return basicDungeonItem("Weathered Copper", Material.WEATHERED_COPPER, getRunTypeById(runType), count)
        }
    },

    "OXIDIZED_COPPER" to object : ItemWithoutScoreboard {
        override fun itemStack(runType: String, count: Int): ItemStack {
            return basicDungeonItem("Oxidized Copper", Material.OXIDIZED_COPPER, getRunTypeById(runType), count)
        }
    },

    "ICE" to object : ItemWithoutScoreboard {
        override fun itemStack(runType: String, count: Int): ItemStack {
            return basicDungeonItem("Ice", Material.ICE, getRunTypeById(runType), count)
        }
    },

    "PACKED_ICE" to object : ItemWithoutScoreboard {
        override fun itemStack(runType: String, count: Int): ItemStack {
            return basicDungeonItem("Packed Ice", Material.PACKED_ICE, getRunTypeById(runType), count)
        }
    },

    "BLUE_ICE" to object : ItemWithoutScoreboard {
        override fun itemStack(runType: String, count: Int): ItemStack {
            return basicDungeonItem("Blue Ice", Material.BLUE_ICE, getRunTypeById(runType), count)
        }
    },

)

val tradeItems = baseTradeItems.plus(cardDescribers()).plus(intoDungeonItems)

val tradeItemsWithQueueTypes = tradeItems.plus(queueItems)

fun cardDescribers(): Map<String, ScoreboardDescriber> {
    return cardConfig.entries.associate {
        it.key to object : ScoreboardDescriber {
            override fun sourceScoreboardName(runType: String): String {
                return ""
            }

            override fun itemStack(runType: String, count: Int): ItemStack {
                TODO("Not yet implemented")
            }
        }
    }
}
