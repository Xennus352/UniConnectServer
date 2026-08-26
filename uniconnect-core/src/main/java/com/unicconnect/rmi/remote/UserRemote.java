package com.unicconnect.rmi.remote;

import com.unicconnect.rmi.contract.CallerContext;
import com.unicconnect.rmi.dto.CreateUserDto;
import com.unicconnect.rmi.dto.UpdateStatusDto;
import com.unicconnect.rmi.dto.UpdateUserDto;
import com.unicconnect.rmi.dto.UserDto;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.UUID;

/** User CRUD over RMI. Binding: "UserService". */
public interface UserRemote extends Remote {

    List<UserDto> listUsers(CallerContext ctx) throws RemoteException;

    UserDto getUser(UUID userId, CallerContext ctx) throws RemoteException;

    List<UserDto> usersByRole(String roleName, CallerContext ctx) throws RemoteException;

    UserDto createUser(CreateUserDto request, CallerContext ctx) throws RemoteException;

    UserDto updateUser(UUID userId, UpdateUserDto request, CallerContext ctx) throws RemoteException;

    UserDto updateStatus(UUID userId, UpdateStatusDto request, CallerContext ctx) throws RemoteException;

    UserDto updateRole(UUID userId, UUID roleId, CallerContext ctx) throws RemoteException;

    void deleteUser(UUID targetUserId, CallerContext ctx) throws RemoteException;

    void deleteUsers(List<UUID> userIds, CallerContext ctx) throws RemoteException;
}
