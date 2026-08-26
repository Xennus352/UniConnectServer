package com.unicconnect.rmi.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MonthlyReportDto(
        UUID studentId,
        String studentName,
        String rollNo,
        String courseCode,
        String courseName,
        int year,
        int month,
        int scheduledPeriods,
        int attendedPeriods,
        int absentPeriods,
        double attendancePercent,
        List<SessionRow> sessions
) implements Serializable {
    private static final long serialVersionUID = 1L;

    public record SessionRow(UUID sessionId, LocalDate date, int scheduledPeriods,
                             int attendedPeriods, double percent) implements Serializable {
        private static final long serialVersionUID = 1L;
    }
}
