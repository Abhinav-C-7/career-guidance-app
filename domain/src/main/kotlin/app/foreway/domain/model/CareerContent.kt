package app.foreway.domain.model

import kotlinx.serialization.Serializable

/**
 * One career's authored content, as it lives in the repository and is reviewed in pull
 * requests before it is published.
 */
@Serializable
public data class CareerContent(
    val careerId: String,
    val title: String,
    val family: CareerFamily,
    /**
     * Free text explaining anything a reader of the criteria would otherwise get wrong —
     * notably requirements the source explicitly does *not* impose. Absence of a criterion
     * is ambiguous on its own: it could mean the rule does not exist, or that nobody has
     * written it yet.
     */
    val notes: List<String> = emptyList(),
    val criteria: List<Criterion>,
)

/** Drives the accent colour in DESIGN.md. One family per career. */
@Serializable
public enum class CareerFamily {
    DEFENCE,
    MEDICAL,
    ENGINEERING,
    COMMERCE,
    CIVIC,
    DESIGN,
    MARITIME,
}
