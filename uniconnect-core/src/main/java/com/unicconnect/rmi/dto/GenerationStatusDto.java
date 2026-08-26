package com.unicconnect.rmi.dto;

import com.unicconnect.entity.GenerationStatus;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record GenerationStatusDto(
        UUID generationId,
        UUID termId,
        String academicYear,
        UUID generatedByStaffId,
        String generatedByStaffNo,
        GenerationStatus status,
        Instant startedAt,
        Instant publishedAt,
        Instant finishedAt,
        Instant createdAt,
        String failureReport
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
