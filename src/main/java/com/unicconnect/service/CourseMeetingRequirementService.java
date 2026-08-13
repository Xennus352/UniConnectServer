package com.unicconnect.service;

import com.unicconnect.dto.request.MeetingRequirementRequest;
import com.unicconnect.dto.response.MeetingRequirementResponse;
import com.unicconnect.entity.Course;
import com.unicconnect.entity.CourseMeetingRequirement;
import com.unicconnect.exception.DuplicateResourceException;
import com.unicconnect.exception.ResourceNotFoundException;
import com.unicconnect.exception.ValidationException;
import com.unicconnect.repository.CourseMeetingRequirementRepository;
import com.unicconnect.repository.CourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CourseMeetingRequirementService {

    private final CourseMeetingRequirementRepository requirementRepository;
    private final CourseRepository courseRepository;

    public CourseMeetingRequirementService(CourseMeetingRequirementRepository requirementRepository,
                                           CourseRepository courseRepository) {
        this.requirementRepository = requirementRepository;
        this.courseRepository = courseRepository;
    }

    public List<MeetingRequirementResponse> getAll() {
        return requirementRepository.findAll().stream()
                .map(CourseMeetingRequirementService::toResponse).toList();
    }

    public MeetingRequirementResponse getById(UUID requirementId) {
        return toResponse(requirementRepository.findById(requirementId)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting requirement not found")));
    }

    @Transactional
    public MeetingRequirementResponse create(MeetingRequirementRequest request) {
        if (request.courseId() == null) {
            throw new ValidationException("courseId is required");
        }
        Course course = courseRepository.findById(request.courseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
        return createInternal(requirementRepository, course, request);
    }

    @Transactional
    public MeetingRequirementResponse update(UUID requirementId, MeetingRequirementRequest request) {
        CourseMeetingRequirement requirement = requirementRepository.findById(requirementId)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting requirement not found"));
        return updateInternal(requirementRepository, requirement.getCourse().getCourseId(), requirementId, request);
    }

    @Transactional
    public void delete(UUID requirementId) {
        CourseMeetingRequirement requirement = requirementRepository.findById(requirementId)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting requirement not found"));
        requirementRepository.delete(requirement);
    }

    static MeetingRequirementResponse createInternal(CourseMeetingRequirementRepository repository,
                                                     Course course, MeetingRequirementRequest request) {
        validate(request);
        if (repository.existsByCourse_CourseIdAndMeetingType(course.getCourseId(), request.meetingType())) {
            throw new DuplicateResourceException(
                    "Meeting requirement already exists for course " + course.getCourseCode()
                            + " and type " + request.meetingType());
        }
        CourseMeetingRequirement requirement = new CourseMeetingRequirement();
        requirement.setCourse(course);
        requirement.setMeetingType(request.meetingType());
        requirement.setSessionsPerWeek(request.sessionsPerWeek());
        requirement.setPeriodsPerSession(request.periodsPerSession());
        return toResponse(repository.save(requirement));
    }

    static MeetingRequirementResponse updateInternal(CourseMeetingRequirementRepository repository,
                                                     UUID courseId, UUID requirementId,
                                                     MeetingRequirementRequest request) {
        validate(request);
        CourseMeetingRequirement requirement = repository.findById(requirementId)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting requirement not found"));
        if (!requirement.getCourse().getCourseId().equals(courseId)) {
            throw new ResourceNotFoundException("Meeting requirement not found for this course");
        }
        if (requirement.getMeetingType() != request.meetingType()) {
            if (repository.existsByCourse_CourseIdAndMeetingType(courseId, request.meetingType())) {
                throw new DuplicateResourceException(
                        "Meeting requirement already exists for this course and type " + request.meetingType());
            }
            requirement.setMeetingType(request.meetingType());
        }
        requirement.setSessionsPerWeek(request.sessionsPerWeek());
        requirement.setPeriodsPerSession(request.periodsPerSession());
        return toResponse(repository.save(requirement));
    }

    private static void validate(MeetingRequirementRequest request) {
        if (request.sessionsPerWeek() == null || request.sessionsPerWeek() <= 0) {
            throw new ValidationException("sessionsPerWeek must be greater than 0");
        }
        if (request.periodsPerSession() == null || request.periodsPerSession() <= 0) {
            throw new ValidationException("periodsPerSession must be greater than 0");
        }
    }

    static MeetingRequirementResponse toResponse(CourseMeetingRequirement requirement) {
        return new MeetingRequirementResponse(
                requirement.getRequirementId(),
                requirement.getCourse().getCourseId(),
                requirement.getCourse().getCourseCode(),
                requirement.getMeetingType(),
                requirement.getSessionsPerWeek(),
                requirement.getPeriodsPerSession());
    }
}