package com.unicconnect.service;

import com.unicconnect.dto.request.MarkAttendanceRequest;
import com.unicconnect.dto.request.UpdateAttendanceRequest;
import com.unicconnect.dto.response.AttendanceResponse;
import com.unicconnect.entity.*;
import com.unicconnect.exception.BusinessRuleException;
import com.unicconnect.exception.ResourceNotFoundException;
import com.unicconnect.exception.ValidationException;
import com.unicconnect.repository.AttendanceRepository;
import com.unicconnect.repository.ClassSessionRepository;
import com.unicconnect.repository.StudentRepository;
import com.unicconnect.repository.StaffRepository;
import com.unicconnect.util.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final ClassSessionRepository sessionRepository;
    private final StudentRepository studentRepository;
    private final StaffRepository staffRepository;
    private final SecurityUtil securityUtil;

    public AttendanceService(AttendanceRepository attendanceRepository,
                             ClassSessionRepository sessionRepository,
                             StudentRepository studentRepository,
                             StaffRepository staffRepository,
                             SecurityUtil securityUtil) {
        this.attendanceRepository = attendanceRepository;
        this.sessionRepository = sessionRepository;
        this.studentRepository = studentRepository;
        this.staffRepository = staffRepository;
        this.securityUtil = securityUtil;
    }

    public List<AttendanceResponse> getAll(UUID sessionId) {
        if (sessionId == null) {
            throw new ValidationException("sessionId query parameter is required");
        }
        List<Attendance> records = attendanceRepository.findBySession_SessionId(sessionId);
        return records.stream().map(AttendanceService::toResponse).toList();
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

        Staff staff = null;
        if (securityUtil.isStaff()) {
            staff = staffRepository.findByUser_UserId(securityUtil.currentUserId()).orElse(null);
        }

        List<Attendance> saved = new java.util.ArrayList<>();
        for (MarkAttendanceRequest.AttendanceEntry entry : request.entries()) {
            Student student = studentRepository.findById(entry.studentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Student not found: " + entry.studentId()));

            Attendance existing = attendanceRepository
                    .findBySession_SessionId(sessionId).stream()
                    .filter(a -> a.getStudent().getStudentId().equals(entry.studentId()))
                    .findFirst().orElse(null);

            if (existing != null) {
                existing.setAttendanceStatus(entry.attendanceStatus());
                existing.setRemark(entry.remark());
                existing.setMarkedByStaff(staff);
                saved.add(attendanceRepository.save(existing));
            } else {
                Attendance attendance = new Attendance();
                attendance.setSession(session);
                attendance.setStudent(student);
                attendance.setAttendanceStatus(entry.attendanceStatus());
                attendance.setRemark(entry.remark());
                attendance.setMarkedByStaff(staff);
                saved.add(attendanceRepository.save(attendance));
            }
        }
        return saved.stream().map(AttendanceService::toResponse).toList();
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

    static AttendanceResponse toResponse(Attendance attendance) {
        return new AttendanceResponse(
                attendance.getAttendanceId(),
                attendance.getSession().getSessionId(),
                attendance.getStudent().getStudentId(),
                attendance.getStudent().getRollNo(),
                attendance.getStudent().getStudentName(),
                attendance.getAttendanceStatus(),
                attendance.getRemark(),
                attendance.getMarkedAt(),
                attendance.getMarkedByStaff() != null ? attendance.getMarkedByStaff().getStaffId() : null);
    }
}
