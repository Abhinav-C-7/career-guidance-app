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
    /** The steps of the pathway, in order. */
    val milestones: List<Milestone> = emptyList(),
    /**
     * The broader career this specialises. A neurosurgeon is a surgeon is a specialist is a
     * doctor: each level writes only its own steps and inherits the rest, so a NEET rule is
     * written once and every specialisation under it stays in step when it changes.
     */
    val parentId: String? = null,
    /** One sentence for the career browser. Description, not eligibility. */
    val summary: String? = null,
    /**
     * False when no licence or mandatory exam stands between a student and the work —
     * software engineering. Every step of an unregulated career must then be
     * [Necessity.TYPICAL], and the app says plainly that the route is not a rule.
     */
    val regulated: Boolean = true,
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
