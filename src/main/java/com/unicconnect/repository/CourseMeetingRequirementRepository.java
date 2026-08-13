package com.unicconnect.repository;

import com.unicconnect.entity.CourseMeetingRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseMeetingRequirementRepository extends JpaRepository<CourseMeetingRequirement, UUID> {
    List<CourseMeetingRequirement> findByCourse_CourseId(UUID courseId);
    Optional<CourseMeetingRequirement> findByCourse_CourseIdAndMeetingType(UUID courseId, com.unicconnect.entity.MeetingType meetingType);
    boolean existsByCourse_CourseIdAndMeetingType(UUID courseId, com.unicconnect.entity.MeetingType meetingType);
}