package com.unicconnect.ops;

import com.unicconnect.dto.response.ScheduleResponse;

import java.util.List;
import java.util.UUID;

/** Timetable QUERY boundary (editing stays local) — LOCAL or RMI-backed per rmi.enabled. */
public interface TimetableQueryOperations {
    List<ScheduleResponse> getAll(UUID termId, UUID sectionId, UUID staffId, Integer dayOfWeek);
    List<ScheduleResponse> getPublished(UUID termId);
    ScheduleResponse getById(UUID scheduleId);
}
