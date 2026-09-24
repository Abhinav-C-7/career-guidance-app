package app.foreway.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.foreway.ui.theme.ForewayColors
import app.foreway.ui.theme.ForewayTypography

/**
 * Where the student is in time. Not a progress bar.
 *
 * DESIGN.md is explicit that progress is position, never a score — so there is no
 * percentage, no fill, and nothing here implies the student is behind. Past markers are
 * faint, the current one is the career accent and larger, the future is hairline.
 *
 * It does not animate on load.
 */
@Composable
fun TimelineRail(
    stages: List<String>,
    currentIndex: Int,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            stages.forEachIndexed { index, _ ->
                val isCurrent = index == currentIndex
                val isPast = index < currentIndex

                Spacer(
                    Modifier
                        .size(if (isCurrent) 15.dp else 9.dp)
                        .background(
                            color = when {
                                isCurrent -> accent
                                isPast -> ForewayColors.InkFaint
                                else -> ForewayColors.Hairline
                            },
                            shape = CircleShape,
                        ),
                )

                if (index != stages.lastIndex) {
                    Spacer(
                        Modifier
                            .weight(1f)
                            .height(2.dp)
                            .background(
                                if (index < currentIndex) ForewayColors.InkFaint
                                else ForewayColors.Hairline,
                            ),
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            stages.forEachIndexed { index, label ->
                Text(
                    text = label,
                    style = ForewayTypography.labelSmall,
                    color = if (index == currentIndex) ForewayColors.Ink else ForewayColors.InkFaint,
                    fontWeight = if (index == currentIndex) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = if (index == 0) TextAlign.Start else TextAlign.End,
                )
            }
        }
    }
}
