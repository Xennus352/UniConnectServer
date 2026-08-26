package com.unicconnect.rmi.dto;

import com.unicconnect.entity.RegistrationStatus;

import java.io.Serializable;

public record UpdateStatusDto(Boolean isActive, RegistrationStatus registrationStatus)
        implements Serializable {
    private static final long serialVersionUID = 1L;
}
