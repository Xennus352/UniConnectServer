package com.unicconnect.dto.response;

import java.util.List;
import java.util.UUID;

public record RollCallStudentsResponse(
        UUID scheduleId,
        List<String> sectionNames,
        int scheduledPeriods,
        List<SlotDto> slots,
        List<StudentDto> students
) {
    public record SlotDto(UUID slotId, int periodNo, String startTime, String endTime) {}
    public record StudentDto(UUID studentId, String rollNo, String studentName,
                             UUID attendanceId, String attendanceStatus,
                             String remark,
                             List<UUID> attendedSlotIds,
                             int attendedPeriods) {}
}
