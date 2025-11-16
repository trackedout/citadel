package org.trackedout.citadel.inventory

import com.saicone.rtag.RtagItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.Potion
import org.bukkit.potion.PotionType
import org.trackedout.citadel.displayNamedText
import org.trackedout.data.RunType

fun dungeonShard(runType: RunType, itemCount: Int): ItemStack {
    return dungeonShard("${runType.displayName} runs", runType.displayNamedText(), itemCount)
}

fun dungeonShard(
    name: String,
    textColor: NamedTextColor = NamedTextColor.AQUA,
    itemCount: Int = 1,
) = dungeonItem(name, 7, textColor, itemCount = itemCount)

fun dungeonShardFragment(runType: RunType, itemCount: Int): ItemStack {
    return dungeonShardFragment("❄☠ Shard Fragment (${runType.displayName}) ☠❄", runType.displayNamedText(), itemCount)
}

fun dungeonShardFragment(
    name: String,
    textColor: NamedTextColor = NamedTextColor.AQUA,
    itemCount: Int = 1,
) = dungeonItem(name, 1, textColor, Material.AMETHYST_SHARD, itemCount)

fun dungeonCrown(
    runType: RunType,
    itemCount: Int,
) = dungeonCrown(runType.displayName, runType.displayNamedText(), itemCount)

fun dungeonCrown(
    crownType: String,
    textColor: NamedTextColor = NamedTextColor.AQUA,
    itemCount: Int = 1,
) = dungeonItem("❄☠ Decked Out Crown (${crownType}) ☠❄", 2, textColor, itemCount = itemCount)

fun dungeonTome(
    runType: RunType,
    itemCount: Int,
) = dungeonTome(runType.displayName, runType.displayNamedText(), itemCount)

fun dungeonTome(
    runType: String,
    textColor: NamedTextColor = NamedTextColor.AQUA,
    itemCount: Int = 1,
) = dungeonItem("❄☠ Victory Tome (${runType}) ☠❄", 6, textColor, itemCount = itemCount)

fun dungeonSlownessPotion(
    runType: RunType,
    itemCount: Int,
) = dungeonSlownessPotion(runType.displayName, runType.displayNamedText(), itemCount)

fun dungeonSlownessPotion(
    runType: String,
    textColor: NamedTextColor = NamedTextColor.AQUA,
    itemCount: Int = 1,
): ItemStack {
    val potion = Potion(PotionType.SLOWNESS, 2)
    potion.isSplash = true
    val itemStack = potion.toItemStack(if (itemCount <= 0) 999 else itemCount)

    val meta = itemStack.itemMeta
    meta.displayName(Component.text("Splash Potion of Slowness (${runType})", textColor))
    meta.setCustomModelData(-1)

    itemStack.itemMeta = meta

    val metadata = mapOf(
        "deckId" to "${runType.shortRunType()}1",
        "tradeId" to "SLOWNESS_POTION"
    )
    return RtagItem.edit(itemStack, fun(tag: RtagItem): ItemStack {
        metadata.entries.forEach { entry -> tag.set(entry.value, entry.key) }
        return tag.loadCopy()
    })
}

fun dungeonKeyLevel1(runType: RunType, count: Int): ItemStack {
    return dungeonKey("❄☠ The Caves of Carnage Key (${runType.displayName}) ☠❄", 201, runType.displayNamedText(), count)
}

fun dungeonKeyLevel2(runType: RunType, count: Int): ItemStack {
    return dungeonKey("❄☠ The Black Mines Key (${runType.displayName}) ☠❄", 203, runType.displayNamedText(), count)
}

fun dungeonKeyLevel3(runType: RunType, count: Int): ItemStack {
    return dungeonKey("❄☠ The Burning Dark Key (${runType.displayName}) ☠❄", 209, runType.displayNamedText(), count)
}

fun dungeonCoin(runType: RunType, count: Int): ItemStack {
    return dungeonKey("❄☠ Decked Out Coin (${runType.displayName}) ☠❄", 1, runType.displayNamedText(), count)
}

fun dungeonKey(
    name: String,
    customModelData: Int,
    textColor: NamedTextColor = NamedTextColor.AQUA,
    itemCount: Int = 1,
) = dungeonItem(name, customModelData, textColor, itemCount = itemCount)

fun repairKit(runType: RunType, count: Int): ItemStack {
    return dungeonItem(
        "❄☠ Rusty Repair Kit (${runType.displayName}) ☠❄",
        2,
        runType.displayNamedText(),
        Material.IRON_INGOT,
        count
    )
}

fun dungeonItem(
    name: String,
    customModelData: Int,
    textColor: NamedTextColor = NamedTextColor.AQUA,
    material: Material = Material.IRON_NUGGET,
    itemCount: Int = 1,
    metadata: Map<String, String> = mapOf(),
): ItemStack {
    val itemStack = ItemStack(material, if (itemCount <= 0) 999 else itemCount)
    val meta = itemStack.itemMeta
    meta.displayName(Component.text(name, textColor))
    meta.setCustomModelData(customModelData)

    itemStack.itemMeta = meta

    return RtagItem.edit(itemStack, fun(tag: RtagItem): ItemStack {
        metadata.entries.forEach { entry -> tag.set(entry.value, entry.key) }
        return tag.loadCopy()
    })
}

fun dungeonDeck(runType: RunType, itemCount: Int = 1): ItemStack = dungeonItem(
    name = "❄☠ Frozen Assets (${runType.displayName} Deck #1) ☠❄",
    customModelData = 7,
    textColor = runType.displayNamedText(),
    material = Material.valueOf(runType.deckMaterial),
    metadata = mapOf("deckId" to "${runType.shortId}1"),
    itemCount = itemCount,
)

fun dungeonArtifacts(runType: RunType, itemCount: Int = 1): ItemStack = dungeonItem(
    name = "❄☠ Collected Artifakes (${runType.displayName}) ☠❄",
    customModelData = 36,
    textColor = runType.displayNamedText(),
    metadata = mapOf("deckId" to "${runType.shortId}1", "action" to "show-artifakes"),
    itemCount = itemCount,
)

fun menuBook(): ItemStack = dungeonItem(
    name = "❄☠ Welcome to Tracked Out ☠❄",
    material = Material.WRITTEN_BOOK,
    textColor = NamedTextColor.GOLD,
    metadata = mapOf("deckId" to "", "action" to "show-main-menu"),
    itemCount = 1,
    customModelData = 0,
)

fun ItemStack.withTradeMeta(runType: String, tradeId: String): ItemStack {
    var metadata = mapOf(
        "deckId" to "${runType.shortRunType()}1",
        "tradeId" to tradeId
    )

    if (intoDungeonItems.containsKey(tradeId)) {
        metadata = metadata.plus("canTakeIntoDungeon" to "1")
    }

    return RtagItem.edit(this, fun(tag: RtagItem): ItemStack {
        metadata.entries.forEach { entry -> tag.set(entry.value, entry.key) }
        return tag.loadCopy()
    })
}

fun ItemStack.oldDungeonItem(): ItemStack {
    this.amount = 999
    return RtagItem.edit(this, fun(tag: RtagItem): ItemStack {
        tag.remove("deckId")
        tag.remove("tradeId")
        tag.remove("canTakeIntoDungeon")
        return tag.loadCopy()
    })
}
