package app.foreway.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.foreway.R
import app.foreway.domain.model.Provenance
import app.foreway.ui.format.monthAndYear
import app.foreway.ui.theme.ForewayColors
import app.foreway.ui.theme.ForewayTypography

/**
 * The receipt: "Official notification · verified May 2026", or "needs re-check" once past
 * its verification window. DESIGN.md requires this on every criterion and every step we
 * show — built once, here, so it cannot drift between screens.
 */
@Composable
fun SourceLine(provenance: Provenance?, isStale: Boolean) {
    val authority = provenance?.authority?.name
        ?.lowercase()?.replace('_', ' ')
        ?.replaceFirstChar(Char::uppercase)
        ?: stringResource(R.string.source_fallback)

    if (isStale) {
        Text(
            text = stringResource(R.string.chip_needs_recheck, authority),
            style = ForewayTypography.labelSmall,
            color = ForewayColors.StateAttention,
        )
    } else {
        Text(
            text = stringResource(R.string.chip_verified, authority, provenance?.lastVerifiedAt?.monthAndYear().orEmpty()),
            style = ForewayTypography.labelSmall,
            color = ForewayColors.InkFaint,
        )
    }
}
