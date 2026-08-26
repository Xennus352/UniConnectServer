package com.unicconnect.rmi.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DailyReportDto(
        UUID sessionId,
        LocalDate sessionDate,
        UUID scheduleId,
        String courseCode,
        String courseName,
        String sectionNames,
        int scheduledPeriods,
        List<SlotDto> slots,
        List<StudentRow> students
) implements Serializable {
    private static final long serialVersionUID = 1L;

    public record SlotDto(int periodNo, String startTime, String endTime) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    public record StudentRow(UUID studentId, String rollNo, String studentName,
                             String status, int attendedPeriods, double attendancePercent)
            implements Serializable {
        private static final long serialVersionUID = 1L;
    }
}
