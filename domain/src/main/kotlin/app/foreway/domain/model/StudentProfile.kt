package app.foreway.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Everything the student has told us. Nothing else.
 *
 * Every field defaults to "not told". The distinction between null and empty is load
 * bearing and must survive every refactor:
 *
 *  - `null`        we have never asked, or they skipped it
 *  - `emptySet()`  they answered, and the answer was none
 *
 * Collapsing those two is how an app starts asserting things about a student it was never
 * told (CLAUDE.md, conventions). A profile that is mostly nulls is the normal case, not a
 * degraded one — a class 8 student legitimately knows almost none of this yet.
 */
@Serializable
public data class StudentProfile(
    val dateOfBirth: LocalDate? = null,
    val sex: Sex? = null,
    val currentClass: SchoolClass? = null,
    val board: String? = null,
    val state: String? = null,
    val nationality: String? = null,
    val maritalState: MaritalState? = null,

    /** Subjects currently taken. Null until they reach a stage where it is a real answer. */
    val subjectsTaken: Set<Subject>? = null,

    val bodyMetrics: Map<BodyMetricKind, Double> = emptyMap(),
    val distantVision: MeasuredVision? = null,
    val colourVision: ColourVisionGrade? = null,

    /** Percentage achieved, per stage. Absent means not sat yet, or not told. */
    val marksByStage: Map<SchoolStage, Double> = emptyMap(),

    /** Attempts already used, keyed by exam id. */
    val attemptsUsed: Map<String, Int> = emptyMap(),

    /** Self-declared medical conditions, by code. Null means never asked. */
    val declaredConditions: Set<String>? = null,

    /** Self-declared administrative bars, by code. Null means never asked. */
    val declaredDisqualifications: Set<String>? = null,
)

/**
 * What an eye test actually reports. Every field past the uncorrected acuities is optional
 * because a school eye camp will not measure dioptres, and we would rather hold a partial
 * record than invent the rest.
 */
@Serializable
public data class MeasuredVision(
    val uncorrectedBetterEye: Int,
    val uncorrectedWorseEye: Int,
    val bestCorrectedEachEye: Int? = null,
    val myopiaDSph: Double? = null,
    val hypermetropiaDSph: Double? = null,
    val hadRefractiveSurgery: Boolean? = null,
)

@Serializable
public enum class SchoolClass(public val number: Int) {
    CLASS_8(8),
    CLASS_9(9),
    CLASS_10(10),
    CLASS_11(11),
    CLASS_12(12),
    PASSED_12(13),
}
