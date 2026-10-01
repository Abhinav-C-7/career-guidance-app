package app.foreway.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.foreway.ui.theme.ForewayColors
import app.foreway.ui.theme.ForewayTypography
import app.foreway.ui.theme.LocalAccent

val Gutter = 20.dp

/**
 * One per screen, full width minus gutters, at least 56 high, pill, bottom anchored with
 * 24 below (DESIGN.md). Disabled is a lighter fill, never hidden: a CTA that vanishes
 * makes the screen look finished when it is not.
 *
 * heightIn rather than a fixed height, so the label can wrap at 200% font scale instead
 * of being clipped.
 */
@Composable
fun PrimaryCta(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = LocalAccent.current,
) {
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = Gutter)
            .padding(bottom = 24.dp)
            .heightIn(min = 56.dp)
            .background(if (enabled) accent else ForewayColors.Hairline, RoundedCornerShape(percent = 50))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = ForewayTypography.labelLarge,
            color = if (enabled) ForewayColors.Canvas else ForewayColors.InkFaint,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A single-choice answer. The whole row is the touch target, comfortably over 48dp, and it
 * announces itself as a radio option to TalkBack.
 */
@Composable
fun OptionRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
) {
    val accent = LocalAccent.current
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(ForewayColors.Card, RoundedCornerShape(16.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) accent else ForewayColors.Hairline,
                shape = RoundedCornerShape(16.dp),
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            eyebrow?.let {
                Text(text = it.uppercase(), style = ForewayTypography.labelMedium, color = ForewayColors.InkMuted)
            }
            Text(text = label, style = ForewayTypography.labelLarge, color = ForewayColors.Ink)
        }
        Spacer(Modifier.width(12.dp))
        // A plain ring that fills. No checkmark animation, nothing that celebrates.
        Box(
            Modifier
                .size(20.dp)
                .border(2.dp, if (selected) accent else ForewayColors.Hairline, CircleShape)
                .padding(5.dp)
                .background(if (selected) accent else Color.Transparent, CircleShape),
        )
    }
}

/** A secondary action — skip, not sure. Quiet, but a real touch target. */
@Composable
fun TextAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = label, style = ForewayTypography.labelLarge, color = ForewayColors.InkMuted)
    }
}

/**
 * Leaves the screen. Ink, not muted, with a drawn arrow: a way out is load-bearing and must
 * read as tappable at a glance (DESIGN.md, 7:1 for anything load-bearing). The system back
 * gesture does the same thing.
 */
@Composable
fun BackAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(20.dp)) {
            val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val y = size.height / 2
            drawPath(
                Path().apply {
                    moveTo(size.width * 0.85f, y)
                    lineTo(size.width * 0.15f, y)
                    moveTo(size.width * 0.45f, size.height * 0.2f)
                    lineTo(size.width * 0.15f, y)
                    lineTo(size.width * 0.45f, size.height * 0.8f)
                },
                color = ForewayColors.Ink,
                style = stroke,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = ForewayTypography.labelLarge, color = ForewayColors.Ink)
    }
}

/**
 * A real choice that is not the screen's one primary CTA — change career, see all careers.
 * A neutral pill on `surface`: clearly a button, without competing with the accent CTA.
 */
@Composable
fun SecondaryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .heightIn(min = 48.dp)
            .background(ForewayColors.Surface, RoundedCornerShape(percent = 50))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = ForewayTypography.labelLarge, color = ForewayColors.Ink)
        Spacer(Modifier.width(8.dp))
        Text(text = "›", style = ForewayTypography.labelLarge, color = ForewayColors.InkMuted)
    }
}
