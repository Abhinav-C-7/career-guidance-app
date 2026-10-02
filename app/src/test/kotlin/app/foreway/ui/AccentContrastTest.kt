package app.foreway.ui

import androidx.compose.ui.graphics.compositeOver
import app.foreway.domain.model.CareerFamily
import app.foreway.ui.theme.ForewayColors
import app.foreway.ui.theme.accentFor
import app.foreway.ui.theme.contrastRatio
import app.foreway.ui.theme.legibleUnderWhite
import app.foreway.ui.theme.strongAccentFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccentContrastTest {

    @Test
    fun `white text reads on every family's hero, even where a shape sits under it`() {
        CareerFamily.entries.forEach { family ->
            val strong = strongAccentFor(family)
            val underShape = ForewayColors.HeroShape.compositeOver(strong)
            val ratio = contrastRatio(ForewayColors.Canvas, underShape)
            assertTrue(ratio >= 4.5f, "$family: white on its hero is $ratio:1, under the 4.5:1 minimum")
        }
    }

    @Test
    fun `an accent already dark enough is left as it is`() {
        assertEquals(ForewayColors.CtaDark, legibleUnderWhite(ForewayColors.CtaDark))
        assertEquals(ForewayColors.CtaDark, strongAccentFor(null))
    }

    @Test
    fun `darkening stops as soon as it is legible, so the family keeps its colour`() {
        CareerFamily.entries.forEach { family ->
            val strong = strongAccentFor(family)
            // Not pushed all the way to ink: still visibly the family's hue.
            assertTrue(contrastRatio(strong, ForewayColors.Ink) > 1.5f, "$family went to ink")
            assertTrue(contrastRatio(ForewayColors.Canvas, strong) >= contrastRatio(ForewayColors.Canvas, accentFor(family)))
        }
    }
}
