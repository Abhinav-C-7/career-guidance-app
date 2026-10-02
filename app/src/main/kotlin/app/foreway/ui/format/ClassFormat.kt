package app.foreway.ui.format

import androidx.annotation.StringRes
import app.foreway.R
import app.foreway.domain.model.SchoolClass

@StringRes
fun classLabel(c: SchoolClass): Int = when (c) {
    SchoolClass.CLASS_8 -> R.string.class_8
    SchoolClass.CLASS_9 -> R.string.class_9
    SchoolClass.CLASS_10 -> R.string.class_10
    SchoolClass.CLASS_11 -> R.string.class_11
    SchoolClass.CLASS_12 -> R.string.class_12
    SchoolClass.PASSED_12 -> R.string.class_passed_12
}
