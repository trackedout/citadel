package org.trackedout.citadel.cubby

import com.sk89q.worldedit.extent.clipboard.Clipboard
import com.sk89q.worldguard.protection.regions.ProtectedRegion
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.util.BoundingBox
import org.trackedout.citadel.regionManager

data class CubbyResetPlan(
    val cubby: ProtectedRegion,
    val owners: List<String>,
    val facing: CubbyFacing?,
    val problems: List<String>,
    val differences: BlockDifferences?,
    val entityCount: Int,
    val rotatedTemplate: Clipboard?,
) {
    val isValid get() = problems.isEmpty()

    fun report(): List<String> = buildList {
        val ownerText = owners.joinToString().ifEmpty { "nobody" }
        add("${cubby.id}: owned by $ownerText, facing ${facing ?: "unknown"}")
        problems.forEach { add("  Cannot reset: $it") }
        differences?.let {
            add("  ${it.total} block(s) differ from the template")
            it.examples.forEach { difference -> add("    $difference") }
            if (it.total > it.examples.size) add("    ... and ${it.total - it.examples.size} more")
        }
        if (isValid) add("  Will remove $entityCount entity(ies), clear members, flags and parent, and archive the current blocks")
    }
}

class CubbyReset(
    private val templateStore: CubbyTemplateStore,
    private val archive: CubbyArchive,
) {
    fun plan(cubbyWorld: CubbyWorld, cubby: ProtectedRegion): CubbyResetPlan {
        val world = cubbyWorld.world
        val box = cubby.toBox()
        val owners = cubby.ownerNames()
        val problems = mutableListOf<String>()

        val template = templateStore.load()
        if (template == null) problems += "no template has been saved yet (/do cubby template save)"

        val facing = (cubbyWorld.facingOf(cubby) as? FacingResult.Resolved)?.facing
        if (facing == null) problems += facingProblem(cubbyWorld.facingOf(cubby))
        problems += onlinePlayerProblems(world, cubby, box)

        val rotated = if (template != null && facing != null) {
            CubbyBlocks.rotate(template.clipboard, template.facing.quarterTurnsTo(facing))
        } else {
            null
        }
        rotated?.let { problems += sizeProblem(box, it) }

        val differences = rotated?.let { CubbyBlocks(world).differences(it, box.min(), box) }
        return CubbyResetPlan(cubby, owners, facing, problems, differences, entitiesIn(world, box).size, rotated)
    }

    /** Must run on the main thread. */
    fun execute(cubbyWorld: CubbyWorld, plan: CubbyResetPlan) {
        val world = cubbyWorld.world
        val box = plan.cubby.toBox()
        val blocks = CubbyBlocks(world)

        archive.save(plan.owners.firstOrNull(), plan.cubby.id, checkNotNull(plan.facing), blocks.copy(box))
        blocks.paste(checkNotNull(plan.rotatedTemplate), box.min())
        entitiesIn(world, box).forEach { it.remove() }
        release(plan.cubby)
    }

    fun saveRegions(world: World) {
        world.regionManager()?.saveChanges()
    }

    private fun release(cubby: ProtectedRegion) {
        cubby.members.removeAll()
        cubby.owners.removeAll()
        cubby.flags = emptyMap()
        cubby.parent = null
        cubby.priority = 0
    }

    private fun facingProblem(result: FacingResult) = when (result) {
        is FacingResult.NoChest -> "no ender chest found directly outside its front, so its facing is unknown (is the region too short?)"
        is FacingResult.Conflict -> "ender chests at the front disagree on its facing: ${result.facings.joinToString()}"
        is FacingResult.Resolved -> error("Resolved facing is not a problem")
    }

    private fun sizeProblem(box: Box, rotatedTemplate: Clipboard): List<String> {
        val expected = Size(rotatedTemplate.dimensions.x, rotatedTemplate.dimensions.y, rotatedTemplate.dimensions.z)
        if (box.size == expected) return emptyList()
        return listOf("region is ${box.size} (x, y, z) but the rotated template is $expected, so the paste would not line up")
    }

    private fun onlinePlayerProblems(world: World, cubby: ProtectedRegion, box: Box): List<String> {
        val onlineOwners = cubby.members.players.mapNotNull { Bukkit.getPlayerExact(it) } +
            cubby.members.uniqueIds.mapNotNull { Bukkit.getPlayer(it) }
        val playersInside = world.players.filter { box.contains(it.location.blockX, it.location.blockY, it.location.blockZ) }

        return (onlineOwners + playersInside).distinct().map { "${it.name} is online or standing inside" }
    }

    private fun entitiesIn(world: World, box: Box): List<Entity> {
        val bounds = BoundingBox(
            box.minX.toDouble(), box.minY.toDouble(), box.minZ.toDouble(),
            box.maxX + 1.0, box.maxY + 1.0, box.maxZ + 1.0,
        )
        return world.getNearbyEntities(bounds).filter { it !is Player }
    }
}
