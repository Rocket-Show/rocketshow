package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.settings.SettingsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {
    private final SettingsService settingsService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public ApiKeyAuthFilter(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws IOException, ServletException {

        String apiKey = request.getHeader("X-API-Key");

        // No API key -> let session-based auth or anonymous continue
        if (apiKey == null || apiKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        // If already authenticated via session, keep existing auth
        Authentication existing = SecurityContextHolder.getContext().getAuthentication();
        if (existing != null && existing.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        var hashes = settingsService.getSettings().getApiKeyList(); // List<String>
        boolean match = false;

        for (var entry : hashes) { // e.g. List<DeviceKey {id, hash}>
            if (encoder.matches(apiKey, entry.getKeyHash())) {
                match = true;
                break;
            }
        }

        if (!match) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key");
            return;
        }

        var auth = new UsernamePasswordAuthenticationToken(
                "device", null, List.of(new SimpleGrantedAuthority("ROLE_DEVICE"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }
}