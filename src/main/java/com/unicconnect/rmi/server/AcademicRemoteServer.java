package com.unicconnect.rmi.server;

import com.unicconnect.repository.AcademicRecordRepository;
import com.unicconnect.rmi.dto.AcademicRecordDto;
import com.unicconnect.rmi.remote.AcademicRemote;
import org.springframework.stereotype.Component;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class AcademicRemoteServer extends UnicastRemoteObject implements AcademicRemote {

    private final AcademicRecordRepository repository;

    public AcademicRemoteServer(AcademicRecordRepository repository) throws RemoteException {
        this.repository = repository;
    }

    @Override
    public List<AcademicRecordDto> getGrades(Long studentId) {
        return repository.findByStudentId(studentId).stream()
                .map(r -> {
                    AcademicRecordDto dto = new AcademicRecordDto();
                    dto.setId(r.getId());
                    dto.setStudentId(r.getStudent().getId());
                    dto.setStudentName(r.getStudent().getFullName());
                    dto.setSubjectCode(r.getSubjectCode());
                    dto.setSubjectName(r.getSubjectName());
                    dto.setAcademicYear(r.getAcademicYear());
                    dto.setGradeLetter(r.getGradeLetter());
                    dto.setMarks(r.getMarks());
                    dto.setPublishedAt(r.getPublishedAt());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<AcademicRecordDto> getGradesByYear(Long studentId, String academicYear) {
        return repository.findByStudentIdAndAcademicYear(studentId, academicYear).stream()
                .map(r -> {
                    AcademicRecordDto dto = new AcademicRecordDto();
                    dto.setId(r.getId());
                    dto.setStudentId(r.getStudent().getId());
                    dto.setStudentName(r.getStudent().getFullName());
                    dto.setSubjectCode(r.getSubjectCode());
                    dto.setSubjectName(r.getSubjectName());
                    dto.setAcademicYear(r.getAcademicYear());
                    dto.setGradeLetter(r.getGradeLetter());
                    dto.setMarks(r.getMarks());
                    dto.setPublishedAt(r.getPublishedAt());
                    return dto;
                })
                .collect(Collectors.toList());
    }
}
