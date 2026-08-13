package com.unicconnect.controller;

import com.unicconnect.dto.request.StudentRequest;
import com.unicconnect.dto.response.AttendanceResponse;
import com.unicconnect.dto.response.ResultDocumentResponse;
import com.unicconnect.dto.response.StudentResponse;
import com.unicconnect.service.StudentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentService service;

    public StudentController(StudentService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<StudentResponse>> getAll(
            @RequestParam(required = false) UUID majorId,
            @RequestParam(required = false) UUID semesterId,
            @RequestParam(required = false) UUID sectionId,
            @RequestParam(required = false) UUID termId) {
        return ResponseEntity.ok(service.getAll(majorId, semesterId, sectionId, termId));
    }

    @GetMapping("/{studentId}")
    public ResponseEntity<StudentResponse> getById(@PathVariable UUID studentId) {
        return ResponseEntity.ok(service.getById(studentId));
    }

    @PostMapping
    public ResponseEntity<StudentResponse> create(@Valid @RequestBody StudentRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @PutMapping("/{studentId}")
    public ResponseEntity<StudentResponse> update(@PathVariable UUID studentId,
                                                  @Valid @RequestBody StudentRequest request) {
        return ResponseEntity.ok(service.update(studentId, request));
    }

    @DeleteMapping("/{studentId}")
    public ResponseEntity<Void> delete(@PathVariable UUID studentId) {
        service.delete(studentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{studentId}/attendance")
    public ResponseEntity<List<AttendanceResponse>> getAttendance(@PathVariable UUID studentId) {
        return ResponseEntity.ok(service.getAttendance(studentId));
    }

    @GetMapping("/{studentId}/results")
    public ResponseEntity<List<ResultDocumentResponse>> getResults(@PathVariable UUID studentId) {
        return ResponseEntity.ok(service.getResults(studentId));
    }

    @GetMapping("/{studentId}/results/{documentId}")
    public ResponseEntity<ResultDocumentResponse> getResult(@PathVariable UUID studentId,
                                                            @PathVariable UUID documentId) {
        return ResponseEntity.ok(service.getResult(studentId, documentId));
    }
}
