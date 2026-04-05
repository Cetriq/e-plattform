package se.eplatform.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${eplatform.cors.allowed-origins:http://localhost:3000}")
    private List<String> allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)  // Disable for API
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info",
                                "/api/v1/public/**",
                                "/graphiql",
                                "/graphql"
                        ).permitAll()
                        // Public read-only flow endpoints (e-tjänster)
                        .requestMatchers(
                                org.springframework.http.HttpMethod.GET,
                                "/api/v1/flows",
                                "/api/v1/flows/{id}",
                                "/api/v1/flows/by-type/{typeId}",
                                "/api/v1/flows/by-category/{categoryId}",
                                "/api/v1/flows/search"
                        ).permitAll()
                        // Mock auth endpoints (development only - remove in production)
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // Case endpoints (temporarily open for development)
                        .requestMatchers("/api/v1/cases/**").permitAll()
                        // File endpoints (temporarily open for development)
                        .requestMatchers("/api/v1/files/**").permitAll()
                        // Admin endpoints (temporarily open for development)
                        .requestMatchers("/api/v1/admin/**").permitAll()
                        // Secured endpoints (commented out for development)
                        // .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().permitAll()
                )
                // For now, use basic auth in development
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
