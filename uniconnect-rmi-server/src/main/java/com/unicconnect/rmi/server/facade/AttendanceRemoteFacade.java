package com.unicconnect.rmi.server.facade;

import com.unicconnect.dto.request.MarkAttendanceRequest;
import com.unicconnect.dto.request.UpdateAttendanceRequest;
import com.unicconnect.rmi.contract.CallerContext;
import com.unicconnect.rmi.server.CallerContextVerifier;
import com.unicconnect.rmi.dto.AttendanceRowDto;
import com.unicconnect.rmi.dto.DailyReportDto;
import com.unicconnect.rmi.dto.MarkAttendanceDto;
import com.unicconnect.rmi.dto.MarkEntryDto;
import com.unicconnect.rmi.dto.MonthlyReportDto;
import com.unicconnect.rmi.dto.UpdateAttendanceDto;
import com.unicconnect.rmi.remote.AttendanceRemote;
import com.unicconnect.service.AttendanceCalculationService;
import com.unicconnect.service.AttendanceService;
import com.unicconnect.service.RollCallService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.rmi.RemoteException;
import java.util.List;
import java.util.UUID;

/**
 * Thin RMI boundary over the shared attendance services.
 * Marking authorization (assigned LECTURER + published timetable +
 * completed-session lock) is enforced INSIDE AttendanceService exactly as in
 * the REST path; daily/monthly reports get an explicit requireLecturer check
 * here because the calculation service itself is guard-free by design.
 */
@Component
public class AttendanceRemoteFacade implements AttendanceRemote {

    private static final Logger log = LoggerFactory.getLogger(AttendanceRemoteFacade.class);

    private final AttendanceService attendanceService;
    private final AttendanceCalculationService calculationService;
    private final RollCallService rollCallService;
    private final CallerContextVerifier verifier;

    public AttendanceRemoteFacade(AttendanceService attendanceService,
                                  AttendanceCalculationService calculationService,
                                  RollCallService rollCallService,
                                  CallerContextVerifier verifier) {
        this.attendanceService = attendanceService;
        this.calculationService = calculationService;
        this.rollCallService = rollCallService;
        this.verifier = verifier;
    }

    @Override
    public DailyReportDto dailyReport(UUID sessionId, CallerContext ctx) throws RemoteException {
        try {
            UUID caller = verifier.verify(ctx);
            rollCallService.requireLecturer(caller);
            log.info("[RMI] AttendanceRemote.dailyReport caller={} session={}", caller, sessionId);
            return com.unicconnect.rmi.dto.RmiMappers.toDailyDto(calculationService.daily(sessionId));
        } catch (RuntimeException e) { throw FacadeGuard.translate(e); }
    }

    @Override
    public MonthlyReportDto monthlyAttendance(UUID studentId, UUID courseId, int year, int month,
                                              CallerContext ctx) throws RemoteException {
        try {
            UUID caller = verifier.verify(ctx);
            rollCallService.requireLecturer(caller);
            log.info("[RMI] AttendanceRemote.monthlyAttendance caller={} student={} {}/{}", caller, studentId, month, year);
            return com.unicconnect.rmi.dto.RmiMappers.toMonthlyDto(
                    calculationService.monthly(studentId, courseId, year, month));
        } catch (RuntimeException e) { throw FacadeGuard.translate(e); }
    }

    @Override
    public List<AttendanceRowDto> markAttendance(UUID sessionId, MarkAttendanceDto request,
                                                 CallerContext ctx) throws RemoteException {
        try {
            UUID caller = verifier.verify(ctx);
            log.info("[RMI] AttendanceRemote.markAttendance caller={} session={} entries={}",
                    caller, sessionId, request.entries().size());
            List<com.unicconnect.dto.response.AttendanceResponse> saved =
                    attendanceService.markAttendance(sessionId,
                            new MarkAttendanceRequest(request.entries().stream()
                                    .map(e -> new MarkAttendanceRequest.AttendanceEntry(
                                            e.studentId(), e.attendanceStatus(), e.remark(),
                                            e.attendanceStartSlotId(), e.attendanceEndSlotId()))
                                    .toList()),
                            caller);
            return com.unicconnect.rmi.dto.RmiMappers.toRowDtos(saved);
        } catch (RuntimeException e) { throw FacadeGuard.translate(e); }
    }

    @Override
    public AttendanceRowDto updateAttendance(UUID attendanceId, UpdateAttendanceDto request,
                                             CallerContext ctx) throws RemoteException {
        try {
            UUID caller = verifier.verify(ctx);
            log.info("[RMI] AttendanceRemote.updateAttendance caller={} row={}", caller, attendanceId);
            return com.unicconnect.rmi.dto.RmiMappers.toRowDto(attendanceService.update(
                    attendanceId,
                    new UpdateAttendanceRequest(request.attendanceStatus(), request.remark(),
                            request.attendanceStartSlotId(), request.attendanceEndSlotId()),
                    caller));
        } catch (RuntimeException e) { throw FacadeGuard.translate(e); }
    }

    @Override
    public void deleteAttendance(UUID attendanceId, CallerContext ctx) throws RemoteException {
        try {
            UUID caller = verifier.verify(ctx);
            log.info("[RMI] AttendanceRemote.deleteAttendance caller={} row={}", caller, attendanceId);
            attendanceService.delete(attendanceId);
        } catch (RuntimeException e) { throw FacadeGuard.translate(e); }
    }
}
