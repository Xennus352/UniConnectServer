package com.unicconnect.controller;

import com.unicconnect.dto.*;
import com.unicconnect.service.AuthService;
import com.unicconnect.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest,
                                              HttpServletResponse httpResponse) {
        String deviceName = httpRequest.getHeader("User-Agent");
        String ipAddress = httpRequest.getRemoteAddr();

        AuthResponse response = authService.login(request, deviceName, ipAddress);

        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", response.getAccessToken())
                .httpOnly(true)
                .secure(true)
                .path("/api/auth")
                .maxAge(604800)
                .sameSite("Lax")
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody java.util.Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        AuthResponse response = authService.refresh(refreshToken);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                                      Authentication authentication) {
        Long userId = Long.parseLong((String) authentication.getPrincipal());
        ApiResponse response = authService.changePassword(userId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse> logout(Authentication authentication) {
        Long userId = Long.parseLong((String) authentication.getPrincipal());
        ApiResponse response = authService.logout(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        Long userId = Long.parseLong((String) authentication.getPrincipal());
        UserResponse response = userService.getUserById(userId);
        return ResponseEntity.ok(response);
    }
}
