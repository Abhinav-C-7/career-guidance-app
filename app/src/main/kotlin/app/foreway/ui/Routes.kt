package app.foreway.ui

import kotlinx.serialization.Serializable

/*
 * Type-safe routes. A career id is the only argument any screen takes: everything else is
 * read from the local store, so a deep link or a notification can open any screen cold.
 */

@Serializable
data object HomeRoute

@Serializable
data class PathwayRoute(val careerId: String)

/** The career browser: all fields when [careerId] is null, else one career and its specialisations. */
@Serializable
data class CareerRoute(val careerId: String? = null)
