package com.unicconnect.rmi.dto;

import java.io.Serializable;

public record CreateUserDto(String email, String password, String roleName, Boolean isActive)
        implements Serializable {
    private static final long serialVersionUID = 1L;
}
