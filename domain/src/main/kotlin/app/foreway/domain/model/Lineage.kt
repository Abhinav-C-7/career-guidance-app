package app.foreway.domain.model

/**
 * The chain of careers from the broadest down to [careerId], root first: doctor, specialist,
 * surgeon, neurosurgeon. A career's pathway is its chain's milestones in this order, and its
 * gates are its chain's criteria.
 */
public object Lineage {

    /** Deeper than any real specialisation; a guard against cycles in bad data. */
    public const val MAX_DEPTH: Int = 8

    /**
     * Root first, ending with [careerId]. A cycle or a chain past [MAX_DEPTH] stops the walk
     * rather than looping: content tests reject both, so on a device this only ever trims
     * data that should never have been published.
     */
    public fun chain(careerId: String, parentOf: (String) -> String?): List<String> {
        val chain = mutableListOf(careerId)
        var next = parentOf(careerId)
        while (next != null && next !in chain && chain.size < MAX_DEPTH) {
            chain += next
            next = parentOf(next)
        }
        return chain.reversed()
    }
}
