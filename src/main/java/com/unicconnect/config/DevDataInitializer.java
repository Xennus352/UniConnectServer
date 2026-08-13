package com.unicconnect.config;

import com.unicconnect.entity.*;
import com.unicconnect.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Component
@Profile("dev")
public class DevDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataInitializer.class);

    private static final String STAFF_PASSWORD = "ucstgo@2026";
    private static final String STUDENT_PASSWORD = "ucstgo@2026";
    private static final String ADMIN_EMAIL = "nyiminyan0099@gmail.com";
    private static final String ADMIN_PASSWORD = "@dmin123";

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final OrganizationalUnitRepository unitRepository;
    private final PositionRepository positionRepository;
    private final StaffRepository staffRepository;
    private final StaffPositionAssignmentRepository spaRepository;
    private final MajorRepository majorRepository;
    private final SemesterRepository semesterRepository;
    private final SectionRepository sectionRepository;
    private final AcademicTermRepository termRepository;
    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;

    public DevDataInitializer(RoleRepository roleRepository,
                              UserRepository userRepository,
                              OrganizationalUnitRepository unitRepository,
                              PositionRepository positionRepository,
                              StaffRepository staffRepository,
                              StaffPositionAssignmentRepository spaRepository,
                              MajorRepository majorRepository,
                              SemesterRepository semesterRepository,
                              SectionRepository sectionRepository,
                              AcademicTermRepository termRepository,
                              StudentRepository studentRepository,
                              PasswordEncoder passwordEncoder) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.unitRepository = unitRepository;
        this.positionRepository = positionRepository;
        this.staffRepository = staffRepository;
        this.spaRepository = spaRepository;
        this.majorRepository = majorRepository;
        this.semesterRepository = semesterRepository;
        this.sectionRepository = sectionRepository;
        this.termRepository = termRepository;
        this.studentRepository = studentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        log.info("=== Dev profile active: seeding development data ===");

        seedOrganizationalUnits();
        seedSemesters();
        seedSections();
        seedMajors();
        seedAcademicTerm();
        seedPositions();
        seedSystemAdmin();
        seedStaff();
        seedStudents();

        log.info("=== Dev data seeding complete ===");
    }

    // ── Organizational Units ───────────────────────────────────

    private void seedOrganizationalUnits() {
        String[][] units = {
            {"CS", "Faculty of Computer Science", "Faculty"},
            {"CST", "Faculty of Computer Systems and Technologies", "Faculty"},
            {"IS", "Faculty of Information Science", "Faculty"},
            {"ITSM", "Department of Information Technologies Support and Maintenance", "Department"},
            {"COMP", "Faculty of Computing", "Faculty"},
            {"NL", "Department of Natural Language", "Department"},
            {"NS", "Department of Natural Science", "Department"},
            {"ADM", "Department of Administration", "Department"},
            {"FIN", "Department of Finance", "Department"},
            {"SA", "Department of Student Affairs", "Department"}
        };
        for (String[] u : units) {
            if (!unitRepository.existsByUnitCode(u[0])) {
                OrganizationalUnit unit = new OrganizationalUnit();
                unit.setUnitCode(u[0]);
                unit.setUnitName(u[1]);
                unit.setUnitType(u[2]);
                unitRepository.save(unit);
            }
        }
        log.info("Organizational units seeded");
    }

    // ── Semesters ──────────────────────────────────────────────

    private void seedSemesters() {
        Set<Integer> existing = semesterRepository.findAll().stream()
                .map(Semester::getSemesterNo).collect(java.util.stream.Collectors.toSet());
        for (int i = 1; i <= 8; i++) {
            final int semNo = i;
            if (!existing.contains(semNo)) {
                Semester sem = new Semester();
                sem.setSemesterNo(semNo);
                semesterRepository.save(sem);
            }
        }
        log.info("Semesters seeded (1-8)");
    }

    // ── Sections ───────────────────────────────────────────────

    private void seedSections() {
        for (String name : List.of("A", "B", "C")) {
            if (sectionRepository.findAll().stream().noneMatch(s -> s.getSectionName().equals(name))) {
                Section section = new Section();
                section.setSectionName(name);
                sectionRepository.save(section);
            }
        }
        log.info("Sections seeded (A, B, C)");
    }

    // ── Majors ─────────────────────────────────────────────────

    private void seedMajors() {
        String csUnitCode = "CS";
        String[][] majors = {
            {"CS", "Computer Science"},
            {"CT", "Computer Technology"},
            {"CST", "Computer Systems and Technology"}
        };
        OrganizationalUnit csUnit = unitRepository.findByUnitCode(csUnitCode)
                .orElseThrow(() -> new RuntimeException("CS unit not found"));

        for (String[] m : majors) {
            List<Major> existing = majorRepository.findAll();
            if (existing.stream().noneMatch(maj -> maj.getMajorCode().equals(m[0]))) {
                Major major = new Major();
                major.setUnit(csUnit);
                major.setMajorCode(m[0]);
                major.setMajorName(m[1]);
                majorRepository.save(major);
            }
        }
        log.info("Majors seeded (CS, CT, CST)");
    }

    // ── Academic Term ──────────────────────────────────────────

    private void seedAcademicTerm() {
        if (termRepository.findByStatus(TermStatus.ACTIVE).isEmpty()) {
            AcademicTerm term = new AcademicTerm();
            term.setAcademicYear(2026);
            term.setStartDate(LocalDate.of(2026, 1, 1));
            term.setEndDate(LocalDate.of(2026, 12, 31));
            term.setStatus(TermStatus.ACTIVE);
            termRepository.save(term);
            log.info("Academic term 2026 created");
        } else {
            log.info("Active academic term already exists");
        }
    }

    // ── Positions ──────────────────────────────────────────────

    private void seedPositions() {
        String[][] positions = {
            {"LECTURER", "Lecturer"},
            {"HOD", "Head of Department"},
            {"STUDENT_AFFAIRS_OFFICER", "Student Affairs Officer"},
            {"FINANCE_OFFICER", "Finance Officer"},
            {"ADMINISTRATIVE_OFFICER", "Administrative Officer"},
            {"SENIOR_CLERK", "Senior Clerk"},
            {"JUNIOR_CLERK", "Junior Clerk"}
        };
        for (String[] p : positions) {
            if (!positionRepository.existsByPositionName(p[0])) {
                Position pos = new Position();
                pos.setPositionName(p[0]);
                pos.setDescription(p[1]);
                positionRepository.save(pos);
            }
        }
        log.info("Positions seeded");
    }

    // ── System Admin ───────────────────────────────────────────

    private void seedSystemAdmin() {
        if (userRepository.existsByEmail(ADMIN_EMAIL)) {
            log.info("System admin already exists: {}", ADMIN_EMAIL);
            return;
        }
        Role adminRole = roleRepository.findByRoleName("SYSTEM_ADMIN")
                .orElseThrow(() -> new RuntimeException("SYSTEM_ADMIN role not found"));

        User user = new User();
        user.setEmail(ADMIN_EMAIL);
        user.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
        user.setRole(adminRole);
        user.setActive(true);
        user.setRegistrationStatus(RegistrationStatus.APPROVED);
        userRepository.save(user);

        log.info("System admin created: {} (password hashed with BCrypt)", ADMIN_EMAIL);
    }

    // ── Staff ──────────────────────────────────────────────────

    private void seedStaff() {
        Role staffRole = roleRepository.findByRoleName("STAFF")
                .orElseThrow(() -> new RuntimeException("STAFF role not found"));

        OrganizationalUnit csUnit = unitRepository.findByUnitCode("CS").orElse(null);
        OrganizationalUnit saUnit = unitRepository.findByUnitCode("SA").orElse(null);
        OrganizationalUnit finUnit = unitRepository.findByUnitCode("FIN").orElse(null);
        OrganizationalUnit admUnit = unitRepository.findByUnitCode("ADM").orElse(null);

        Position lecturerPos = positionRepository.findByPositionName("LECTURER").orElse(null);
        Position hodPos = positionRepository.findByPositionName("HOD").orElse(null);
        Position saoPos = positionRepository.findByPositionName("STUDENT_AFFAIRS_OFFICER").orElse(null);
        Position foPos = positionRepository.findByPositionName("FINANCE_OFFICER").orElse(null);
        Position aoPos = positionRepository.findByPositionName("ADMINISTRATIVE_OFFICER").orElse(null);
        Position scPos = positionRepository.findByPositionName("SENIOR_CLERK").orElse(null);
        Position jcPos = positionRepository.findByPositionName("JUNIOR_CLERK").orElse(null);

        Staff adminStaff = null;

        // ── Lecturers (CS faculty) ─────────────────────────────
        staff("STF001", "dawmya@gmail.com", "Daw Mya", "09-1111111", csUnit,
                List.of(new PosAssign(lecturerPos, LocalDate.of(2024, 1, 1)),
                        new PosAssign(hodPos, LocalDate.of(2024, 1, 1))),
                staffRole);

        staff("STF002", "dawaye@gmail.com", "Daw Aye", "09-2222222", csUnit,
                List.of(new PosAssign(lecturerPos, LocalDate.of(2024, 1, 1))),
                staffRole);

        staff("STF003", "dawsandar@gmail.com", "Daw Sandar", "09-3333333", csUnit,
                List.of(new PosAssign(lecturerPos, LocalDate.of(2024, 1, 1))),
                staffRole);

        // ── Student Affairs ─────────────────────────────────────
        staff("STF004", "kohtet@gmail.com", "Ko Htet", "09-4444444", saUnit,
                List.of(new PosAssign(saoPos, LocalDate.of(2024, 1, 1)),
                        new PosAssign(scPos, LocalDate.of(2024, 6, 1))),
                staffRole);

        staff("STF005", "mama@gmail.com", "Ma Ma", "09-5555555", saUnit,
                List.of(new PosAssign(saoPos, LocalDate.of(2024, 1, 1)),
                        new PosAssign(jcPos, LocalDate.of(2024, 6, 1))),
                staffRole);

        staff("STF006", "dawsu@gmail.com", "Daw Su", "09-6666666", saUnit,
                List.of(new PosAssign(saoPos, LocalDate.of(2024, 1, 1)),
                        new PosAssign(hodPos, LocalDate.of(2024, 1, 1))),
                staffRole);

        // ── Finance ─────────────────────────────────────────────
        staff("STF007", "komyo@gmail.com", "Ko Myo", "09-7777777", finUnit,
                List.of(new PosAssign(foPos, LocalDate.of(2024, 1, 1))),
                staffRole);

        staff("STF008", "dawyin@gmail.com", "Daw Yin", "09-8888888", finUnit,
                List.of(new PosAssign(foPos, LocalDate.of(2024, 1, 1)),
                        new PosAssign(scPos, LocalDate.of(2024, 1, 1))),
                staffRole);

        staff("STF009", "makhin@gmail.com", "Ma Khin", "09-9999999", finUnit,
                List.of(new PosAssign(foPos, LocalDate.of(2024, 1, 1)),
                        new PosAssign(hodPos, LocalDate.of(2024, 1, 1)),
                        new PosAssign(scPos, LocalDate.of(2024, 6, 1))),
                staffRole);

        // ── Administration ──────────────────────────────────────
        staff("STF010", "dawnwe@gmail.com", "Daw Nwe", "09-1010101", admUnit,
                List.of(new PosAssign(aoPos, LocalDate.of(2024, 1, 1)),
                        new PosAssign(hodPos, LocalDate.of(2024, 1, 1))),
                staffRole);

        staff("STF011", "dawphyu@gmail.com", "Daw Phyu", "09-2020202", admUnit,
                List.of(new PosAssign(aoPos, LocalDate.of(2024, 1, 1)),
                        new PosAssign(jcPos, LocalDate.of(2024, 6, 1))),
                staffRole);

        staff("STF012", "komyo2@gmail.com", "Ko Zaw", "09-3030303", admUnit,
                List.of(new PosAssign(aoPos, LocalDate.of(2024, 1, 1)),
                        new PosAssign(scPos, LocalDate.of(2024, 1, 1))),
                staffRole);

        // ── Admin's staff record ────────────────────────────────
        adminStaff = staff("STF000", ADMIN_EMAIL, "System Admin", "09-0000000", csUnit,
                List.of(new PosAssign(lecturerPos, LocalDate.of(2024, 1, 1))),
                staffRole);

        log.info("Staff seeded (13 total: 3 lecturers, 3 student affairs, 3 finance, 3 administration, 1 admin)");
    }

    private Staff staff(String staffNo, String email, String name, String phone,
                        OrganizationalUnit unit, List<PosAssign> positions, Role role) {
        if (staffRepository.existsByStaffNo(staffNo)) {
            return staffRepository.findByStaffNo(staffNo).orElse(null);
        }

        User user;
        if (userRepository.existsByEmail(email)) {
            user = userRepository.findByEmail(email).orElse(null);
            if (user == null) return null;
        } else {
            user = new User();
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode(STAFF_PASSWORD));
            user.setRole(role);
            user.setActive(true);
            user.setRegistrationStatus(RegistrationStatus.APPROVED);
            user = userRepository.save(user);
        }

        Staff s = new Staff();
        s.setUser(user);
        s.setStaffNo(staffNo);
        s.setStaffName(name);
        s.setPhoneNo(phone);
        s.setUnit(unit);
        s.setJoinedAt(LocalDate.of(2024, 1, 1));
        s = staffRepository.save(s);

        for (PosAssign pa : positions) {
            if (pa.position != null && !spaRepository.existsByStaff_StaffIdAndPosition_PositionIdAndStartDate(
                    s.getStaffId(), pa.position.getPositionId(), pa.startDate)) {
                StaffPositionAssignment spa = new StaffPositionAssignment();
                spa.setStaff(s);
                spa.setPosition(pa.position);
                spa.setStartDate(pa.startDate);
                spaRepository.save(spa);
            }
        }
        return s;
    }

    private record PosAssign(Position position, LocalDate startDate) {}

    // ── Students ───────────────────────────────────────────────

    private void seedStudents() {
        Role studentRole = roleRepository.findByRoleName("STUDENT")
                .orElseThrow(() -> new RuntimeException("STUDENT role not found"));

        AcademicTerm term = termRepository.findByStatus(TermStatus.ACTIVE).stream().findFirst()
                .orElseThrow(() -> new RuntimeException("No active academic term"));

        Map<Integer, Semester> semesters = new HashMap<>();
        semesterRepository.findAll().forEach(s -> semesters.put(s.getSemesterNo(), s));

        Map<String, Section> sections = new HashMap<>();
        sectionRepository.findAll().forEach(s -> sections.put(s.getSectionName(), s));

        Map<String, Major> majors = new HashMap<>();
        majorRepository.findAll().forEach(m -> majors.put(m.getMajorCode(), m));

        // Pre-load existing emails and roll numbers to avoid per-record DB checks
        Set<String> existingEmails = new HashSet<>(userRepository.findAll()
                .stream().map(User::getEmail).toList());
        Set<String> existingRollNos = new HashSet<>(studentRepository.findAll()
                .stream().map(Student::getRollNo).toList());

        int totalStudents = 0;
        int studentCounter = 0;

        int[] semestersToSeed = {2, 4, 6, 8};
        String[] sectionNames = {"A", "B", "C"};
        String[] majorCodes = {"CS", "CT", "CST"};

        for (int sem : semestersToSeed) {
            for (String secName : sectionNames) {
                for (String majorCode : majorCodes) {
                    for (int i = 1; i <= 20; i++) {
                        studentCounter++;
                        String email = String.format("student.sem%d.%s.%03d@gmail.com",
                                sem, secName.toLowerCase(), i);
                        String rollNo = String.format("UCSTGO-%d-%s%s%03d",
                                sem, majorCode, secName, i);

                        if (existingEmails.contains(email) || existingRollNos.contains(rollNo)) {
                            continue;
                        }

                        String studentName = String.format("Student %s %d-%s-%03d",
                                majorCode, sem, secName, i);

                        User user = new User();
                        user.setEmail(email);
                        user.setPasswordHash(passwordEncoder.encode(STUDENT_PASSWORD));
                        user.setRole(studentRole);
                        user.setActive(true);
                        user.setRegistrationStatus(RegistrationStatus.APPROVED);
                        user = userRepository.save(user);
                        existingEmails.add(email);

                        Student student = new Student();
                        student.setUser(user);
                        student.setMajor(majors.get(majorCode));
                        student.setSemester(semesters.get(sem));
                        student.setSection(sections.get(secName));
                        student.setTerm(term);
                        student.setRollNo(rollNo);
                        student.setStudentName(studentName);
                        student.setPhoneNo(String.format("09-%07d", studentCounter));
                        student.setBirthYear(2000 + (8 - sem));
                        studentRepository.save(student);
                        existingRollNos.add(rollNo);

                        totalStudents++;
                    }
                }
            }
        }

        log.info("Students seeded: {} total across semesters 2,4,6,8 x sections A,B,C x majors CS,CT,CST (20 each)",
                totalStudents);
    }
}
