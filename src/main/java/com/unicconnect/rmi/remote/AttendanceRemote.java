package com.unicconnect.rmi.remote;

import com.unicconnect.rmi.dto.AttendanceSummaryDto;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface AttendanceRemote extends Remote {
    List<AttendanceSummaryDto> getAttendance(Long studentId) throws RemoteException;
    AttendanceSummaryDto calculateAttendance(Long studentId, String subjectCode) throws RemoteException;
    List<AttendanceSummaryDto> getStudentsBelow75() throws RemoteException;
}
