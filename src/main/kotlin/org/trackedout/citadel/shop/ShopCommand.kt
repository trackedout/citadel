package org.trackedout.citadel.shop

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Description
import co.aikar.commands.annotation.Subcommand
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.md_5.bungee.api.chat.BaseComponent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.ChatColor
import org.bukkit.Nameable
import org.bukkit.NamespacedKey
import org.bukkit.block.Barrel
import org.bukkit.block.ShulkerBox
import org.bukkit.block.TileState
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.inventory.ShopView.Companion.SHOP_RULES_REGEX
import org.trackedout.citadel.sendGreenMessage
import org.trackedout.citadel.sendRedMessage
import xyz.upperlevel.spigot.book.BookUtil
import xyz.upperlevel.spigot.book.BookUtil.PageBuilder

@CommandAlias("do")
@Subcommand("shop")
class ShopCommand(
    private val plugin: Citadel,
) : BaseCommand() {

    @Subcommand("edit")
    @CommandPermission("decked-out.shop.admin")
    @Description("Shop management")
    fun openShopEditor(player: Player) {
        executeOnValidShop(player) { tileState, _ ->
//            showShopEditor(tileState, player)
        }
    }

    @Subcommand("rename")
    @CommandPermission("decked-out.shop.admin")
    @Description("Shop management - set name")
    fun setName(player: Player, name: String) {
        executeOnValidShop(player) { tileState, shopData ->
            shopData.name = name
            shopData.save(plugin, tileState)
            player.sendGreenMessage("Shop name updated to: ${shopData.name}")
        }
    }

    @Subcommand("info")
    @CommandPermission("decked-out.shop.admin")
    @Description("Shop management - show shop info")
    fun getShopInfo(player: Player) {
        executeOnValidShop(player) { _, shopData ->
            player.sendMessage("Shop name: ${shopData.name}")
            if (shopData.trades.isNotEmpty()) {
                player.sendMessage("Trades: ")
                shopData.trades.forEach {
                    player.sendMessage("  - $it")
                }
            } else {
                player.sendMessage("Shop does not have any trades")
            }
        }
    }

    @Subcommand("disable")
    @CommandPermission("decked-out.shop.admin")
    @Description("Shop management - disable shop at location")
    fun disableShop(source: CommandSender, x: Int, y: Int, z: Int) {
        val shopData = getShopDataAtLocation(plugin, x, y, z)
        if (shopData == null) {
            source.sendRedMessage("Shop not found")
        } else {
            shopData.disabled = true
            shopData.save(plugin, x, y, z)

            source.sendGreenMessage("Shop '${shopData.name}' at [$x, $y, $z] is now disabled")
        }
    }

    @Subcommand("enable")
    @CommandPermission("decked-out.shop.admin")
    @Description("Shop management - enable shop at location")
    fun enableShop(source: CommandSender, x: Int, y: Int, z: Int) {
        val shopData = getShopDataAtLocation(plugin, x, y, z)
        if (shopData == null) {
            source.sendRedMessage("Shop not found")
        } else {
            shopData.disabled = false
            shopData.save(plugin, x, y, z)

            source.sendGreenMessage("Shop '${shopData.name}' at [$x, $y, $z] is now enabled")
        }
    }

    @Subcommand("convert")
    @CommandPermission("decked-out.shop.admin")
    @Description("Shop management - convert shop from name tag to config")
    fun convertShop(player: Player) {
        executeOnValidShop(player) { tileState, shopData ->
            val targetBlock = player.getTargetBlock(null, 4)

            val block = (targetBlock.state as Nameable)
            block.customName()?.let { customName ->
                val title = (customName as net.kyori.adventure.text.TextComponent).content()
                val titleComponents = title.removePrefix("Shop ").split(" ")
                val shopName = titleComponents.filter { !it.matches(SHOP_RULES_REGEX) }.joinToString(" ")
                val shopRules = titleComponents.filter { it.matches(SHOP_RULES_REGEX) }

                shopData.name = shopName
                shopData.trades += shopRules
                shopData.trades = shopData.trades.distinct()

                shopData.save(plugin, tileState)
                player.sendGreenMessage("Shop '${shopName.ifEmpty { "default" }}' now has the following trades: ${shopData.trades}")
            }
        }
    }

    @Subcommand("trade add")
    @CommandPermission("decked-out.shop.admin")
    @Description("Shop management - add a trade")
    fun addTrade(player: Player, trade: String) {
        executeOnValidShop(player) { tileState, shopData ->
            shopData.trades += trade
            shopData.trades = shopData.trades.distinct()

            shopData.save(plugin, tileState)
            player.sendGreenMessage("Shop now has the following trades: ${shopData.trades}")
        }
    }

    @Subcommand("trade remove")
    @CommandPermission("decked-out.shop.admin")
    @Description("Shop management - remove a trade")
    fun removeTrade(player: Player, trade: String) {
        executeOnValidShop(player) { tileState, shopData ->
            if (trade == "ALL") {
                shopData.trades = mutableListOf()
            } else {
                shopData.trades -= trade
                shopData.trades = shopData.trades.distinct()
            }

            shopData.save(plugin, tileState)
            player.sendGreenMessage("Shop now has the following trades: ${shopData.trades}")
        }
    }

    private fun executeOnValidShop(player: Player, executeOnShop: (TileState, ShopData) -> Unit) {
        val targetBlock = player.getTargetBlock(null, 4)
        targetBlock.state.type
        if (targetBlock.state !is Barrel && targetBlock.state !is ShulkerBox) {
            player.sendRedMessage("You must target a shop block (barrel or shulker)")
            return
        }

        if (targetBlock.state !is Nameable) {
            player.sendRedMessage("Target block is not a nameable block")
            return
        }

        val block = (targetBlock.state as Nameable)
        block.customName()?.let { customName ->
            val title = (customName as net.kyori.adventure.text.TextComponent).content()
            if (title.startsWith("Shop ")) {
                (block as? TileState)?.let {
                    executeOnShop(it, it.getShopData(plugin))
                } ?: throw Exception("Target block is not of type TileState")
            } else {
                player.sendRedMessage("Target block is not a shop (needs to be named with 'Shop ...')")
            }
        } ?: player.sendRedMessage("Target block is not a shop (needs to be named with 'Shop ...')")
    }

    private fun showShopEditor(block: TileState, player: Player) {
        val nsKey = NamespacedKey(plugin, "shop")

        val shopDataJson = block.persistentDataContainer.getOrDefault(nsKey, PersistentDataType.STRING, Json.encodeToString(ShopData()))
        val shopData = Json.decodeFromString<ShopData>(shopDataJson)

        val book = BookUtil.writtenBook()
            .author("SnowyCoder")
            .title("The Test-ament")
            .pages(
                arrayOf<BaseComponent>(
                    TextComponent("Introduction page")
                ),
                PageBuilder()
                    .add(TextComponent("visit "))
                    .add(
                        BookUtil.TextBuilder.of("Spigot")
                            .color(ChatColor.GOLD)
                            .style(ChatColor.BOLD, ChatColor.ITALIC)
                            .onClick(BookUtil.ClickAction.openUrl("https://www.spigotmc.org"))
                            .onHover(BookUtil.HoverAction.showText("Open spigot!"))
                            .build()
                    )
                    .add(" or ")
                    .newLine()
                    .add("I think that the ")
                    .add(
                        BookUtil.TextBuilder.of("TextBuilder")
                            .color(ChatColor.AQUA)
                            .style(ChatColor.BOLD)
                            .onClick(BookUtil.ClickAction.changePage(3))
                            .onHover(BookUtil.HoverAction.showText("TextBuilder's page"))
                            .build()
                    )
                    .add(" is really useful to ")
                    .add(
                        BookUtil.TextBuilder.of("you")
                            .color(ChatColor.AQUA)
                            .style(ChatColor.BOLD)
                            .onClick(BookUtil.ClickAction.runCommand("/kill")) //lol
                            .onHover(BookUtil.HoverAction.showText("Kill yasself"))
                            .build()
                    )
                    .build(),
                PageBuilder()
                    .add("TextBuilder's page")
                    .newLine().newLine()
                    .add("Isn't this amazing?")
                    .build()
            )
            .build()

        // This fires an async inventory open event
        BookUtil.openPlayer(player, book)

//        block.persistentDataContainer.set(nsKey, PersistentDataType.STRING, Json.encodeToString(shopData))
    }

}
