package org.trackedout.citadel.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

typealias ArtifactConfig = HashMap<String, ArtifactConfigValue>

@Serializable
data class ArtifactConfigValue(
    val id: String,
    val emberValue: Int,
    val tag: Tag,
)

@Serializable
data class Tag(
    @SerialName("CustomModelData")
    val customModelData: Int,

    val display: Display,
)

@Serializable
data class Display(
    @SerialName("Lore")
    val lore: List<String>,

    @SerialName("Name")
    val name: String,
)

fun String.cleanDataPackEscaping(): String {
    return this.trim().removeSurrounding("'").replace("\\\\", "\\")
}

fun ArtifactConfigValue.itemStack(itemID: String, itemCount: Int = 1): ItemStack {
    val material = Material.matchMaterial(itemID) ?: Material.IRON_NUGGET
    val itemStack = ItemStack(material, if (itemCount <= 0) 999 else itemCount)
    val meta = itemStack.itemMeta
    val displayName = JSONComponentSerializer.json().deserialize(this.tag.display.name.cleanDataPackEscaping())
    val loreComponents = this.tag.display.lore.map { JSONComponentSerializer.json().deserialize(it.cleanDataPackEscaping()) }

    meta.displayName(displayName)
    meta.lore(loreComponents)
    meta.setCustomModelData(this.tag.customModelData)

    itemStack.itemMeta = meta
    return itemStack
}

fun ArtifactConfigValue.itemStackNotYetAcquired(itemCount: Int = 1): ItemStack {
    val itemStack = ItemStack(Material.SNOWBALL, if (itemCount <= 0) 999 else itemCount)
    val meta = itemStack.itemMeta
    meta.displayName(Component.text("Some Artifake").decorate(TextDecoration.OBFUSCATED).color(NamedTextColor.GRAY))
    meta.lore(listOf(Component.text("You have yet to acquire this artifake").color(NamedTextColor.GRAY)))

    itemStack.itemMeta = meta
    return itemStack
}

fun loadArtifactConfig(): ArtifactConfig? {
    // Load the JSON file content
    return object {}.javaClass.getResource("/items_json/Artifacts.json")?.readText()?.let {
        // Deserialize the JSON content into the ArtifactConfig type
        Json.decodeFromString(it)
    }
}
