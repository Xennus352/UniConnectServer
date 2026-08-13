package com.unicconnect.service;

import com.unicconnect.dto.request.CourseRequest;
import com.unicconnect.dto.request.MeetingRequirementRequest;
import com.unicconnect.dto.response.CourseResponse;
import com.unicconnect.dto.response.MeetingRequirementResponse;
import com.unicconnect.entity.Course;
import com.unicconnect.entity.CourseMeetingRequirement;
import com.unicconnect.exception.DuplicateResourceException;
import com.unicconnect.exception.ResourceNotFoundException;
import com.unicconnect.exception.ValidationException;
import com.unicconnect.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CourseService {

    private final CourseRepository courseRepository;
    private final OrganizationalUnitRepository unitRepository;
    private final MajorRepository majorRepository;
    private final SemesterRepository semesterRepository;
    private final CourseMeetingRequirementRepository requirementRepository;

    public CourseService(CourseRepository courseRepository,
                         OrganizationalUnitRepository unitRepository,
                         MajorRepository majorRepository,
                         SemesterRepository semesterRepository,
                         CourseMeetingRequirementRepository requirementRepository) {
        this.courseRepository = courseRepository;
        this.unitRepository = unitRepository;
        this.majorRepository = majorRepository;
        this.semesterRepository = semesterRepository;
        this.requirementRepository = requirementRepository;
    }

    public List<CourseResponse> getAll(UUID majorId, UUID semesterId, UUID unitId) {
        List<Course> courses;
        if (majorId != null) {
            courses = courseRepository.findByMajor_MajorId(majorId);
        } else if (semesterId != null) {
            courses = courseRepository.findBySemester_SemesterId(semesterId);
        } else if (unitId != null) {
            courses = courseRepository.findByUnit_UnitId(unitId);
        } else {
            courses = courseRepository.findAll();
        }
        return courses.stream().map(CourseService::toResponse).toList();
    }

    public CourseResponse getById(UUID courseId) {
        return toResponse(findCourse(courseId));
    }

    @Transactional
    public CourseResponse create(CourseRequest request) {
        if (courseRepository.existsByCourseCode(request.courseCode())) {
            throw new DuplicateResourceException("Course code already exists: " + request.courseCode());
        }
        Course course = new Course();
        apply(course, request);
        return toResponse(courseRepository.save(course));
    }

    @Transactional
    public CourseResponse update(UUID courseId, CourseRequest request) {
        Course course = findCourse(courseId);
        if (!course.getCourseCode().equals(request.courseCode())
                && courseRepository.existsByCourseCode(request.courseCode())) {
            throw new DuplicateResourceException("Course code already exists: " + request.courseCode());
        }
        apply(course, request);
        return toResponse(courseRepository.save(course));
    }

    @Transactional
    public void delete(UUID courseId) {
        findCourse(courseId);
        courseRepository.deleteById(courseId);
    }

    // ---------- Meeting requirements ----------

    public List<MeetingRequirementResponse> getMeetingRequirements(UUID courseId) {
        findCourse(courseId);
        return requirementRepository.findByCourse_CourseId(courseId).stream()
                .map(CourseMeetingRequirementService::toResponse).toList();
    }

    @Transactional
    public MeetingRequirementResponse addMeetingRequirement(UUID courseId, MeetingRequirementRequest request) {
        Course course = findCourse(courseId);
        return CourseMeetingRequirementService.createInternal(requirementRepository, course, request);
    }

    @Transactional
    public MeetingRequirementResponse updateMeetingRequirement(UUID courseId, UUID requirementId,
                                                               MeetingRequirementRequest request) {
        findCourse(courseId);
        return CourseMeetingRequirementService.updateInternal(
                requirementRepository, courseId, requirementId, request);
    }

    @Transactional
    public void deleteMeetingRequirement(UUID courseId, UUID requirementId) {
        CourseMeetingRequirement requirement = requirementRepository.findById(requirementId)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting requirement not found"));
        if (!requirement.getCourse().getCourseId().equals(courseId)) {
            throw new ResourceNotFoundException("Meeting requirement not found for this course");
        }
        requirementRepository.delete(requirement);
    }

    private void apply(Course course, CourseRequest request) {
        course.setUnit(unitRepository.findById(request.unitId())
                .orElseThrow(() -> new ResourceNotFoundException("Organizational unit not found")));
        course.setCourseCode(request.courseCode());
        course.setCourseName(request.courseName());
        course.setCreditUnit(request.creditUnit());
        course.setMajor(request.majorId() != null
                ? majorRepository.findById(request.majorId())
                        .orElseThrow(() -> new ResourceNotFoundException("Major not found")) : null);
        course.setSemester(request.semesterId() != null
                ? semesterRepository.findById(request.semesterId())
                        .orElseThrow(() -> new ResourceNotFoundException("Semester not found")) : null);
        course.setRequired(request.isRequired() != null && request.isRequired());
        course.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : 0);
    }

    public Course findCourse(UUID courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
    }

    static CourseResponse toResponse(Course course) {
        return new CourseResponse(
                course.getCourseId(),
                course.getUnit().getUnitId(), course.getUnit().getUnitCode(),
                course.getCourseCode(), course.getCourseName(), course.getCreditUnit(),
                course.getMajor() != null ? course.getMajor().getMajorId() : null,
                course.getMajor() != null ? course.getMajor().getMajorCode() : null,
                course.getSemester() != null ? course.getSemester().getSemesterId() : null,
                course.getSemester() != null ? course.getSemester().getSemesterNo() : null,
                course.isRequired(), course.getDisplayOrder());
    }
}