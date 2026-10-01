package app.foreway.ui.onboarding

import androidx.annotation.StringRes
import app.foreway.R

/**
 * Display names for [app.foreway.domain.model.IndianStates.codes]. A unit test keeps the
 * two in step, so a code without a name — or a name stored under a code no content
 * matches — fails the build rather than a student.
 */
object IndianStateNames {
    val all: List<Pair<String, Int>> = listOf(
        "IN-AN" to R.string.state_an,
        "IN-AP" to R.string.state_ap,
        "IN-AR" to R.string.state_ar,
        "IN-AS" to R.string.state_as,
        "IN-BR" to R.string.state_br,
        "IN-CH" to R.string.state_ch,
        "IN-CG" to R.string.state_cg,
        "IN-DH" to R.string.state_dh,
        "IN-DL" to R.string.state_dl,
        "IN-GA" to R.string.state_ga,
        "IN-GJ" to R.string.state_gj,
        "IN-HR" to R.string.state_hr,
        "IN-HP" to R.string.state_hp,
        "IN-JK" to R.string.state_jk,
        "IN-JH" to R.string.state_jh,
        "IN-KA" to R.string.state_ka,
        "IN-KL" to R.string.state_kl,
        "IN-LA" to R.string.state_la,
        "IN-LD" to R.string.state_ld,
        "IN-MP" to R.string.state_mp,
        "IN-MH" to R.string.state_mh,
        "IN-MN" to R.string.state_mn,
        "IN-ML" to R.string.state_ml,
        "IN-MZ" to R.string.state_mz,
        "IN-NL" to R.string.state_nl,
        "IN-OD" to R.string.state_od,
        "IN-PY" to R.string.state_py,
        "IN-PB" to R.string.state_pb,
        "IN-RJ" to R.string.state_rj,
        "IN-SK" to R.string.state_sk,
        "IN-TN" to R.string.state_tn,
        "IN-TS" to R.string.state_ts,
        "IN-TR" to R.string.state_tr,
        "IN-UP" to R.string.state_up,
        "IN-UK" to R.string.state_uk,
        "IN-WB" to R.string.state_wb,
    )

    @StringRes
    fun nameOf(code: String): Int? = all.firstOrNull { it.first == code }?.second
}
