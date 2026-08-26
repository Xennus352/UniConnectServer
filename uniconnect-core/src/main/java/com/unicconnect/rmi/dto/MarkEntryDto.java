package com.unicconnect.rmi.dto;

import com.unicconnect.entity.AttendanceStatus;

import java.io.Serializable;
import java.util.UUID;

public record MarkEntryDto(
        UUID studentId,
        AttendanceStatus attendanceStatus,
        String remark,
        UUID attendanceStartSlotId,
        UUID attendanceEndSlotId
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
