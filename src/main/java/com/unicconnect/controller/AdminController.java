package com.unicconnect.controller;

import com.unicconnect.dto.ApiResponse;
import com.unicconnect.dto.CreatedAccountResponse;
import com.unicconnect.dto.CreateAccountRequest;
import com.unicconnect.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AuthService authService;

    public AdminController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/users/create")
    public ResponseEntity<CreatedAccountResponse> createUser(@Valid @RequestBody CreateAccountRequest request) {
        CreatedAccountResponse response = authService.createAccount(request);
        return ResponseEntity.ok(response);
    }
}
