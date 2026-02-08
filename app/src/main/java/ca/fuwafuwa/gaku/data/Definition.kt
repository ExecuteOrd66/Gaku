package ca.fuwafuwa.gaku.data

/**
 * Simple data class for definitions.
 * No longer an @Entity because it is embedded directly into the Term table via JSON.
 */
data class Definition(
    val content: String,
    val type: String
)