package app.foreway.ui

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * The only place the app reads the date. The rules engine never does — it takes `asOf`
 * as an argument (GateEvaluator) — so every verdict is reproducible in a test by passing
 * a fixed date instead of calling this.
 */
fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
