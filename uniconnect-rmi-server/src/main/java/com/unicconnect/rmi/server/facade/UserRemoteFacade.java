package com.unicconnect.rmi.server.facade;

import com.unicconnect.dto.request.CreateUserRequest;
import com.unicconnect.dto.request.UpdateUserRequest;
import com.unicconnect.dto.request.UpdateUserRoleRequest;
import com.unicconnect.dto.request.UpdateUserStatusRequest;
import com.unicconnect.rmi.contract.CallerContext;
import com.unicconnect.rmi.server.CallerContextVerifier;
import com.unicconnect.rmi.dto.CreateUserDto;
import com.unicconnect.rmi.dto.UpdateStatusDto;
import com.unicconnect.rmi.dto.UpdateUserDto;
import com.unicconnect.rmi.dto.UserDto;
import com.unicconnect.rmi.remote.UserRemote;
import com.unicconnect.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.rmi.RemoteException;
import java.util.List;
import java.util.UUID;

/** Thin RMI boundary over the shared UserService. */
@Component
public class UserRemoteFacade implements UserRemote {

    private static final Logger log = LoggerFactory.getLogger(UserRemoteFacade.class);

    private final UserService userService;
    private final CallerContextVerifier verifier;

    public UserRemoteFacade(UserService userService, CallerContextVerifier verifier) {
        this.userService = userService;
        this.verifier = verifier;
    }

    @Override
    public List<UserDto> listUsers(CallerContext ctx) throws RemoteException {
        try {
            UUID caller = verifier.verify(ctx);
            log.info("[RMI] UserRemote.listUsers caller={}", caller);
            return com.unicconnect.rmi.dto.RmiMappers.toUserDtos(userService.getAllUsers());
        } catch (RuntimeException e) { throw FacadeGuard.translate(e); }
    }

    @Override
    public UserDto getUser(UUID userId, CallerContext ctx) throws RemoteException {
        try {
            UUID caller = verifier.verify(ctx);
            log.info("[RMI] UserRemote.getUser caller={} target={}", caller, userId);
            return com.unicconnect.rmi.dto.RmiMappers.toUserDto(userService.getUserById(userId));
        } catch (RuntimeException e) { throw FacadeGuard.translate(e); }
    }

    @Override
    public List<UserDto> usersByRole(String roleName, CallerContext ctx) throws RemoteException {
        try {
            UUID caller = verifier.verify(ctx);
            log.info("[RMI] UserRemote.usersByRole role={} caller={}", roleName, caller);
            return com.unicconnect.rmi.dto.RmiMappers.toUserDtos(userService.getUsersByRole(roleName));
        } catch (RuntimeException e) { throw FacadeGuard.translate(e); }
    }

    @Override
    public UserDto createUser(CreateUserDto request, CallerContext ctx) throws RemoteException {
        try {
            UUID caller = verifier.verify(ctx);
            log.info("[RMI] UserRemote.createUser caller={} email={}", caller, request.email());
            return com.unicconnect.rmi.dto.RmiMappers.toUserDto(userService.createUser(
                    new CreateUserRequest(request.email(), request.password(),
                            request.roleName(), request.isActive())));
        } catch (RuntimeException e) { throw FacadeGuard.translate(e); }
    }

    @Override
    public UserDto updateUser(UUID userId, UpdateUserDto request, CallerContext ctx) throws RemoteException {
        try {
            UUID caller = verifier.verify(ctx);
            log.info("[RMI] UserRemote.updateUser caller={} target={}", caller, userId);
            return com.unicconnect.rmi.dto.RmiMappers.toUserDto(userService.updateUser(userId,
                    new UpdateUserRequest(request.email(), request.isActive())));
        } catch (RuntimeException e) { throw FacadeGuard.translate(e); }
    }

    @Override
    public UserDto updateStatus(UUID userId, UpdateStatusDto request, CallerContext ctx) throws RemoteException {
        try {
            UUID caller = verifier.verify(ctx);
            log.info("[RMI] UserRemote.updateStatus caller={} target={}", caller, userId);
            return com.unicconnect.rmi.dto.RmiMappers.toUserDto(userService.updateStatus(userId,
                    new UpdateUserStatusRequest(request.isActive(), request.registrationStatus())));
        } catch (RuntimeException e) { throw FacadeGuard.translate(e); }
    }

    @Override
    public UserDto updateRole(UUID userId, UUID roleId, CallerContext ctx) throws RemoteException {
        try {
            UUID caller = verifier.verify(ctx);
            log.info("[RMI] UserRemote.updateRole caller={} target={} roleId={}", caller, userId, roleId);
            return com.unicconnect.rmi.dto.RmiMappers.toUserDto(userService.updateRole(userId,
                    new UpdateUserRoleRequest(roleId)));
        } catch (RuntimeException e) { throw FacadeGuard.translate(e); }
    }

    @Override
    public void deleteUser(UUID targetUserId, CallerContext ctx) throws RemoteException {
        try {
            UUID caller = verifier.verify(ctx);
            log.info("[RMI] UserRemote.deleteUser caller={} target={}", caller, targetUserId);
            userService.deleteUser(caller, targetUserId);
        } catch (RuntimeException e) { throw FacadeGuard.translate(e); }
    }

    @Override
    public void deleteUsers(List<UUID> userIds, CallerContext ctx) throws RemoteException {
        try {
            UUID caller = verifier.verify(ctx);
            log.info("[RMI] UserRemote.deleteUsers caller={} count={}", caller, userIds.size());
            userService.deleteUsers(caller, userIds);
        } catch (RuntimeException e) { throw FacadeGuard.translate(e); }
    }
}
