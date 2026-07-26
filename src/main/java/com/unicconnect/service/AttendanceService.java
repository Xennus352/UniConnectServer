package com.unicconnect.service;

import com.unicconnect.rmi.client.AttendanceRmiClient;
import com.unicconnect.rmi.dto.AttendanceSummaryDto;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AttendanceService {

    private final AttendanceRmiClient rmiClient;

    public AttendanceService(AttendanceRmiClient rmiClient) {
        this.rmiClient = rmiClient;
    }

    public List<AttendanceSummaryDto> getAttendance(Long studentId) {
        return rmiClient.getAttendance(studentId);
    }

    public AttendanceSummaryDto calculateAttendance(Long studentId, String subjectCode) {
        return rmiClient.calculateAttendance(studentId, subjectCode);
    }

    public List<AttendanceSummaryDto> getStudentsBelow75() {
        return rmiClient.getStudentsBelow75();
    }
}
