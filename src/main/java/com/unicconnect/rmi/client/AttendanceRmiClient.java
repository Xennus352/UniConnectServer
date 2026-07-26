package com.unicconnect.rmi.client;

import com.unicconnect.rmi.dto.AttendanceSummaryDto;
import com.unicconnect.rmi.remote.AttendanceRemote;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.rmi.Naming;
import java.util.List;

@Component
public class AttendanceRmiClient {

    private static final Logger log = LoggerFactory.getLogger(AttendanceRmiClient.class);

    @Value("${rmi.host:localhost}")
    private String rmiHost;

    @Value("${rmi.port:1099}")
    private int rmiPort;

    private AttendanceRemote remote;

    @PostConstruct
    public void init() {
        try {
            String url = "rmi://" + rmiHost + ":" + rmiPort + "/AttendanceService";
            remote = (AttendanceRemote) Naming.lookup(url);
            log.info("AttendanceRmiClient connected to {}", url);
        } catch (Exception e) {
            log.warn("AttendanceRmiClient not available: {}", e.getMessage());
        }
    }

    public List<AttendanceSummaryDto> getAttendance(Long studentId) {
        if (remote == null) throw new RuntimeException("Attendance RMI service unavailable");
        try {
            return remote.getAttendance(studentId);
        } catch (Exception e) {
            throw new RuntimeException("RMI call failed: getAttendance", e);
        }
    }

    public AttendanceSummaryDto calculateAttendance(Long studentId, String subjectCode) {
        if (remote == null) throw new RuntimeException("Attendance RMI service unavailable");
        try {
            return remote.calculateAttendance(studentId, subjectCode);
        } catch (Exception e) {
            throw new RuntimeException("RMI call failed: calculateAttendance", e);
        }
    }

    public List<AttendanceSummaryDto> getStudentsBelow75() {
        if (remote == null) throw new RuntimeException("Attendance RMI service unavailable");
        try {
            return remote.getStudentsBelow75();
        } catch (Exception e) {
            throw new RuntimeException("RMI call failed: getStudentsBelow75", e);
        }
    }
}
