package app.foreway.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.foreway.R
import app.foreway.data.CareerSummary
import app.foreway.ui.format.familyLabel
import app.foreway.ui.theme.ForewayColors
import app.foreway.ui.theme.ForewayTypography

/**
 * One career in a list: title, its one-line summary, and how many specialisations sit
 * beneath it. Tapping opens the career; it never sets a goal on its own — choosing a goal is
 * a decision, and gets its own screen and CTA (DESIGN.md, one decision per screen).
 */
@Composable
fun CareerCard(
    career: CareerSummary,
    specialisations: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    showFamily: Boolean = false,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(ForewayColors.Card, RoundedCornerShape(16.dp))
            .border(1.dp, ForewayColors.Hairline, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (showFamily) {
                Text(
                    text = stringResource(familyLabel(career.family)).uppercase(),
                    style = ForewayTypography.labelMedium,
                    color = ForewayColors.InkMuted,
                )
            }
            Text(career.title, style = ForewayTypography.labelLarge, color = ForewayColors.Ink)
            career.summary?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = ForewayTypography.bodyLarge, color = ForewayColors.InkMuted)
            }
            if (specialisations > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    pluralStringResource(R.plurals.browse_specialisations, specialisations, specialisations),
                    style = ForewayTypography.labelSmall,
                    color = ForewayColors.InkFaint,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text("›", style = ForewayTypography.titleLarge, color = ForewayColors.InkFaint)
    }
}

/** The plain statement every unregulated career carries, so a route is never read as a rule. */
@Composable
fun UnregulatedNote(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.unregulated_note),
        style = ForewayTypography.bodyLarge,
        color = ForewayColors.Ink,
        modifier = modifier
            .fillMaxWidth()
            .background(ForewayColors.Surface, RoundedCornerShape(16.dp))
            .padding(16.dp),
    )
}
