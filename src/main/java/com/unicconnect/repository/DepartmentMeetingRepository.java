package com.unicconnect.repository;

import com.unicconnect.model.DepartmentMeeting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DepartmentMeetingRepository extends JpaRepository<DepartmentMeeting, Long> {
    List<DepartmentMeeting> findByDepartmentId(Long departmentId);
}