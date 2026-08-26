package com.unicconnect.rmi.dto;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

public record SemesterSelectionDto(UUID semesterId, List<UUID> sectionIds) implements Serializable {
    private static final long serialVersionUID = 1L;
}
