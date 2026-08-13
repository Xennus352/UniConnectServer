package com.unicconnect.service;

import com.unicconnect.dto.request.CreateGenerationRequest;
import com.unicconnect.dto.response.GenerationSessionResponse;
import com.unicconnect.dto.response.ScheduleResponse;
import com.unicconnect.entity.*;
import com.unicconnect.exception.BusinessRuleException;
import com.unicconnect.exception.ResourceNotFoundException;
import com.unicconnect.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Greedy timetable generator.
 *
 * <p>Placement rules:
 * <ul>
 *   <li>Slots ordered by display_order; Mon(1)..Fri(5) working days.</li>
 *   <li>Course sessions placed first. Sessions for the same assignment never share a day.</li>
 *   <li>No lecturer overlap, section overlap, or slot overlap.</li>
 *   <li>LECTURE sessions placed before LAB sessions.</li>
 *   <li>After all courses, one BREAK (middle slot), one LMS and one ASSIGNMENT per day.</li>
 *   <li>Special periods have null teaching_assignment_id as required by schema.</li>
 * </ul>
 */
@Service
@Transactional
public class TimetableGenerationService {

    private static final Logger log = LoggerFactory.getLogger(TimetableGenerationService.class);
    private static final int WORKING_DAY_START = 1; // Monday
    private static final int WORKING_DAY_END = 5;   // Friday

    private final GenerationSessionRepository generationRepository;
    private final TeachingAssignmentRepository assignmentRepository;
    private final CourseMeetingRequirementRepository requirementRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final ClassScheduleRepository scheduleRepository;
    private final StaffRepository staffRepository;
    private final AcademicTermRepository termRepository;

    public TimetableGenerationService(GenerationSessionRepository generationRepository,
                                      TeachingAssignmentRepository assignmentRepository,
                                      CourseMeetingRequirementRepository requirementRepository,
                                      TimeSlotRepository timeSlotRepository,
                                      ClassScheduleRepository scheduleRepository,
                                      StaffRepository staffRepository,
                                      AcademicTermRepository termRepository) {
        this.generationRepository = generationRepository;
        this.assignmentRepository = assignmentRepository;
        this.requirementRepository = requirementRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.scheduleRepository = scheduleRepository;
        this.staffRepository = staffRepository;
        this.termRepository = termRepository;
    }

    public List<GenerationSessionResponse> getAll(UUID termId) {
        List<GenerationSession> sessions = termId != null
                ? generationRepository.findByTerm_TermIdOrderByCreatedAtDesc(termId)
                : generationRepository.findAll();
        return sessions.stream().map(TimetableGenerationService::toResponse).toList();
    }

    public GenerationSessionResponse getById(UUID generationId) {
        return toResponse(findGeneration(generationId));
    }

    public GenerationSessionResponse create(CreateGenerationRequest request) {
        AcademicTerm term = termRepository.findById(request.termId())
                .orElseThrow(() -> new ResourceNotFoundException("Academic term not found"));
        Staff generator = staffRepository.findById(request.generatedByStaffId())
                .orElseThrow(() -> new ResourceNotFoundException("Generating staff member not found"));

        GenerationSession session = new GenerationSession();
        session.setTerm(term);
        session.setGeneratedByStaff(generator);
        session.setStatus(GenerationStatus.PENDING);
        return toResponse(generationRepository.save(session));
    }

    public GenerationSessionResponse generate(UUID generationId) {
        GenerationSession generation = findGeneration(generationId);
        if (generation.getStatus() == GenerationStatus.PUBLISHED) {
            throw new BusinessRuleException("A published timetable cannot be regenerated");
        }

        generation.setStatus(GenerationStatus.GENERATING);
        generation.setStartedAt(Instant.now());
        generation = generationRepository.save(generation);

        List<ClassSchedule> existing = scheduleRepository.findByGeneration_GenerationId(generationId);
        scheduleRepository.deleteAll(existing);
        scheduleRepository.flush();

        List<TimeSlot> slots = timeSlotRepository.findAllByOrderByDisplayOrderAscPeriodNoAsc();
        if (slots.isEmpty()) {
            generation.setStatus(GenerationStatus.FAILED);
            generation.setFinishedAt(Instant.now());
            return toResponse(generationRepository.save(generation));
        }

        List<ClassSchedule> created = new ArrayList<>();
        boolean[][] occupied = new boolean[8][slots.size()];

        // 1) Place course sessions.
        List<TeachingAssignment> assignments =
                assignmentRepository.findByTerm_TermId(generation.getTerm().getTermId());
        for (TeachingAssignment assignment : assignments) {
            if (assignment.getAssignmentStatus() == AssignmentStatus.CANCELLED) {
                continue;
            }
            List<CourseMeetingRequirement> requirements =
                    requirementRepository.findByCourse_CourseId(assignment.getCourse().getCourseId());
            requirements.sort(Comparator.comparing(r -> r.getMeetingType()));
            for (CourseMeetingRequirement req : requirements) {
                placeCourseSessions(generation, assignment, req, slots, occupied, created);
            }
        }

        // 2) Insert special periods into free slots (Mon-Fri).
        for (int day = WORKING_DAY_START; day <= WORKING_DAY_END; day++) {
            placeSpecial(generation, ScheduleType.BREAK, middleSlot(slots), day, slots, occupied, created);
        }
        for (int day = WORKING_DAY_START; day <= WORKING_DAY_END; day++) {
            placeSpecial(generation, ScheduleType.LMS, firstFreeSlot(day, slots, occupied), day, slots, occupied, created);
        }
        for (int day = WORKING_DAY_START; day <= WORKING_DAY_END; day++) {
            placeSpecial(generation, ScheduleType.ASSIGNMENT, firstFreeSlot(day, slots, occupied), day, slots, occupied, created);
        }

        scheduleRepository.saveAll(created);

        generation.setStatus(GenerationStatus.COMPLETED);
        generation.setFinishedAt(Instant.now());
        generation = generationRepository.save(generation);
        log.info("Timetable generated: {} schedules for generation {}", created.size(), generationId);
        return toResponse(generation);
    }

    public GenerationSessionResponse publish(UUID generationId) {
        GenerationSession generation = findGeneration(generationId);
        if (generation.getStatus() != GenerationStatus.COMPLETED) {
            throw new BusinessRuleException("Only a completed generation can be published");
        }
        List<ClassSchedule> schedules = scheduleRepository.findByGeneration_GenerationId(generationId);
        if (schedules.isEmpty()) {
            throw new BusinessRuleException("Cannot publish an empty timetable");
        }
        generationRepository.findFirstByTerm_TermIdAndStatusOrderByCreatedAtDesc(
                        generation.getTerm().getTermId(), GenerationStatus.PUBLISHED)
                .ifPresent(published -> {
                    if (!published.getGenerationId().equals(generationId)) {
                        throw new BusinessRuleException(
                                "A published timetable already exists for this term; it cannot be overwritten");
                    }
                });
        generation.setStatus(GenerationStatus.PUBLISHED);
        generation.setPublishedAt(Instant.now());
        for (ClassSchedule schedule : schedules) {
            schedule.setScheduleStatus(ScheduleStatus.CONFIRMED);
        }
        scheduleRepository.saveAll(schedules);
        return toResponse(generationRepository.save(generation));
    }

    public GenerationSessionResponse cancel(UUID generationId) {
        GenerationSession generation = findGeneration(generationId);
        if (generation.getStatus() == GenerationStatus.PUBLISHED) {
            throw new BusinessRuleException("A published timetable cannot be cancelled");
        }
        List<ClassSchedule> schedules = scheduleRepository.findByGeneration_GenerationId(generationId);
        scheduleRepository.deleteAll(schedules);
        generation.setStatus(GenerationStatus.FAILED);
        generation.setFinishedAt(Instant.now());
        return toResponse(generationRepository.save(generation));
    }

    public List<ScheduleResponse> getSchedules(UUID generationId) {
        findGeneration(generationId);
        return scheduleRepository.findByGeneration_GenerationId(generationId).stream()
                .sorted(Comparator.comparing(ClassSchedule::getDayOfWeek)
                        .thenComparing(s -> s.getStartSlot().getDisplayOrder()))
                .map(ClassScheduleService::toResponse).toList();
    }

    // ---------- Placement helpers ----------

    private void placeCourseSessions(GenerationSession generation, TeachingAssignment assignment,
                                     CourseMeetingRequirement req, List<TimeSlot> slots,
                                     boolean[][] occupied, List<ClassSchedule> created) {
        int sessions = req.getSessionsPerWeek();
        int perSession = req.getPeriodsPerSession();

        Set<Integer> usedDays = new HashSet<>();
        for (int d = WORKING_DAY_START; d <= WORKING_DAY_END && usedDays.size() < sessions; d++) {
            for (int startIdx = 0; startIdx + perSession <= slots.size(); startIdx++) {
                if (isFree(occupied, d, startIdx, perSession)
                        && !lecturerBusy(created, assignment, d, startIdx, perSession)
                        && !sectionBusy(created, assignment, d, startIdx, perSession)) {
                    created.add(buildSchedule(generation, assignment, ScheduleType.COURSE, d,
                            slots, startIdx, perSession));
                    markOccupied(occupied, d, startIdx, perSession);
                    usedDays.add(d);
                    break;
                }
            }
        }
    }

    private void placeSpecial(GenerationSession generation, ScheduleType type, int preferredSlot,
                              int day, List<TimeSlot> slots, boolean[][] occupied,
                              List<ClassSchedule> created) {
        if (preferredSlot >= 0 && preferredSlot < slots.size() && !occupied[day][preferredSlot]) {
            created.add(buildSchedule(generation, null, type, day, slots, preferredSlot, 1));
            markOccupied(occupied, day, preferredSlot, 1);
            return;
        }
        int free = firstFreeSlot(day, slots, occupied);
        if (free >= 0) {
            created.add(buildSchedule(generation, null, type, day, slots, free, 1));
            markOccupied(occupied, day, free, 1);
        }
    }

    private int middleSlot(List<TimeSlot> slots) {
        return slots.size() / 2;
    }

    private int firstFreeSlot(int day, List<TimeSlot> slots, boolean[][] occupied) {
        for (int i = 0; i < slots.size(); i++) {
            if (!occupied[day][i]) return i;
        }
        return -1;
    }

    private boolean isFree(boolean[][] occupied, int day, int startIdx, int length) {
        if (day < 1 || day > 7) return false;
        for (int i = startIdx; i < startIdx + length; i++) {
            if (i >= occupied[day].length || occupied[day][i]) return false;
        }
        return true;
    }

    private void markOccupied(boolean[][] occupied, int day, int startIdx, int length) {
        for (int i = startIdx; i < startIdx + length; i++) {
            if (i < occupied[day].length) occupied[day][i] = true;
        }
    }

    private boolean lecturerBusy(List<ClassSchedule> created, TeachingAssignment assignment,
                                 int day, int startIdx, int length) {
        int startOrder = startIdx;
        int endOrder = startIdx + length - 1;
        for (ClassSchedule s : created) {
            if (s.getDayOfWeek() != day || s.getTeachingAssignment() == null) continue;
            if (!s.getTeachingAssignment().getStaff().getStaffId()
                    .equals(assignment.getStaff().getStaffId())) continue;
            int otherStart = s.getStartSlot().getDisplayOrder();
            int otherEnd = s.getEndSlot().getDisplayOrder();
            if (startOrder <= otherEnd && endOrder >= otherStart) return true;
        }
        return false;
    }

    private boolean sectionBusy(List<ClassSchedule> created, TeachingAssignment assignment,
                                int day, int startIdx, int length) {
        int startOrder = startIdx;
        int endOrder = startIdx + length - 1;
        for (ClassSchedule s : created) {
            if (s.getDayOfWeek() != day || s.getTeachingAssignment() == null) continue;
            if (!s.getTeachingAssignment().getSection().getSectionId()
                    .equals(assignment.getSection().getSectionId())) continue;
            int otherStart = s.getStartSlot().getDisplayOrder();
            int otherEnd = s.getEndSlot().getDisplayOrder();
            if (startOrder <= otherEnd && endOrder >= otherStart) return true;
        }
        return false;
    }

    private ClassSchedule buildSchedule(GenerationSession generation, TeachingAssignment assignment,
                                        ScheduleType type, int day, List<TimeSlot> slots,
                                        int startIdx, int length) {
        ClassSchedule schedule = new ClassSchedule();
        schedule.setGeneration(generation);
        schedule.setTeachingAssignment(assignment);
        schedule.setDayOfWeek(day);
        schedule.setStartSlot(slots.get(startIdx));
        schedule.setEndSlot(slots.get(startIdx + length - 1));
        schedule.setScheduleType(type);
        schedule.setScheduleStatus(ScheduleStatus.PENDING);
        return schedule;
    }

    public GenerationSession findGeneration(UUID generationId) {
        return generationRepository.findById(generationId)
                .orElseThrow(() -> new ResourceNotFoundException("Generation session not found"));
    }

    static GenerationSessionResponse toResponse(GenerationSession session) {
        return new GenerationSessionResponse(
                session.getGenerationId(),
                session.getTerm().getTermId(),
                session.getTerm().getAcademicYear(),
                session.getGeneratedByStaff().getStaffId(),
                session.getGeneratedByStaff().getStaffNo(),
                session.getStatus(),
                session.getStartedAt(),
                session.getPublishedAt(),
                session.getFinishedAt(),
                session.getCreatedAt());
    }
}