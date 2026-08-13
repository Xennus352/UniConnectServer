package com.unicconnect.controller;

import com.unicconnect.dto.request.CreateGenerationRequest;
import com.unicconnect.dto.response.GenerationSessionResponse;
import com.unicconnect.dto.response.ScheduleResponse;
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

    public TimetableGenerationController(TimetableGenerationService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<GenerationSessionResponse>> getAll(
            @RequestParam(required = false) UUID termId) {
        return ResponseEntity.ok(service.getAll(termId));
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
    public ResponseEntity<GenerationSessionResponse> generate(@PathVariable UUID generationId) {
        return ResponseEntity.ok(service.generate(generationId));
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
}
