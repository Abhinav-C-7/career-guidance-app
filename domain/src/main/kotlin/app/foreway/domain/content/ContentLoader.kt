package app.foreway.domain.content

import app.foreway.domain.model.CareerContent
import kotlinx.serialization.json.Json

/**
 * Parses authored career content.
 *
 * Unknown keys are a hard failure on purpose. A content file with a misspelled field is a
 * file where somebody meant to say something and it silently did not take effect — which,
 * for eligibility data, is indistinguishable from lying to a student.
 */
public object ContentLoader {

    private val json: Json = Json {
        ignoreUnknownKeys = false
        prettyPrint = true
    }

    public fun parse(text: String): CareerContent = json.decodeFromString(text)
}
