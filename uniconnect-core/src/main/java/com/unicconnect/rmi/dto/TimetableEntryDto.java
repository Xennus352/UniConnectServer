package com.unicconnect.rmi.dto;

import com.unicconnect.entity.ScheduleStatus;
import com.unicconnect.entity.ScheduleType;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TimetableEntryDto(
        UUID scheduleId,
        UUID generationId,
        UUID teachingAssignmentId,
        UUID teachingGroupId,
        String courseCode,
        String courseName,
        String staffName,
        String sectionName,
        Integer semesterNo,
        Integer dayOfWeek,
        UUID startSlotId,
        Integer startPeriodNo,
        String startTime,
        UUID endSlotId,
        Integer endPeriodNo,
        String endTime,
        ScheduleStatus scheduleStatus,
        ScheduleType scheduleType,
        List<String> sections,
        List<String> staffNames,
        Instant createdAt
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
