package org.trackedout.citadel.inventory

import com.saicone.rtag.RtagItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.Potion
import org.bukkit.potion.PotionType


fun practiceShard(value: Int) = dungeonShard("Practice runs", NamedTextColor.GREEN, itemCount = value)

fun competitiveShard(value: Int) = dungeonShard("Competitive runs", itemCount = value)

fun dungeonShard(
    name: String,
    textColor: NamedTextColor = NamedTextColor.AQUA,
    itemCount: Int = 1,
) = dungeonItem(name, 7, textColor, itemCount = itemCount)


fun practiceCrown(itemCount: Int) = dungeonCrown("Practice", NamedTextColor.GREEN, itemCount = itemCount)

fun competitiveCrown(itemCount: Int) = dungeonCrown("Competitive", itemCount = itemCount)

fun dungeonCrown(
    crownType: String,
    textColor: NamedTextColor = NamedTextColor.AQUA,
    itemCount: Int = 1,
) = dungeonItem("❄☠ Decked Out Crown (${crownType}) ☠❄", 2, textColor, itemCount = itemCount)


fun practiceTome(itemCount: Int) = dungeonTome("Practice", NamedTextColor.GREEN, itemCount = itemCount)

fun competitiveTome(itemCount: Int) = dungeonTome("Competitive", itemCount = itemCount)

fun dungeonTome(
    runType: String,
    textColor: NamedTextColor = NamedTextColor.AQUA,
    itemCount: Int = 1,
) = dungeonItem("❄☠ Victory Tome (${runType}) ☠❄", 6, textColor, itemCount = itemCount)


fun practiceSlownessPotion(itemCount: Int) = dungeonSlownessPotion("Practice", NamedTextColor.GREEN, itemCount = itemCount)

fun competitiveSlownessPotion(itemCount: Int) = dungeonSlownessPotion("Competitive", itemCount = itemCount)

fun dungeonSlownessPotion(
    runType: String,
    textColor: NamedTextColor = NamedTextColor.AQUA,
    itemCount: Int = 1,
): ItemStack {
    val potion = Potion(PotionType.SLOWNESS, 2);
    potion.isSplash = true;
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
        return tag.loadCopy();
    })

    return itemStack
}


fun key1Practice(count: Int) = practiceKey("❄☠ The Caves of Carnage Key (Practice) ☠❄", 201, count)
fun key1Competitive(count: Int) = competitiveKey("❄☠ The Caves of Carnage Key (Competitive) ☠❄", 201, count)

fun key2Practice(count: Int) = practiceKey("❄☠ The Black Mines Key (Practice) ☠❄", 203, count)
fun key2Competitive(count: Int) = competitiveKey("❄☠ The Black Mines Key (Competitive) ☠❄", 203, count)

fun key3Practice(count: Int) = practiceKey("❄☠ The Burning Dark Key (Practice) ☠❄", 209, count)
fun key3Competitive(count: Int) = competitiveKey("❄☠ The Burning Dark Key (Competitive) ☠❄", 209, count)

fun practiceCoin(count: Int) = practiceKey("❄☠ Decked Out Coin (Practice) ☠❄", 1, count)
fun competitiveCoin(count: Int) = competitiveKey("❄☠ Decked Out Coin (Competitive) ☠❄", 1, count)

fun practiceKey(name: String, customModelData: Int, count: Int) = dungeonKey(name, customModelData, NamedTextColor.GREEN, count)
fun competitiveKey(name: String, customModelData: Int, count: Int) = dungeonKey(name, customModelData, itemCount = count)

fun dungeonKey(
    name: String,
    customModelData: Int,
    textColor: NamedTextColor = NamedTextColor.AQUA,
    itemCount: Int = 1,
) = dungeonItem(name, customModelData, textColor, itemCount = itemCount)

fun practiceRepairKit(count: Int) = dungeonItem("❄☠ Rusty Repair Kit (Practice) ☠❄", 2, NamedTextColor.GREEN, Material.IRON_INGOT, count)
fun competitiveRepairKit(count: Int) = dungeonItem("❄☠ Rusty Repair Kit (Competitive) ☠❄", 2, NamedTextColor.AQUA, Material.IRON_INGOT, count)


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
        return tag.loadCopy();
    })
}

fun practiceDeck() = dungeonDeck(
    "❄☠ Frozen Assets (Practice Deck #1) ☠❄",
    textColor = NamedTextColor.GREEN,
    material = Material.LIME_SHULKER_BOX,
    deckId = "p1"
)

fun competitiveDeck() = dungeonDeck("❄☠ Frozen Assets (Competitive Deck #1) ☠❄", deckId = "c1")

fun dungeonDeck(
    name: String = "❄☠ Frozen Assets ☠❄",
    textColor: NamedTextColor = NamedTextColor.AQUA,
    material: Material = Material.CYAN_SHULKER_BOX,
    deckId: String,
) = dungeonItem(name, 7, textColor, material = material, metadata = mapOf("deckId" to deckId))


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
        return tag.loadCopy();
    })
}
