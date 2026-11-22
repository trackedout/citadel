package org.trackedout.citadel.cubby

import com.sk89q.worldedit.extent.clipboard.Clipboard
import java.io.File
import org.bukkit.configuration.file.YamlConfiguration

private const val FACING_KEY = "facing"

/** A saved cubby design, together with the way it faces, so it can be rotated onto any cubby. */
data class CubbyTemplate(val clipboard: Clipboard, val facing: CubbyFacing) {
    val size: Size get() = Size(clipboard.dimensions.x, clipboard.dimensions.y, clipboard.dimensions.z)
}

class CubbyTemplateStore(dataFolder: File) {
    private val schematicFile = File(dataFolder, "cubby-template.schem")
    private val metadataFile = File(dataFolder, "cubby-template.yml")

    fun exists() = schematicFile.exists() && metadataFile.exists()

    fun load(): CubbyTemplate? {
        if (!exists()) return null
        val facing = CubbyFacing.valueOf(YamlConfiguration.loadConfiguration(metadataFile).getString(FACING_KEY).orEmpty())
        return CubbyTemplate(CubbyBlocks.read(schematicFile), facing)
    }

    fun save(template: CubbyTemplate) {
        CubbyBlocks.write(template.clipboard, schematicFile)
        YamlConfiguration().apply { set(FACING_KEY, template.facing.name) }.save(metadataFile)
    }
}
