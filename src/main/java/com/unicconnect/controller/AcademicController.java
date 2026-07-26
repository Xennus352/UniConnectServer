package com.unicconnect.controller;

import com.unicconnect.rmi.dto.AcademicRecordDto;
import com.unicconnect.service.AcademicService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/academic")
public class AcademicController {

    private final AcademicService academicService;

    public AcademicController(AcademicService academicService) {
        this.academicService = academicService;
    }

    @GetMapping("/grades/{studentId}")
    public ResponseEntity<List<AcademicRecordDto>> getGrades(@PathVariable Long studentId) {
        return ResponseEntity.ok(academicService.getGrades(studentId));
    }

    @GetMapping("/grades/{studentId}/{academicYear}")
    public ResponseEntity<List<AcademicRecordDto>> getGradesByYear(
            @PathVariable Long studentId, @PathVariable String academicYear) {
        return ResponseEntity.ok(academicService.getGradesByYear(studentId, academicYear));
    }
}
