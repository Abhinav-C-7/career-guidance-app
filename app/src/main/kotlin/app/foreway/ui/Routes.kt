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
