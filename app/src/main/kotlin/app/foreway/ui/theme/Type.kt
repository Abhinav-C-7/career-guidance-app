package app.foreway.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

// TODO: DESIGN.md specifies Plus Jakarta Sans for display and Inter for body. Both are
// still the platform default here — the font files are not yet bundled, and Devanagari
// fallbacks need testing before localisation. Sizes and weights below already follow the
// spec, so swapping the family later is a one-line change.
private val Display = FontFamily.SansSerif
private val Body = FontFamily.SansSerif

/** The scale from DESIGN.md. Sizes are sp so the user's font-scale setting is honoured. */
val ForewayTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp,
        textAlign = TextAlign.Start,
    ),
    labelLarge = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 26.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.78.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)
