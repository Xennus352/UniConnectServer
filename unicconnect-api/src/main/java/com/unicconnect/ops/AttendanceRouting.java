package com.unicconnect.ops;

import com.unicconnect.dto.request.MarkAttendanceRequest;
import com.unicconnect.dto.request.UpdateAttendanceRequest;
import com.unicconnect.dto.response.AttendanceResponse;
import com.unicconnect.dto.response.DailyAttendanceResponse;
import com.unicconnect.dto.response.MonthlyAttendanceResponse;
import com.unicconnect.rmi.client.RmiClientConfig.AttendanceRmiClient;
import com.unicconnect.rmi.contract.CallerContext;
import com.unicconnect.rmi.dto.MarkAttendanceDto;
import com.unicconnect.rmi.dto.MarkEntryDto;
import com.unicconnect.security.CallerContextFactory;
import com.unicconnect.service.AttendanceCalculationService;
import com.unicconnect.service.AttendanceService;

import java.util.List;
import java.util.UUID;

/** Hybrid routing for attendance marking + calculation. */
public final class AttendanceRouting {

    private AttendanceRouting() {}

    public static final class Local implements AttendanceOperations {
        private final AttendanceService service;
        private final AttendanceCalculationService calculationService;
        public Local(AttendanceService service, AttendanceCalculationService calculationService) {
            this.service = service;
            this.calculationService = calculationService;
        }
        @Override public List<AttendanceResponse> mark(UUID sessionId, MarkAttendanceRequest r, UUID caller) {
            return service.markAttendance(sessionId, r, caller);
        }
        @Override public AttendanceResponse update(UUID id, UpdateAttendanceRequest r, UUID caller) {
            return service.update(id, r, caller);
        }
        @Override public void delete(UUID id, UUID caller) { service.delete(id); }
        @Override public DailyAttendanceResponse daily(UUID sessionId, UUID caller) {
            // Calculation is guard-free; the controller already ran requireLecturer.
            return calculationService.daily(sessionId);
        }
        @Override public MonthlyAttendanceResponse monthly(UUID studentId, UUID courseId,
                                                           int year, int month, UUID caller) {
            return calculationService.monthly(studentId, courseId, year, month);
        }
    }

    public static final class Remote implements AttendanceOperations {
        private final AttendanceRmiClient client;
        private final CallerContextFactory ctxFactory;

        public Remote(AttendanceRmiClient client, CallerContextFactory ctxFactory) {
            this.client = client;
            this.ctxFactory = ctxFactory;
        }

        @Override public List<AttendanceResponse> mark(UUID sessionId, MarkAttendanceRequest r, UUID caller) {
            CallerContext ctx = ctxFactory.forCurrentUser();
            List<com.unicconnect.rmi.dto.AttendanceRowDto> out = client.write(remote ->
                    remote.markAttendance(sessionId,
                            new MarkAttendanceDto(r.entries().stream()
                                    .map(e -> new MarkEntryDto(e.studentId(), e.attendanceStatus(),
                                            e.remark(), e.attendanceStartSlotId(), e.attendanceEndSlotId()))
                                    .toList()), ctx));
            return com.unicconnect.rmi.dto.RmiMappers.toAttendanceResponses(out);
        }

        @Override public AttendanceResponse update(UUID id, UpdateAttendanceRequest r, UUID caller) {
            com.unicconnect.rmi.dto.AttendanceRowDto d = client.write(remote ->
                    remote.updateAttendance(id,
                            new com.unicconnect.rmi.dto.UpdateAttendanceDto(r.attendanceStatus(), r.remark(),
                                    r.attendanceStartSlotId(), r.attendanceEndSlotId()),
                            ctxFactory.forCurrentUser()));
            return com.unicconnect.rmi.dto.RmiMappers.toAttendanceResponse(d);
        }

        @Override public void delete(UUID id, UUID caller) {
            client.write(remote -> { remote.deleteAttendance(id, ctxFactory.forCurrentUser()); return null; });
        }

        @Override public DailyAttendanceResponse daily(UUID sessionId, UUID caller) {
            return com.unicconnect.rmi.dto.RmiMappers.fromDailyDto(
                    client.read(remote -> remote.dailyReport(sessionId, ctxFactory.forCurrentUser())));
        }

        @Override public MonthlyAttendanceResponse monthly(UUID studentId, UUID courseId,
                                                           int year, int month, UUID caller) {
            return com.unicconnect.rmi.dto.RmiMappers.fromMonthlyDto(
                    client.read(remote -> remote.monthlyAttendance(studentId, courseId, year, month,
                            ctxFactory.forCurrentUser())));
        }
    }
}
