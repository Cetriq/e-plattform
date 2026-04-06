package se.eplatform.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import se.eplatform.common.security.JwtAuthenticationFilter;
import se.eplatform.common.security.RateLimitFilter;

import java.util.Arrays;
import java.util.List;

/**
 * Security configuration for the e-Plattform API.
 *
 * Security model:
 * - Public endpoints: Health checks, public flow listings, auth endpoints
 * - Authenticated endpoints: Case management, file uploads
 * - Admin endpoints: Flow/category management (requires ADMIN or FLOW_EDITOR role)
 * - Manager endpoints: Case handling (requires MANAGER role)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${eplatform.cors.allowed-origins:http://localhost:3000}")
    private List<String> allowedOrigins;

    @Value("${eplatform.security.enforce-roles:false}")
    private boolean enforceRoles;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, RateLimitFilter rateLimitFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)  // Disable CSRF for stateless API
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        if (enforceRoles) {
            // Production mode: enforce role-based access
            configureProductionSecurity(http);
        } else {
            // Development mode: relaxed security for easier testing
            configureDevelopmentSecurity(http);
        }

        return http.build();
    }

    /**
     * Development security configuration - relaxed for testing.
     * All endpoints are accessible but JWT tokens are still validated when present.
     */
    private void configureDevelopmentSecurity(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers(
                        "/actuator/health",
                        "/actuator/info",
                        "/api/v1/public/**",
                        // Swagger/OpenAPI endpoints
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/api-docs",
                        "/api-docs/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**"
                ).permitAll()
                // Public read-only flow endpoints (e-tjänster)
                .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/flows",
                        "/api/v1/flows/{id}",
                        "/api/v1/flows/by-type/{typeId}",
                        "/api/v1/flows/by-category/{categoryId}",
                        "/api/v1/flows/search"
                ).permitAll()
                // In dev mode, allow all other requests (but still validate JWT if present)
                .anyRequest().permitAll()
        );
    }

    /**
     * Production security configuration - strict role-based access.
     */
    private void configureProductionSecurity(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers(
                        "/actuator/health",
                        "/actuator/info",
                        "/api/v1/public/**",
                        // Swagger/OpenAPI endpoints
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/api-docs",
                        "/api-docs/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**"
                ).permitAll()
                // Public read-only flow endpoints (e-tjänster)
                .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/flows",
                        "/api/v1/flows/{id}",
                        "/api/v1/flows/by-type/{typeId}",
                        "/api/v1/flows/by-category/{categoryId}",
                        "/api/v1/flows/search"
                ).permitAll()
                // Admin endpoints - require ADMIN or FLOW_EDITOR role
                .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "FLOW_EDITOR")
                // Manager endpoints - require MANAGER role
                .requestMatchers("/api/v1/manager/**").hasRole("MANAGER")
                // Case endpoints - authenticated users
                .requestMatchers("/api/v1/cases/**").authenticated()
                // File endpoints - authenticated users
                .requestMatchers("/api/v1/files/**").authenticated()
                // All other API endpoints require authentication
                .requestMatchers("/api/v1/**").authenticated()
                // Permit other requests (static resources, etc.)
                .anyRequest().permitAll()
        );
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin",
                "X-Requested-With"
        ));
        configuration.setExposedHeaders(Arrays.asList(
                "Authorization",
                "Content-Disposition"
        ));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
