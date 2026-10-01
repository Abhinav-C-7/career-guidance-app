package app.foreway.domain.model

/**
 * The codes stored in [StudentProfile.board] and [StudentProfile.state], and matched by
 * [Applicability.boards] and [Applicability.states].
 *
 * They are a contract between the app and the content store. A content author who writes
 * "Karnataka" where the app stores "IN-KA" has written a criterion that applies to nobody,
 * silently — so both sides use these constants, and content tests check against them.
 */
public object BoardCodes {
    public const val CBSE: String = "CBSE"

    /** ICSE (class 10) and ISC (class 12). */
    public const val CISCE: String = "CISCE"

    /** Any state board. Pair with [Applicability.states] when a rule is state-specific. */
    public const val STATE: String = "STATE"

    public const val NIOS: String = "NIOS"

    /** IB, Cambridge and similar. */
    public const val INTERNATIONAL: String = "INTERNATIONAL"

    public const val OTHER: String = "OTHER"

    public val all: Set<String> = setOf(CBSE, CISCE, STATE, NIOS, INTERNATIONAL, OTHER)
}

/**
 * ISO 3166-2:IN subdivision codes, 2023 revision: 28 states and 8 union territories.
 * Codes only — display names are UI copy and live in the app's string resources.
 */
public object IndianStates {
    public val codes: Set<String> = setOf(
        // States
        "IN-AP", "IN-AR", "IN-AS", "IN-BR", "IN-CG", "IN-GA", "IN-GJ", "IN-HR", "IN-HP",
        "IN-JH", "IN-KA", "IN-KL", "IN-MP", "IN-MH", "IN-MN", "IN-ML", "IN-MZ", "IN-NL",
        "IN-OD", "IN-PB", "IN-RJ", "IN-SK", "IN-TN", "IN-TS", "IN-TR", "IN-UP", "IN-UK",
        "IN-WB",
        // Union territories
        "IN-AN", "IN-CH", "IN-DH", "IN-DL", "IN-JK", "IN-LA", "IN-LD", "IN-PY",
    )
}
