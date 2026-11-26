package org.trackedout.citadel.inventory

/**
 * What it takes to make a target deck hold the same entries as a source deck.
 * Entries are card shorthands and trade ids, one list element per copy.
 * Only the difference is applied, so entries that are already in the target are left alone.
 */
data class DeckCopyPlan(
    val toAdd: Map<String, Int>,
    val toRemove: Map<String, Int>,
    val notInTargetCatalog: Map<String, Int>,
) {
    val isNoop: Boolean get() = toAdd.isEmpty() && toRemove.isEmpty()

    companion object {
        fun of(sourceEntries: List<String>, targetEntries: List<String>, validInTarget: Set<String>): DeckCopyPlan {
            val (copyable, notInTargetCatalog) = sourceEntries.partition { it in validInTarget }
            val wanted = copyable.groupingBy { it }.eachCount()
            val present = targetEntries.groupingBy { it }.eachCount()

            val names = wanted.keys + present.keys
            val difference = names.associateWith { (wanted[it] ?: 0) - (present[it] ?: 0) }

            return DeckCopyPlan(
                toAdd = difference.filterValues { it > 0 },
                toRemove = difference.filterValues { it < 0 }.mapValues { -it.value },
                notInTargetCatalog = notInTargetCatalog.groupingBy { it }.eachCount(),
            )
        }
    }
}
