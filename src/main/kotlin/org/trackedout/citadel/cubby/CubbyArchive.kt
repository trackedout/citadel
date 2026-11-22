package org.trackedout.citadel.cubby

import com.sk89q.worldedit.extent.clipboard.Clipboard
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private const val UNOWNED = "_unowned"
private const val FIELD_SEPARATOR = "_"
private val SAFE_DIRECTORY_NAME = Regex("^[a-zA-Z0-9_-]{1,36}$")
private val TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

/** A cubby saved before it was reset. */
data class CubbyArchiveEntry(val file: File, val cubbyId: String, val facing: CubbyFacing)

/**
 * Keeps every reset cubby forever as `<owner>/<timestamp>_<cubby-id>_<FACING>.schem`.
 * The facing is part of the name so the archive can be rotated onto a different cubby later.
 */
class CubbyArchive(dataFolder: File) {
    private val root = File(dataFolder, "cubby-archive")

    fun save(owner: String?, cubbyId: String, facing: CubbyFacing, clipboard: Clipboard, now: Instant = Instant.now()): File {
        val name = listOf(TIMESTAMP_FORMAT.format(now), cubbyId, facing.name).joinToString(FIELD_SEPARATOR) + ".schem"
        val file = File(File(root, ownerDirectory(owner)), name)
        CubbyBlocks.write(clipboard, file)
        return file
    }

    fun latestFor(owner: String): CubbyArchiveEntry? {
        val files = File(root, ownerDirectory(owner)).listFiles { file -> file.extension == "schem" }.orEmpty()
        return files.maxByOrNull { it.name }?.let { parse(it) }
    }

    private fun parse(file: File): CubbyArchiveEntry? {
        val fields = file.nameWithoutExtension.split(FIELD_SEPARATOR)
        val facing = CubbyFacing.entries.firstOrNull { it.name == fields.last() } ?: return null
        val cubbyId = fields.drop(1).dropLast(1).joinToString(FIELD_SEPARATOR)
        return CubbyArchiveEntry(file, cubbyId, facing)
    }

    private fun ownerDirectory(owner: String?): String {
        if (owner == null) return UNOWNED
        require(SAFE_DIRECTORY_NAME.matches(owner)) { "Unsafe archive owner name: $owner" }
        return owner.lowercase()
    }
}
