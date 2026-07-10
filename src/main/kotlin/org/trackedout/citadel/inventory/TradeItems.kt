package org.trackedout.citadel.inventory

import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.trackedout.actions.returnableItems
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

    "GAUNTLET_TROPHY" to object : ScoreboardDescriber {
        override fun sourceScoreboardName(runType: String): String {
            return "${runType}-do2.lifetime.gauntlet_survival"
        }

        override fun itemStack(runType: String, count: Int): ItemStack {
            return gauntletTrophy(if (count > 0) 1 else 0)
        }
    },
)

val intoDungeonItems: Map<String, ScoreboardDescriber> = mapOf(
    "SWIFTNESS_POTION" to object : ItemWithoutScoreboard {
        override fun itemStack(runType: String, count: Int): ItemStack {
            return dungeonSwiftnessPotion(getRunTypeById(runType), count).withDungeonItemLore("SWIFTNESS_POTION")
        }
    },

    "SLOWNESS_POTION" to object : ItemWithoutScoreboard {
        override fun itemStack(runType: String, count: Int): ItemStack {
            return dungeonSlownessPotion(getRunTypeById(runType), count).withDungeonItemLore("SLOWNESS_POTION")
        }
    },

    "WEAKNESS_POTION" to object : ItemWithoutScoreboard {
        override fun itemStack(runType: String, count: Int): ItemStack {
            return dungeonWeaknessPotion(getRunTypeById(runType), count).withDungeonItemLore("WEAKNESS_POTION")
        }
    },

    "HEALTH_POTION" to object : ItemWithoutScoreboard {
        override fun itemStack(runType: String, count: Int): ItemStack {
            return dungeonHealthPotion(getRunTypeById(runType), count).withDungeonItemLore("HEALTH_POTION")
        }
    },

    "CAVES_OF_CARNAGE_KEY" to object : ItemWithoutScoreboard {
        override fun itemStack(runType: String, count: Int): ItemStack {
            return dungeonKeyLevel1(getRunTypeById(runType), count).withDungeonItemLore("CAVES_OF_CARNAGE_KEY")
        }
    },

    "BLACK_MINES_KEY" to object : ItemWithoutScoreboard {
        override fun itemStack(runType: String, count: Int): ItemStack {
            return dungeonKeyLevel2(getRunTypeById(runType), count).withDungeonItemLore("BLACK_MINES_KEY")
        }
    },

    "BURNING_DARK_KEY" to object : ItemWithoutScoreboard {
        override fun itemStack(runType: String, count: Int): ItemStack {
            return dungeonKeyLevel3(getRunTypeById(runType), count).withDungeonItemLore("BURNING_DARK_KEY")
        }
    },

    "COIN" to object : ItemWithoutScoreboard {
        override fun itemStack(runType: String, count: Int): ItemStack {
            return dungeonCoin(getRunTypeById(runType), count).withDungeonItemLore("COIN")
        }
    },

    "RUSTY_REPAIR_KIT" to object : ItemWithoutScoreboard {
        override fun itemStack(runType: String, count: Int): ItemStack {
            return repairKit(getRunTypeById(runType), count).withDungeonItemLore("RUSTY_REPAIR_KIT")
        }
    },

    "COPPER_BLOCK" to object : ItemWithoutScoreboard {
        override fun itemStack(runType: String, count: Int): ItemStack {
            return basicDungeonItem("Copper Block", Material.COPPER_BLOCK, getRunTypeById(runType), count).withDungeonItemLore("COPPER_BLOCK")
        }
    },

    "EXPOSED_COPPER" to object : ItemWithoutScoreboard {
        override fun itemStack(runType: String, count: Int): ItemStack {
            return basicDungeonItem("Exposed Copper", Material.EXPOSED_COPPER, getRunTypeById(runType), count).withDungeonItemLore("EXPOSED_COPPER")
        }
    },

    "WEATHERED_COPPER" to object : ItemWithoutScoreboard {
        override fun itemStack(runType: String, count: Int): ItemStack {
            return basicDungeonItem("Weathered Copper", Material.WEATHERED_COPPER, getRunTypeById(runType), count).withDungeonItemLore("WEATHERED_COPPER")
        }
    },

    "OXIDIZED_COPPER" to object : ItemWithoutScoreboard {
        override fun itemStack(runType: String, count: Int): ItemStack {
            return basicDungeonItem("Oxidized Copper", Material.OXIDIZED_COPPER, getRunTypeById(runType), count).withDungeonItemLore("OXIDIZED_COPPER")
        }
    },

    "ICE" to object : ItemWithoutScoreboard {
        override fun itemStack(runType: String, count: Int): ItemStack {
            return basicDungeonItem("Ice", Material.ICE, getRunTypeById(runType), count).withDungeonItemLore("ICE")
        }
    },

    "PACKED_ICE" to object : ItemWithoutScoreboard {
        override fun itemStack(runType: String, count: Int): ItemStack {
            return basicDungeonItem("Packed Ice", Material.PACKED_ICE, getRunTypeById(runType), count).withDungeonItemLore("PACKED_ICE")
        }
    },

    "BLUE_ICE" to object : ItemWithoutScoreboard {
        override fun itemStack(runType: String, count: Int): ItemStack {
            return basicDungeonItem("Blue Ice", Material.BLUE_ICE, getRunTypeById(runType), count).withDungeonItemLore("BLUE_ICE")
        }
    },

    "RED_DYE" to object : ItemWithoutScoreboard {
        override fun itemStack(runType: String, count: Int): ItemStack {
            return basicDungeonItem("Red Dye", Material.RED_DYE, getRunTypeById(runType), count).withDungeonItemLore("RED_DYE")
        }
    },

)

private fun ItemStack.withDungeonItemLore(itemKey: String): ItemStack {
    return if (returnableItems.contains(itemKey)) {
        this.withLore(text("Returned to deck on win", NamedTextColor.GREEN))
    } else {
        this.withLore(text("Consumed on dungeon entry", NamedTextColor.RED))
    }
}

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
