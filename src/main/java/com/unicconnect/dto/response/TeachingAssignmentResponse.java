package com.unicconnect.dto.response;

import com.unicconnect.entity.AssignmentStatus;

import java.time.Instant;
import java.util.UUID;

public record TeachingAssignmentResponse(
        UUID assignmentId,
        UUID courseId,
        String courseCode,
        String courseName,
        UUID staffId,
        String staffNo,
        String staffName,
        UUID sectionId,
        String sectionName,
        UUID termId,
        Integer academicYear,
        AssignmentStatus assignmentStatus,
        Instant assignedAt,
        UUID assignedByStaffId
) {}