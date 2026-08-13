package com.unicconnect.dto.request;

import com.unicconnect.entity.AttendanceStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateAttendanceRequest(
        @NotNull AttendanceStatus attendanceStatus,
        String remark
) {}