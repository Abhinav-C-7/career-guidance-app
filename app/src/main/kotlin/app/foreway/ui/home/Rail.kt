package app.foreway.ui.home

import app.foreway.domain.model.SchoolClass

/** A stop on the home timeline: a school class, or the open stretch after class 12. */
sealed interface RailStop {
    data class InClass(val schoolClass: SchoolClass) : RailStop
    data object AfterSchool : RailStop
}

data class Rail(val stops: List<RailStop>, val currentIndex: Int)

/**
 * The window of the student's school years shown on home: one stop behind, the current
 * one, and what is ahead — at most four, so the labels fit at 360dp.
 *
 * Position in time, never progress (DESIGN.md). A class 12 student is not "further along"
 * than a class 8 student; they are just at a different point on the same line.
 */
fun railFor(current: SchoolClass): Rail {
    val all: List<RailStop> = SchoolClass.entries
        .filter { it != SchoolClass.PASSED_12 }
        .map { RailStop.InClass(it) } + RailStop.AfterSchool

    val here = if (current == SchoolClass.PASSED_12) {
        all.lastIndex
    } else {
        all.indexOf(RailStop.InClass(current))
    }

    val start = (here - 1).coerceAtLeast(0)
    val window = all.subList(start, minOf(start + MAX_STOPS, all.size))
    return Rail(window, here - start)
}

private const val MAX_STOPS = 4
