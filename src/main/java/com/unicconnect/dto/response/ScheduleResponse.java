package com.unicconnect.dto.response;

import com.unicconnect.entity.ScheduleStatus;
import com.unicconnect.entity.ScheduleType;

import java.time.Instant;
import java.util.UUID;

public record ScheduleResponse(
        UUID scheduleId,
        UUID generationId,
        UUID teachingAssignmentId,
        String courseCode,
        String staffName,
        String sectionName,
        Integer dayOfWeek,
        UUID startSlotId,
        Integer startPeriodNo,
        UUID endSlotId,
        Integer endPeriodNo,
        ScheduleStatus scheduleStatus,
        ScheduleType scheduleType,
        Instant createdAt
) {}