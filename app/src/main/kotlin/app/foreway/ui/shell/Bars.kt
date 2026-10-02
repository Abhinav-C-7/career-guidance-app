package app.foreway.ui.shell

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.foreway.R
import app.foreway.ui.ExploreGraph
import app.foreway.ui.ExploreRoute
import app.foreway.ui.PathwayGraph
import app.foreway.ui.PathwayRoute
import app.foreway.ui.TodayGraph
import app.foreway.ui.TodayRoute
import app.foreway.ui.YouGraph
import app.foreway.ui.YouRoute
import app.foreway.ui.components.Gutter
import app.foreway.ui.theme.ForewayColors
import app.foreway.ui.theme.ForewayTypography
import app.foreway.ui.theme.LocalAccent
import app.foreway.ui.theme.LocalStrongAccent

/** The four places in the app. Order is the bottom bar's order, and the direction tabs slide. */
enum class Tab(
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    val graph: Any,
    val root: Any,
) {
    TODAY(R.string.tab_today, R.drawable.ic_tab_today, TodayGraph, TodayRoute),
    PATHWAY(R.string.tab_pathway, R.drawable.ic_tab_pathway, PathwayGraph, PathwayRoute),
    EXPLORE(R.string.tab_explore, R.drawable.ic_tab_explore, ExploreGraph, ExploreRoute),
    YOU(R.string.tab_you, R.drawable.ic_tab_you, YouGraph, YouRoute),
}

private val PillWidth = 60.dp
private val PillHeight = 32.dp
private val BarPadTop = 8.dp
private val BarPadBottom = 10.dp

/**
 * The top of every screen after onboarding: a title, an optional way back, optional actions.
 *
 * Canvas white over the surface backdrop. The hairline under it appears only once content
 * has scrolled beneath — one depth level, no shadow (DESIGN.md).
 */
@Composable
fun ForewayTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    scrolled: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(ForewayColors.Canvas)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(start = if (onBack != null) 4.dp else Gutter, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconAction(R.drawable.ic_back, stringResource(R.string.action_back), onBack)
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = title,
                style = ForewayTypography.titleLarge,
                color = ForewayColors.Ink,
                // Two lines rather than a clipped title at 200% font.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
                    .semantics { heading() },
            )
            actions()
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(if (scrolled) ForewayColors.Hairline else ForewayColors.Canvas),
        )
    }
}

/** A 48dp icon button in ink, for the top bar. */
@Composable
fun IconAction(@DrawableRes icon: Int, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = description, tint = ForewayColors.Ink, modifier = Modifier.size(24.dp))
    }
}

/**
 * The bar's height above the system navigation bar. It grows with the label at large font
 * sizes, and tab roots reserve exactly this much, so nothing hides behind it.
 */
@Composable
fun bottomBarHeight(): Dp {
    val label = with(LocalDensity.current) { ForewayTypography.labelSmall.lineHeight.toDp() }
    return 1.dp + maxOf(64.dp, BarPadTop + PillHeight + 4.dp + label + BarPadBottom)
}

/**
 * Where you are in the app. Custom rather than Material's NavigationBar, to keep the look
 * DESIGN.md asks for.
 *
 * One pill slides to the chosen tab, in a wash of the goal's colour, with the icon and label
 * in its strong shade. There is never a badge or a count here (DESIGN.md, anti-patterns).
 */
@Composable
fun ForewayBottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    val accent = LocalAccent.current
    val strong = LocalStrongAccent.current
    val haptics = LocalHapticFeedback.current
    val height = bottomBarHeight() - 1.dp
    Column(
        Modifier
            .fillMaxWidth()
            .background(ForewayColors.Canvas)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(ForewayColors.Hairline))
        BoxWithConstraints(Modifier.fillMaxWidth().height(height)) {
            val slot = maxWidth / Tab.entries.size
            val pillX by animateDpAsState(
                targetValue = slot * current.ordinal + (slot - PillWidth) / 2,
                animationSpec = tween(280, easing = FastOutSlowInEasing),
                label = "tab-pill",
            )
            Box(
                Modifier
                    .offset(x = pillX, y = BarPadTop)
                    .size(PillWidth, PillHeight)
                    .background(accent.copy(alpha = 0.14f), RoundedCornerShape(percent = 50)),
            )
            Row(Modifier.fillMaxSize().selectableGroup()) {
                Tab.entries.forEach { tab ->
                    val selected = tab == current
                    val tint by animateColorAsState(
                        if (selected) strong else ForewayColors.InkMuted,
                        tween(200),
                        label = "tab-tint",
                    )
                    val scale by animateFloatAsState(if (selected) 1.08f else 1f, tween(200), label = "tab-scale")
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(
                                selected = selected,
                                role = Role.Tab,
                                onClick = {
                                    if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onSelect(tab)
                                },
                            )
                            .padding(top = BarPadTop),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top,
                    ) {
                        Box(Modifier.size(PillWidth, PillHeight), contentAlignment = Alignment.Center) {
                            Icon(
                                painterResource(tab.icon),
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier
                                    .size(22.dp)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    },
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(tab.label),
                            style = ForewayTypography.labelSmall,
                            color = tint,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A tab's root screen: top bar and content on the surface backdrop.
 *
 * The shell draws the bottom bar over the space reserved at the foot, so the bar stays put
 * while tabs slide beneath it, and slides away whole when a screen is pushed.
 */
@Composable
fun TabScaffold(
    topBar: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(ForewayColors.Surface)) {
        topBar()
        Column(Modifier.weight(1f).fillMaxWidth(), content = content)
        Spacer(Modifier.fillMaxWidth().height(bottomBarHeight()))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

/** A pushed screen: back-arrow top bar, content, and room for its own bottom CTA. */
@Composable
fun PushedScaffold(
    topBar: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(ForewayColors.Surface)) {
        topBar()
        Column(Modifier.weight(1f).fillMaxWidth(), content = content)
    }
}
