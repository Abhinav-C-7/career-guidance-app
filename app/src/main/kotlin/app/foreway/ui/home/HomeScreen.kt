package app.foreway.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.foreway.ui.components.CriterionRow
import app.foreway.ui.components.TimelineRail
import app.foreway.ui.theme.ForewayColors
import app.foreway.ui.theme.ForewayTheme
import app.foreway.ui.theme.ForewayTypography
import app.foreway.ui.theme.LocalAccent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val Gutter = 20.dp

/**
 * Home answers one question in two seconds: where am I, and is anything live?
 *
 * It is read-only. Every element navigates; nothing here mutates state, which keeps the
 * screen trivially cacheable and lets it open from the local store with no network.
 *
 * There is no streak, no percentage, no badge. See DESIGN.md's anti-patterns list.
 */
@Composable
fun HomeScreen() {
    ForewayTheme(family = HomeFixture.family) {
        val accent = LocalAccent.current

        Box(
            Modifier
                .fillMaxSize()
                .background(ForewayColors.Canvas),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    // Inset padding must sit OUTSIDE the scroll, or it scrolls away and
                    // content slides under the status bar.
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .verticalScroll(rememberScrollState())
                    // Clears the floating CTA plus the gesture bar, so the last criterion
                    // is reachable rather than permanently hidden behind the button.
                    .padding(bottom = 140.dp),
            ) {
                Spacer(Modifier.height(24.dp))

                Text(
                    text = HomeFixture.familyLabel,
                    style = ForewayTypography.labelMedium,
                    color = accent,
                    modifier = Modifier.padding(horizontal = Gutter),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = HomeFixture.careerTitle,
                    style = ForewayTypography.headlineLarge,
                    color = ForewayColors.Ink,
                    modifier = Modifier.padding(horizontal = Gutter),
                )

                Spacer(Modifier.height(24.dp))

                // Position in time, on the recessed surface DESIGN.md specifies.
                Box(
                    Modifier
                        .padding(horizontal = Gutter)
                        .fillMaxWidth()
                        .background(ForewayColors.Surface, RoundedCornerShape(20.dp))
                        .padding(20.dp),
                ) {
                    TimelineRail(
                        stages = HomeFixture.stages,
                        currentIndex = HomeFixture.currentStageIndex,
                        accent = accent,
                    )
                }

                Spacer(Modifier.height(28.dp))

                Text(
                    text = "Live right now",
                    style = ForewayTypography.titleLarge,
                    color = ForewayColors.Ink,
                    modifier = Modifier.padding(horizontal = Gutter),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Six things stand between you and this career. Here is where each one sits today.",
                    style = ForewayTypography.bodyLarge,
                    color = ForewayColors.InkMuted,
                    modifier = Modifier.padding(horizontal = Gutter),
                )

                Spacer(Modifier.height(12.dp))

                Column(
                    Modifier
                        .padding(horizontal = Gutter)
                        .fillMaxWidth()
                        .background(ForewayColors.Card, RoundedCornerShape(20.dp))
                        .border(1.dp, ForewayColors.Hairline, RoundedCornerShape(20.dp))
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    HomeFixture.assessed.forEach { CriterionRow(it) }
                }
            }

            PrimaryCta(
                label = "View full pathway",
                accent = accent,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * One per screen, full width minus gutters, 56 high, pill, bottom anchored with 24 below.
 */
@Composable
private fun PrimaryCta(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = Gutter)
            .padding(bottom = 24.dp)
            .height(56.dp)
            .background(accent, RoundedCornerShape(percent = 50)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = ForewayTypography.labelLarge,
            color = ForewayColors.Canvas,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(name = "Home", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun HomePreview() {
    HomeScreen()
}

/** The layout must survive a user running large text. DESIGN.md asks for 200%. */
@Preview(name = "Home at 200% font", showBackground = true, widthDp = 360, heightDp = 780, fontScale = 2.0f)
@Composable
private fun HomeLargeTextPreview() {
    HomeScreen()
}
