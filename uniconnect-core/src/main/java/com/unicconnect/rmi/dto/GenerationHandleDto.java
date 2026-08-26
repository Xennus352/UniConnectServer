package com.unicconnect.rmi.dto;

import java.io.Serializable;
import java.util.UUID;

public record GenerationHandleDto(UUID generationId) implements Serializable {
    private static final long serialVersionUID = 1L;
}
