package com.unicconnect.ops;

import com.unicconnect.dto.request.CreateGenerationRequest;
import com.unicconnect.dto.request.GenerateTimetableRequest;
import com.unicconnect.dto.response.GenerationSessionResponse;

import java.util.List;
import java.util.UUID;

/**
 * Timetable generation boundary — LOCAL or RMI-backed per rmi.enabled.
 * In RMI mode {@code generate(...)} returns the session snapshot immediately
 * and the solver keeps running inside the RMI Server JVM.
 */
public interface TimetableGenerationOperations {
    GenerationSessionResponse create(CreateGenerationRequest request);
    GenerationSessionResponse generate(UUID generationId, GenerateTimetableRequest request);
    GenerationSessionResponse getById(UUID generationId);
    List<GenerationSessionResponse> getAll(UUID termId);
}
