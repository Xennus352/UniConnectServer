package com.unicconnect.ops;

import com.unicconnect.dto.request.CreateUserRequest;
import com.unicconnect.dto.request.UpdateUserRequest;
import com.unicconnect.dto.request.UpdateUserRoleRequest;
import com.unicconnect.dto.request.UpdateUserStatusRequest;
import com.unicconnect.dto.response.UserResponse;

import java.util.List;
import java.util.UUID;

/** User CRUD boundary — LOCAL (in-process) or RMI-backed per rmi.enabled. */
public interface UserOperations {
    List<UserResponse> getAll();
    UserResponse getById(UUID userId);
    List<UserResponse> byRole(String roleName);
    UserResponse create(CreateUserRequest request);
    UserResponse update(UUID userId, UpdateUserRequest request);
    UserResponse updateStatus(UUID userId, UpdateUserStatusRequest request);
    UserResponse updateRole(UUID userId, UpdateUserRoleRequest request);
    void delete(UUID actingUserId, UUID targetUserId);
    void deleteBulk(UUID actingUserId, List<UUID> userIds);
}
