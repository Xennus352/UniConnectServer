package com.unicconnect.controller;

import com.unicconnect.dto.request.MarkAttendanceRequest;
import com.unicconnect.dto.request.RollCallSessionRequest;
import com.unicconnect.dto.response.AttendanceResponse;
import com.unicconnect.dto.response.ClassSessionResponse;
import com.unicconnect.dto.response.DailyAttendanceResponse;
import com.unicconnect.dto.response.MonthlyAttendanceResponse;
import com.unicconnect.dto.response.RollCallScheduleResponse;
import com.unicconnect.dto.response.RollCallStudentsResponse;
import com.unicconnect.entity.Staff;
import com.unicconnect.service.AttendanceCalculationService;
import com.unicconnect.service.AttendanceService;
import com.unicconnect.service.RollCallService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Roll Call endpoints. Every mutating/reading method re-checks backend
 * authorization (active LECTURER position) and that schedules belong to the
 * latest PUBLISHED timetable.
 */
@RestController
@RequestMapping("/api/rollcall")
public class RollCallController {

    private final RollCallService rollCallService;
    private final AttendanceService attendanceService;
    private final AttendanceCalculationService calculationService;

    public RollCallController(RollCallService rollCallService,
                              AttendanceService attendanceService,
                              AttendanceCalculationService calculationService) {
        this.rollCallService = rollCallService;
        this.attendanceService = attendanceService;
        this.calculationService = calculationService;
    }

    /** Lecturer's weekly schedule from the latest PUBLISHED timetable. */
    @GetMapping("/my-schedule")
    public ResponseEntity<List<RollCallScheduleResponse>> mySchedule() {
        Staff lecturer = rollCallService.requireLecturer();
        return ResponseEntity.ok(rollCallService.mySchedule(lecturer));
    }

    /** Today's classes + current-class auto selection. */
    @GetMapping("/today")
    public ResponseEntity<List<RollCallScheduleResponse>> today() {
        Staff lecturer = rollCallService.requireLecturer();
        var todayName = java.time.LocalDate.now().getDayOfWeek().name();
        return ResponseEntity.ok(rollCallService.mySchedule(lecturer).stream()
                .filter(r -> r.dayName().equalsIgnoreCase(todayName))
                .toList());
    }

    /** Auto-detected currently-running class (may be null). */
    @GetMapping("/current")
    public ResponseEntity<RollCallScheduleResponse> current() {
        Staff lecturer = rollCallService.requireLecturer();
        return ResponseEntity.ok(rollCallService.currentClass(lecturer));
    }

    /** Create/reuse today's CLASS_SESSION for a published schedule. */
    @PostMapping("/sessions")
    public ResponseEntity<ClassSessionResponse> ensureSession(
            @Valid @RequestBody RollCallSessionRequest request) {
        Staff lecturer = rollCallService.requireLecturer();
        var session = rollCallService.ensureTodaySession(request.scheduleId(), lecturer);
        var schedule = session.getSchedule();
        String courseCode = schedule.getTeachingAssignment() != null
                ? schedule.getTeachingAssignment().getCourse().getCourseCode()
                : schedule.getTeachingGroup() != null
                        ? schedule.getTeachingGroup().getCourse().getCourseCode()
                        : null;
        var section = schedule.getTeachingAssignment() != null
                ? schedule.getTeachingAssignment().getSection()
                : null;
        return ResponseEntity.ok(new ClassSessionResponse(
                session.getSessionId(),
                schedule.getScheduleId(),
                schedule.getGeneration().getTerm().getTermId(),
                courseCode,
                section != null ? section.getSectionId() : null,
                section != null ? section.getSectionName() : null,
                session.getSessionDate(),
                session.getSessionStatus(),
                session.getStartedAt(),
                session.getEndedAt()));
    }

    /** Roster + saved attendance for a schedule (optionally today's session). */
    @GetMapping("/students")
    public ResponseEntity<RollCallStudentsResponse> students(
            @RequestParam UUID scheduleId,
            @RequestParam(required = false) UUID sessionId) {
        Staff lecturer = rollCallService.requireLecturer();
        return ResponseEntity.ok(
                rollCallService.students(scheduleId, sessionId, lecturer));
    }

    /** Submit attendance; delegates to AttendanceService (transactional). */
    @PostMapping("/sessions/{sessionId}/attendance")
    public ResponseEntity<List<AttendanceResponse>> submitAttendance(
            @PathVariable UUID sessionId,
            @Valid @RequestBody MarkAttendanceRequest request) {
        return ResponseEntity.ok(attendanceService.markAttendance(sessionId, request));
    }

    /** Dynamic daily report for one session. */
    @GetMapping("/report/daily/{sessionId}")
    public ResponseEntity<DailyAttendanceResponse> daily(@PathVariable UUID sessionId) {
        rollCallService.requireLecturer();
        return ResponseEntity.ok(calculationService.daily(sessionId));
    }

    /** Dynamic monthly per student (+optional course). */
    @GetMapping("/report/monthly")
    public ResponseEntity<MonthlyAttendanceResponse> monthly(
            @RequestParam UUID studentId,
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(required = false) UUID courseId) {
        rollCallService.requireLecturer();
        return ResponseEntity.ok(
                calculationService.monthly(studentId, courseId, year, month));
    }
}
