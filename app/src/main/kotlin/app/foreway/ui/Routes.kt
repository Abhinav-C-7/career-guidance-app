package app.foreway.ui

import kotlinx.serialization.Serializable

/*
 * Type-safe routes. Four tabs, each its own nested graph with its own back stack, and a few
 * pushed screens inside them. A career id is the only argument any career screen takes:
 * everything else is read from the local store, so a deep link or a notification can open
 * any screen cold.
 */

// --- Today ---

@Serializable
data object TodayGraph

@Serializable
data object TodayRoute

/** Every gate for the goal, closed doors first. Pushed from Today. */
@Serializable
data object GatesRoute

// --- Pathway ---

@Serializable
data object PathwayGraph

/** Always the saved goal's pathway. */
@Serializable
data object PathwayRoute

// --- Explore ---

@Serializable
data object ExploreGraph

/** All fields, top-level careers grouped by family. */
@Serializable
data object ExploreRoute

/** One career: what it builds on, its specialisations, and "make this my goal". */
@Serializable
data class CareerRoute(val careerId: String)

// --- You ---

@Serializable
data object YouGraph

@Serializable
data object YouRoute

/** Change one answer the student gave. [question] is a [app.foreway.ui.you.ProfileQuestion] name. */
@Serializable
data class EditAnswerRoute(val question: String)

@Serializable
data object AboutRoute
