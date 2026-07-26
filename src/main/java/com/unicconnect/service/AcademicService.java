package com.unicconnect.service;

import com.unicconnect.rmi.client.AcademicRmiClient;
import com.unicconnect.rmi.dto.AcademicRecordDto;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AcademicService {

    private final AcademicRmiClient rmiClient;

    public AcademicService(AcademicRmiClient rmiClient) {
        this.rmiClient = rmiClient;
    }

    public List<AcademicRecordDto> getGrades(Long studentId) {
        return rmiClient.getGrades(studentId);
    }

    public List<AcademicRecordDto> getGradesByYear(Long studentId, String academicYear) {
        return rmiClient.getGradesByYear(studentId, academicYear);
    }
}
