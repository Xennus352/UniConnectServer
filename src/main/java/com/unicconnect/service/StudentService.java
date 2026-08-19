package com.unicconnect.service;

import com.unicconnect.dto.request.CreateStudentUserRequest;
import com.unicconnect.dto.request.StudentRequest;
import com.unicconnect.dto.response.AttendanceResponse;
import com.unicconnect.dto.response.ResultDocumentResponse;
import com.unicconnect.dto.response.ScheduleResponse;
import com.unicconnect.dto.response.StudentResponse;
import com.unicconnect.entity.Course;
import com.unicconnect.entity.GenerationStatus;
import com.unicconnect.entity.RegistrationStatus;
import com.unicconnect.entity.Student;
import com.unicconnect.entity.TeachingAssignment;
import com.unicconnect.entity.TermStatus;
import com.unicconnect.entity.User;
import com.unicconnect.exception.DuplicateResourceException;
import com.unicconnect.exception.ResourceNotFoundException;
import com.unicconnect.exception.ValidationException;
import com.unicconnect.repository.*;
import com.unicconnect.util.SecurityUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class StudentService {

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final MajorRepository majorRepository;
    private final SemesterRepository semesterRepository;
    private final SectionRepository sectionRepository;
    private final AcademicTermRepository termRepository;
    private final AttendanceRepository attendanceRepository;
    private final ExamResultDocumentRepository resultDocumentRepository;
    private final SecurityUtil securityUtil;
    private final ClassScheduleRepository classScheduleRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public StudentService(StudentRepository studentRepository,
                          UserRepository userRepository,
                          MajorRepository majorRepository,
                          SemesterRepository semesterRepository,
                          SectionRepository sectionRepository,
                          AcademicTermRepository termRepository,
                          AttendanceRepository attendanceRepository,
                          ExamResultDocumentRepository resultDocumentRepository,
                          SecurityUtil securityUtil,
                          ClassScheduleRepository classScheduleRepository,
                          RoleRepository roleRepository,
                          PasswordEncoder passwordEncoder) {
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.majorRepository = majorRepository;
        this.semesterRepository = semesterRepository;
        this.sectionRepository = sectionRepository;
        this.termRepository = termRepository;
        this.attendanceRepository = attendanceRepository;
        this.resultDocumentRepository = resultDocumentRepository;
        this.securityUtil = securityUtil;
        this.classScheduleRepository = classScheduleRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<StudentResponse> getAll(UUID majorId, UUID semesterId, UUID sectionId, UUID termId) {
        List<Student> students;
        if (majorId != null) {
            students = studentRepository.findByMajor_MajorIdWithDetails(majorId);
        } else if (semesterId != null) {
            students = studentRepository.findBySemester_SemesterIdWithDetails(semesterId);
        } else if (sectionId != null) {
            students = studentRepository.findBySection_SectionIdWithDetails(sectionId);
        } else if (termId != null) {
            students = studentRepository.findByTerm_TermIdWithDetails(termId);
        } else {
            students = studentRepository.findAllWithDetails();
        }
        return students.stream().map(StudentService::toResponse).toList();
    }

    public StudentResponse getById(UUID studentId) {
        return toResponse(findStudent(studentId));
    }

    public List<ScheduleResponse> getSchedules(UUID studentId, UUID termId) {
        Student student = findStudent(studentId);
        if (student.getSection() == null) {
            return List.of();
        }
        UUID effectiveTermId = termId;
        if (effectiveTermId == null) {
            effectiveTermId = termRepository.findByStatus(TermStatus.ACTIVE).stream()
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("No active academic term"))
                    .getTermId();
        }
        // A student sees only the officially PUBLISHED timetable of their own
        // semester and section. Sections are shared rows across semesters, so
        // the semester of the schedule's course narrows the result set.
        UUID studentSemesterId = student.getSemester() != null ? student.getSemester().getSemesterId() : null;
        return classScheduleRepository.findByTermAndSectionWithDetails(effectiveTermId, student.getSection().getSectionId())
                .stream()
                .filter(s -> s.getGeneration().getStatus() == GenerationStatus.PUBLISHED)
                .filter(s -> {
                    if (studentSemesterId == null) return true;
                    TeachingAssignment ta = s.getTeachingAssignment();
                    Course co = ta != null ? ta.getCourse() : null;
                    return co != null && co.getSemester() != null
                            && studentSemesterId.equals(co.getSemester().getSemesterId());
                })
                .map(ClassScheduleService::toResponse)
                .toList();
    }

    @Transactional
    public StudentResponse create(StudentRequest request) {
        if (studentRepository.existsByRollNo(request.rollNo())) {
            throw new DuplicateResourceException("Roll number already exists: " + request.rollNo());
        }
        if (studentRepository.existsByUser_UserId(request.userId())) {
            throw new DuplicateResourceException("A student profile already exists for this user");
        }
        Student student = new Student();
        apply(student, request);
        return toResponse(studentRepository.save(student));
    }

    @Transactional
    public StudentResponse createWithUser(CreateStudentUserRequest request) {
        if (userRepository.existsByEmail(request.email().toLowerCase())) {
            throw new DuplicateResourceException("Email is already in use");
        }
        if (studentRepository.existsByRollNo(request.rollNo())) {
            throw new DuplicateResourceException("Roll number already exists: " + request.rollNo());
        }

        User user = new User();
        user.setEmail(request.email().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(roleRepository.findByRoleName("STUDENT")
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: STUDENT")));
        user.setActive(request.isActive() == null || request.isActive());
        user.setRegistrationStatus(RegistrationStatus.APPROVED);
        user = userRepository.save(user);

        Student student = new Student();
        student.setUser(user);
        student.setMajor(majorRepository.findById(request.majorId())
                .orElseThrow(() -> new ResourceNotFoundException("Major not found")));
        student.setSemester(request.semesterId() != null
                ? semesterRepository.findById(request.semesterId())
                        .orElseThrow(() -> new ResourceNotFoundException("Semester not found")) : null);
        student.setSection(request.sectionId() != null
                ? sectionRepository.findById(request.sectionId())
                        .orElseThrow(() -> new ResourceNotFoundException("Section not found")) : null);
        student.setTerm(request.termId() != null
                ? termRepository.findById(request.termId())
                        .orElseThrow(() -> new ResourceNotFoundException("Academic term not found")) : null);
        student.setRollNo(request.rollNo());
        student.setStudentName(request.studentName());
        student.setPhoneNo(request.phoneNo());
        student.setAddress(request.address());
        student.setBatchYear(request.batchYear());
        return toResponse(studentRepository.save(student));
    }

    @Transactional
    public StudentResponse update(UUID studentId, StudentRequest request) {
        Student student = findStudent(studentId);
        if (!student.getRollNo().equals(request.rollNo()) && studentRepository.existsByRollNo(request.rollNo())) {
            throw new DuplicateResourceException("Roll number already exists: " + request.rollNo());
        }
        apply(student, request);
        return toResponse(studentRepository.save(student));
    }

    @Transactional
    public void delete(UUID studentId) {
        findStudent(studentId);
        studentRepository.deleteById(studentId);
    }

    public List<AttendanceResponse> getAttendance(UUID studentId) {
        findStudent(studentId);
        return attendanceRepository.findByStudent_StudentId(studentId).stream()
                .map(AttendanceService::toResponse).toList();
    }

    public List<ResultDocumentResponse> getResults(UUID studentId) {
        Student student = findStudent(studentId);
        verifyStudentAccess(student);
        return resultDocumentRepository.findByStudent_StudentId(studentId).stream()
                .map(ResultDocumentService::toResponse).toList();
    }

    public ResultDocumentResponse getResult(UUID studentId, UUID documentId) {
        Student student = findStudent(studentId);
        verifyStudentAccess(student);
        return ResultDocumentService.toResponse(
                resultDocumentRepository.findById(documentId)
                        .orElseThrow(() -> new ResourceNotFoundException("Result document not found")));
    }

    private void verifyStudentAccess(Student student) {
        if (securityUtil.isAdmin() || securityUtil.isStaff()) {
            return;
        }
        UUID currentUserId = securityUtil.currentUserId();
        if (!student.getUser().getUserId().equals(currentUserId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You are not authorized to view this student's results");
        }
    }

    private void apply(Student student, StudentRequest request) {
        student.setUser(userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found")));
        student.setMajor(majorRepository.findById(request.majorId())
                .orElseThrow(() -> new ResourceNotFoundException("Major not found")));
        student.setSemester(request.semesterId() != null
                ? semesterRepository.findById(request.semesterId())
                        .orElseThrow(() -> new ResourceNotFoundException("Semester not found")) : null);
        student.setSection(request.sectionId() != null
                ? sectionRepository.findById(request.sectionId())
                        .orElseThrow(() -> new ResourceNotFoundException("Section not found")) : null);
        student.setTerm(request.termId() != null
                ? termRepository.findById(request.termId())
                        .orElseThrow(() -> new ResourceNotFoundException("Academic term not found")) : null);
        student.setRollNo(request.rollNo());
        student.setStudentName(request.studentName());
        student.setPhoneNo(request.phoneNo());
        student.setAddress(request.address());
        student.setBatchYear(request.batchYear());
    }

    public Student findStudent(UUID studentId) {
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));
    }

    static StudentResponse toResponse(Student student) {
        return new StudentResponse(
                student.getStudentId(), student.getUser().getUserId(), student.getUser().getEmail(),
                student.getMajor().getMajorId(), student.getMajor().getMajorCode(),
                student.getSemester() != null ? student.getSemester().getSemesterId() : null,
                student.getSemester() != null ? student.getSemester().getSemesterNo() : null,
                student.getSection() != null ? student.getSection().getSectionId() : null,
                student.getSection() != null ? student.getSection().getSectionName() : null,
                student.getTerm() != null ? student.getTerm().getTermId() : null,
                student.getTerm() != null ? student.getTerm().getAcademicYear() : null,
                student.getRollNo(), student.getStudentName(), student.getPhoneNo(),
                student.getAddress(), student.getBatchYear(), student.getCreatedAt());
    }
}