package com.unicconnect.rmi.dto;

import com.unicconnect.dto.response.AttendanceResponse;
import com.unicconnect.dto.response.DailyAttendanceResponse;
import com.unicconnect.dto.response.GenerationSessionResponse;
import com.unicconnect.dto.response.MonthlyAttendanceResponse;
import com.unicconnect.dto.response.ScheduleResponse;
import com.unicconnect.dto.response.UserResponse;

import java.util.List;

/**
 * Single authoritative mapping between REST response records and the
 * Serializable RMI DTOs, used by BOTH tiers so the wire format can never
 * drift between client and server.
 */
public final class RmiMappers {

    private RmiMappers() {}

    // ---------- users ----------
    public static UserDto toUserDto(UserResponse r) {
        return new UserDto(r.userId(), r.email(), r.roleName(), r.isActive(),
                r.registrationStatus(), r.lastLogin(), r.createdAt());
    }

    public static UserResponse toUserResponse(UserDto d) {
        return new UserResponse(d.userId(), d.email(), d.roleName(), d.isActive(),
                d.registrationStatus(), d.lastLogin(), d.createdAt());
    }

    public static List<UserDto> toUserDtos(List<UserResponse> l) { return l.stream().map(RmiMappers::toUserDto).toList(); }

    public static List<UserResponse> toUserResponses(List<UserDto> l) { return l.stream().map(RmiMappers::toUserResponse).toList(); }

    // ---------- attendance ----------
    public static AttendanceRowDto toRowDto(AttendanceResponse r) {
        return new AttendanceRowDto(r.attendanceId(), r.sessionId(), r.studentId(),
                r.rollNo(), r.studentName(), r.attendanceStatus(), r.remark(),
                r.markedAt(), r.markedByStaffId(), r.attendedPeriods(),
                r.attendanceStartSlotId(), r.attendanceEndSlotId());
    }

    public static AttendanceResponse toAttendanceResponse(AttendanceRowDto d) {
        return new AttendanceResponse(d.attendanceId(), d.sessionId(), d.studentId(),
                d.rollNo(), d.studentName(), d.attendanceStatus(), d.remark(),
                d.markedAt(), d.markedByStaffId(), d.attendedPeriods(),
                d.attendanceStartSlotId(), d.attendanceEndSlotId());
    }

    public static List<AttendanceRowDto> toRowDtos(List<AttendanceResponse> l) { return l.stream().map(RmiMappers::toRowDto).toList(); }

    public static List<AttendanceResponse> toAttendanceResponses(List<AttendanceRowDto> l) { return l.stream().map(RmiMappers::toAttendanceResponse).toList(); }

    // ---------- daily report ----------
    public static DailyReportDto toDailyDto(DailyAttendanceResponse r) {
        return new DailyReportDto(r.sessionId(), r.sessionDate(), r.scheduleId(),
                r.courseCode(), r.courseName(), r.sectionNames(), r.scheduledPeriods(),
                r.slots().stream().map(s -> new DailyReportDto.SlotDto(
                        s.periodNo(), s.startTime(), s.endTime())).toList(),
                r.students().stream().map(s -> new DailyReportDto.StudentRow(
                        s.studentId(), s.rollNo(), s.studentName(), s.status(),
                        s.attendedPeriods(), s.attendancePercent())).toList());
    }

    public static DailyAttendanceResponse fromDailyDto(DailyReportDto d) {
        return new DailyAttendanceResponse(d.sessionId(), d.sessionDate(), d.scheduleId(),
                d.courseCode(), d.courseName(), d.sectionNames(), d.scheduledPeriods(),
                d.slots().stream().map(s -> new DailyAttendanceResponse.SlotDto(
                        s.periodNo(), s.startTime(), s.endTime())).toList(),
                d.students().stream().map(s -> new DailyAttendanceResponse.StudentRow(
                        s.studentId(), s.rollNo(), s.studentName(), s.status(),
                        s.attendedPeriods(), s.attendancePercent())).toList());
    }

    // ---------- monthly report ----------
    public static MonthlyReportDto toMonthlyDto(MonthlyAttendanceResponse r) {
        return new MonthlyReportDto(r.studentId(), r.studentName(), r.rollNo(),
                r.courseCode(), r.courseName(), r.year(), r.month(),
                r.scheduledPeriods(), r.attendedPeriods(), r.absentPeriods(),
                r.attendancePercent(),
                r.sessions().stream().map(s -> new MonthlyReportDto.SessionRow(
                        s.sessionId(), s.date(), s.scheduledPeriods(),
                        s.attendedPeriods(), s.percent())).toList());
    }

    public static MonthlyAttendanceResponse fromMonthlyDto(MonthlyReportDto d) {
        return new MonthlyAttendanceResponse(d.studentId(), d.studentName(), d.rollNo(),
                d.courseCode(), d.courseName(), d.year(), d.month(),
                d.scheduledPeriods(), d.attendedPeriods(), d.absentPeriods(),
                d.attendancePercent(),
                d.sessions().stream().map(s -> new MonthlyAttendanceResponse.SessionRow(
                        s.sessionId(), s.date(), s.scheduledPeriods(),
                        s.attendedPeriods(), s.percent())).toList());
    }

    // ---------- timetable ----------
    public static TimetableEntryDto toEntryDto(ScheduleResponse r) {
        return new TimetableEntryDto(r.scheduleId(), r.generationId(),
                r.teachingAssignmentId(), r.teachingGroupId(), r.courseCode(),
                r.courseName(), r.staffName(), r.sectionName(), r.semesterNo(),
                r.dayOfWeek(), r.startSlotId(), r.startPeriodNo(), r.startTime(),
                r.endSlotId(), r.endPeriodNo(), r.endTime(), r.scheduleStatus(),
                r.scheduleType(), r.sections(), r.staffNames(), r.createdAt());
    }

    public static ScheduleResponse fromEntryDto(TimetableEntryDto d) {
        return new ScheduleResponse(d.scheduleId(), d.generationId(),
                d.teachingAssignmentId(), d.teachingGroupId(), d.courseCode(),
                d.courseName(), d.staffName(), d.sectionName(), d.semesterNo(),
                d.dayOfWeek(), d.startSlotId(), d.startPeriodNo(), d.startTime(),
                d.endSlotId(), d.endPeriodNo(), d.endTime(), d.scheduleStatus(),
                d.scheduleType(), d.sections(), d.staffNames(), d.createdAt());
    }

    public static List<TimetableEntryDto> toEntryDtos(List<ScheduleResponse> l) { return l.stream().map(RmiMappers::toEntryDto).toList(); }

    public static List<ScheduleResponse> fromEntryDtos(List<TimetableEntryDto> l) { return l.stream().map(RmiMappers::fromEntryDto).toList(); }

    // ---------- generation status ----------
    public static GenerationStatusDto toStatusDto(GenerationSessionResponse r) {
        return new GenerationStatusDto(r.generationId(), r.termId(), r.academicYear(),
                r.generatedByStaffId(), r.generatedByStaffNo(), r.status(),
                r.startedAt(), r.publishedAt(), r.finishedAt(), r.createdAt(),
                r.failureReport());
    }

    public static GenerationSessionResponse fromStatusDto(GenerationStatusDto d) {
        return new GenerationSessionResponse(d.generationId(), d.termId(), d.academicYear(),
                d.generatedByStaffId(), d.generatedByStaffNo(), d.status(),
                d.startedAt(), d.publishedAt(), d.finishedAt(), d.createdAt(),
                d.failureReport());
    }
}
