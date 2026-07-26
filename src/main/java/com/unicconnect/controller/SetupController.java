package com.unicconnect.controller;

import com.unicconnect.dto.ApiResponse;
import com.unicconnect.dto.SetupAdminRequest;
import com.unicconnect.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/setup")
public class SetupController {

    private final AuthService authService;

    public SetupController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/admin")
    public ResponseEntity<ApiResponse> setupAdmin(@Valid @RequestBody SetupAdminRequest request) {
        ApiResponse response = authService.setupFirstAdmin(request);
        return ResponseEntity.ok(response);
    }
}
