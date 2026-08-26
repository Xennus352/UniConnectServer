package com.unicconnect.rmi.dto;

import java.io.Serializable;
import java.util.UUID;

public record RoleChangeDto(UUID roleId) implements Serializable {
    private static final long serialVersionUID = 1L;
}
