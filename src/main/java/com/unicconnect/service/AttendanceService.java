package com.unicconnect.service;

import com.unicconnect.dto.request.MarkAttendanceRequest;
import com.unicconnect.dto.request.UpdateAttendanceRequest;
import com.unicconnect.dto.response.AttendanceResponse;
import com.unicconnect.entity.*;
import com.unicconnect.exception.BusinessRuleException;
import com.unicconnect.exception.ResourceNotFoundException;
import com.unicconnect.exception.ValidationException;
import com.unicconnect.repository.AttendancePeriodRepository;
import com.unicconnect.repository.AttendanceRepository;
import com.unicconnect.repository.ClassSessionRepository;
import com.unicconnect.repository.StudentRepository;
import com.unicconnect.repository.StaffRepository;
import com.unicconnect.repository.TimeSlotRepository;
import com.unicconnect.util.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Roll-call attendance decisions + the actual periods a student received
 * credit for. Percentages are NEVER stored here; they are derived by
 * {@link AttendanceCalculationService}.
 */
@Service
@Transactional(readOnly = true)
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final AttendancePeriodRepository periodRepository;
    private final ClassSessionRepository sessionRepository;
    private final StudentRepository studentRepository;
    private final StaffRepository staffRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final SecurityUtil securityUtil;
    private final RollCallService rollCallService;

    public AttendanceService(AttendanceRepository attendanceRepository,
                             AttendancePeriodRepository periodRepository,
                             ClassSessionRepository sessionRepository,
                             StudentRepository studentRepository,
                             StaffRepository staffRepository,
                             TimeSlotRepository timeSlotRepository,
                             SecurityUtil securityUtil,
                             RollCallService rollCallService) {
        this.attendanceRepository = attendanceRepository;
        this.periodRepository = periodRepository;
        this.sessionRepository = sessionRepository;
        this.studentRepository = studentRepository;
        this.staffRepository = staffRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.securityUtil = securityUtil;
        this.rollCallService = rollCallService;
    }

    public List<AttendanceResponse> getAll(UUID sessionId) {
        if (sessionId == null) {
            throw new ValidationException("sessionId query parameter is required");
        }
        List<Attendance> records = attendanceRepository.findBySession_SessionId(sessionId);
        return records.stream().map(this::toResponse).toList();
    }

    public AttendanceResponse getById(UUID attendanceId) {
        return toResponse(findAttendance(attendanceId));
    }

    @Transactional
    public List<AttendanceResponse> markAttendance(UUID sessionId, MarkAttendanceRequest request) {
        ClassSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Class session not found"));
        if (session.getSessionStatus() == SessionStatus.COMPLETED) {
            throw new BusinessRuleException("Attendance cannot be modified after the session is completed");
        }

        // Backend authorization: only the assigned lecturer (an active
        // LECTURER position holder covered by the schedule) may mark.
        Staff staff = staffRepository.findByUser_UserId(securityUtil.currentUserId())
                .orElseThrow(() -> new BusinessRuleException("Only staff can perform roll call"));
        rollCallService.authorizeLecturerForSchedule(staff, session.getSchedule());

        LocalDate today = LocalDate.now();
        if (!session.getSessionDate().equals(today)) {
            throw new BusinessRuleException(
                    "Attendance can only be submitted for today's session");
        }

        // Valid credit slots for this schedule: start..end inclusive.
        List<TimeSlot> spanSlots = timeSlotRepository.findAll().stream()
                .filter(t -> t.getDisplayOrder() >= session.getSchedule().getStartSlot().getDisplayOrder()
                        && t.getDisplayOrder() <= session.getSchedule().getEndSlot().getDisplayOrder())
                .sorted(java.util.Comparator.comparing(TimeSlot::getDisplayOrder))
                .toList();
        Set<UUID> validSlotIds = new HashSet<>();
        spanSlots.forEach(t -> validSlotIds.add(t.getSlotId()));

        List<AttendanceResponse> saved = new ArrayList<>();
        for (MarkAttendanceRequest.AttendanceEntry entry : request.entries()) {
            Student student = studentRepository.findById(entry.studentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Student not found: " + entry.studentId()));

            List<UUID> slotIds = entry.periodSlotIds() == null ? List.of() : entry.periodSlotIds();

            AttendanceStatus status = entry.attendanceStatus();
            if (status == AttendanceStatus.ABSENT && !slotIds.isEmpty()) {
                throw new ValidationException(
                        "ABSENT students cannot have attended periods: " + student.getStudentName());
            }
            if (status == AttendanceStatus.PRESENT && slotIds.isEmpty()) {
                throw new ValidationException(
                        "Select at least one attended period for "
                                + student.getStudentName()
                                + " (or mark the student ABSENT)");
            }
            for (UUID slotId : slotIds) {
                if (!validSlotIds.contains(slotId)) {
                    throw new ValidationException(
                            "Selected period is outside the scheduled range for "
                                    + student.getStudentName());
                }
            }

            Attendance attendance = attendanceRepository
                    .findBySession_SessionId(sessionId).stream()
                    .filter(a -> a.getStudent().getStudentId().equals(entry.studentId()))
                    .findFirst().orElse(null);

            if (attendance == null) {
                attendance = new Attendance();
                attendance.setSession(session);
                attendance.setStudent(student);
            }
            attendance.setAttendanceStatus(status);
            attendance.setRemark(entry.remark());
            attendance.setMarkedByStaff(staff);
            attendance = attendanceRepository.save(attendance);

            // Replace credited periods transactionally.
            periodRepository.deleteByAttendance_AttendanceId(attendance.getAttendanceId());
            periodRepository.flush();
            for (UUID slotId : slotIds) {
                TimeSlot slot = timeSlotRepository.findById(slotId).orElseThrow();
                AttendancePeriod ap = new AttendancePeriod();
                ap.setAttendance(attendance);
                ap.setSlot(slot);
                periodRepository.save(ap);
            }
            saved.add(toResponse(attendance));
        }
        return saved;
    }

    @Transactional
    public AttendanceResponse update(UUID attendanceId, UpdateAttendanceRequest request) {
        Attendance attendance = findAttendance(attendanceId);
        if (attendance.getSession().getSessionStatus() == SessionStatus.COMPLETED) {
            throw new BusinessRuleException("Attendance cannot be modified after the session is completed");
        }
        attendance.setAttendanceStatus(request.attendanceStatus());
        attendance.setRemark(request.remark());
        return toResponse(attendanceRepository.save(attendance));
    }

    @Transactional
    public void delete(UUID attendanceId) {
        Attendance attendance = findAttendance(attendanceId);
        if (attendance.getSession().getSessionStatus() == SessionStatus.COMPLETED) {
            throw new BusinessRuleException("Attendance cannot be deleted after the session is completed");
        }
        attendanceRepository.deleteById(attendanceId);
    }

    public Attendance findAttendance(UUID attendanceId) {
        return attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance record not found"));
    }

    public AttendanceResponse toResponse(Attendance attendance) {
        List<AttendancePeriod> periods =
                periodRepository.findByAttendance_AttendanceId(attendance.getAttendanceId());
        List<UUID> slotIds = periods.stream()
                .map(p -> p.getSlot().getSlotId())
                .sorted(java.util.Comparator.comparing(id -> id))
                .toList();
        return new AttendanceResponse(
                attendance.getAttendanceId(),
                attendance.getSession().getSessionId(),
                attendance.getStudent().getStudentId(),
                attendance.getStudent().getRollNo(),
                attendance.getStudent().getStudentName(),
                attendance.getAttendanceStatus(),
                attendance.getRemark(),
                attendance.getMarkedAt(),
                attendance.getMarkedByStaff() != null ? attendance.getMarkedByStaff().getStaffId() : null,
                slotIds.size(),
                slotIds);
    }
}

