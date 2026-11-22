package org.trackedout.citadel.cubby

import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard
import com.sk89q.worldedit.extent.clipboard.Clipboard
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat
import com.sk89q.worldedit.extent.transform.BlockTransformExtent
import com.sk89q.worldedit.function.operation.ForwardExtentCopy
import com.sk89q.worldedit.function.operation.Operations
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.math.transform.AffineTransform
import com.sk89q.worldedit.regions.CuboidRegion
import java.io.File
import kotlin.math.roundToInt
import org.bukkit.World

private const val DEGREES_PER_QUARTER_TURN = 90.0
private const val MAX_REPORTED_DIFFERENCES = 20

data class BlockDifference(val x: Int, val y: Int, val z: Int, val expected: String, val found: String) {
    override fun toString() = "($x, $y, $z) expected $expected, found $found"
}

data class BlockDifferences(val total: Int, val examples: List<BlockDifference>)

/** Copies, rotates, pastes and compares the blocks of a cubby using WorldEdit clipboards. */
class CubbyBlocks(private val world: World) {
    private val editWorld = BukkitAdapter.adapt(world)

    fun copyBlocks(box: Box): Clipboard = copy(box, copyEntities = false)

    /** Also keeps item frames, armor stands and similar, which a restored cubby needs. */
    fun copyBlocksAndEntities(box: Box): Clipboard = copy(box, copyEntities = true)

    private fun copy(box: Box, copyEntities: Boolean): Clipboard {
        val region = CuboidRegion(editWorld, box.min(), box.max())
        val clipboard = BlockArrayClipboard(region)
        clipboard.origin = region.minimumPoint

        WorldEdit.getInstance().newEditSession(editWorld).use { session ->
            val copy = ForwardExtentCopy(session, region, region.minimumPoint, clipboard, region.minimumPoint)
            copy.isCopyingEntities = copyEntities
            Operations.complete(copy)
        }
        return clipboard
    }

    fun paste(clipboard: Clipboard, atMin: BlockVector3) {
        WorldEdit.getInstance().newEditSession(editWorld).use { session ->
            val copy = ForwardExtentCopy(clipboard, clipboard.region, clipboard.region.minimumPoint, session, atMin)
            copy.isCopyingEntities = true
            Operations.complete(copy)
        }
    }

    /** Compares the clipboard with the world, only within [bounds] when the region is smaller than the clipboard. */
    fun differences(rotated: Clipboard, atMin: BlockVector3, bounds: Box): BlockDifferences {
        val offset = atMin.subtract(rotated.region.minimumPoint)
        val examples = mutableListOf<BlockDifference>()
        var total = 0

        for (position in rotated.region) {
            val expected = rotated.getFullBlock(position).toImmutableState()
            val target = position.add(offset)
            if (!bounds.contains(target.x, target.y, target.z)) continue

            val found = editWorld.getBlock(target)
            if (expected == found) continue

            total++
            if (examples.size < MAX_REPORTED_DIFFERENCES) {
                examples += BlockDifference(target.x, target.y, target.z, expected.asString, found.asString)
            }
        }
        return BlockDifferences(total, examples)
    }

    companion object {
        fun rotate(source: Clipboard, quarterTurns: Int): Clipboard {
            if (quarterTurns == 0) return source

            // WorldEdit's own //rotate negates the angle to turn clockwise
            val transform = AffineTransform().rotateY(-DEGREES_PER_QUARTER_TURN * quarterTurns)
            val rotatedCorners = horizontalCorners(source.dimensions).map { transform.apply(it.toVector3()) }
            val rotatedMinX = rotatedCorners.minOf { it.x }.roundToInt()
            val rotatedMinZ = rotatedCorners.minOf { it.z }.roundToInt()
            val rotatedSize = Size(source.dimensions.x, source.dimensions.y, source.dimensions.z).rotated(quarterTurns)

            val destination = BlockArrayClipboard(
                CuboidRegion(BlockVector3.ZERO, BlockVector3.at(rotatedSize.x - 1, rotatedSize.y - 1, rotatedSize.z - 1)),
            )
            val destinationOfOrigin = BlockVector3.at(-rotatedMinX, 0, -rotatedMinZ)
            val turnedBlocks = BlockTransformExtent(source, transform)
            val copy = ForwardExtentCopy(turnedBlocks, source.region, source.origin, destination, destinationOfOrigin)
            copy.transform = transform
            copy.isCopyingEntities = true
            Operations.complete(copy)
            return destination
        }

        fun read(file: File): Clipboard {
            val format = BuiltInClipboardFormat.SPONGE_SCHEMATIC
            val clipboard = file.inputStream().use { stream -> format.getReader(stream).use { it.read() } }
            clipboard.origin = clipboard.region.minimumPoint
            return clipboard
        }

        fun write(clipboard: Clipboard, file: File) {
            file.parentFile.mkdirs()
            file.outputStream().use { stream ->
                BuiltInClipboardFormat.SPONGE_SCHEMATIC.getWriter(stream).use { it.write(clipboard) }
            }
        }

        private fun horizontalCorners(size: BlockVector3): List<BlockVector3> {
            val maxX = size.x - 1
            val maxZ = size.z - 1
            return listOf(BlockVector3.at(0, 0, 0), BlockVector3.at(maxX, 0, 0), BlockVector3.at(0, 0, maxZ), BlockVector3.at(maxX, 0, maxZ))
        }
    }
}

fun Box.min(): BlockVector3 = BlockVector3.at(minX, minY, minZ)

fun Box.max(): BlockVector3 = BlockVector3.at(maxX, maxY, maxZ)
