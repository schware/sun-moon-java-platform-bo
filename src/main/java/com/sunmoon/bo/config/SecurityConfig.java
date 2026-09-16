package com.sunmoon.bo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The reason BO is a Spring service (the umbrella repo's ADR-0004). Everything this class
 * configures — server-side sessions, BCrypt, per-action authorization,
 * CSRF — was hand-written in the Netty version it replaces.
 *
 * <p>Sessions rather than JWT, for the same reason as before: an admin
 * panel wants a compromised or offboarded operator revoked immediately,
 * which is one record deleted; JWT's stateless advantage buys nothing for
 * a service that runs as one instance.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final ObjectMapper objectMapper;

    public SecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // The token goes in a readable cookie so a browser client can echo
        // it back in X-XSRF-TOKEN. SameSite=Lax (application.yml) already
        // stops the classic cross-site POST; this covers the rest.
        CookieCsrfTokenRepository csrfTokens = CookieCsrfTokenRepository.withHttpOnlyFalse();

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokens)
                        // Spring 6 defers token resolution by default, which
                        // means the cookie is only issued once something asks
                        // for it. BO has no page that would, so resolve eagerly.
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/csrf").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/prometheus", "/actuator/info").permitAll()
                        // The built React app. These are static files with no
                        // data in them; every screen behind them still calls an
                        // API that checks the session and the permission.
                        .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico", "/vite.svg").permitAll()
                        // The SPA's own routes. They forward to index.html
                        // (WebConfig); the screens behind them still fetch an
                        // API that checks the session and the permission.
                        .requestMatchers("/board", "/common-code", "/devices", "/menus", "/stores",
                                "/sales", "/terminals", "/operators", "/tables", "/sold-out").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginProcessingUrl("/api/auth/login")
                        .successHandler((request, response, authentication) ->
                                writeJson(response, HttpServletResponse.SC_OK, Map.of(
                                        "username", authentication.getName(),
                                        "authorities", authentication.getAuthorities().stream()
                                                .map(Object::toString).sorted().toList())))
                        .failureHandler((request, response, exception) ->
                                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        Map.of("error", "Invalid username or password"))))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) ->
                                writeJson(response, HttpServletResponse.SC_OK, Map.of("loggedOut", true))))
                // Without this, an unauthenticated call to a JSON API gets a
                // 302 to a login page that does not exist.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        Map.of("error", "Authentication required")))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                                        Map.of("error", "Not permitted for this screen and action"))));

        return http.build();
    }

    /** Cost 12, matching what the Netty version used — {@link UserDetails} passwords are always BCrypt here. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    private void writeJson(HttpServletResponse response, int status, Map<String, ?> body) {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        try {
            objectMapper.writeValue(response.getWriter(), new LinkedHashMap<>(body));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write JSON response", e);
        }
    }
}
