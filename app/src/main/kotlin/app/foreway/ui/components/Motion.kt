package app.foreway.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** The most a staggered reveal waits, so a long list never makes the student wait for its end. */
private const val MAX_STAGGER = 5

/**
 * Rises and fades in the first time a screen shows it, [index] places behind the one before.
 * Once only: coming back to a tab shows it settled, never replayed. Compose scales this by the
 * system animator setting, so "Remove animations" turns it off (DESIGN.md, Motion).
 */
@Composable
fun Reveal(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val delay = minOf(index, MAX_STAGGER) * 55
    val rise = with(LocalDensity.current) { 20.dp.roundToPx() }
    Reveal(
        enter = fadeIn(tween(260, delayMillis = delay)) +
            slideInVertically(tween(320, delayMillis = delay, easing = FastOutSlowInEasing)) { rise },
        modifier = modifier,
        content = content,
    )
}

/** As above, with the caller's own entrance. */
@Composable
fun Reveal(enter: EnterTransition, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var seen by rememberSaveable { mutableStateOf(false) }
    val visible = remember { MutableTransitionState(seen).apply { targetState = true } }
    LaunchedEffect(Unit) { seen = true }
    AnimatedVisibility(visibleState = visible, modifier = modifier, enter = enter, exit = ExitTransition.None) {
        content()
    }
}

/**
 * Clickable, with a small press-in so a card feels like something under the finger.
 * A quick ease, no spring and no overshoot (DESIGN.md: nothing bounces). [shape] clips the
 * content and the ripple to the card's corners.
 */
fun Modifier.tappable(shape: Shape? = null, role: Role = Role.Button, onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(if (pressed) 90 else 160),
        label = "press",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
        if (shape != null) {
            this.shape = shape
            clip = true
        }
    }.clickable(interactionSource = interaction, indication = LocalIndication.current, role = role, onClick = onClick)
}
