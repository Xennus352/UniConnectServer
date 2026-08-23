package com.unicconnect.service;

import com.unicconnect.entity.Course;
import com.unicconnect.entity.Major;
import com.unicconnect.entity.Section;
import com.unicconnect.entity.Student;
import com.unicconnect.exception.BusinessRuleException;
import com.unicconnect.repository.MajorRepository;
import com.unicconnect.repository.StudentRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class CurriculumEligibilityService {

    public static final String SHARED_MAJOR_CODE = "CST";

    private static final Set<String> SHARED_AUDIENCE = Set.of("CS", "CST", "CT");

    private final StudentRepository studentRepository;
    private final MajorRepository majorRepository;

    public CurriculumEligibilityService(StudentRepository studentRepository,
                                        MajorRepository majorRepository) {
        this.studentRepository = studentRepository;
        this.majorRepository = majorRepository;
    }

    public String ownerMajorCode(Course course) {
        if (course.getMajor() == null || course.getMajor().getMajorCode() == null) {
            throw new BusinessRuleException("Course " + course.getCourseCode()
                    + " has no major assigned; course ownership is mandatory");
        }
        return course.getMajor().getMajorCode();
    }

    public Set<String> eligibleMajorCodes(Course course) {
        String owner = ownerMajorCode(course);
        if (SHARED_MAJOR_CODE.equals(owner)) {
            return SHARED_AUDIENCE;
        }
        return Set.of(owner);
    }

    /**
     * Cohort majors represented by a delivery section. Derived first from the
     * majors of enrolled students (sections may be mixed-major); when a section
     * has no enrolled students, the section name matching a major_code is used
     * as fallback.
     */
    public Set<String> cohortMajorCodes(Section section) {
        Set<String> cohort = new LinkedHashSet<>();
        for (Student student : studentRepository.findBySection_SectionId(section.getSectionId())) {
            Major major = student.getMajor();
            if (major != null && major.getMajorCode() != null) {
                cohort.add(major.getMajorCode());
            }
        }
        if (cohort.isEmpty() && section.getSectionName() != null) {
            String name = section.getSectionName().trim();
            for (Major major : majorRepository.findAll()) {
                if (major.getMajorCode() != null && major.getMajorCode().equalsIgnoreCase(name)) {
                    cohort.add(major.getMajorCode());
                }
            }
        }
        return cohort;
    }

    public boolean isEligibleFor(Course course, String studentMajorCode, UUID semesterId) {
        if (studentMajorCode == null || semesterId == null) {
            return false;
        }
        if (course.getSemester() == null
                || !semesterId.equals(course.getSemester().getSemesterId())) {
            return false;
        }
        return eligibleMajorCodes(course).contains(studentMajorCode);
    }

    public boolean isEligibleForSection(Course course, Section section, UUID semesterId) {
        if (course.getSemester() == null
                || !semesterId.equals(course.getSemester().getSemesterId())) {
            return false;
        }
        Set<String> eligible = eligibleMajorCodes(course);
        return cohortMajorCodes(section).stream().anyMatch(eligible::contains);
    }

    /**
     * A dedicated-cohort section serves exactly one major (e.g. a "CT" section
     * with no enrolled students yet falls back to its name). Curriculum
     * auto-binding and completeness enforcement apply to these sections: a
     * single-major section is the mandatory delivery vehicle for its major's
     * required courses. Mixed-major sections are HOD-managed - their students
     * receive major-specific courses through the dedicated sections.
     */
    public boolean isDedicatedCohortSection(Section section) {
        return cohortMajorCodes(section).size() == 1;
    }

    public boolean isVisibleToStudent(Course course, Student student) {
        if (course.getMajor() == null) {
            return false;
        }
        if (student == null || student.getMajor() == null
                || student.getMajor().getMajorCode() == null) {
            return false;
        }
        if (!eligibleMajorCodes(course).contains(student.getMajor().getMajorCode())) {
            return false;
        }
        if (student.getSemester() != null && course.getSemester() != null) {
            return student.getSemester().getSemesterId()
                    .equals(course.getSemester().getSemesterId());
        }
        return true;
    }

    public boolean canShareDelivery(Course course, Collection<String> majorCodes, UUID semesterId) {
        if (majorCodes == null || majorCodes.isEmpty()) {
            return false;
        }
        if (course.getSemester() == null
                || !semesterId.equals(course.getSemester().getSemesterId())) {
            return false;
        }
        Set<String> eligible = eligibleMajorCodes(course);
        return majorCodes.stream().allMatch(eligible::contains);
    }

    public boolean isRequiredCurriculumCourse(Course course) {
        return course.isRequired();
    }
}
