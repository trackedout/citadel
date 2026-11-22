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
import org.trackedout.citadel.cubby.CubbyBlocks
import org.trackedout.citadel.cubby.CubbyDefinitionPlanner
import org.trackedout.citadel.cubby.CubbyFacing
import org.trackedout.citadel.cubby.CubbyFlags
import org.trackedout.citadel.cubby.DEFAULT_WALL_GAP
import org.trackedout.citadel.cubby.PlannedCubby
import org.trackedout.citadel.cubby.max
import org.trackedout.citadel.cubby.min
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion
import org.trackedout.citadel.regionManager
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
private const val MAX_CUBBIES_PER_DEFINE = 50
private const val CONFIRM_HINT = "Nothing was changed. Add --confirm to apply."

/** Admin tooling to maintain cubbies. Every command only reports what it would do unless given --confirm. */
@CommandAlias("decked-out|do")
@CommandPermission(ADMIN_PERMISSION)
class CubbyAdminCommand(
    private val plugin: Citadel,
    private val templateStore: CubbyTemplateStore,
    private val reset: CubbyReset,
) : BaseCommand() {
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

    @Subcommand("cubby define")
    @Description("Define <count> more cubby regions in a row next to your WorldEdit selection (one cubby). Options: --direction <north|east|south|west>, --gap <blocks>")
    fun defineCubbies(player: Player, count: Int, @Optional args: Array<String>?) {
        val flags = CubbyFlags.of(args)
        val cubbyWorld = CubbyWorld(player.world)
        val rowDirection = rowDirection(player, flags.value("direction")) ?: return
        val reference = selectedBox(player) ?: return
        if (count !in 1..MAX_CUBBIES_PER_DEFINE) {
            player.sendRedMessage("Count must be between 1 and $MAX_CUBBIES_PER_DEFINE")
            return
        }

        val existing = cubbyWorld.cubbies.associate { it.id to it.toBox() }
        val gap = flags.intValue("gap") ?: DEFAULT_WALL_GAP
        val planned = CubbyDefinitionPlanner.plan(reference, rowDirection, count, gap, cubbyWorld.nextNumber(), existing)

        warnIfUncommonShape(player, cubbyWorld, reference.size)
        planned.forEach { player.sendMessage("${it.id}: ${it.box.min()} to ${it.box.max()}${overlapText(it)}", if (it.overlaps.isEmpty()) NamedTextColor.WHITE else NamedTextColor.RED) }

        if (planned.any { it.overlaps.isNotEmpty() }) {
            player.sendRedMessage("Cannot define cubbies that overlap existing ones. Nothing was changed")
            return
        }
        if (!flags.confirmed) {
            player.sendMessage("$CONFIRM_HINT Only regions are defined: copy the cubby itself, with the ender chest at its front, using WorldEdit.", NamedTextColor.GRAY)
            return
        }

        val regionManager = checkNotNull(player.world.regionManager()) { "No region manager for ${player.world.name}" }
        planned.forEach { regionManager.addRegion(ProtectedCuboidRegion(it.id, it.box.min(), it.box.max())) }
        regionManager.saveChanges()
        plugin.logger.info("${player.name} defined ${planned.joinToString { it.id }}")
        player.sendGreenMessage("Defined ${planned.size} cubby region(s): ${planned.first().id} to ${planned.last().id}")
    }

    private fun overlapText(planned: PlannedCubby) =
        if (planned.overlaps.isEmpty()) "" else " OVERLAPS ${planned.overlaps.joinToString()}"

    private fun rowDirection(player: Player, name: String?): CubbyFacing? {
        if (name == null) return CubbyFacing.fromYaw(player.location.yaw)
        val direction = CubbyFacing.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (direction == null) player.sendRedMessage("Direction must be one of ${CubbyFacing.entries.joinToString().lowercase()}")
        return direction
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
