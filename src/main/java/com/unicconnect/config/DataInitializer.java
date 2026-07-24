package com.unicconnect.config;

import com.unicconnect.model.*;
import com.unicconnect.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final DepartmentHeadRepository departmentHeadRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final AcademicRecordRepository academicRecordRepository;
    private final AttendanceSummaryRepository attendanceSummaryRepository;
    private final FinancialRecordRepository financialRecordRepository;
    private final DepartmentMeetingRepository departmentMeetingRepository;
    private final ProjectSupervisionRepository projectSupervisionRepository;

    public DataInitializer(DepartmentRepository departmentRepository,
                           UserRepository userRepository,
                           DepartmentHeadRepository departmentHeadRepository,
                           StudentProfileRepository studentProfileRepository,
                           AcademicRecordRepository academicRecordRepository,
                           AttendanceSummaryRepository attendanceSummaryRepository,
                           FinancialRecordRepository financialRecordRepository,
                           DepartmentMeetingRepository departmentMeetingRepository,
                           ProjectSupervisionRepository projectSupervisionRepository) {
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.departmentHeadRepository = departmentHeadRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.academicRecordRepository = academicRecordRepository;
        this.attendanceSummaryRepository = attendanceSummaryRepository;
        this.financialRecordRepository = financialRecordRepository;
        this.departmentMeetingRepository = departmentMeetingRepository;
        this.projectSupervisionRepository = projectSupervisionRepository;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("Data already exists, skipping initialization");
            return;
        }

        log.info("Initializing sample data...");

        Department cs = departmentRepository.save(new Department("Computer Science", "CS"));
        Department ee = departmentRepository.save(new Department("Electrical Engineering", "EE"));
        Department me = departmentRepository.save(new Department("Mechanical Engineering", "ME"));

        User teacher1 = userRepository.save(createUser("dr.smith@university.edu", "hashed_pass_1", "Dr. John Smith", UserRole.TEACHER, cs));
        User teacher2 = userRepository.save(createUser("dr.jane@university.edu", "hashed_pass_2", "Dr. Jane Doe", UserRole.TEACHER, ee));
        User student1 = userRepository.save(createUser("alice@university.edu", "hashed_pass_3", "Alice Johnson", UserRole.STUDENT, cs));
        User student2 = userRepository.save(createUser("bob@university.edu", "hashed_pass_4", "Bob Williams", UserRole.STUDENT, cs));
        User student3 = userRepository.save(createUser("charlie@university.edu", "hashed_pass_5", "Charlie Brown", UserRole.STUDENT, ee));
        User admin = userRepository.save(createUser("admin@university.edu", "hashed_pass_6", "Admin User", UserRole.SYSTEM_ADMIN, null));
        User accountant = userRepository.save(createUser("finance@university.edu", "hashed_pass_7", "Finance Officer", UserRole.FINANCE_ACCOUNTANT, null));
        User rector = userRepository.save(createUser("rector@university.edu", "hashed_pass_8", "Prof. Rector", UserRole.RECTOR_PRO_RECTOR, null));

        departmentHeadRepository.save(createDepartmentHead(cs, teacher1));
        departmentHeadRepository.save(createDepartmentHead(ee, teacher2));

        studentProfileRepository.save(createStudentProfile(student1, "STU001", 2023, "CS Third Year", "A"));
        studentProfileRepository.save(createStudentProfile(student2, "STU002", 2023, "CS Third Year", "A"));
        studentProfileRepository.save(createStudentProfile(student3, "STU003", 2024, "EE Second Year", "B"));

        academicRecordRepository.save(createAcademicRecord(student1, "CS301", "Data Structures", "2024/2025", "A", new BigDecimal("88.50")));
        academicRecordRepository.save(createAcademicRecord(student1, "CS302", "Algorithms", "2024/2025", "B+", new BigDecimal("78.00")));
        academicRecordRepository.save(createAcademicRecord(student2, "CS301", "Data Structures", "2024/2025", "B", new BigDecimal("72.00")));

        attendanceSummaryRepository.save(createAttendanceSummary(student1, "CS301", 30, 28));
        attendanceSummaryRepository.save(createAttendanceSummary(student1, "CS302", 30, 22));
        attendanceSummaryRepository.save(createAttendanceSummary(student2, "CS301", 30, 15));

        financialRecordRepository.save(createFinancialRecord(student1, FinancialType.TUITION_FEE, new BigDecimal("5000.00"), FinancialStatus.PAID, "Tuition fee 2024/2025", accountant));
        financialRecordRepository.save(createFinancialRecord(teacher1, FinancialType.SALARY, new BigDecimal("75000.00"), FinancialStatus.APPROVED, "Monthly salary", accountant));
        financialRecordRepository.save(createFinancialRecord(student2, FinancialType.SCHOLARSHIP, new BigDecimal("2000.00"), FinancialStatus.PENDING, "Merit scholarship", null));

        departmentMeetingRepository.save(createDepartmentMeeting(cs, "CS Department Meeting", LocalDateTime.now().plusDays(7), "Discuss curriculum changes", teacher1));
        departmentMeetingRepository.save(createDepartmentMeeting(ee, "EE Lab Planning", LocalDateTime.now().plusDays(14), "Plan new lab equipment", teacher2));

        projectSupervisionRepository.save(createProjectSupervision(teacher1, "AI Chatbot for Student Services", cs));
        projectSupervisionRepository.save(createProjectSupervision(teacher2, "Smart Grid Simulation", ee));

        log.info("Sample data initialized successfully");

        log.info("=== DATA SUMMARY ===");
        log.info("Departments: {}", departmentRepository.count());
        log.info("Users: {}", userRepository.count());
        log.info("Department Heads: {}", departmentHeadRepository.count());
        log.info("Student Profiles: {}", studentProfileRepository.count());
        log.info("Academic Records: {}", academicRecordRepository.count());
        log.info("Attendance Summaries: {}", attendanceSummaryRepository.count());
        log.info("Financial Records: {}", financialRecordRepository.count());
        log.info("Department Meetings: {}", departmentMeetingRepository.count());
        log.info("Project Supervisions: {}", projectSupervisionRepository.count());
        log.info("===================");
    }

    private User createUser(String email, String passwordHash, String fullName, UserRole role, Department department) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setFullName(fullName);
        user.setRole(role);
        user.setDepartment(department);
        user.setRegistrationStatus(RegistrationStatus.APPROVED);
        user.setIsActive(true);
        return user;
    }

    private DepartmentHead createDepartmentHead(Department department, User teacher) {
        DepartmentHead dh = new DepartmentHead();
        dh.setDepartment(department);
        dh.setTeacher(teacher);
        dh.setIsActive(true);
        return dh;
    }

    private StudentProfile createStudentProfile(User user, String studentId, int batchYear, String academicYear, String section) {
        StudentProfile sp = new StudentProfile();
        sp.setUser(user);
        sp.setStudentIdNumber(studentId);
        sp.setBatchYear(batchYear);
        sp.setAcademicYear(academicYear);
        sp.setSection(section);
        return sp;
    }

    private AcademicRecord createAcademicRecord(User student, String subjectCode, String subjectName, String academicYear, String gradeLetter, BigDecimal marks) {
        AcademicRecord ar = new AcademicRecord();
        ar.setStudent(student);
        ar.setSubjectCode(subjectCode);
        ar.setSubjectName(subjectName);
        ar.setAcademicYear(academicYear);
        ar.setGradeLetter(gradeLetter);
        ar.setMarks(marks);
        return ar;
    }

    private AttendanceSummary createAttendanceSummary(User student, String subjectCode, int total, int attended) {
        AttendanceSummary as = new AttendanceSummary();
        as.setStudent(student);
        as.setSubjectCode(subjectCode);
        as.setTotalClasses(total);
        as.setAttendedClasses(attended);
        return as;
    }

    private FinancialRecord createFinancialRecord(User user, FinancialType type, BigDecimal amount, FinancialStatus status, String description, User processedBy) {
        FinancialRecord fr = new FinancialRecord();
        fr.setUser(user);
        fr.setType(type);
        fr.setAmount(amount);
        fr.setStatus(status);
        fr.setDescription(description);
        fr.setProcessedBy(processedBy);
        return fr;
    }

    private DepartmentMeeting createDepartmentMeeting(Department department, String title, LocalDateTime scheduledAt, String summary, User createdBy) {
        DepartmentMeeting dm = new DepartmentMeeting();
        dm.setDepartment(department);
        dm.setTitle(title);
        dm.setScheduledAt(scheduledAt);
        dm.setSummaryNotes(summary);
        dm.setCreatedBy(createdBy);
        return dm;
    }

    private ProjectSupervision createProjectSupervision(User teacher, String projectTitle, Department department) {
        ProjectSupervision ps = new ProjectSupervision();
        ps.setTeacher(teacher);
        ps.setProjectTitle(projectTitle);
        ps.setDepartment(department);
        return ps;
    }
}