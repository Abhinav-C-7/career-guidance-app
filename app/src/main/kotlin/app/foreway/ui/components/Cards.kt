package app.foreway.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.foreway.ui.art.IconTile
import app.foreway.ui.theme.ForewayColors
import app.foreway.ui.theme.ForewayTypography

private val CardShape = RoundedCornerShape(20.dp)

/**
 * DESIGN.md's card: white on the surface backdrop, radius 20, hairline border. [padded]
 * false is for cards made of full-width rows, which pad themselves. A card with [onClick]
 * presses in under the finger.
 */
@Composable
fun ForewayCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .padding(horizontal = Gutter)
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.tappable(shape = CardShape, onClick = onClick) else Modifier.clip(CardShape))
            .background(ForewayColors.Card, CardShape)
            .border(1.dp, ForewayColors.Hairline, CardShape)
            .then(if (padded) Modifier.padding(20.dp) else Modifier.padding(vertical = 4.dp)),
        content = content,
    )
}

/** A card's small heading, muted, above its content. */
@Composable
fun CardLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = ForewayTypography.labelLarge,
        color = ForewayColors.InkMuted,
        modifier = modifier.semantics { heading() },
    )
}

/** A card's heading with its icon tile: what the card is about, at a glance. */
@Composable
fun CardHeader(@DrawableRes icon: Int, title: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = ForewayTypography.labelLarge,
            color = ForewayColors.Ink,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
    }
}

/** A heading between cards on a dashboard. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = ForewayTypography.titleLarge,
        color = ForewayColors.Ink,
        modifier = modifier
            .padding(horizontal = Gutter)
            .semantics { heading() },
    )
}

/**
 * A tappable row inside a card: optional icon tile, label, optional value, chevron. 56dp
 * minimum, the whole row is the target.
 */
@Composable
fun NavRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    divider: Boolean = false,
    @DrawableRes icon: Int? = null,
) {
    Column(modifier) {
        if (divider) {
            Box(
                Modifier
                    .padding(start = if (icon != null) 68.dp else 20.dp, end = 20.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ForewayColors.Hairline),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = if (icon != null) 16.dp else 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                IconTile(icon, size = 36.dp)
                Spacer(Modifier.width(16.dp))
            }
            Text(label, style = ForewayTypography.bodyLarge, color = ForewayColors.Ink, modifier = Modifier.weight(1f))
            value?.let {
                Spacer(Modifier.width(12.dp))
                Text(
                    it,
                    style = ForewayTypography.bodyLarge,
                    color = ForewayColors.InkMuted,
                    textAlign = TextAlign.End,
                    // Fills its half, so every row's chevron lines up at the right edge.
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text("›", style = ForewayTypography.titleLarge, color = ForewayColors.InkFaint)
        }
    }
}

/** "See all ›" at the foot of a card. Ink, a real 48dp target. */
@Composable
fun CardLink(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = ForewayTypography.labelLarge, color = ForewayColors.Ink, modifier = Modifier.weight(1f))
        Text("›", style = ForewayTypography.titleLarge, color = ForewayColors.InkMuted)
    }
}

/** An empty state that leads somewhere: a picture, a title, a line, and one way forward. */
@Composable
fun EmptyCard(
    title: String,
    body: String,
    action: String?,
    onAction: () -> Unit,
    art: (@Composable () -> Unit)? = null,
) {
    ForewayCard {
        if (art != null) {
            art()
            Spacer(Modifier.height(20.dp))
        }
        Text(title, style = ForewayTypography.titleLarge, color = ForewayColors.Ink)
        Spacer(Modifier.height(8.dp))
        Text(body, style = ForewayTypography.bodyLarge, color = ForewayColors.InkMuted)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            SecondaryAction(action, onAction)
        }
    }
}
