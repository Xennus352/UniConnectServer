package com.unicconnect.rmi.remote;

import com.unicconnect.rmi.dto.AcademicRecordDto;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface AcademicRemote extends Remote {
    List<AcademicRecordDto> getGrades(Long studentId) throws RemoteException;
    List<AcademicRecordDto> getGradesByYear(Long studentId, String academicYear) throws RemoteException;
}
