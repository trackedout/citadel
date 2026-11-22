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
            add("  ${it.total} block(s) will change")
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
    /** Plans putting the saved template into the cubby. */
    fun planReset(cubbyWorld: CubbyWorld, cubby: ProtectedRegion, releasingOwner: Player? = null): CubbyResetPlan =
        plan(cubbyWorld, cubby, templateStore.load(), releasingOwner)

    /**
     * Plans putting [design] into the cubby, which is the template unless an archived cubby is being restored.
     * @param releasingOwner an owner who may be online because they are the one releasing the cubby
     */
    fun plan(cubbyWorld: CubbyWorld, cubby: ProtectedRegion, design: CubbyTemplate?, releasingOwner: Player? = null): CubbyResetPlan {
        val world = cubbyWorld.world
        val box = cubby.toBox()
        val owners = cubby.ownerNames()
        val problems = mutableListOf<String>()

        if (design == null) problems += "no template has been saved yet (/do cubby template save)"

        val facingResult = cubbyWorld.facingOf(cubby)
        val facing = (facingResult as? FacingResult.Resolved)?.facing
        if (facing == null) problems += facingProblem(facingResult)
        problems += onlinePlayerProblems(world, cubby, box, releasingOwner)

        val rotated = if (design != null && facing != null) {
            CubbyBlocks.rotate(design.clipboard, design.facing.quarterTurnsTo(facing))
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

        archive.save(plan.owners.firstOrNull(), plan.cubby.id, checkNotNull(plan.facing), blocks.copyBlocksAndEntities(box))
        entitiesIn(world, box).forEach { it.remove() }
        blocks.paste(checkNotNull(plan.rotatedTemplate), box.min())
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

    private fun onlinePlayerProblems(world: World, cubby: ProtectedRegion, box: Box, releasingOwner: Player?): List<String> {
        val onlineOwners = (cubby.members.players.mapNotNull { Bukkit.getPlayerExact(it) } +
            cubby.members.uniqueIds.mapNotNull { Bukkit.getPlayer(it) }).filter { it != releasingOwner }
        val playersInside = world.players.filter { box.contains(it.location.blockX, it.location.blockY, it.location.blockZ) }

        return onlineOwners.distinct().map { "${it.name} is online" } + playersInside.map { "${it.name} is standing inside" }
    }

    private fun entitiesIn(world: World, box: Box): List<Entity> {
        val bounds = BoundingBox(
            box.minX.toDouble(), box.minY.toDouble(), box.minZ.toDouble(),
            box.maxX + 1.0, box.maxY + 1.0, box.maxZ + 1.0,
        )
        return world.getNearbyEntities(bounds).filter { it !is Player }
    }
}
