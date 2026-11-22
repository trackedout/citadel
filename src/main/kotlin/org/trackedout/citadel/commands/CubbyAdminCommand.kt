package org.trackedout.citadel.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Description
import co.aikar.commands.annotation.Optional
import co.aikar.commands.annotation.Subcommand
import com.sk89q.worldedit.IncompleteRegionException
import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.regions.CuboidRegion
import com.sk89q.worldguard.protection.regions.ProtectedRegion
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.trackedout.citadel.Citadel
import org.trackedout.citadel.cubby.Box
import org.trackedout.citadel.cubby.CUBBY_PREFIX
import org.trackedout.citadel.cubby.CubbyArchive
import org.trackedout.citadel.cubby.CubbyBlocks
import org.trackedout.citadel.cubby.CubbyFlags
import org.trackedout.citadel.cubby.CubbyReset
import org.trackedout.citadel.cubby.CubbyTemplate
import org.trackedout.citadel.cubby.CubbyTemplateStore
import org.trackedout.citadel.cubby.CubbyWorld
import org.trackedout.citadel.cubby.FacingResult
import org.trackedout.citadel.cubby.Size
import org.trackedout.citadel.cubby.toBox
import org.trackedout.citadel.getCubbyForPlayer
import org.trackedout.citadel.sendGreenMessage
import org.trackedout.citadel.sendMessage
import org.trackedout.citadel.sendRedMessage

private const val ADMIN_PERMISSION = "decked-out.cubby.admin"
private const val CONFIRM_HINT = "Nothing was changed. Add --confirm to apply."

/** Admin tooling to maintain cubbies. Every command only reports what it would do unless given --confirm. */
@CommandAlias("decked-out|do")
@CommandPermission(ADMIN_PERMISSION)
class CubbyAdminCommand(
    private val plugin: Citadel,
) : BaseCommand() {
    private val templateStore = CubbyTemplateStore(plugin.dataFolder)
    private val reset = CubbyReset(templateStore, CubbyArchive(plugin.dataFolder))

    @Subcommand("cubby template save")
    @Description("Save a cubby as the template for resets: your WorldEdit selection, or --from <cubby-id>")
    fun saveTemplate(source: CommandSender, @Optional args: Array<String>?) {
        val flags = CubbyFlags.of(args)
        val cubbyWorld = cubbyWorldOf(source) ?: return
        val box = templateBox(source, cubbyWorld, flags.value("from")) ?: return

        val facing = (cubbyWorld.facingOf(box) as? FacingResult.Resolved)?.facing
        if (facing == null) {
            source.sendRedMessage("Cannot tell which way the selection faces. Select a whole cubby that has an ender chest at its front")
            return
        }

        source.sendGreenMessage("Template: ${box.size} (x, y, z), facing $facing${if (templateStore.exists()) ", replacing the existing template" else ""}")
        warnIfUncommonShape(source, cubbyWorld, box.size)

        if (!flags.confirmed) {
            source.sendMessage(CONFIRM_HINT, NamedTextColor.GRAY)
            return
        }
        templateStore.save(CubbyTemplate(CubbyBlocks(cubbyWorld.world).copy(box), facing))
        source.sendGreenMessage("Cubby template saved")
    }

    @Subcommand("cubby reset")
    @Description("Archive a cubby, restore the template and release its owner")
    @CommandCompletion("@cubbies")
    fun resetCubby(source: CommandSender, target: String, @Optional args: Array<String>?) {
        val flags = CubbyFlags.of(args)
        val cubbyWorld = cubbyWorldOf(source) ?: return
        val cubby = findCubby(cubbyWorld, target)
        if (cubby == null) {
            source.sendRedMessage("No cubby found for '$target'")
            return
        }

        val plan = reset.plan(cubbyWorld, cubby)
        plan.report().forEach { source.sendMessage(it, if (plan.isValid) NamedTextColor.WHITE else NamedTextColor.RED) }
        if (!plan.isValid) return
        if (!flags.confirmed) {
            source.sendMessage(CONFIRM_HINT, NamedTextColor.GRAY)
            return
        }

        reset.execute(cubbyWorld, plan)
        reset.saveRegions(cubbyWorld.world)
        plugin.logger.info("${source.name} reset ${cubby.id}, previously owned by ${plan.owners.joinToString().ifEmpty { "nobody" }}")
        source.sendGreenMessage("Reset ${cubby.id}")
    }

    private fun templateBox(source: CommandSender, cubbyWorld: CubbyWorld, fromCubby: String?): Box? {
        if (fromCubby != null) {
            val cubby = cubbyWorld.find(fromCubby)
            if (cubby == null) source.sendRedMessage("No cubby named '$fromCubby'")
            return cubby?.toBox()
        }
        if (source !is Player) {
            source.sendRedMessage("Give a cubby to copy with --from <cubby-id>, or run this as a player with a WorldEdit selection")
            return null
        }
        return selectedBox(source)
    }

    private fun selectedBox(player: Player): Box? {
        val session = WorldEdit.getInstance().sessionManager.get(BukkitAdapter.adapt(player))
        val region = try {
            session.getSelection(BukkitAdapter.adapt(player.world))
        } catch (e: IncompleteRegionException) {
            player.sendRedMessage("Select a cubby with the WorldEdit wand first")
            return null
        }
        if (region !is CuboidRegion) {
            player.sendRedMessage("The selection must be a cuboid")
            return null
        }
        return Box(
            region.minimumPoint.x, region.minimumPoint.y, region.minimumPoint.z,
            region.maximumPoint.x, region.maximumPoint.y, region.maximumPoint.z,
        )
    }

    private fun warnIfUncommonShape(sender: CommandSender, cubbyWorld: CubbyWorld, size: Size) {
        val (commonShape, count) = cubbyWorld.commonShape() ?: return
        if (size.shape() == commonShape) return

        sender.sendMessage(
            "WARNING: this selection is ${size.shape()} but $count of ${cubbyWorld.cubbies.size} cubbies are $commonShape. " +
                "A template of a different size will not line up with existing cubby regions, so resets of them will be refused",
            NamedTextColor.YELLOW,
        )
    }

    private fun cubbyWorldOf(source: CommandSender): CubbyWorld? {
        val world = if (source is Player) source.world else plugin.server.worlds.find { it.name == "world" }
        if (world == null) source.sendRedMessage("World not found")
        return world?.let { CubbyWorld(it) }
    }

    private fun findCubby(cubbyWorld: CubbyWorld, target: String): ProtectedRegion? {
        if (target.startsWith(CUBBY_PREFIX, ignoreCase = true)) return cubbyWorld.find(target)
        val uuid = plugin.server.getOfflinePlayerIfCached(target)?.uniqueId
        return cubbyWorld.world.getCubbyForPlayer(target, uuid)?.takeIf { it.id.startsWith(CUBBY_PREFIX) }
    }
}
