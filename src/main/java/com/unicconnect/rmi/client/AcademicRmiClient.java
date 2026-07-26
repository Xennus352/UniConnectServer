package com.unicconnect.rmi.client;

import com.unicconnect.rmi.dto.AcademicRecordDto;
import com.unicconnect.rmi.remote.AcademicRemote;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.rmi.Naming;
import java.util.List;

@Component
public class AcademicRmiClient {

    private static final Logger log = LoggerFactory.getLogger(AcademicRmiClient.class);

    @Value("${rmi.host:localhost}")
    private String rmiHost;

    @Value("${rmi.port:1099}")
    private int rmiPort;

    private AcademicRemote remote;

    @PostConstruct
    public void init() {
        try {
            String url = "rmi://" + rmiHost + ":" + rmiPort + "/AcademicService";
            remote = (AcademicRemote) Naming.lookup(url);
            log.info("AcademicRmiClient connected to {}", url);
        } catch (Exception e) {
            log.warn("AcademicRmiClient not available: {}", e.getMessage());
        }
    }

    public List<AcademicRecordDto> getGrades(Long studentId) {
        if (remote == null) throw new RuntimeException("Academic RMI service unavailable");
        try {
            return remote.getGrades(studentId);
        } catch (Exception e) {
            throw new RuntimeException("RMI call failed: getGrades", e);
        }
    }

    public List<AcademicRecordDto> getGradesByYear(Long studentId, String academicYear) {
        if (remote == null) throw new RuntimeException("Academic RMI service unavailable");
        try {
            return remote.getGradesByYear(studentId, academicYear);
        } catch (Exception e) {
            throw new RuntimeException("RMI call failed: getGradesByYear", e);
        }
    }
}
