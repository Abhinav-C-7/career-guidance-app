package app.foreway.content.config

import java.time.Clock
import java.time.ZoneId
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    /** One reviewer for now. The password is hashed at startup and the plain text dropped. */
    @Bean
    fun users(reviewer: ReviewerProperties, encoder: PasswordEncoder): UserDetailsService =
        InMemoryUserDetailsManager(
            User.withUsername(reviewer.name)
                .password(encoder.encode(reviewer.password))
                .roles("REVIEWER")
                .build(),
        )

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests {
                it.requestMatchers("/login", "/css/**").permitAll()
                    .anyRequest().hasRole("REVIEWER")
            }
            .formLogin { it.loginPage("/login").permitAll() }
            .logout { it.logoutSuccessUrl("/login?out") }
            // CSRF stays on (the default): every sign-off is a POST from a logged-in session,
            // exactly what a forged request would try to ride on.
            .headers { h ->
                h.contentSecurityPolicy {
                    it.policyDirectives("default-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'")
                }
            }
        return http.build()
    }

    /** Verification dates are Indian dates, whatever zone the server runs in. */
    @Bean
    fun clock(): Clock = Clock.system(ZoneId.of("Asia/Kolkata"))
}
