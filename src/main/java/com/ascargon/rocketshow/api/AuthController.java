package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.settings.SettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.xml.bind.JAXBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final static Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final SettingsService settingsService;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextRepository securityContextRepository;
    private final LoginThrottleService loginThrottleService;

    public AuthController(
            SettingsService settingsService,
            PasswordEncoder passwordEncoder,
            SecurityContextRepository securityContextRepository,
            LoginThrottleService loginThrottleService
    ) {
        this.settingsService = settingsService;
        this.passwordEncoder = passwordEncoder;
        this.securityContextRepository = securityContextRepository;
        this.loginThrottleService = loginThrottleService;
    }

    public record LoginRequest(String password) {
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        loginThrottleService.delayIfNeeded();

        String hash = settingsService.getSettings().getAdminPasswordHash();

        if (!passwordEncoder.matches(request.password(), hash)) {
            logger.info("Login attempt failed");
            loginThrottleService.onFailure();
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        loginThrottleService.onSuccess();
        logger.info("User logged successfully in");

        authenticateAdmin(httpRequest, httpResponse);

        return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "passwordConfigured", true,
                "username", "admin",
                "roles", List.of("ROLE_ADMIN")
        ));
    }

    private boolean hasAdminPassword() {
        String hash = settingsService.getSettings().getAdminPasswordHash();
        return hash != null && !hash.isBlank();
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        boolean passwordConfigured = hasAdminPassword();

        if (authentication == null || !authentication.isAuthenticated()) {
            return Map.of(
                    "authenticated", false,
                    "passwordConfigured", passwordConfigured
            );
        }

        return Map.of(
                "authenticated", true,
                "passwordConfigured", passwordConfigured,
                "username", authentication.getName(),
                "roles", authentication.getAuthorities().stream()
                        .map(a -> a.getAuthority())
                        .toList()
        );
    }

    private void authenticateAdmin(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "admin",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    @PostMapping("/setup")
    public ResponseEntity<?> setup(@RequestBody SetupRequest request,
                                   HttpServletRequest httpRequest,
                                   HttpServletResponse httpResponse) {

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must not be empty");
        }

        if (hasAdminPassword()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin password already configured");
        }

        String hash = passwordEncoder.encode(request.getPassword());
        settingsService.getSettings().setAdminPasswordHash(hash);

        settingsService.getSettings().setLanguage(request.getLanguage());
        settingsService.getSettings().setDeviceName(request.getDeviceName());

        try {
            settingsService.save();
        } catch (JAXBException e) {
            logger.error("Could not save settings after setup", e);
        }

        authenticateAdmin(httpRequest, httpResponse);

        logger.info("Initial setup finished");

        return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "passwordConfigured", true,
                "username", "admin",
                "roles", List.of("ROLE_ADMIN")
        ));
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();

        logger.info("User logged out");
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> ChangePassword(@RequestBody ChangePasswordRequest request,
                                            HttpServletRequest httpRequest,
                                            HttpServletResponse httpResponse) {
        String oldPasswordHash = settingsService.getSettings().getAdminPasswordHash();

        if (!passwordEncoder.matches(request.getOldPassword(), oldPasswordHash)) {
            logger.info("Change password attempt failed");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        String newPasswordHash = passwordEncoder.encode(request.getNewPassword());
        settingsService.getSettings().setAdminPasswordHash(newPasswordHash);

        try {
            settingsService.save();
        } catch (JAXBException e) {
            logger.error("Could not save settings after changing the admin password", e);
        }

        authenticateAdmin(httpRequest, httpResponse);

        logger.info("User changed password successfully");

        return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "passwordConfigured", true,
                "username", "admin",
                "roles", List.of("ROLE_ADMIN")
        ));
    }
}