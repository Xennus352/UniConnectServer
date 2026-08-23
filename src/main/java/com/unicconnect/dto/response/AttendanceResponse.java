package com.unicconnect.dto.response;

import com.unicconnect.entity.AttendanceStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AttendanceResponse(
        UUID attendanceId,
        UUID sessionId,
        UUID studentId,
        String rollNo,
        String studentName,
        AttendanceStatus attendanceStatus,
        String remark,
        Instant markedAt,
        UUID markedByStaffId,
        int attendedPeriods,
        List<UUID> attendedSlotIds
) {}
