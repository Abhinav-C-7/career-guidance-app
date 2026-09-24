package app.foreway.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.foreway.R
import app.foreway.domain.model.AssessedCriterion
import app.foreway.domain.model.GateOutcome
import app.foreway.ui.format.describe
import app.foreway.ui.format.displayValue
import app.foreway.ui.format.monthAndYear
import app.foreway.ui.theme.ForewayColors
import app.foreway.ui.theme.ForewayTypography

/**
 * The workhorse row: status dot, label, value, and the source chip that is mandatory on
 * every criterion we display.
 *
 * A criterion rendered without its source is a bug, not a style choice (DESIGN.md).
 */
@Composable
fun CriterionRow(
    assessed: AssessedCriterion,
    modifier: Modifier = Modifier,
) {
    val outcome = assessed.outcome
    val blocked = outcome as? GateOutcome.CannotBeMet

    Row(modifier = modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Spacer(
            Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .background(dotColour(outcome), CircleShape),
        )
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = assessed.criterion.label,
                style = ForewayTypography.bodyLarge,
                color = ForewayColors.Ink,
            )

            assessed.criterion.requirement?.value?.displayValue()
                ?.takeIf { it.isNotBlank() }
                ?.let { value ->
                    Text(
                        text = value,
                        style = ForewayTypography.labelLarge,
                        color = if (blocked != null) ForewayColors.InkFaint else ForewayColors.InkMuted,
                        // A closed door strikes through the target it cannot reach.
                        textDecoration = if (blocked != null) TextDecoration.LineThrough else null,
                    )
                }

            outcome.describe()?.let { note ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = note,
                    style = ForewayTypography.bodyLarge,
                    color = ForewayColors.InkMuted,
                )
            }

            // A closed door is never rendered alone. DESIGN.md requires the adjacent
            // "here's what this still allows" affordance, always.
            if (blocked != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.still_open_link),
                    style = ForewayTypography.labelLarge,
                    color = ForewayColors.StateBlocked,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(6.dp))
            SourceChip(assessed)
        }
    }
}

/**
 * Source and verification date, or an honest admission that we have neither.
 */
@Composable
private fun SourceChip(assessed: AssessedCriterion) {
    val text: String
    val colour: Color

    when {
        assessed.sourceGap -> {
            val pointer = assessed.criterion.lookUpAt
            text = if (pointer != null) {
                stringResource(R.string.chip_no_figure_with_pointer, pointer.describedAs.take(48))
            } else {
                stringResource(R.string.chip_no_figure)
            }
            colour = ForewayColors.StateAttention
        }
        assessed.isStale -> {
            text = stringResource(R.string.chip_needs_recheck, authorityLabel(assessed))
            colour = ForewayColors.StateAttention
        }
        else -> {
            val verified = assessed.criterion.requirement?.provenance?.lastVerifiedAt
            text = stringResource(
                R.string.chip_verified,
                authorityLabel(assessed),
                verified?.monthAndYear().orEmpty(),
            )
            colour = ForewayColors.InkFaint
        }
    }

    Text(text = text, style = ForewayTypography.labelSmall, color = colour)
}

private fun authorityLabel(assessed: AssessedCriterion): String =
    assessed.criterion.requirement?.provenance?.authority?.name
        ?.lowercase()?.replace('_', ' ')
        ?.replaceFirstChar(Char::uppercase)
        ?: "Source"

private fun dotColour(outcome: GateOutcome): Color = when (outcome) {
    is GateOutcome.Met -> ForewayColors.StateMet
    is GateOutcome.NotYetAssessed -> ForewayColors.StatePending
    is GateOutcome.AtRisk -> ForewayColors.StateAttention
    is GateOutcome.CannotBeMet -> ForewayColors.StateBlocked
}
