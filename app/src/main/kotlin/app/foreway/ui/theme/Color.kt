package app.foreway.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import app.foreway.domain.model.CareerFamily

/**
 * The palette from DESIGN.md. Tokens only — no literal hex anywhere else in the app.
 *
 * The app is light-only by design. A student checking their pathway in daylight on a cheap
 * screen needs maximum contrast, and a second theme is a second set of contrast bugs.
 */
@Immutable
object ForewayColors {
    val Canvas = Color(0xFFFFFFFF)
    val Surface = Color(0xFFF6F6F8)
    val Card = Color(0xFFFFFFFF)

    val Ink = Color(0xFF111114)
    val InkMuted = Color(0xFF6B6B73)
    val InkFaint = Color(0xFF9A9AA3)

    val Hairline = Color(0xFFE7E7EC)
    val CtaDark = Color(0xFF232329)

    // --- Semantic. Never reused as decoration. ---

    val StateMet = Color(0xFF0E9F6E)
    val StatePending = Color(0xFF9A9AA3)
    val StateAttention = Color(0xFFC77700)

    /**
     * Deliberately slate, not red. A colour-blind student learning they cannot fly
     * commercially should not see their own body rendered as a system error.
     * See DESIGN.md — this is not negotiable.
     */
    val StateBlocked = Color(0xFF4A4A57)

    /** App failures only. Network, crash, bad input. Never an eligibility outcome. */
    val StateError = Color(0xFFD32F2F)
}

/**
 * One accent per career family, applied to the eyebrow label, the primary CTA and the
 * active timeline node — and nothing else.
 */
fun accentFor(family: CareerFamily): Color = when (family) {
    CareerFamily.DEFENCE -> Color(0xFF2B5CE6)
    CareerFamily.MEDICAL -> Color(0xFF0E9F6E)
    CareerFamily.ENGINEERING -> Color(0xFF6D4AFF)
    CareerFamily.COMMERCE -> Color(0xFFE8830C)
    CareerFamily.CIVIC -> Color(0xFF8B3A62)
    CareerFamily.DESIGN -> Color(0xFFE2574C)
    CareerFamily.MARITIME -> Color(0xFF0D7C8C)
}

/** A pale wash of the same accent, for icon tiles and chips. */
fun accentTintFor(family: CareerFamily): Color = accentFor(family).copy(alpha = 0.10f)
