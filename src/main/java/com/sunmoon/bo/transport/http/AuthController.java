package com.sunmoon.bo.transport.http;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Login and logout are Spring Security's own filters (see
 * {@code SecurityConfig}); what is left for a controller is the two things
 * a client needs around them — the CSRF token to send, and who it is
 * currently logged in as.
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Auth", description = "운영자 인증")
public class AuthController {

    @GetMapping("/csrf")
    @Operation(summary = "CSRF token",
            description = "Also sets the XSRF-TOKEN cookie. Call this before POST/PUT/DELETE, "
                    + "including login, and echo the value in the X-XSRF-TOKEN header.")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @GetMapping("/me")
    @Operation(summary = "Current operator",
            description = "Username plus granted authorities — what a Frontend would draw its menu from.")
    public Map<String, Object> me(Authentication authentication) {
        List<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", authentication.getName());
        body.put("superAdmin", authorities.contains("ROLE_SUPER_ADMIN"));
        body.put("authorities", authorities);
        return body;
    }
}
