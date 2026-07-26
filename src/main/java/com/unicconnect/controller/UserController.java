package com.unicconnect.controller;

import com.unicconnect.dto.UserResponse;
import com.unicconnect.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @GetMapping("/role/{role}")
    public ResponseEntity<List<UserResponse>> getUsersByRole(@PathVariable String role) {
        return ResponseEntity.ok(userService.getUsersByRole(role));
    }

    @GetMapping("/role/{role}/details")
    public ResponseEntity<List<UserResponse>> getUsersByRoleDetailed(@PathVariable String role) {
        return ResponseEntity.ok(userService.getUsersByRoleDetailed(role));
    }

    @GetMapping("/department/{departmentId}")
    public ResponseEntity<List<UserResponse>> getUsersByDepartment(@PathVariable Long departmentId) {
        return ResponseEntity.ok(userService.getUsersByDepartment(departmentId));
    }

    @GetMapping("/department/{departmentId}/details")
    public ResponseEntity<List<UserResponse>> getUsersByDepartmentDetailed(@PathVariable Long departmentId) {
        return ResponseEntity.ok(userService.getUsersByDepartmentDetailed(departmentId));
    }
}
