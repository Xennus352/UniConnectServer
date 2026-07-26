package com.unicconnect.controller;

import com.unicconnect.rmi.dto.AttendanceSummaryDto;
import com.unicconnect.service.AttendanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @GetMapping("/{studentId}")
    public ResponseEntity<List<AttendanceSummaryDto>> getAttendance(@PathVariable Long studentId) {
        return ResponseEntity.ok(attendanceService.getAttendance(studentId));
    }

    @GetMapping("/calculate/{studentId}/{subjectCode}")
    public ResponseEntity<AttendanceSummaryDto> calculateAttendance(
            @PathVariable Long studentId, @PathVariable String subjectCode) {
        return ResponseEntity.ok(attendanceService.calculateAttendance(studentId, subjectCode));
    }

    @GetMapping("/below75")
    public ResponseEntity<List<AttendanceSummaryDto>> getStudentsBelow75() {
        return ResponseEntity.ok(attendanceService.getStudentsBelow75());
    }
}
