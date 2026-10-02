package app.foreway.ui.you

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.foreway.R
import app.foreway.ui.art.IconTile
import app.foreway.ui.components.ForewayCard
import app.foreway.ui.components.Reveal
import app.foreway.ui.shell.ForewayTopBar
import app.foreway.ui.shell.PushedScaffold
import app.foreway.ui.theme.ForewayColors
import app.foreway.ui.theme.ForewayTheme
import app.foreway.ui.theme.ForewayTypography

/** What Foreway is and is not, in the student's words rather than ours. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val scroll = rememberScrollState()
    ForewayTheme(family = null) {
        PushedScaffold(
            topBar = { ForewayTopBar(stringResource(R.string.you_about), onBack = onBack, scrolled = scroll.value > 0) },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(top = 8.dp, bottom = 24.dp),
            ) {
                listOf(
                    Triple(R.drawable.ic_board, R.string.about_sources_title, R.string.about_sources_body),
                    Triple(R.drawable.ic_career_security, R.string.about_never_title, R.string.about_never_body),
                    Triple(R.drawable.ic_phone, R.string.about_phone_title, R.string.about_phone_body),
                    Triple(R.drawable.ic_family_commerce, R.string.about_cutoffs_title, R.string.about_cutoffs_body),
                ).forEachIndexed { i, (icon, title, body) ->
                    if (i > 0) Spacer(Modifier.height(12.dp))
                    Reveal(i) {
                        ForewayCard {
                            IconTile(icon)
                            Spacer(Modifier.height(14.dp))
                            Text(stringResource(title), style = ForewayTypography.titleLarge, color = ForewayColors.Ink)
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(body), style = ForewayTypography.bodyLarge, color = ForewayColors.InkMuted)
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "About", showBackground = true, widthDp = 360, heightDp = 1100)
@Composable
private fun AboutPreview() {
    AboutScreen(onBack = {})
}
