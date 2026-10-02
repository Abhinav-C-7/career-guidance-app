package app.foreway.ui.format

import app.foreway.data.CareerSummary

/**
 * "Doctor (MBBS) › Specialist doctor (MD or MS) › General surgeon (MS)", root first.
 *
 * A chevron, not an arrow: some phones' system fonts draw the arrow glyph as junk (an Infinix
 * shows "’n"), and until our own fonts ship, the system font is what renders.
 */
fun chainLabel(careers: List<CareerSummary>): String = careers.joinToString(" › ") { it.title }
