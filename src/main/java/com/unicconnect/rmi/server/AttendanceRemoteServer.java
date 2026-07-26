package com.unicconnect.rmi.server;

import com.unicconnect.repository.AttendanceSummaryRepository;
import com.unicconnect.rmi.dto.AttendanceSummaryDto;
import com.unicconnect.rmi.remote.AttendanceRemote;
import org.springframework.stereotype.Component;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class AttendanceRemoteServer extends UnicastRemoteObject implements AttendanceRemote {

    private final AttendanceSummaryRepository repository;

    public AttendanceRemoteServer(AttendanceSummaryRepository repository) throws RemoteException {
        this.repository = repository;
    }

    @Override
    public List<AttendanceSummaryDto> getAttendance(Long studentId) {
        return repository.findByStudentId(studentId).stream()
                .map(a -> {
                    AttendanceSummaryDto dto = new AttendanceSummaryDto();
                    dto.setId(a.getId());
                    dto.setStudentId(a.getStudent().getId());
                    dto.setStudentName(a.getStudent().getFullName());
                    dto.setSubjectCode(a.getSubjectCode());
                    dto.setTotalClasses(a.getTotalClasses());
                    dto.setAttendedClasses(a.getAttendedClasses());
                    dto.setPercentage(a.getPercentage());
                    dto.setIsBelow75(a.getIsBelow75());
                    dto.setUpdatedAt(a.getUpdatedAt());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Override
    public AttendanceSummaryDto calculateAttendance(Long studentId, String subjectCode) {
        return repository.findByStudentIdAndSubjectCode(studentId, subjectCode)
                .map(a -> {
                    AttendanceSummaryDto dto = new AttendanceSummaryDto();
                    dto.setId(a.getId());
                    dto.setStudentId(a.getStudent().getId());
                    dto.setStudentName(a.getStudent().getFullName());
                    dto.setSubjectCode(a.getSubjectCode());
                    dto.setTotalClasses(a.getTotalClasses());
                    dto.setAttendedClasses(a.getAttendedClasses());
                    dto.setPercentage(a.getPercentage());
                    dto.setIsBelow75(a.getIsBelow75());
                    dto.setUpdatedAt(a.getUpdatedAt());
                    return dto;
                })
                .orElse(null);
    }

    @Override
    public List<AttendanceSummaryDto> getStudentsBelow75() {
        return repository.findByIsBelow75True().stream()
                .map(a -> {
                    AttendanceSummaryDto dto = new AttendanceSummaryDto();
                    dto.setId(a.getId());
                    dto.setStudentId(a.getStudent().getId());
                    dto.setStudentName(a.getStudent().getFullName());
                    dto.setSubjectCode(a.getSubjectCode());
                    dto.setTotalClasses(a.getTotalClasses());
                    dto.setAttendedClasses(a.getAttendedClasses());
                    dto.setPercentage(a.getPercentage());
                    dto.setIsBelow75(a.getIsBelow75());
                    dto.setUpdatedAt(a.getUpdatedAt());
                    return dto;
                })
                .collect(Collectors.toList());
    }
}
