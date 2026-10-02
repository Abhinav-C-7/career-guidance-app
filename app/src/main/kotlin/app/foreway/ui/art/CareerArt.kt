package app.foreway.ui.art

import androidx.annotation.DrawableRes
import app.foreway.R
import app.foreway.data.CareerSummary
import app.foreway.domain.model.CareerFamily

/**
 * The icon for a career: its own where this build has one, else its family's — so a career
 * published after this release still gets a fitting picture without waiting for an update.
 * Art only. Nothing here is a fact about the career, so it is fine for it to live in code.
 */
@DrawableRes
fun careerIcon(career: CareerSummary): Int = byCareer[career.id] ?: familyIcon(career.family)

/** One icon per field. Null is a family this build does not know: a neutral compass. */
@DrawableRes
fun familyIcon(family: CareerFamily?): Int = when (family) {
    CareerFamily.DEFENCE -> R.drawable.ic_family_defence
    CareerFamily.MEDICAL -> R.drawable.ic_family_medical
    CareerFamily.ENGINEERING -> R.drawable.ic_family_engineering
    CareerFamily.COMMERCE -> R.drawable.ic_family_commerce
    CareerFamily.CIVIC -> R.drawable.ic_family_civic
    CareerFamily.DESIGN -> R.drawable.ic_family_design
    CareerFamily.MARITIME -> R.drawable.ic_family_maritime
    null -> R.drawable.ic_tab_explore
}

private val byCareer: Map<String, Int> = mapOf(
    "nda-officer-entry" to R.drawable.ic_career_nda,
    "doctor-mbbs" to R.drawable.ic_career_doctor,
    "specialist-md-ms" to R.drawable.ic_career_specialist,
    "physician-general-medicine" to R.drawable.ic_career_physician,
    "cardiologist" to R.drawable.ic_career_cardiologist,
    "surgeon-general-surgery" to R.drawable.ic_career_surgeon,
    "neurosurgeon" to R.drawable.ic_career_neurosurgeon,
    "software-engineer" to R.drawable.ic_career_software,
    "ml-engineer" to R.drawable.ic_career_ml,
    "security-engineer" to R.drawable.ic_career_security,
)
