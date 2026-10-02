package app.foreway.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.foreway.domain.model.CareerFamily

/**
 * The accent for whatever career is in context. Defaults to the neutral CTA colour so a
 * screen with no career (onboarding, settings) is never accidentally tinted.
 */
val LocalAccent = staticCompositionLocalOf { ForewayColors.CtaDark }

/** The same accent, dark enough to carry white text: hero cards, filled tiles, the CTA. */
val LocalStrongAccent = staticCompositionLocalOf { ForewayColors.CtaDark }

private val Scheme = lightColorScheme(
    background = ForewayColors.Canvas,
    surface = ForewayColors.Card,
    surfaceVariant = ForewayColors.Surface,
    onBackground = ForewayColors.Ink,
    onSurface = ForewayColors.Ink,
    onSurfaceVariant = ForewayColors.InkMuted,
    outline = ForewayColors.Hairline,
    error = ForewayColors.StateError,
)

@Composable
fun ForewayTheme(
    family: CareerFamily? = null,
    content: @Composable () -> Unit,
) {
    val accent: Color = family?.let { accentFor(it) } ?: ForewayColors.CtaDark
    CompositionLocalProvider(LocalAccent provides accent, LocalStrongAccent provides strongAccentFor(family)) {
        MaterialTheme(
            colorScheme = Scheme.copy(primary = accent),
            typography = ForewayTypography,
            content = content,
        )
    }
}
