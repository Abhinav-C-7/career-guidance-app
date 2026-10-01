package app.foreway.ui.format

import androidx.annotation.StringRes
import app.foreway.R
import app.foreway.domain.model.CareerFamily

/** Eyebrow label for a career family. Null is a family this build does not know yet. */
@StringRes
fun familyLabel(family: CareerFamily?): Int = when (family) {
    CareerFamily.DEFENCE -> R.string.family_defence
    CareerFamily.MEDICAL -> R.string.family_medical
    CareerFamily.ENGINEERING -> R.string.family_engineering
    CareerFamily.COMMERCE -> R.string.family_commerce
    CareerFamily.CIVIC -> R.string.family_civic
    CareerFamily.DESIGN -> R.string.family_design
    CareerFamily.MARITIME -> R.string.family_maritime
    null -> R.string.family_unknown
}
