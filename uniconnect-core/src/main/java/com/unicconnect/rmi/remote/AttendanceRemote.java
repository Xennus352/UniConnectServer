package com.unicconnect.rmi.remote;

import com.unicconnect.rmi.contract.CallerContext;
import com.unicconnect.rmi.dto.AttendanceRowDto;
import com.unicconnect.rmi.dto.DailyReportDto;
import com.unicconnect.rmi.dto.MarkAttendanceDto;
import com.unicconnect.rmi.dto.MonthlyReportDto;
import com.unicconnect.rmi.dto.UpdateAttendanceDto;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.UUID;

/** Attendance marking + calculation over RMI. Binding: "AttendanceService". */
public interface AttendanceRemote extends Remote {

    /** Daily derived report for one class session (LECTURER required). */
    DailyReportDto dailyReport(UUID sessionId, CallerContext ctx) throws RemoteException;

    /** Monthly per-student report; nullable courseId = all courses (LECTURER required). */
    MonthlyReportDto monthlyAttendance(UUID studentId, UUID courseId, int year, int month,
                                       CallerContext ctx) throws RemoteException;

    /** Mark/upsert attendance for a session (assigned LECTURER only). */
    List<AttendanceRowDto> markAttendance(UUID sessionId, MarkAttendanceDto request,
                                          CallerContext ctx) throws RemoteException;

    /** Edit one attendance row (assigned LECTURER only). */
    AttendanceRowDto updateAttendance(UUID attendanceId, UpdateAttendanceDto request,
                                      CallerContext ctx) throws RemoteException;

    /** Delete one attendance row before its session completes (LECTURER). */
    void deleteAttendance(UUID attendanceId, CallerContext ctx) throws RemoteException;
}
