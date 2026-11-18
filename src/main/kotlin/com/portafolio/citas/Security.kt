package com.portafolio.citas

import com.nimbusds.jose.jwk.source.ImmutableSecret
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.Customizer.withDefaults
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.*
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import javax.crypto.spec.SecretKeySpec

@Configuration
class SecurityConfig(@Value("\${app.jwt-secret}") private val secret: String) {
    private fun key(): SecretKeySpec {
        val bytes = secret.toByteArray(Charsets.UTF_8)
        require(bytes.size >= 32) { "JWT_SECRET debe tener al menos 32 bytes" }
        return SecretKeySpec(bytes, "HmacSHA256")
    }

    @Bean fun encoder(): JwtEncoder = NimbusJwtEncoder(ImmutableSecret(key()))
    @Bean fun decoder(): JwtDecoder = NimbusJwtDecoder.withSecretKey(key()).macAlgorithm(MacAlgorithm.HS256).build()
    @Bean fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun security(http: HttpSecurity): SecurityFilterChain = http
        .csrf { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .authorizeHttpRequests { it.requestMatchers("/api/auth/login", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll().anyRequest().authenticated() }
        .oauth2ResourceServer { it.jwt(withDefaults()) }
        .build()

    @Bean
    fun initialAdmin(users: UserRepository, passwords: PasswordEncoder,
                     @Value("\${app.admin-email}") email: String,
                     @Value("\${app.admin-password}") password: String) = ApplicationRunner {
        require(email.isNotBlank() && password.length >= 12) { "Configura ADMIN_EMAIL y una ADMIN_PASSWORD de al menos 12 caracteres" }
        if (users.count() == 0L) users.save(AppUser(email = email.trim().lowercase(), passwordHash = passwords.encode(password)))
    }
}

data class LoginRequest(@field:NotBlank @field:Email val email: String, @field:NotBlank val password: String)
data class TokenResponse(val accessToken: String, val tokenType: String = "Bearer", val expiresIn: Long = 3600)

@RestController
@RequestMapping("/api/auth")
class AuthController(private val users: UserRepository, private val passwords: PasswordEncoder, private val jwt: JwtEncoder) {
    @Operation(summary = "Iniciar sesión", description = "Devuelve un token Bearer válido durante una hora.")
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): TokenResponse {
        val user = users.findByEmail(request.email.trim().lowercase())
        if (user == null || !passwords.matches(request.password, user.passwordHash)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas")
        }
        val now = Instant.now()
        val claims = JwtClaimsSet.builder().subject(user.id.toString()).issuedAt(now).expiresAt(now.plusSeconds(3600)).build()
        val headers = JwsHeader.with(MacAlgorithm.HS256).build()
        return TokenResponse(jwt.encode(JwtEncoderParameters.from(headers, claims)).tokenValue)
    }
}
