package com.fitness.app.config;

import com.fitness.app.common.dto.ErrorResponse;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.iam.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The whole access policy of the system in one file: which routes are public,
 * which need a role, and what a rejection looks like.
 *
 * There is no UserDetailsService and no DaoAuthenticationProvider: those exist
 * for Spring Security's own form login, which this API does not use. AuthService
 * loads the account and compares the password with PasswordEncoder.matches().
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig
{
    private static final String[] PUBLIC_ENDPOINTS =
    {
        "/api/v1/auth/login",
        "/api/v1/auth/challenges/*/verifications",
        "/api/v1/auth/password-recoveries",
        "/api/v1/auth/password-resets",
        "/swagger-ui.html",
        "/swagger-ui/**",
        "/v3/api-docs/**"
    };

    private final TokenService tokenService;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder()
    {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception
    {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(requests -> requests
                    .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                    // Order matters: the own-account routes are open to every role,
                    // the rest of /users belongs to the administrator.
                    .requestMatchers("/api/v1/users/me/**").authenticated()
                    .requestMatchers("/api/v1/users/**").hasRole("ADMIN")
                    // directory. Whether a member reaches *this* file is decided in
                    // MemberService: a matcher sees the path, not the row behind it.
                    .requestMatchers(HttpMethod.GET,   "/api/v1/members").hasAnyRole("ADMIN", "RECEPTIONIST", "TRAINER")
                    .requestMatchers(HttpMethod.POST,  "/api/v1/members").hasAnyRole("ADMIN", "RECEPTIONIST")
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/members/*/status").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET,   "/api/v1/members/*").hasAnyRole("ADMIN", "RECEPTIONIST", "TRAINER", "MEMBER")
                    .requestMatchers(HttpMethod.PUT,   "/api/v1/members/*").hasAnyRole("ADMIN", "RECEPTIONIST", "MEMBER")
                    .requestMatchers(HttpMethod.GET,   "/api/v1/members/*/memberships").hasAnyRole("ADMIN", "RECEPTIONIST", "MEMBER")
                    .requestMatchers("/api/v1/employees/**").hasRole("ADMIN")
                    // A member reads the trainers to choose one; only the administrator writes them.
                    .requestMatchers(HttpMethod.GET,   "/api/v1/trainers/**").hasAnyRole("ADMIN", "RECEPTIONIST", "TRAINER", "MEMBER")
                    .requestMatchers("/api/v1/trainers/**").hasRole("ADMIN")
                    // membership. Everyone reads the catalog to compare plans before
                    // contracting; only the administrator maintains it.
                    .requestMatchers(HttpMethod.GET,   "/api/v1/membership-plans").hasAnyRole("ADMIN", "RECEPTIONIST", "TRAINER", "MEMBER")
                    .requestMatchers(HttpMethod.GET,   "/api/v1/membership-plans/*").hasAnyRole("ADMIN", "RECEPTIONIST", "MEMBER")
                    .requestMatchers("/api/v1/membership-plans/**").hasRole("ADMIN")
                    // Reactivation is manual and staff-only: "el administrador o el
                    // recepcionista deben poder reactivarla". Everything else under
                    // /memberships/* the member may request for their own contract.
                    .requestMatchers(HttpMethod.GET,   "/api/v1/memberships").hasAnyRole("ADMIN", "RECEPTIONIST")
                    .requestMatchers(HttpMethod.POST,  "/api/v1/memberships").hasAnyRole("ADMIN", "RECEPTIONIST")
                    .requestMatchers(HttpMethod.POST,  "/api/v1/memberships/*/reactivations").hasAnyRole("ADMIN", "RECEPTIONIST")
                    .requestMatchers("/api/v1/memberships/**").hasAnyRole("ADMIN", "RECEPTIONIST", "MEMBER")
                    // access. Check-in, check-out and guest passes are front desk only.
                    .requestMatchers("/api/v1/visits/**").hasAnyRole("ADMIN", "RECEPTIONIST")
                    .requestMatchers("/api/v1/guest-passes/**").hasAnyRole("ADMIN", "RECEPTIONIST")
                    // classes. Catalog and billboard are readable by everyone with a
                    // role; only the administrator writes the recurring definition.
                    .requestMatchers(HttpMethod.GET,   "/api/v1/group-classes", "/api/v1/group-classes/*",
                                                       "/api/v1/group-classes/*/sessions")
                            .hasAnyRole("ADMIN", "RECEPTIONIST", "TRAINER", "MEMBER")
                    .requestMatchers("/api/v1/group-classes/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET,   "/api/v1/class-sessions", "/api/v1/class-sessions/*")
                            .hasAnyRole("ADMIN", "RECEPTIONIST", "TRAINER", "MEMBER")
                    .requestMatchers(HttpMethod.PUT,   "/api/v1/class-sessions/*").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/class-sessions/*/status").hasAnyRole("ADMIN", "TRAINER")
                    .requestMatchers(HttpMethod.POST,  "/api/v1/class-sessions/*/cancellations").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET,   "/api/v1/class-sessions/*/enrollments")
                            .hasAnyRole("ADMIN", "RECEPTIONIST", "TRAINER")
                    .requestMatchers(HttpMethod.POST,  "/api/v1/class-sessions/*/enrollments")
                            .hasAnyRole("ADMIN", "RECEPTIONIST", "MEMBER")
                    .requestMatchers(HttpMethod.PUT,   "/api/v1/class-sessions/*/attendances").hasAnyRole("ADMIN", "TRAINER")
                    .requestMatchers(HttpMethod.GET,   "/api/v1/class-sessions/*/waitlist-entries")
                            .hasAnyRole("ADMIN", "RECEPTIONIST", "TRAINER")
                    .requestMatchers(HttpMethod.POST,  "/api/v1/class-sessions/*/waitlist-entries")
                            .hasAnyRole("ADMIN", "RECEPTIONIST", "MEMBER")
                    .requestMatchers(HttpMethod.GET,   "/api/v1/class-sessions/*/ratings").hasAnyRole("ADMIN", "TRAINER")
                    .requestMatchers(HttpMethod.POST,  "/api/v1/class-sessions/*/ratings").hasRole("MEMBER")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/enrollments/*", "/api/v1/waitlist-entries/*")
                            .hasAnyRole("ADMIN", "RECEPTIONIST", "MEMBER")
                    .requestMatchers(HttpMethod.POST,  "/api/v1/waitlist-entries/*/confirmations").hasRole("MEMBER")
                    .requestMatchers(HttpMethod.GET,   "/api/v1/members/*/enrollments")
                            .hasAnyRole("ADMIN", "RECEPTIONIST", "TRAINER", "MEMBER")
                    // training. Only the trainer-member relationship so far; the rest
                    // of §3.7 arrives with its own module. Everyone reads it -the
                                        // training. §3.7. GET /members/{id}/trainer y la transferencia ya
                    // estaban; llegan el libro de asignaciones, el catálogo de ejercicios,
                    // las rutinas, el seguimiento y la cola de alertas.
                    .requestMatchers(HttpMethod.GET,   "/api/v1/members/*/trainer")
                            .hasAnyRole("ADMIN", "RECEPTIONIST", "TRAINER", "MEMBER")
                    .requestMatchers(HttpMethod.POST,  "/api/v1/members/*/trainer").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST,  "/api/v1/trainers/*/member-transfers").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET,   "/api/v1/trainer-assignments")
                            .hasAnyRole("ADMIN", "TRAINER")
                    .requestMatchers(HttpMethod.POST,  "/api/v1/trainer-assignments").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/trainer-assignments/*").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET,   "/api/v1/exercises").hasAnyRole("ADMIN", "TRAINER")
                    .requestMatchers(HttpMethod.POST,  "/api/v1/exercises").hasAnyRole("ADMIN", "TRAINER")
                    .requestMatchers(HttpMethod.PUT,   "/api/v1/exercises/*").hasAnyRole("ADMIN", "TRAINER")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/exercises/*").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET,   "/api/v1/routines").hasAnyRole("ADMIN", "TRAINER", "MEMBER")
                    .requestMatchers(HttpMethod.POST,  "/api/v1/routines").hasRole("TRAINER")
                    .requestMatchers(HttpMethod.GET,   "/api/v1/routines/*").hasAnyRole("ADMIN", "TRAINER", "MEMBER")
                    .requestMatchers(HttpMethod.PUT,   "/api/v1/routines/*").hasRole("TRAINER")
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/routines/*/status").hasRole("TRAINER")
                    .requestMatchers(HttpMethod.GET,   "/api/v1/members/*/measurements")
                            .hasAnyRole("ADMIN", "TRAINER", "MEMBER")
                    .requestMatchers(HttpMethod.POST,  "/api/v1/members/*/measurements").hasRole("TRAINER")
                    .requestMatchers(HttpMethod.PUT,   "/api/v1/measurements/*").hasRole("TRAINER")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/measurements/*").hasRole("TRAINER")
                    .requestMatchers(HttpMethod.GET,   "/api/v1/members/*/notes")
                            .hasAnyRole("ADMIN", "TRAINER", "MEMBER")
                    .requestMatchers(HttpMethod.POST,  "/api/v1/members/*/notes").hasRole("TRAINER")
                    .requestMatchers(HttpMethod.GET,   "/api/v1/trainer-alerts").hasAnyRole("ADMIN", "TRAINER")
                    .requestMatchers(HttpMethod.POST,  "/api/v1/trainer-alerts").hasRole("TRAINER")
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/trainer-alerts/*/status").hasRole("ADMIN")
                    // member checks who their trainer is- but the administrator "es el
                    // único usuario autorizado para asignar formalmente un entrenador
                    // personal a un socio" (Enunciado), so the receptionist is out.
                    .requestMatchers(HttpMethod.GET,   "/api/v1/members/*/trainer")
                            .hasAnyRole("ADMIN", "RECEPTIONIST", "TRAINER", "MEMBER")
                    .requestMatchers(HttpMethod.POST,  "/api/v1/members/*/trainer").hasRole("ADMIN")
                    // notification. The inbox is the caller's own, so the rule is the
                    // token and nothing else: the scope lives in NotificationService.
                    .requestMatchers("/api/v1/notifications/**").authenticated()
                    // GET /payments/{id}/receipt y GET /payments/{id} van ANTES del genérico GET /payments/*
                    .requestMatchers(HttpMethod.GET,   "/api/v1/payments/*/receipt").hasAnyRole("ADMIN", "RECEPTIONIST", "MEMBER")
                    .requestMatchers(HttpMethod.POST,  "/api/v1/payments/*/confirmations").hasAnyRole("ADMIN", "RECEPTIONIST")
                    .requestMatchers(HttpMethod.POST,  "/api/v1/payments/*/voids").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET,   "/api/v1/payments/*").hasAnyRole("ADMIN", "RECEPTIONIST", "MEMBER")
                    .requestMatchers(HttpMethod.GET,   "/api/v1/payments").hasAnyRole("ADMIN", "RECEPTIONIST", "MEMBER")
                    .requestMatchers(HttpMethod.POST,  "/api/v1/payments").hasAnyRole("ADMIN", "RECEPTIONIST")
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/promotions/*/status").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PUT,   "/api/v1/promotions/*").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET,   "/api/v1/promotions/*").hasAnyRole("ADMIN", "RECEPTIONIST")
                    .requestMatchers(HttpMethod.GET,   "/api/v1/promotions").hasAnyRole("ADMIN", "RECEPTIONIST")
                    .requestMatchers(HttpMethod.POST,  "/api/v1/promotions").hasRole("ADMIN")
                    // report. All endpoints read-only, admin-only (role A).
                    .requestMatchers("/api/v1/reports/**").hasRole("ADMIN")

                    .requestMatchers(HttpMethod.PUT,
                                "/api/v1/members/*/routines",
                                "/api/v1/members/*/nutrition-goal")
                                .hasAnyRole("ADMIN", "TRAINER", "MEMBER")
                    .requestMatchers(HttpMethod.GET,
                                "/api/v1/members/*/routines",
                                "/api/v1/members/*/nutrition-summary",
                                "/api/v1/members/*/nutrition-goal")
                                .hasAnyRole("ADMIN", "TRAINER", "MEMBER")

                // --- nutrition : catálogo de alimentos (solo ADMIN edita)
                     .requestMatchers(HttpMethod.GET, "/api/v1/foods", "/api/v1/foods/*")
                                .hasAnyRole("ADMIN", "TRAINER", "MEMBER")
                      .requestMatchers(HttpMethod.POST, "/api/v1/foods", "/api/v1/foods/*").hasRole("ADMIN")
                      .requestMatchers(HttpMethod.PUT, "/api/v1/foods", "/api/v1/foods/*").hasRole("ADMIN")
                      .requestMatchers(HttpMethod.DELETE, "/api/v1/foods", "/api/v1/foods/*").hasRole("ADMIN")

                // --- nutrition comidas diarias (S registra/edita/elimina; T y S consultan)
                      .requestMatchers(HttpMethod.GET, "/api/v1/meals", "/api/v1/meals/*").hasAnyRole("TRAINER", "MEMBER")
                      .requestMatchers(HttpMethod.POST, "/api/v1/meals").hasRole("MEMBER")
                      .requestMatchers(HttpMethod.PUT, "/api/v1/meals/*").hasRole("MEMBER")
                      .requestMatchers(HttpMethod.DELETE, "/api/v1/meals/*").hasRole("MEMBER")

                    .anyRequest().authenticated())
            .exceptionHandling(handling -> handling
                    .authenticationEntryPoint((request, response, exception) ->
                            writeError(request, response, ErrorCode.UNAUTHENTICATED))
                    .accessDeniedHandler((request, response, exception) ->
                            writeError(request, response, ErrorCode.FORBIDDEN_RESOURCE)))
            .addFilterBefore(new JwtAuthenticationFilter(tokenService),
                             UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String allowedOrigins)
    {
        var configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        var source = new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    /** A rejection from the filter chain answers with the same body as the rest of the system. */
    private void writeError(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode)
            throws IOException
    {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        objectMapper.writeValue(response.getOutputStream(),
                                ErrorResponse.of(errorCode, request.getRequestURI()));
    }
}
