package com.unicconnect.ops;

import com.unicconnect.dto.request.MarkAttendanceRequest;
import com.unicconnect.dto.request.UpdateAttendanceRequest;
import com.unicconnect.dto.response.AttendanceResponse;
import com.unicconnect.dto.response.DailyAttendanceResponse;
import com.unicconnect.dto.response.MonthlyAttendanceResponse;

import java.util.List;
import java.util.UUID;

/** Attendance marking/calculation boundary — LOCAL or RMI-backed per rmi.enabled. */
public interface AttendanceOperations {
    List<AttendanceResponse> mark(UUID sessionId, MarkAttendanceRequest request, UUID callerUserId);
    AttendanceResponse update(UUID attendanceId, UpdateAttendanceRequest request, UUID callerUserId);
    void delete(UUID attendanceId, UUID callerUserId);
    DailyAttendanceResponse daily(UUID sessionId, UUID callerUserId);
    MonthlyAttendanceResponse monthly(UUID studentId, UUID courseId, int year, int month, UUID callerUserId);
}
