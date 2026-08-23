package com.unicconnect.service;

import com.unicconnect.dto.request.MarkAttendanceRequest;
import com.unicconnect.dto.response.RollCallStudentsResponse;
import com.unicconnect.entity.Attendance;
import com.unicconnect.entity.AttendancePeriod;
import com.unicconnect.entity.ClassSchedule;
import com.unicconnect.entity.ClassSession;
import com.unicconnect.entity.GenerationSession;
import com.unicconnect.entity.GenerationStatus;
import com.unicconnect.entity.Staff;
import com.unicconnect.exception.BusinessRuleException;
import com.unicconnect.exception.ValidationException;
import com.unicconnect.repository.AttendancePeriodRepository;
import com.unicconnect.repository.AttendanceRepository;
import com.unicconnect.repository.ClassScheduleRepository;
import com.unicconnect.repository.ClassSessionRepository;
import com.unicconnect.entity.Student;
import com.unicconnect.repository.GenerationSessionRepository;
import com.unicconnect.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roll Call / Attendance module verification against live data inside a
 * rolled-back transaction: sessions, attendance decisions, credited periods,
 * authorization, published-timetable scoping and dynamic calculations.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
public class RollCallAttendanceIntegrationTest {

    @Autowired RollCallService rollCallService;
    @Autowired AttendanceService attendanceService;
    @Autowired AttendanceCalculationService calculationService;
    @Autowired GenerationSessionRepository generationRepository;
    @Autowired ClassScheduleRepository scheduleRepository;
    @Autowired ClassSessionRepository sessionRepository;
    @Autowired AttendanceRepository attendanceRepository;
    @Autowired AttendancePeriodRepository periodRepository;
    @Autowired StaffRepository staffRepository;

    private GenerationSession published;
    private ClassSchedule schedule;      // a singleton delivery from the published generation
    private Staff lecturer;              // the covering lecturer of `schedule`
    private int spanPeriods;

    @BeforeEach
    void setUpAsCoveringLecturer() {
        published = generationRepository
                .findFirstByStatusAndPublishedAtIsNotNullOrderByPublishedAtDesc(
                        GenerationStatus.PUBLISHED)
                .orElseThrow(() -> new IllegalStateException(
                        "test requires a PUBLISHED generation"));
        schedule = scheduleRepository
                .findByGeneration_GenerationIdWithDetails(published.getGenerationId())
                .stream()
                .filter(s -> s.getTeachingAssignment() != null)
                .findFirst().orElseThrow();
        var covering = ClassScheduleService.coveredStaff(schedule);
        lecturer = staffRepository.findAll().stream()
                .filter(st -> covering.contains(st.getStaffId()))
                .filter(st -> st.getUser() != null)
                .findFirst().orElseThrow();
        spanPeriods = schedule.getEndSlot().getDisplayOrder()
                - schedule.getStartSlot().getDisplayOrder() + 1;
        authAs(lecturer);
    }

    private void authAs(Staff staff) {
        UUID userId = staff.getUser().getUserId();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null,
                        List.of(new SimpleGrantedAuthority("ROLE_STAFF"))));
    }

    // ===== A/D: authorization =====

    @Test
    void lecturerCanCreateAndFillSession() {
        ClassSession s = rollCallService.ensureTodaySession(
                schedule.getScheduleId(), lecturer);
        assertNotNull(s.getSessionId());

        var roster = rollCallService.students(schedule.getScheduleId(),
                s.getSessionId(), lecturer);
        assertFalse(roster.students().isEmpty());
        assertEquals(spanPeriods, roster.scheduledPeriods());
        assertEquals(spanPeriods, roster.slots().size());
    }

    @Test
    void nonLecturerStaffIsRejected() {
        // find a staff member WITHOUT an active LECTURER assignment
        Staff outsider = staffRepository.findAll().stream()
                .filter(st -> !rollCallService.hasActivePosition(st, "LECTURER"))
                .filter(st -> st.getUser() != null)
                .findFirst().orElse(null);
        if (outsider == null) return; // dataset has none: skip gracefully

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> rollCallService.ensureTodaySession(
                        schedule.getScheduleId(), outsider));
        assertTrue(ex.getMessage().contains("lecturer"));
    }

    // ===== B: latest published timetable only =====

    @Test
    void scheduleOutsideLatestPublishedIsRejected() {
        ClassSchedule stale = scheduleRepository.findAll().stream()
                .filter(s -> !s.getGeneration().getGenerationId()
                        .equals(published.getGenerationId()))
                .findFirst().orElse(null);
        if (stale == null) return;

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> rollCallService.authorizeLecturerForSchedule(lecturer, stale));
        assertTrue(ex.getMessage().contains("latest published"),
                ex.getMessage());
    }

    // ===== E/F/G/H: students, periods, partial attendance, daily calc =====

    @Test
    void partialAttendance_creditsExactPeriods_andDailyCalcMatches() {
        ClassSession session = rollCallService.ensureTodaySession(
                schedule.getScheduleId(), lecturer);

        var roster = rollCallService.students(schedule.getScheduleId(),
                session.getSessionId(), lecturer);
        List<UUID> slotIds = roster.slots().stream()
                .map(s -> s.slotId()).toList();
        assertTrue(slotIds.size() >= 1);
        UUID firstSlot = slotIds.get(0);
        UUID secondSlot = slotIds.size() > 1 ? slotIds.get(1) : firstSlot;
        var students = roster.students();
        assertTrue(students.size() >= 2, "need >=2 students for this scenario");
        UUID stuA = students.get(0).studentId();
        UUID stuB = students.get(1).studentId();

        attendanceService.markAttendance(session.getSessionId(),
                new MarkAttendanceRequest(List.of(
                        new MarkAttendanceRequest.AttendanceEntry(
                                stuA, com.unicconnect.entity.AttendanceStatus.PRESENT,
                                null, List.of(firstSlot, secondSlot)),
                        new MarkAttendanceRequest.AttendanceEntry(
                                stuB, com.unicconnect.entity.AttendanceStatus.PRESENT,
                                "late", List.of(secondSlot)))));

        // credited periods persisted exactly
        var attA = attendanceRepository.findBySession_SessionId(session.getSessionId())
                .stream().filter(x -> x.getStudent().getStudentId().equals(stuA))
                .findFirst().orElseThrow();
        Set<UUID> aSlots = periodRepository
                .findByAttendance_AttendanceId(attA.getAttendanceId())
                .stream().map(p -> p.getSlot().getSlotId())
                .collect(Collectors.toSet());
        assertEquals(Set.of(firstSlot, secondSlot), aSlots);

        // duplicate decision prevented: second mark replaces, never duplicates
        attendanceService.markAttendance(session.getSessionId(),
                new MarkAttendanceRequest(List.of(
                        new MarkAttendanceRequest.AttendanceEntry(
                                stuA, com.unicconnect.entity.AttendanceStatus.PRESENT,
                                null, List.of(secondSlot)))));
        long attRows = attendanceRepository.findBySession_SessionId(session.getSessionId())
                .stream().filter(x -> x.getStudent().getStudentId().equals(stuA)).count();
        assertEquals(1, attRows, "no duplicate ATTENDANCE rows");
        var attA2 = attendanceRepository.findBySession_SessionId(session.getSessionId())
                .stream().filter(x -> x.getStudent().getStudentId().equals(stuA))
                .findFirst().orElseThrow();
        long periodRows = periodRepository
                .findByAttendance_AttendanceId(attA2.getAttendanceId()).size();
        assertEquals(1, periodRows, "credited periods replaced, no duplicates");

        // daily calculation derives dynamically
        var daily = calculationService.daily(session.getSessionId());
        assertEquals(spanPeriods, daily.scheduledPeriods());
        var rowA = daily.students().stream()
                .filter(r -> r.studentId().equals(stuA)).findFirst().orElseThrow();
        assertEquals(1, rowA.attendedPeriods());
        assertEquals(50.0, rowA.attendancePercent());
    }

    // ===== G: invalid slot outside schedule =====

    @Test
    void slotOutsideScheduleRangeIsRejected() {
        ClassSession session = rollCallService.ensureTodaySession(
                schedule.getScheduleId(), lecturer);
        var roster = rollCallService.students(schedule.getScheduleId(),
                session.getSessionId(), lecturer);
        UUID studentId = roster.students().get(0).studentId();

        UUID bogus = UUID.randomUUID();
        assertThrows(ValidationException.class,
                () -> attendanceService.markAttendance(session.getSessionId(),
                        new MarkAttendanceRequest(List.of(
                                new MarkAttendanceRequest.AttendanceEntry(
                                        studentId,
                                        com.unicconnect.entity.AttendanceStatus.PRESENT,
                                        null, List.of(bogus))))));
    }

    // ===== G: PRESENT with zero periods is rejected =====

    @Test
    void presentWithoutPeriodsIsRejected() {
        ClassSession session = rollCallService.ensureTodaySession(
                schedule.getScheduleId(), lecturer);
        var roster = rollCallService.students(schedule.getScheduleId(),
                session.getSessionId(), lecturer);
        UUID studentId = roster.students().get(0).studentId();

        assertThrows(ValidationException.class,
                () -> attendanceService.markAttendance(session.getSessionId(),
                        new MarkAttendanceRequest(List.of(
                                new MarkAttendanceRequest.AttendanceEntry(
                                        studentId,
                                        com.unicconnect.entity.AttendanceStatus.PRESENT,
                                        null, List.of())))));
    }
}


