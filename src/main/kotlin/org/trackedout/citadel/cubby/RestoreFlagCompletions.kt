package org.trackedout.citadel.cubby

private const val CUBBY_FLAG = "--cubby"
private const val ARCHIVE_FLAG = "--archive"
private const val CONFIRM_FLAG_NAME = "--confirm"
private val RESTORE_FLAGS = listOf(CUBBY_FLAG, ARCHIVE_FLAG, CONFIRM_FLAG_NAME)

/** Tab completion for the trailing flags of `/do cubby restore <player> ...`. */
object RestoreFlagCompletions {
    /**
     * @param typedArgs everything typed after the command, ending with the word being completed
     * @param archiveTimestampsOf the archive timestamps of a player, newest first
     */
    fun suggest(typedArgs: List<String>, freeCubbyIds: List<String>, archiveTimestampsOf: (String) -> List<String>): List<String> {
        val finishedWords = typedArgs.dropLast(1)
        return when (finishedWords.lastOrNull()) {
            CUBBY_FLAG -> freeCubbyIds
            ARCHIVE_FLAG -> archiveTimestampsOf(finishedWords.first())
            else -> RESTORE_FLAGS - finishedWords.toSet()
        }
    }
}
