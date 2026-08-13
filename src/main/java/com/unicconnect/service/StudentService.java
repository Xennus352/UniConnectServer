package com.unicconnect.service;

import com.unicconnect.dto.request.StudentRequest;
import com.unicconnect.dto.response.AttendanceResponse;
import com.unicconnect.dto.response.ResultDocumentResponse;
import com.unicconnect.dto.response.StudentResponse;
import com.unicconnect.entity.Student;
import com.unicconnect.exception.DuplicateResourceException;
import com.unicconnect.exception.ResourceNotFoundException;
import com.unicconnect.exception.ValidationException;
import com.unicconnect.repository.*;
import com.unicconnect.util.SecurityUtil;
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

    public StudentService(StudentRepository studentRepository,
                          UserRepository userRepository,
                          MajorRepository majorRepository,
                          SemesterRepository semesterRepository,
                          SectionRepository sectionRepository,
                          AcademicTermRepository termRepository,
                          AttendanceRepository attendanceRepository,
                          ExamResultDocumentRepository resultDocumentRepository,
                          SecurityUtil securityUtil) {
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.majorRepository = majorRepository;
        this.semesterRepository = semesterRepository;
        this.sectionRepository = sectionRepository;
        this.termRepository = termRepository;
        this.attendanceRepository = attendanceRepository;
        this.resultDocumentRepository = resultDocumentRepository;
        this.securityUtil = securityUtil;
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
        student.setBirthYear(request.birthYear());
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
                student.getAddress(), student.getBirthYear(), student.getCreatedAt());
    }
}