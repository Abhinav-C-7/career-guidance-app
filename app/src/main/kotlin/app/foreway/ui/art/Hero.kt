package app.foreway.ui.art

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.foreway.data.CareerSummary
import app.foreway.domain.model.CareerFamily
import app.foreway.ui.components.Gutter
import app.foreway.ui.components.Reveal
import app.foreway.ui.components.tappable
import app.foreway.ui.theme.ForewayColors
import app.foreway.ui.theme.ForewayTypography
import app.foreway.ui.theme.LocalAccent
import app.foreway.ui.theme.LocalStrongAccent
import app.foreway.ui.theme.accentFor
import app.foreway.ui.theme.strongAccentFor
import kotlin.math.min
import kotlin.random.Random

private val HeroCorner = RoundedCornerShape(24.dp)
private val TileCorner = RoundedCornerShape(20.dp)
private val OnHero = ForewayColors.Canvas

/**
 * A career's picture: its family colour as a gradient, two soft shapes and the family's
 * motif scattered faintly, all drawn in code — no bitmaps, nothing to download, a few hundred
 * bytes of APK. The arrangement is seeded by [seed] (the career id), so every career gets its
 * own picture without anyone drawing one.
 *
 * The colour is the strong accent: white text on it passes 4.5:1 even where a shape sits
 * under the text (AccentContrastTest).
 */
@Composable
fun HeroBackdrop(
    family: CareerFamily?,
    seed: String,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val strong = strongAccentFor(family)
    val deep = lerp(strong, ForewayColors.Ink, 0.3f)
    val motif = painterResource(familyIcon(family))
    val layout = remember(seed) { HeroLayout.of(seed) }
    Box(modifier.background(Brush.linearGradient(listOf(strong, deep)))) {
        // Shapes drawn opaque into one layer, then the layer faded as a whole, so where two
        // overlap they never add up to more than the 8% the contrast test allows.
        Spacer(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    alpha = ForewayColors.HeroShape.alpha
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawBehind { drawHero(layout, motif) },
        )
        content()
    }
}

/**
 * A card whose top is a [HeroBackdrop] carrying [header] in white, and whose optional [body]
 * sits on plain white below it.
 */
@Composable
fun HeroCard(
    family: CareerFamily?,
    seed: String,
    modifier: Modifier = Modifier,
    body: (@Composable ColumnScope.() -> Unit)? = null,
    header: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .padding(horizontal = Gutter)
            .fillMaxWidth()
            .clip(HeroCorner)
            .background(ForewayColors.Card)
            .border(1.dp, ForewayColors.Hairline, HeroCorner),
    ) {
        HeroBackdrop(family, seed, Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(20.dp), content = header)
        }
        if (body != null) Column(Modifier.fillMaxWidth().padding(20.dp), content = body)
    }
}

@Composable
fun HeroEyebrow(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = ForewayTypography.labelMedium, color = OnHero, modifier = modifier)
}

@Composable
fun HeroTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = ForewayTypography.headlineLarge, color = OnHero, modifier = modifier)
}

@Composable
fun HeroText(text: String, modifier: Modifier = Modifier, small: Boolean = false) {
    Text(
        text,
        style = if (small) ForewayTypography.labelSmall else ForewayTypography.bodyLarge,
        color = OnHero,
        modifier = modifier,
    )
}

/**
 * The career's icon on a white disc with a soft halo, for the hero. It settles in once, the
 * first time the screen shows it.
 */
@Composable
fun IconBadge(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    tint: Color = LocalStrongAccent.current,
) {
    Reveal(
        enter = scaleIn(tween(320, delayMillis = 120, easing = FastOutSlowInEasing), initialScale = 0.7f) +
            fadeIn(tween(240, delayMillis = 120)),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .size(size + 10.dp)
                .background(OnHero.copy(alpha = 0.22f), CircleShape)
                .padding(5.dp)
                .background(OnHero, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
        }
    }
}

/**
 * A small rounded square in a pale wash of the accent, with the icon in the strong accent.
 * The colour is decoration: it is never a gate state (DESIGN.md, semantic colours).
 */
@Composable
fun IconTile(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    accent: Color = LocalAccent.current,
    strong: Color = LocalStrongAccent.current,
    size: Dp = 40.dp,
) {
    Box(
        modifier
            .size(size)
            .background(accent.copy(alpha = 0.12f), RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = strong, modifier = Modifier.size(size * 0.55f))
    }
}

/** [IconTile] in a career's own family colour, wherever the screen's accent is not that career's. */
@Composable
fun CareerIconTile(career: CareerSummary, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val family = career.family
    IconTile(
        icon = careerIcon(career),
        modifier = modifier,
        accent = family?.let { accentFor(it) } ?: ForewayColors.CtaDark,
        strong = strongAccentFor(family),
        size = size,
    )
}

/**
 * The careers a goal builds on, root first, as a row of icons: past ones faint, the goal
 * itself on a solid disc. Decoration beside the "Builds on" line, never instead of it.
 */
@Composable
fun LineageStrip(chain: List<CareerSummary>, modifier: Modifier = Modifier) {
    val strong = LocalStrongAccent.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        chain.forEachIndexed { i, career ->
            val goal = i == chain.lastIndex
            if (i > 0) Box(Modifier.width(12.dp).height(2.dp).background(OnHero.copy(alpha = 0.5f)))
            Box(
                Modifier
                    .size(if (goal) 36.dp else 28.dp)
                    .background(if (goal) OnHero else OnHero.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(careerIcon(career)),
                    contentDescription = null,
                    tint = if (goal) strong else OnHero,
                    modifier = Modifier.size(if (goal) 20.dp else 16.dp),
                )
            }
        }
    }
}

/** A career as a small picture card, for carousels. */
@Composable
fun CareerTile(career: CareerSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    HeroBackdrop(
        family = career.family,
        seed = career.id,
        modifier = modifier
            .width(212.dp)
            .heightIn(min = 168.dp)
            .tappable(shape = TileCorner, onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(
                Modifier.size(44.dp).background(OnHero, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(careerIcon(career)), null, tint = strongAccentFor(career.family), modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(career.title, style = ForewayTypography.labelLarge, color = OnHero, minLines = 2, maxLines = 3, overflow = TextOverflow.Ellipsis)
            career.summary?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = ForewayTypography.labelSmall, color = OnHero, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * A field to filter by: a small hero in the family colour, its icon and name. The chosen one
 * gets an ink ring, which reads on every family colour and on the grey backdrop alike.
 */
@Composable
fun FieldTile(
    label: String,
    detail: String?,
    @DrawableRes icon: Int,
    family: CareerFamily?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .border(2.5.dp, if (selected) ForewayColors.Ink else Color.Transparent, RoundedCornerShape(25.dp))
            .padding(5.dp),
    ) {
        HeroBackdrop(
            family = family,
            seed = label,
            modifier = Modifier
                .width(120.dp)
                .semantics { this.selected = selected }
                .tappable(shape = TileCorner, role = Role.Tab, onClick = onClick),
        ) {
            Column(Modifier.padding(14.dp)) {
                Icon(painterResource(icon), contentDescription = null, tint = OnHero, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(18.dp))
                // Three lines for every tile, so the row is even whichever field has the longest name.
                Text(label, style = ForewayTypography.labelLarge, color = OnHero, minLines = 3, maxLines = 3, overflow = TextOverflow.Ellipsis)
                detail?.let { Text(it, style = ForewayTypography.labelSmall, color = OnHero) }
            }
        }
    }
}

/**
 * Every field at once, as overlapping coloured discs: the picture for "you have not chosen
 * yet". Each disc settles in a beat after the one before, once.
 */
@Composable
fun FieldMosaic(modifier: Modifier = Modifier, families: List<CareerFamily> = CareerFamily.entries) {
    val disc = 46.dp
    val step = 32.dp
    Box(modifier.width(disc + step * (families.size - 1)).height(disc)) {
        families.forEachIndexed { i, family ->
            Reveal(
                enter = scaleIn(tween(280, delayMillis = 60 * i, easing = FastOutSlowInEasing), initialScale = 0.5f) +
                    fadeIn(tween(200, delayMillis = 60 * i)),
                modifier = Modifier.offset(x = step * i),
            ) {
                Box(
                    Modifier
                        .size(disc)
                        .background(ForewayColors.Card, CircleShape)
                        .padding(3.dp)
                        .background(strongAccentFor(family), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(familyIcon(family)), null, tint = OnHero, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** Where the shapes go, as fractions of the hero, picked once per seed. */
private class HeroLayout(
    val circle: Offset,
    val circleRadius: Float,
    val ring: Offset,
    val ringRadius: Float,
    val motifs: List<Motif>,
) {
    class Motif(val at: Offset, val size: Dp, val degrees: Float)

    companion object {
        fun of(seed: String): HeroLayout {
            val r = Random(seed.hashCode())
            fun between(a: Float, b: Float) = a + r.nextFloat() * (b - a)
            return HeroLayout(
                circle = Offset(between(0.82f, 1.02f), between(-0.2f, 0.1f)),
                circleRadius = between(0.62f, 0.8f),
                ring = Offset(between(0.55f, 0.8f), between(0.95f, 1.15f)),
                ringRadius = between(0.32f, 0.45f),
                motifs = List(3) { i ->
                    Motif(
                        // Spread down the right-hand side, away from where the text starts.
                        at = Offset(between(0.62f, 0.94f), between(0.15f + i * 0.28f, 0.3f + i * 0.28f)),
                        size = between(22f, 38f).dp,
                        degrees = between(-24f, 24f),
                    )
                },
            )
        }
    }
}

private fun DrawScope.drawHero(layout: HeroLayout, motif: Painter) {
    val white = ForewayColors.HeroShape.copy(alpha = 1f)
    // Shapes scale with the hero's height, capped so a tall hero at 200% font is not all circle.
    val unit = min(size.height, 240.dp.toPx())
    drawCircle(
        color = white,
        radius = unit * layout.circleRadius,
        center = Offset(size.width * layout.circle.x, size.height * layout.circle.y),
    )
    drawCircle(
        color = white,
        radius = unit * layout.ringRadius,
        center = Offset(size.width * layout.ring.x, size.height * layout.ring.y),
        style = Stroke(width = 12.dp.toPx()),
    )
    layout.motifs.forEach { m ->
        val s = m.size.toPx()
        translate(size.width * m.at.x - s / 2, size.height * m.at.y - s / 2) {
            rotate(m.degrees, pivot = Offset(s / 2, s / 2)) {
                with(motif) { draw(Size(s, s), colorFilter = ColorFilter.tint(white)) }
            }
        }
    }
}
