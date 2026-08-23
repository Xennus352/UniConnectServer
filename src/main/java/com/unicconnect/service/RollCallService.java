package com.unicconnect.service;

import com.unicconnect.dto.response.RollCallScheduleResponse;
import com.unicconnect.dto.response.RollCallStudentsResponse;
import com.unicconnect.entity.Attendance;
import com.unicconnect.entity.AttendancePeriod;
import com.unicconnect.entity.ClassSchedule;
import com.unicconnect.entity.ClassSession;
import com.unicconnect.entity.GenerationSession;
import com.unicconnect.entity.GenerationStatus;
import com.unicconnect.entity.Staff;
import com.unicconnect.entity.StaffPositionAssignment;
import com.unicconnect.entity.Student;
import com.unicconnect.entity.TimeSlot;
import com.unicconnect.exception.BusinessRuleException;
import com.unicconnect.repository.AttendancePeriodRepository;
import com.unicconnect.repository.AttendanceRepository;
import com.unicconnect.repository.ClassScheduleRepository;
import com.unicconnect.repository.ClassSessionRepository;
import com.unicconnect.repository.GenerationSessionRepository;
import com.unicconnect.repository.StudentRepository;
import com.unicconnect.repository.TimeSlotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Roll Call: the lecturer's schedule comes ONLY from the latest PUBLISHED
 * generated timetable; class sessions are created/reused per
 * (schedule, session_date); student lists resolve through each schedule's
 * actual assignment/group section coverage.
 */
@Service
@Transactional(readOnly = true)
public class RollCallService {

    private final GenerationSessionRepository generationRepository;
    private final ClassScheduleRepository scheduleRepository;
    private final ClassSessionRepository sessionRepository;
    private final StudentRepository studentRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final AttendanceRepository attendanceRepository;
    private final AttendancePeriodRepository periodRepository;
    private final com.unicconnect.repository.StaffRepository staffRepository;
    private final com.unicconnect.repository.StaffPositionAssignmentRepository positionAssignmentRepository;
    private final com.unicconnect.util.SecurityUtil securityUtil;

    public RollCallService(GenerationSessionRepository generationRepository,
                           ClassScheduleRepository scheduleRepository,
                           ClassSessionRepository sessionRepository,
                           StudentRepository studentRepository,
                           TimeSlotRepository timeSlotRepository,
                           AttendanceRepository attendanceRepository,
                           AttendancePeriodRepository periodRepository,
                           com.unicconnect.repository.StaffRepository staffRepository,
                           com.unicconnect.repository.StaffPositionAssignmentRepository positionAssignmentRepository,
                           com.unicconnect.util.SecurityUtil securityUtil) {
        this.generationRepository = generationRepository;
        this.scheduleRepository = scheduleRepository;
        this.sessionRepository = sessionRepository;
        this.studentRepository = studentRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.attendanceRepository = attendanceRepository;
        this.periodRepository = periodRepository;
        this.staffRepository = staffRepository;
        this.positionAssignmentRepository = positionAssignmentRepository;
        this.securityUtil = securityUtil;
    }

    // ========== access ==========

    /** Current staff holding an active LECTURER position today. */
    public Staff requireLecturer() {
        UUID userId = securityUtil.currentUserId();
        Staff staff = staffRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new BusinessRuleException("Only staff can perform roll call"));
        if (!hasActivePosition(staff, "LECTURER")) {
            throw new BusinessRuleException("Only lecturers can perform roll call");
        }
        return staff;
    }

    public boolean hasActivePosition(Staff staff, String positionName) {
        LocalDate today = LocalDate.now();
        return activePositions(staff, today).contains(positionName);
    }

    private Set<String> activePositions(Staff staff, LocalDate today) {
        Set<String> out = new HashSet<>();
        for (StaffPositionAssignment pa
                : positionAssignmentRepository.findByStaff_StaffId(staff.getStaffId())) {
            if (pa.getStartDate() != null && pa.getStartDate().isAfter(today)) continue;
            if (pa.getEndDate() != null && pa.getEndDate().isBefore(today)) continue;
            if (pa.getPosition() != null && pa.getPosition().getPositionName() != null) {
                out.add(pa.getPosition().getPositionName());
            }
        }
        return out;
    }

    // ========== latest published timetable ==========

    public GenerationSession latestPublished() {
        return generationRepository
                .findFirstByStatusAndPublishedAtIsNotNullOrderByPublishedAtDesc(
                        GenerationStatus.PUBLISHED)
                .orElseThrow(() -> new BusinessRuleException(
                        "No published timetable exists yet"));
    }

    // ========== lecturer weekly schedule ==========

    public List<RollCallScheduleResponse> mySchedule(Staff lecturer) {
        GenerationSession published = latestPublished();
        LocalDate today = LocalDate.now();
        List<ClassSchedule> rows = scheduleRepository
                .findByGeneration_GenerationIdWithDetails(published.getGenerationId())
                .stream()
                .filter(s -> ClassScheduleService.coveredStaff(s).contains(lecturer.getStaffId()))
                .sorted(Comparator.comparingInt(ClassSchedule::getDayOfWeek)
                        .thenComparing(s -> s.getStartSlot().getStartTime()))
                .toList();
        List<RollCallScheduleResponse> out = new ArrayList<>();
        for (ClassSchedule s : rows) {
            boolean isToday = s.getDayOfWeek() == today.getDayOfWeek().getValue();
            ClassSession todays = isToday
                    ? sessionRepository.findBySchedule_ScheduleIdAndSessionDate(
                            s.getScheduleId(), today).orElse(null)
                    : null;
            out.add(toScheduleResponse(s, todays));
        }
        return out;
    }

    // ========== current-class auto selection ==========

    /** The class happening RIGHT NOW for this lecturer, or null. */
    public RollCallScheduleResponse currentClass(Staff lecturer) {
        var now = java.time.LocalTime.now();
        var todayName = LocalDate.now().getDayOfWeek().name();
        return mySchedule(lecturer).stream()
                .filter(r -> r.dayName().equalsIgnoreCase(todayName))
                .filter(r -> !now.isBefore(r.startTime()) && now.isBefore(r.endTime()))
                .findFirst()
                .orElse(null);
    }

    // ========== session create/reuse ==========

    @Transactional
    public ClassSession ensureTodaySession(UUID scheduleId, Staff lecturer) {
        ClassSchedule schedule = ownedPublishedSchedule(scheduleId, lecturer);
        LocalDate today = LocalDate.now();
        return sessionRepository
                .findBySchedule_ScheduleIdAndSessionDate(scheduleId, today)
                .orElseGet(() -> {
                    ClassSession session = new ClassSession();
                    session.setSchedule(schedule);
                    session.setSessionDate(today);
                    session.setSessionStatus(com.unicconnect.entity.SessionStatus.SCHEDULED);
                    return sessionRepository.save(session);
                });
    }

    // ========== students for a schedule (coverage-aware) ==========

    public RollCallStudentsResponse students(UUID scheduleId, UUID sessionId, Staff lecturer) {
        ownedPublishedSchedule(scheduleId, lecturer);

        List<UUID> sections = new ArrayList<>(coveredSectionIds(scheduleId));
        if (sections.isEmpty()) {
            throw new BusinessRuleException("Schedule has no section coverage");
        }
        Map<UUID, Student> unique = new LinkedHashMap<>();
        for (UUID sectionId : sections) {
            for (Student st : studentRepository.findBySection_SectionId(sectionId)) {
                unique.putIfAbsent(st.getStudentId(), st);
            }
        }
        List<Student> students = unique.values().stream()
                .sorted(Comparator.comparing(Student::getRollNo,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        ClassSchedule schedule = scheduleRepository.findById(scheduleId).orElseThrow();
        int scheduledPeriods = spanCount(schedule);
        List<TimeSlot> spanSlots = slotsInRange(schedule);

        Map<UUID, Attendance> attendanceByStudent = new LinkedHashMap<>();
        if (sessionId != null) {
            for (Attendance a : attendanceRepository.findBySession_SessionId(sessionId)) {
                attendanceByStudent.putIfAbsent(a.getStudent().getStudentId(), a);
            }
        }

        List<RollCallStudentsResponse.StudentDto> studentDtos = new ArrayList<>();
        for (Student st : students) {
            Attendance a = attendanceByStudent.get(st.getStudentId());
            List<UUID> slotIds = new ArrayList<>();
            String status = null;
            String remark = null;
            if (a != null) {
                status = a.getAttendanceStatus().name();
                remark = a.getRemark();
                for (AttendancePeriod p : periodRepository
                        .findByAttendance_AttendanceId(a.getAttendanceId())) {
                    slotIds.add(p.getSlot().getSlotId());
                }
            }
            studentDtos.add(new RollCallStudentsResponse.StudentDto(
                    st.getStudentId(), st.getRollNo(), st.getStudentName(),
                    a != null ? a.getAttendanceId() : null,
                    status, remark, slotIds, slotIds.size()));
        }

        List<RollCallStudentsResponse.SlotDto> slotDtos = spanSlots.stream()
                .map(t -> new RollCallStudentsResponse.SlotDto(
                        t.getSlotId(), t.getPeriodNo(),
                        t.getStartTime().toString(), t.getEndTime().toString()))
                .toList();

        List<String> secNames = new ArrayList<>(coveredSectionNames(scheduleId));
        secNames.sort(String::compareTo);
        return new RollCallStudentsResponse(
                scheduleId, secNames, scheduledPeriods, slotDtos, studentDtos);
    }

    // ========== authorization helper ==========

    public void authorizeLecturerForSchedule(Staff staff, ClassSchedule schedule) {
        if (!hasActivePosition(staff, "LECTURER")) {
            throw new BusinessRuleException("Only lecturers can submit roll call");
        }
        if (!ClassScheduleService.coveredStaff(schedule).contains(staff.getStaffId())) {
            throw new BusinessRuleException(
                    "You are not the assigned lecturer for this schedule");
        }
        GenerationSession published = latestPublished();
        if (!schedule.getGeneration().getGenerationId()
                .equals(published.getGenerationId())) {
            throw new BusinessRuleException(
                    "Schedule does not belong to the latest published timetable");
        }
    }

    private ClassSchedule ownedPublishedSchedule(UUID scheduleId, Staff lecturer) {
        ClassSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new BusinessRuleException("Schedule not found"));
        authorizeLecturerForSchedule(lecturer, schedule);
        return schedule;
    }

    // ========== shared internals ==========

    Set<UUID> coveredSectionIds(UUID scheduleId) {
        ClassSchedule s = scheduleRepository.findById(scheduleId).orElseThrow();
        return ClassScheduleService.coveredSections(s);
    }

    Set<String> coveredSectionNames(UUID scheduleId) {
        ClassSchedule s = scheduleRepository.findById(scheduleId).orElseThrow();
        Set<String> names = new LinkedHashSet<>();
        if (s.getTeachingAssignment() != null) {
            names.add(s.getTeachingAssignment().getSection().getSectionName());
        } else if (s.getTeachingGroup() != null) {
            s.getTeachingGroup().getMembers().forEach(m ->
                    names.add(m.getAssignment().getSection().getSectionName()));
        }
        return names;
    }

    static String dayName(int iso) {
        return DayOfWeek.of(iso).getDisplayName(
                java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);
    }

    private RollCallScheduleResponse toScheduleResponse(ClassSchedule s, ClassSession todays) {
        List<String> names = new ArrayList<>(coveredSectionNames(s.getScheduleId()));
        names.sort(String::compareTo);
        return new RollCallScheduleResponse(
                s.getScheduleId(),
                s.getDayOfWeek(),
                dayName(s.getDayOfWeek()),
                s.getStartSlot().getStartTime(),
                s.getEndSlot().getEndTime(),
                spanCount(s),
                courseCodeOfRow(s),
                courseNameOfRow(s),
                semesterNumberOfRow(s),
                names,
                s.getTeachingGroup() != null,
                todays != null ? todays.getSessionId() : null,
                todays != null && todays.getSessionStatus()
                        == com.unicconnect.entity.SessionStatus.COMPLETED);
    }

    private int spanCount(ClassSchedule s) {
        return s.getEndSlot().getDisplayOrder() - s.getStartSlot().getDisplayOrder() + 1;
    }

    private List<TimeSlot> slotsInRange(ClassSchedule s) {
        int lo = Math.min(s.getStartSlot().getDisplayOrder(), s.getEndSlot().getDisplayOrder());
        int hi = Math.max(s.getStartSlot().getDisplayOrder(), s.getEndSlot().getDisplayOrder());
        return timeSlotRepository.findAll().stream()
                .filter(t -> t.getDisplayOrder() >= lo && t.getDisplayOrder() <= hi)
                .sorted(Comparator.comparing(TimeSlot::getDisplayOrder))
                .toList();
    }

    static String courseCodeOfRow(ClassSchedule s) {
        if (s.getTeachingAssignment() != null)
            return s.getTeachingAssignment().getCourse().getCourseCode();
        if (s.getTeachingGroup() != null)
            return s.getTeachingGroup().getCourse().getCourseCode();
        return null;
    }

    static String courseNameOfRow(ClassSchedule s) {
        if (s.getTeachingAssignment() != null)
            return s.getTeachingAssignment().getCourse().getCourseName();
        if (s.getTeachingGroup() != null)
            return s.getTeachingGroup().getCourse().getCourseName();
        return null;
    }

    static Integer semesterNumberOfRow(ClassSchedule s) {
        var sem = semesterOfRowInternal(s);
        return sem != null ? sem.getSemesterNo() : null;
    }

    private static com.unicconnect.entity.Semester semesterOfRowInternal(ClassSchedule s) {
        if (s.getTeachingAssignment() != null)
            return s.getTeachingAssignment().getCourse().getSemester();
        if (s.getTeachingGroup() != null)
            return s.getTeachingGroup().getCourse().getSemester();
        return null;
    }

    static List<String> sectionNamesSortedForRow(ClassSchedule s) {
        Set<String> names = new LinkedHashSet<>();
        if (s.getTeachingAssignment() != null) {
            names.add(s.getTeachingAssignment().getSection().getSectionName());
        } else if (s.getTeachingGroup() != null) {
            s.getTeachingGroup().getMembers().forEach(m ->
                    names.add(m.getAssignment().getSection().getSectionName()));
        }
        return names.stream().sorted().toList();
    }
}

