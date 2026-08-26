package com.unicconnect.rmi.dto;

import com.unicconnect.entity.AttendanceStatus;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record AttendanceRowDto(
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
        UUID attendanceStartSlotId,
        UUID attendanceEndSlotId
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
