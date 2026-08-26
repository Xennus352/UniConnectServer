package com.unicconnect.controller;

import com.unicconnect.dto.request.CreateUserRequest;
import com.unicconnect.dto.request.DeleteUsersRequest;
import com.unicconnect.dto.request.UpdateMeRequest;
import com.unicconnect.dto.request.UpdateUserRequest;
import com.unicconnect.dto.request.UpdateUserStatusRequest;
import com.unicconnect.dto.request.UpdateUserRoleRequest;
import com.unicconnect.dto.response.UserResponse;
import com.unicconnect.ops.UserOperations;
import com.unicconnect.service.UserService;
import com.unicconnect.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final UserOperations userOperations;
    private final SecurityUtil securityUtil;

    public UserController(UserService userService,
                          UserOperations userOperations,
                          SecurityUtil securityUtil) {
        this.userService = userService;
        this.userOperations = userOperations;
        this.securityUtil = securityUtil;
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAll() {
        return ResponseEntity.ok(userOperations.getAll());
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(201).body(userOperations.create(request));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe() {
        return ResponseEntity.ok(userService.getMe(securityUtil.currentUserId()));
    }

    @GetMapping("/role/{roleName}")
    public ResponseEntity<List<UserResponse>> getByRole(@PathVariable String roleName) {
        return ResponseEntity.ok(userOperations.byRole(roleName));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getById(@PathVariable UUID userId) {
        return ResponseEntity.ok(userOperations.getById(userId));
    }

    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateMe(@Valid @RequestBody UpdateMeRequest request) {
        return ResponseEntity.ok(userService.updateMe(securityUtil.currentUserId(), request));
    }

    @PatchMapping("/{userId}/status")
    public ResponseEntity<UserResponse> updateStatus(@PathVariable UUID userId,
                                                     @Valid @RequestBody UpdateUserStatusRequest request) {
        return ResponseEntity.ok(userOperations.updateStatus(userId, request));
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<UserResponse> update(@PathVariable UUID userId,
                                               @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userOperations.update(userId, request));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> delete(@PathVariable UUID userId) {
        userOperations.delete(securityUtil.currentUserId(), userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bulk-delete")
    public ResponseEntity<Void> bulkDelete(@Valid @RequestBody DeleteUsersRequest request) {
        userOperations.deleteBulk(securityUtil.currentUserId(), request.userIds());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{userId}/role")
    public ResponseEntity<UserResponse> updateRole(@PathVariable UUID userId,
                                                   @Valid @RequestBody UpdateUserRoleRequest request) {
        return ResponseEntity.ok(userOperations.updateRole(userId, request));
    }
}
