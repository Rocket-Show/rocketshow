package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    private final static Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    SecurityFilterChain appSecurity(
            HttpSecurity http,
            ApiKeyAuthFilter apiKeyAuthFilter,
            AuthorizationManager<RequestAuthorizationContext> systemTestAuthorizationManager
    ) throws Exception {
        http
                .cors(Customizer.withDefaults())

                .csrf(csrf -> csrf.disable())

                // IMPORTANT: not STATELESS, because admins use session auth
                .sessionManagement(sm -> sm
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )

                .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)

                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/", "/index.html", "/assets/**", "/*.js", "/*.css", "/*.ico").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()

                        .requestMatchers(HttpMethod.POST, "/api/auth/setup").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/system/device-information").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/system/device-information").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/system/health").permitAll()

                        // Permit the state, because otherwise a new login is required after booting into a new slot (with
                        // maybe different persisted Spring Boot serialized session).
                        .requestMatchers(HttpMethod.GET, "/api/system/state").permitAll()

                        .requestMatchers(HttpMethod.POST, "/api/system/test").access(systemTestAuthorizationManager)

                        // Shared endpoints
                        // .requestMatchers(HttpMethod.POST, "/api/transport/play").hasAnyRole("ADMIN", "DEVICE")

                        // Device-only
                        // .requestMatchers("/api/device/**").hasRole("DEVICE")

                        // Admin-only
                        .requestMatchers("/api/auth/**").hasRole("ADMIN")
                        .requestMatchers("/api/session/**").hasRole("ADMIN")
                        .requestMatchers("/api/lead-sheet/**").hasRole("ADMIN")
                        .requestMatchers("/api/lead-sheet/**").hasRole("ADMIN")
                        .requestMatchers("/api/system/settings").hasRole("ADMIN")
                        .requestMatchers("/api/system/factory-reset").hasRole("ADMIN")

                        // Everything else under /api requires authenticated access
                        .requestMatchers("/api/**").authenticated()

                        .anyRequest().permitAll()
                )

                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable());

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(List.of("http://localhost:4200", "http://app.rocketshow.local:4200")); // Angular dev server
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }

    @Bean
    AuthorizationManager<RequestAuthorizationContext> systemTestAuthorizationManager(SettingsService settingsService) {
        return (authentication, context) -> {
            Settings settings = settingsService.getSettings();
            boolean setupFinished = settings.getAdminPasswordHash() != null && !settings.getAdminPasswordHash().isEmpty();

            if (!setupFinished) {
                return new AuthorizationDecision(true);
            }

            Authentication auth = authentication.get();
            boolean isAdmin = auth != null
                    && auth.isAuthenticated()
                    && auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

            return new AuthorizationDecision(isAdmin);
        };
    }
}