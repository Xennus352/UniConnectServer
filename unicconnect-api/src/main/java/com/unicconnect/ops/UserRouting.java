package com.unicconnect.ops;

import com.unicconnect.dto.request.CreateUserRequest;
import com.unicconnect.dto.request.UpdateUserRequest;
import com.unicconnect.dto.request.UpdateUserRoleRequest;
import com.unicconnect.dto.request.UpdateUserStatusRequest;
import com.unicconnect.dto.response.UserResponse;
import com.unicconnect.rmi.client.RmiClientConfig.UserRmiClient;
import com.unicconnect.rmi.contract.CallerContext;
import com.unicconnect.rmi.dto.CreateUserDto;
import com.unicconnect.rmi.dto.UpdateStatusDto;
import com.unicconnect.rmi.dto.UpdateUserDto;
import com.unicconnect.security.CallerContextFactory;
import com.unicconnect.service.UserService;

import java.util.List;
import java.util.UUID;

/**
 * Hybrid routing for User CRUD.
 * LOCAL adapter : delegates to the original UserService (default).
 * RMI adapter   : signs a CallerContext from the authenticated principal and
 *                 crosses the JRMP boundary. The acting identity always comes
 *                 from authentication, never from controller arguments.
 */
public final class UserRouting {

    private UserRouting() {}

    public static final class Local implements UserOperations {
        private final UserService service;
        public Local(UserService service) { this.service = service; }
        @Override public List<UserResponse> getAll() { return service.getAllUsers(); }
        @Override public UserResponse getById(UUID id) { return service.getUserById(id); }
        @Override public List<UserResponse> byRole(String role) { return service.getUsersByRole(role); }
        @Override public UserResponse create(CreateUserRequest r) { return service.createUser(r); }
        @Override public UserResponse update(UUID id, UpdateUserRequest r) { return service.updateUser(id, r); }
        @Override public UserResponse updateStatus(UUID id, UpdateUserStatusRequest r) { return service.updateStatus(id, r); }
        @Override public UserResponse updateRole(UUID id, UpdateUserRoleRequest r) { return service.updateRole(id, r); }
        @Override public void delete(UUID acting, UUID target) { service.deleteUser(acting, target); }
        @Override public void deleteBulk(UUID acting, List<UUID> ids) { service.deleteUsers(acting, ids); }
    }

    public static final class Remote implements UserOperations {
        private final UserRmiClient client;
        private final CallerContextFactory ctxFactory;

        public Remote(UserRmiClient client, CallerContextFactory ctxFactory) {
            this.client = client;
            this.ctxFactory = ctxFactory;
        }

        @Override public List<UserResponse> getAll() {
            return com.unicconnect.rmi.dto.RmiMappers.toUserResponses(
                    client.read(remote -> remote.listUsers(ctxFactory.forCurrentUser())));
        }

        @Override public UserResponse getById(UUID id) {
            return com.unicconnect.rmi.dto.RmiMappers.toUserResponse(
                    client.read(remote -> remote.getUser(id, ctxFactory.forCurrentUser())));
        }

        @Override public List<UserResponse> byRole(String role) {
            return com.unicconnect.rmi.dto.RmiMappers.toUserResponses(
                    client.read(remote -> remote.usersByRole(role, ctxFactory.forCurrentUser())));
        }

        @Override public UserResponse create(CreateUserRequest r) {
            CallerContext ctx = ctxFactory.forCurrentUser();
            return com.unicconnect.rmi.dto.RmiMappers.toUserResponse(client.write(remote ->
                    remote.createUser(new CreateUserDto(r.email(), r.password(), r.roleName(), r.isActive()), ctx)));
        }

        @Override public UserResponse update(UUID id, UpdateUserRequest r) {
            return com.unicconnect.rmi.dto.RmiMappers.toUserResponse(client.write(remote ->
                    remote.updateUser(id, new UpdateUserDto(r.email(), r.isActive()), ctxFactory.forCurrentUser())));
        }

        @Override public UserResponse updateStatus(UUID id, UpdateUserStatusRequest r) {
            return com.unicconnect.rmi.dto.RmiMappers.toUserResponse(client.write(remote ->
                    remote.updateStatus(id, new UpdateStatusDto(r.isActive(), r.registrationStatus()),
                            ctxFactory.forCurrentUser())));
        }

        @Override public UserResponse updateRole(UUID id, UpdateUserRoleRequest r) {
            return com.unicconnect.rmi.dto.RmiMappers.toUserResponse(client.write(remote ->
                    remote.updateRole(id, r.roleId(), ctxFactory.forCurrentUser())));
        }

        /** Acting identity is re-derived server-side from the signed context;
         *  {@code acting} is honored only by the local path. */
        @Override public void delete(UUID acting, UUID target) {
            client.write(remote -> { remote.deleteUser(target, ctxFactory.forCurrentUser()); return null; });
        }

        @Override public void deleteBulk(UUID acting, List<UUID> ids) {
            client.write(remote -> { remote.deleteUsers(ids, ctxFactory.forCurrentUser()); return null; });
        }
    }
}
