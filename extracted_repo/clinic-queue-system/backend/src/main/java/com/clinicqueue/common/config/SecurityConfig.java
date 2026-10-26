package com.clinicqueue.common.config;

import com.clinicqueue.clinic.ClinicRepository;
import com.clinicqueue.common.security.CookieApiCsrfTokenRepository;
import com.clinicqueue.common.security.PublicHeadersFilter;
import com.clinicqueue.common.security.RateLimitFilter;
import com.clinicqueue.common.security.RequestIdFilter;
import com.clinicqueue.identity.ClinicAuthenticationProvider;
import com.clinicqueue.identity.JsonLoginFilter;
import com.clinicqueue.identity.SecurityErrorHandlers;
import com.clinicqueue.identity.StaffUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;

/**
 * Deny-by-default security. Clinic identity always comes from the authenticated
 * principal (never from the request), role checks are enforced server-side,
 * and CSRF protection applies to cookie-authenticated state changes.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${app.ratelimit.login-per-minute:30}")
    private int loginRatePerMinute;

    @Value("${app.ratelimit.public-per-minute:120}")
    private int publicRatePerMinute;

    @Value("${app.security.bcrypt-strength:10}")
    private int bcryptStrength;

    @Value("${APP_CORS_ORIGINS:http://localhost:5180,http://localhost:5173,http://localhost:4173}")
    private String corsOrigins;

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(bcryptStrength);
    }

    @Bean
    public AuthenticationManager authenticationManager(StaffUserRepository staffUserRepository,
                                                        ClinicRepository clinicRepository,
                                                        PasswordEncoder encoder) {
        ClinicAuthenticationProvider provider = new ClinicAuthenticationProvider(staffUserRepository, clinicRepository, encoder);
        return new ProviderManager(provider);
    }

    @Bean
    public RateLimitFilter rateLimitFilter() {
        return new RateLimitFilter(loginRatePerMinute, publicRatePerMinute);
    }

    /** XSRF-TOKEN cookie readable by the SPA, which echoes it in the X-XSRF-TOKEN header. */
    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        return new CookieApiCsrfTokenRepository();
    }

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
                                                       AuthenticationManager authenticationManager,
                                                       com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                                                       ClinicRepository clinicRepository,
                                                       RateLimitFilter rateLimitFilter,
                                                       CsrfTokenRepository csrfTokenRepository) throws Exception {
        JsonLoginFilter jsonLoginFilter = new JsonLoginFilter(objectMapper, clinicRepository);
        jsonLoginFilter.setAuthenticationManager(authenticationManager);
        http
                .securityMatcher("/api/v1/**")
                // The default CSRF matcher protects unsafe methods only; GETs stay
                // protected-free but still bootstrap the XSRF-TOKEN cookie. The plain
                // request handler makes the client send the raw token value in
                // X-XSRF-TOKEN (the classic SPA pattern).
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(
                        org.springframework.security.config.http.SessionCreationPolicy.IF_REQUIRED))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny()))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(SecurityErrorHandlers.entryPoint())
                        .accessDeniedHandler(SecurityErrorHandlers.accessDeniedHandler()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
                .authorizeHttpRequests(auth -> auth
                        // public + bootstrap
                        .requestMatchers("/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").permitAll()
                        .requestMatchers("/api/v1/public/**").permitAll()
                        // everything else under /api/v1 requires authentication; role checks
                        // are enforced per endpoint with @PreAuthorize and clinic scoping.
                        .anyRequest().authenticated())
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAt(jsonLoginFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(corsOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList());
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("X-Request-Id", "X-XSRF-TOKEN"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
