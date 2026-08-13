package com.unicconnect.dto.request;

import com.unicconnect.entity.AttendanceStatus;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record MarkAttendanceRequest(
        List<AttendanceEntry> entries
) {

    public record AttendanceEntry(
            @NotNull UUID studentId,
            @NotNull AttendanceStatus attendanceStatus,
            String remark
    ) {}
}