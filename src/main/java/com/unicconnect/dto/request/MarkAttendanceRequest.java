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
            String remark,
            /** Timetable slots the student actually attended (subset of the
             *  schedule's slot range). Empty/ABSENT => zero credited periods. */
            List<UUID> periodSlotIds
    ) {}
}
