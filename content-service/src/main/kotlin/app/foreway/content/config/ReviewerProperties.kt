package app.foreway.content.config

import app.foreway.domain.model.Provenance
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * The reviewer allowed to sign. [name] is what goes into verified_by — a real person's
 * name, because a signature is a statement that a person checked the value.
 *
 * Set from FOREWAY_REVIEWER_NAME and FOREWAY_REVIEWER_PASSWORD. The service refuses to
 * start with a short password: this page is on the public internet and can put eligibility
 * facts in front of students.
 */
@ConfigurationProperties("foreway.reviewer")
data class ReviewerProperties(
    val name: String,
    val password: String,
) {
    init {
        require(name.isNotBlank()) { "foreway.reviewer.name is empty" }
        require(name !in setOf(Provenance.UNREVIEWED, Provenance.WITHHELD)) {
            "foreway.reviewer.name must be a person's name, not a reserved signature"
        }
        require(password.length >= MIN_PASSWORD) {
            "foreway.reviewer.password must be at least $MIN_PASSWORD characters"
        }
    }

    // Keeps the password out of logs if the properties object is ever printed.
    override fun toString(): String = "ReviewerProperties(name=$name, password=****)"

    companion object {
        const val MIN_PASSWORD = 16
    }
}
