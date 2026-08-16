package com.unicconnect.controller;

import com.unicconnect.dto.request.CreateGenerationRequest;
import com.unicconnect.dto.request.GenerateTimetableRequest;
import com.unicconnect.dto.response.GenerationManageResponse;
import com.unicconnect.dto.response.GenerationScopeSemester;
import com.unicconnect.dto.response.GenerationSessionResponse;
import com.unicconnect.dto.response.ScheduleResponse;
import com.unicconnect.dto.response.TimetableLockResponse;
import com.unicconnect.service.TimetableEditLockService;
import com.unicconnect.service.TimetableGenerationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/generations")
public class TimetableGenerationController {

    private final TimetableGenerationService service;
    private final TimetableEditLockService lockService;

    public TimetableGenerationController(TimetableGenerationService service,
                                         TimetableEditLockService lockService) {
        this.service = service;
        this.lockService = lockService;
    }

    @GetMapping
    public ResponseEntity<List<GenerationSessionResponse>> getAll(
            @RequestParam(required = false) UUID termId) {
        return ResponseEntity.ok(service.getAll(termId));
    }

    /**
     * Server-side management authorization + current draft for the generation
     * workspace. {@code canManage} is decided from the authenticated staff member's
     * active HOD assignment, never from a frontend flag.
     */
    @GetMapping("/manage")
    public ResponseEntity<GenerationManageResponse> manage(
            @RequestParam(required = false) UUID termId) {
        return ResponseEntity.ok(service.getManagementContext(termId));
    }

    /**
     * Generation configuration options: applicable semesters (per the selected
     * Mid/Final exam type) and the existing sections that have teaching
     * assignments for the term.
     */
    @GetMapping("/scope")
    public ResponseEntity<List<GenerationScopeSemester>> scope(
            @RequestParam UUID termId,
            @RequestParam(required = false) UUID examTypeId) {
        return ResponseEntity.ok(service.getGenerationScope(termId, examTypeId));
    }

    @GetMapping("/{generationId}")
    public ResponseEntity<GenerationSessionResponse> getById(@PathVariable UUID generationId) {
        return ResponseEntity.ok(service.getById(generationId));
    }

    @PostMapping
    public ResponseEntity<GenerationSessionResponse> create(
            @Valid @RequestBody CreateGenerationRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @PostMapping("/{generationId}/generate")
    public ResponseEntity<GenerationSessionResponse> generate(
            @PathVariable UUID generationId,
            @RequestBody(required = false) GenerateTimetableRequest request) {
        return ResponseEntity.ok(service.generate(generationId, request));
    }

    @PostMapping("/{generationId}/publish")
    public ResponseEntity<GenerationSessionResponse> publish(@PathVariable UUID generationId) {
        return ResponseEntity.ok(service.publish(generationId));
    }

    @PostMapping("/{generationId}/cancel")
    public ResponseEntity<GenerationSessionResponse> cancel(@PathVariable UUID generationId) {
        return ResponseEntity.ok(service.cancel(generationId));
    }

    @GetMapping("/{generationId}/schedules")
    public ResponseEntity<List<ScheduleResponse>> getSchedules(@PathVariable UUID generationId) {
        return ResponseEntity.ok(service.getSchedules(generationId));
    }

    @DeleteMapping("/{generationId}")
    public ResponseEntity<Void> delete(@PathVariable UUID generationId) {
        service.delete(generationId);
        return ResponseEntity.noContent().build();
    }

    // ---------- Single-operator drag/drop editing lock ----------

    @GetMapping("/{generationId}/lock")
    public ResponseEntity<TimetableLockResponse> lockStatus(@PathVariable UUID generationId) {
        return ResponseEntity.ok(lockService.status(generationId));
    }

    @PostMapping("/{generationId}/lock")
    public ResponseEntity<TimetableLockResponse> acquireLock(@PathVariable UUID generationId) {
        return ResponseEntity.ok(lockService.acquire(generationId));
    }

    @PostMapping("/{generationId}/lock/heartbeat")
    public ResponseEntity<TimetableLockResponse> heartbeatLock(@PathVariable UUID generationId) {
        return ResponseEntity.ok(lockService.heartbeat(generationId));
    }

    @PostMapping("/{generationId}/lock/release")
    public ResponseEntity<TimetableLockResponse> releaseLock(@PathVariable UUID generationId) {
        return ResponseEntity.ok(lockService.release(generationId));
    }
}
