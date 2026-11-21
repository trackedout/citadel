package org.trackedout.citadel.commands


/** A world a warm dungeon can serve, and the persistent build server it is edited on, if any */
data class AdminWorld(val slug: String, val buildServer: String?)

/** A warm dungeon and the raw value of its `dungeon-type` config, null when unset */
data class AdminDungeon(val name: String, val dungeonType: String?)

data class AdminToggles(val details: Boolean, val networkStatus: Boolean, val clickDebug: Boolean)

private const val DUNGEON_HEADER = "<italic><red>Dungeon Management</red></italic>\n"
private const val SHUTDOWN_ALL_BUTTON = "<click:run_command:'/do shutdown-all-empty-dungeons'><red>[Shutdown All Empty]</red></click>"

/**
 * Builds the Dungeon Ops book.
 *
 * @param assignableWorlds worlds offered as buttons on every dungeon
 * @param knownWorlds every world that exists, so a dungeon-type that is not one of them is flagged as stale
 */
fun buildAdminBookPages(
    dungeons: List<AdminDungeon>,
    assignableWorlds: List<AdminWorld>,
    knownWorlds: List<AdminWorld>,
    toggles: AdminToggles,
): List<String> =
    dungeonPages(dungeons, assignableWorlds.map { it.slug }, knownWorlds.map { it.slug }.toSet()) +
        backupsPage(knownWorlds) +
        debugPage(toggles)

private fun dungeonPages(dungeons: List<AdminDungeon>, assignableSlugs: List<String>, knownSlugs: Set<String>): List<String> {
    val entries = dungeons.map { dungeonEntry(it, assignableSlugs, knownSlugs) } + SHUTDOWN_ALL_BUTTON
    val pages = mutableListOf<String>()
    var current = DUNGEON_HEADER

    for (entry in entries) {
        val candidate = "$current\n$entry"
        val pageIsEmpty = current == DUNGEON_HEADER
        if (!pageIsEmpty && !BookLayout.fits(candidate)) {
            pages += current
            current = "$DUNGEON_HEADER\n$entry"
        } else {
            current = candidate
        }
    }
    return pages + current
}

private fun dungeonEntry(dungeon: AdminDungeon, assignableSlugs: List<String>, knownSlugs: Set<String>): String {
    val type = dungeon.dungeonType
    val isStale = type == null || type !in knownSlugs
    val shutdown = "<click:run_command:'/do shutdown-dungeon ${dungeon.name}'><red>[X]</red></click>"
    val state = when {
        isStale -> " <red>${type ?: "unset"}</red>"
        type !in assignableSlugs -> " <gray>$type</gray>"
        else -> ""
    }
    val buttons = assignableSlugs.joinToString(" ") { worldButton(dungeon.name, it, it == type) }
    return "${dungeon.name}$state $shutdown\n$buttons"
}

private fun worldButton(dungeon: String, worldSlug: String, isCurrent: Boolean): String =
    if (isCurrent) "<aqua>[<u>$worldSlug</u>]</aqua>"
    else "<click:run_command:'/do config set $dungeon dungeon-type $worldSlug'><gray>[$worldSlug]</gray></click>"

private fun backupsPage(worlds: List<AdminWorld>): String {
    val snapshotButtons = worlds
        .mapNotNull { world -> world.buildServer?.let { it to world.slug } }
        .sortedBy { it.first }
        .joinToString("\n\n") { (buildServer, worldSlug) ->
            "<click:run_command:'/k8s create-snapshot $buildServer'><aqua>[Snapshot $buildServer ($worldSlug)]</aqua></click>"
        }
    val backupDatabase = "<click:run_command:'/k8s backup-database'><aqua>[Backup Database]</aqua></click>"
    return "      <italic><gold>Backups</gold></italic>\n\n" +
        (if (snapshotButtons.isEmpty()) "" else "$snapshotButtons\n\n") +
        backupDatabase
}

private fun debugPage(toggles: AdminToggles): String {
    fun toggleLabel(label: String, on: Boolean) = if (on) "<green>[$label: ON]</green>" else "<red>[$label: OFF]</red>"
    return """
    <italic><gold>Debug Configs</gold></italic>

<click:run_command:'/do details'>${toggleLabel("Dungeon Details", toggles.details)}</click>
<click:run_command:'/do status'>${toggleLabel("Network Status", toggles.networkStatus)}</click>
<click:run_command:'/do debug'>${toggleLabel("Click Debug", toggles.clickDebug)}</click>

<click:run_command:'/do config show'><gold>[List Configs]</gold></click>
    """.trimIndent()
}
