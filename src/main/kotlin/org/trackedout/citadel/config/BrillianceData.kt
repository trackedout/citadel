package org.trackedout.citadel.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.trackedout.data.BrillianceCard
import org.trackedout.listeners.json

val json = Json { ignoreUnknownKeys = true }

typealias ArtifactConfig = HashMap<String, ArtifactConfigValue>

@Serializable
data class ArtifactConfigValue(
    val id: String,
    val emberValue: Int,
    val tag: Tag,
)

@Serializable
data class Tag(
    @SerialName("CustomModelData") val customModelData: Int,

    val display: Display,
)

@Serializable
data class Display(
    @SerialName("Lore") val lore: List<String>,

    @SerialName("Name") val name: String,
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
    return object {}.javaClass.getResource("/items_json/Artifacts.json")?.readText()?.let {
        json.decodeFromString(it)
    }
}

val cardConfig: Map<String, BrillianceCard> by lazy {
    object {}.javaClass.getResource("/items_json/Cards.json")?.readText()?.let {
        val jsonElement = json.parseToJsonElement(it).jsonObject
        jsonElement.mapValues { (_, value) ->
            val rawTag = value.jsonObject["tag"]?.toString() ?: error("Missing tag field")
            val brillianceCard = json.decodeFromJsonElement(BrillianceCard.serializer(), value)
            brillianceCard.copy(tagRaw = rawTag)
        }
    } ?: emptyMap()
}
