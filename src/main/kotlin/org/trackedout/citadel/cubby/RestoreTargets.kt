package org.trackedout.citadel.cubby

object RestoreTargets {
    /** Free cubbies in the order to try them: the cubby the archive came from, then the lowest numbers. */
    fun order(freeCubbyIds: List<String>, originalCubbyId: String): List<String> =
        freeCubbyIds.sortedWith(compareBy({ it != originalCubbyId }, { cubbyNumber(it) ?: Int.MAX_VALUE }))
}
