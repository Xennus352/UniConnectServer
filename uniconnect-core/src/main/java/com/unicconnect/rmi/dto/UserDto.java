package com.unicconnect.rmi.dto;

import com.unicconnect.entity.RegistrationStatus;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record UserDto(
        UUID userId,
        String email,
        String roleName,
        boolean isActive,
        RegistrationStatus registrationStatus,
        Instant lastLogin,
        Instant createdAt
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
