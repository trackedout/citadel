package org.trackedout.citadel.shop

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
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
import org.trackedout.citadel.config.HARDCORE_SLUG
import org.trackedout.citadel.config.PRACTICE_SLUG
import org.trackedout.citadel.inventory.ShopView.Companion.SHOP_RULES_REGEX
import org.trackedout.citadel.sendGreenMessage
import org.trackedout.citadel.sendRedMessage
import java.util.concurrent.TimeUnit
import xyz.upperlevel.spigot.book.BookUtil
import xyz.upperlevel.spigot.book.BookUtil.PageBuilder

@CommandAlias("do")
@Subcommand("shop")
class ShopCommand(
    private val plugin: Citadel,
) : BaseCommand() {

    companion object {
        // Legacy grammar: [r]<char><SOURCE>x<n>=<char><TARGET>x<m>[,<TARGET>x<m>...]
        // The leading char (and the per-target char after '=') selected the run type.
        private val LEGACY_RULE_REGEX =
            "^(?<repeat>r|)(?<type>[a-z])(?<sourceType>.+?)x(?<sourceCount>\\d{1,3})=[a-z](?<targetTypes>(?:.+?x\\d{1,3},?)+)$".toRegex()

        // Legacy single-char run-type prefix -> canonical league slug.
        private val LEGACY_CHAR_TO_SLUG = mapOf(
            "p" to PRACTICE_SLUG,
            "c" to "do2-s3-comp",
            "h" to HARDCORE_SLUG,
        )

        // Rewrite a single legacy char-prefixed rule to the slug syntax
        // (<slug>:<SOURCE>x<n>=<TARGETS>[:r]). Returns null if the rule is not
        // legacy (e.g. already in slug form or unrecognised).
        fun rewriteLegacyRule(rule: String): String? {
            val match = LEGACY_RULE_REGEX.matchEntire(rule) ?: return null
            val groups = match.groups

            val char = groups["type"]?.value ?: return null
            val slug = LEGACY_CHAR_TO_SLUG[char] ?: return null
            val sourceType = groups["sourceType"]?.value ?: return null
            val sourceCount = groups["sourceCount"]?.value ?: return null
            val targetTypes = groups["targetTypes"]?.value ?: return null
            val repeat = groups["repeat"]?.value == "r"

            val suffix = if (repeat) ":r" else ""
            return "$slug:${sourceType}x$sourceCount=$targetTypes$suffix"
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

    @Subcommand("add")
    @CommandPermission("decked-out.shop.admin")
    @Description("Shop management - add a trade")
    fun addTrade(player: Player, trade: String) {
        if (!trade.matches(SHOP_RULES_REGEX)) {
            player.sendRedMessage("Invalid trade syntax: '$trade'")
            player.sendRedMessage("Expected: <slug>:<SOURCE>x<n>=<TARGET>x<m>[,<TARGET>x<m>...][:r]")
            player.sendRedMessage("Example: do2-s3-comp:CROWNx10=SHARDx1")
            return
        }

        executeOnValidShop(player) { tileState, shopData ->
            shopData.trades += trade
            shopData.trades = shopData.trades.distinct()

            shopData.save(plugin, tileState)
            player.sendGreenMessage("Shop now has the following trades: ${shopData.trades}")
        }
    }

    @Subcommand("remove")
    @CommandPermission("decked-out.shop.admin")
    @CommandCompletion("@shopTrades")
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

    @Subcommand("rewrite")
    @CommandPermission("decked-out.shop.admin")
    @Description("Shop management - migrate legacy char-prefixed trades to the slug syntax")
    fun rewriteTrades(player: Player) {
        executeOnValidShop(player) { tileState, shopData ->
            val rewritten = shopData.trades.map { rule ->
                rewriteLegacyRule(rule) ?: rule
            }

            val changed = rewritten.filterIndexed { i, r -> r != shopData.trades[i] }
            if (changed.isEmpty()) {
                player.sendGreenMessage("No legacy trades to rewrite; shop already uses the slug syntax")
                return@executeOnValidShop
            }

            shopData.trades = rewritten.distinct()
            shopData.save(plugin, tileState)
            player.sendGreenMessage("Rewrote ${changed.size} trade(s). Shop now has: ${shopData.trades}")
        }
    }

    // Trades of the shop the player is currently looking at, for tab-completion.
    // Tab-completion runs on an async thread, but reading block state (getTargetBlock
    // / TileState) is only safe on the main thread, so hop there and wait briefly.
    // Returns an empty list on timeout/failure or when no valid shop is targeted,
    // since this runs on every keystroke.
    fun tradesForTargetedShop(player: Player): List<String> {
        return try {
            plugin.server.scheduler.callSyncMethod(plugin) {
                val state = player.getTargetBlock(null, 4).state
                if (state !is Barrel && state !is ShulkerBox) return@callSyncMethod emptyList<String>()
                (state as? TileState)?.getShopData(plugin)?.trades ?: emptyList()
            }.get(500, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            emptyList()
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
