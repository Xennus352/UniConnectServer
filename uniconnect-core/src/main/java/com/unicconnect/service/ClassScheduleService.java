package com.unicconnect.service;

import com.unicconnect.dto.request.ScheduleRequest;
import com.unicconnect.dto.request.SwapScheduleRequest;
import com.unicconnect.dto.response.ScheduleResponse;
import com.unicconnect.dto.response.SwapScheduleResponse;
import com.unicconnect.entity.*;
import com.unicconnect.exception.BusinessRuleException;
import com.unicconnect.exception.ResourceNotFoundException;
import com.unicconnect.exception.ValidationException;
import com.unicconnect.repository.*;
import com.unicconnect.service.port.TimetableAccessPort;
import com.unicconnect.service.port.TimetableEventPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ClassScheduleService {

    private final ClassScheduleRepository scheduleRepository;
    private final GenerationSessionRepository generationRepository;
    private final TeachingAssignmentRepository assignmentRepository;
    private final TeachingAssignmentGroupRepository teachingGroupRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final TimetableAccessPort accessPort;
    private final TimetableEventPort eventPort;

    public ClassScheduleService(ClassScheduleRepository scheduleRepository,
                                GenerationSessionRepository generationRepository,
                                TeachingAssignmentRepository assignmentRepository,
                                TeachingAssignmentGroupRepository teachingGroupRepository,
                                TimeSlotRepository timeSlotRepository,
                                 TimetableAccessPort accessPort,
                                 TimetableEventPort eventPort) {
        this.scheduleRepository = scheduleRepository;
        this.generationRepository = generationRepository;
        this.assignmentRepository = assignmentRepository;
        this.teachingGroupRepository = teachingGroupRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.accessPort = accessPort;
        this.eventPort = eventPort;
    }

    public List<ScheduleResponse> getAll(UUID termId, UUID sectionId, UUID staffId, Integer dayOfWeek) {
        List<ClassSchedule> schedules;
        if (termId != null) {
            schedules = scheduleRepository.findByTermIdWithDetails(termId);
            // Normal users must never see draft schedules: only the published
            // generation for the term (if any) is visible to non-HOD callers.
            if (accessPort.currentHod().isEmpty()) {
                UUID publishedId = generationRepository
                        .findFirstByTerm_TermIdAndStatusOrderByCreatedAtDesc(
                                termId, GenerationStatus.PUBLISHED)
                        .map(GenerationSession::getGenerationId)
                        .orElse(null);
                schedules = schedules.stream()
                        .filter(s -> publishedId != null
                                && s.getGeneration().getGenerationId().equals(publishedId))
                        .toList();
            }
        } else if (sectionId != null) {
            schedules = scheduleRepository.findBySectionIdWithDetails(sectionId);
        } else if (staffId != null) {
            schedules = scheduleRepository.findByStaffIdWithDetails(staffId);
        } else if (dayOfWeek != null) {
            schedules = scheduleRepository.findByDayOfWeekWithDetails(dayOfWeek);
        } else {
            schedules = scheduleRepository.findAllWithDetails();
        }
        return schedules.stream()
                .sorted(Comparator.comparing(ClassSchedule::getDayOfWeek)
                        .thenComparing(s -> s.getStartSlot().getDisplayOrder()))
                .map(ClassScheduleService::toResponse).toList();
    }

    /**
     * Only the schedules of the term's published generation — the "normal" view.
     */
    public List<ScheduleResponse> getPublished(UUID termId) {
        UUID publishedId = generationRepository
                .findFirstByTerm_TermIdAndStatusOrderByCreatedAtDesc(termId, GenerationStatus.PUBLISHED)
                .map(GenerationSession::getGenerationId)
                .orElse(null);
        if (publishedId == null) {
            return List.of();
        }
        return scheduleRepository.findByGeneration_GenerationId(publishedId).stream()
                .filter(s -> s.getScheduleStatus() != ScheduleStatus.CANCELLED)
                .sorted(Comparator.comparing(ClassSchedule::getDayOfWeek)
                        .thenComparing(s -> s.getStartSlot().getDisplayOrder()))
                .map(ClassScheduleService::toResponse).toList();
    }

    public ScheduleResponse getById(UUID scheduleId) {
        return toResponse(findSchedule(scheduleId));
    }

    @Transactional
    public ScheduleResponse create(ScheduleRequest request) {
        accessPort.requireHod();
        GenerationSession generation = generationRepository.findById(request.generationId())
                .orElseThrow(() -> new ResourceNotFoundException("Generation session not found"));
        requireEditable(generation);
        accessPort.requireEditLockOwnership(generation.getGenerationId());
        ClassSchedule schedule = new ClassSchedule();
        apply(schedule, request);
        ScheduleResponse response = toResponse(scheduleRepository.save(schedule));
        eventPort.publishForGeneration(generation.getGenerationId(),
                TimetableEventPort.SCHEDULE_CREATED,
                Map.of("generationId", generation.getGenerationId(),
                        "scheduleId", response.scheduleId()));
        return response;
    }

    @Transactional
    public ScheduleResponse update(UUID scheduleId, ScheduleRequest request) {
        accessPort.requireHod();
        ClassSchedule schedule = findSchedule(scheduleId);
        GenerationSession generation = schedule.getGeneration();
        requireEditable(generation);
        accessPort.requireEditLockOwnership(generation.getGenerationId());
        apply(schedule, request);
        ScheduleResponse response = toResponse(scheduleRepository.save(schedule));
        eventPort.publishForGeneration(generation.getGenerationId(),
                TimetableEventPort.SCHEDULE_UPDATED,
                Map.of("generationId", generation.getGenerationId(),
                        "scheduleId", scheduleId));
        return response;
    }

    /**
     * Swap two schedules (or move one when the drop cell is empty).
     *
     * <p>When the target cell is occupied by another schedule the swap is
     * simulated first. If the simulated swap creates conflicts and the caller has
     * not confirmed ({@code force=false}), the swap is <b>not</b> applied and the
     * conflict descriptions are returned so the UI can ask
     * "Are you sure you want to switch these periods?". Only an explicit
     * {@code force=true} confirmation applies a conflicting swap.
     */
    @Transactional
    public SwapScheduleResponse swap(UUID generationId, SwapScheduleRequest request) {
        accessPort.requireHod();
        ClassSchedule source = scheduleRepository.findById(request.scheduleId())
                .orElseThrow(() -> new ResourceNotFoundException("Class schedule not found"));
        if (!source.getGeneration().getGenerationId().equals(generationId)) {
            throw new ValidationException("Schedule does not belong to this generation");
        }
        GenerationSession generation = source.getGeneration();
        requireEditable(generation);
        accessPort.requireEditLockOwnership(generationId);

        if (request.targetDay() < 1 || request.targetDay() > 5) {
            throw new ValidationException("Schedules may only be placed Monday-Friday (day 1-5)");
        }
        List<TimeSlot> slots = timeSlotRepository.findAllByOrderByDisplayOrderAscPeriodNoAsc();
        TimeSlot targetStart = slotByPeriod(slots, request.targetPeriod());
        if (targetStart == null) {
            throw new ValidationException("Target period does not exist");
        }
        int sourceSpan = source.getEndSlot().getPeriodNo() - source.getStartSlot().getPeriodNo();
        TimeSlot sourceNewEnd = slotByPeriod(slots, request.targetPeriod() + sourceSpan);
        if (sourceNewEnd == null) {
            throw new ValidationException("That move would overflow the timetable");
        }

        UUID sourceId = source.getScheduleId();
        ClassSchedule target = scheduleRepository.findByGeneration_GenerationId(generationId).stream()
                .filter(s -> s.getScheduleStatus() != ScheduleStatus.CANCELLED)
                .filter(s -> s.getDayOfWeek().equals(request.targetDay()))
                .filter(s -> s.getStartSlot().getPeriodNo().equals(request.targetPeriod()))
                .filter(s -> !s.getScheduleId().equals(sourceId))
                .findFirst()
                .orElse(null);

        if (target == null) {
            // Plain move: reuse the single-schedule update path (conflicts reject).
            ScheduleResponse moved = update(source.getScheduleId(),
                    new ScheduleRequest(generationId,
                            source.getTeachingAssignment() != null
                                    ? source.getTeachingAssignment().getAssignmentId() : null,
                            source.getTeachingGroup() != null
                                    ? source.getTeachingGroup().getGroupId() : null,
                            request.targetDay(),
                            targetStart.getSlotId(),
                            sourceNewEnd.getSlotId(),
                            source.getScheduleType(),
                            source.getScheduleStatus()));
            return new SwapScheduleResponse(false, List.of(), List.of(moved));
        }

        // Swap: both schedules exchange positions.
        Integer sourceDay = source.getDayOfWeek();
        int targetSpan = target.getEndSlot().getPeriodNo() - target.getStartSlot().getPeriodNo();
        TimeSlot targetNewStart = source.getStartSlot();
        TimeSlot targetNewEnd = slotByPeriod(slots, source.getStartSlot().getPeriodNo() + targetSpan);
        if (targetNewEnd == null) {
            throw new ValidationException("That swap would overflow the timetable");
        }

        List<String> conflicts = new ArrayList<>();
        conflicts.addAll(collectSwapConflicts(source, request.targetDay(), targetStart, sourceNewEnd, target));
        conflicts.addAll(collectSwapConflicts(target, sourceDay, targetNewStart, targetNewEnd, source));

        if (!conflicts.isEmpty() && !request.force()) {
            return new SwapScheduleResponse(false, conflicts, null);
        }

        source.setDayOfWeek(request.targetDay());
        source.setStartSlot(targetStart);
        source.setEndSlot(sourceNewEnd);
        target.setDayOfWeek(sourceDay);
        target.setStartSlot(targetNewStart);
        target.setEndSlot(targetNewEnd);
        source = scheduleRepository.save(source);
        target = scheduleRepository.save(target);

        ScheduleResponse sourceResponse = toResponse(source);
        ScheduleResponse targetResponse = toResponse(target);
        eventPort.publishForGeneration(generationId,
                TimetableEventPort.SCHEDULE_UPDATED,
                Map.of("generationId", generationId, "scheduleId", sourceResponse.scheduleId()));
        eventPort.publishForGeneration(generationId,
                TimetableEventPort.SCHEDULE_UPDATED,
                Map.of("generationId", generationId, "scheduleId", targetResponse.scheduleId()));
        return new SwapScheduleResponse(true, List.of(), List.of(sourceResponse, targetResponse));
    }

    private TimeSlot slotByPeriod(List<TimeSlot> slots, int periodNo) {
        return slots.stream().filter(t -> t.getPeriodNo().equals(periodNo)).findFirst().orElse(null);
    }

    /**
     * Non-throwing conflict scan for one side of a simulated swap: would
     * {@code moved} at ({@code day}, {@code start}-{@code end}) collide with any
     * schedule other than itself and its swap partner? Mirrors the rules of
     * {@link #validateNoConflicts} but returns human-readable descriptions.
     */
    private List<String> collectSwapConflicts(ClassSchedule moved, int day, TimeSlot start, TimeSlot end,
                                              ClassSchedule partner) {
        List<String> messages = new ArrayList<>();
        List<ClassSchedule> daySchedules = scheduleRepository.findByGeneration_GenerationId(
                moved.getGeneration().getGenerationId()).stream()
                .filter(s -> s.getDayOfWeek().equals(day))
                .filter(s -> !s.getScheduleId().equals(moved.getScheduleId()))
                .filter(s -> !s.getScheduleId().equals(partner.getScheduleId()))
                .filter(s -> s.getScheduleStatus() != ScheduleStatus.CANCELLED)
                .toList();

        if (moved.getScheduleType() == ScheduleType.COURSE) {
            Set<UUID> candidateStaff = new HashSet<>();
            Set<UUID> candidateSections = new HashSet<>();
            String candidateCourse = null;
            Course candidateCourseEntity = null;
            UUID candidateGroupId = null;
            UUID candidateAssignmentId = null;
            if (moved.getTeachingGroup() != null) {
                candidateGroupId = moved.getTeachingGroup().getGroupId();
                for (TeachingAssignmentGroupMember m : moved.getTeachingGroup().getMembers()) {
                    candidateStaff.add(m.getAssignment().getStaff().getStaffId());
                    candidateSections.add(m.getAssignment().getSection().getSectionId());
                }
                candidateCourse = moved.getTeachingGroup().getCourse().getCourseCode();
                candidateCourseEntity = moved.getTeachingGroup().getCourse();
            } else if (moved.getTeachingAssignment() != null) {
                TeachingAssignment assignment = moved.getTeachingAssignment();
                candidateAssignmentId = assignment.getAssignmentId();
                candidateStaff.add(assignment.getStaff().getStaffId());
                candidateSections.add(assignment.getSection().getSectionId());
                candidateCourse = assignment.getCourse().getCourseCode();
                candidateCourseEntity = assignment.getCourse();
            }

            for (ClassSchedule other : daySchedules) {
                boolean special = other.getScheduleType() != ScheduleType.COURSE;

                // RULE 13: one session per teaching unit per day.
                if (!special && candidateGroupId != null && other.getTeachingGroup() != null
                        && other.getTeachingGroup().getGroupId().equals(candidateGroupId)) {
                    messages.add(describeSwapConflict(moved, other, day,
                            "the same combined course already has a session on this day"));
                    continue;
                }
                if (!special && candidateAssignmentId != null && other.getTeachingAssignment() != null
                        && other.getTeachingAssignment().getAssignmentId().equals(candidateAssignmentId)) {
                    messages.add(describeSwapConflict(moved, other, day,
                            "this course already has a session for this teaching assignment on this day"));
                    continue;
                }

                if (overlaps(start, end, List.of(other))) {
                    Set<UUID> otherStaff = coveredStaff(other);
                    Set<UUID> otherSections = coveredSections(other);
                    boolean sameElectiveGroup = !special && other.getTeachingAssignment() != null
                            && sameElectiveGroup(candidateCourseEntity, other.getTeachingAssignment().getCourse());
                    boolean identicalWindow = start.getDisplayOrder() == other.getStartSlot().getDisplayOrder()
                            && end.getDisplayOrder() == other.getEndSlot().getDisplayOrder();
                    boolean sectionOverlap = !Collections.disjoint(candidateSections, otherSections);
                    // Sections are shared rows across semesters: different-semester
                    // cohorts may legitimately co-exist in one slot (the solver is
                    // semester-scoped); only same-semester co-existence conflicts.
                    boolean sameSemester = sameSemesterCourse(candidateCourseEntity, other);
                    if (special || !Collections.disjoint(candidateStaff, otherStaff)
                            || (sameSemester && !sameElectiveGroup && sectionOverlap)
                            || (sameSemester && sameElectiveGroup && !identicalWindow && sectionOverlap)) {
                        String reason = special ? "a " + other.getScheduleType() + " period"
                                : (!Collections.disjoint(candidateStaff, otherStaff)
                                        ? "another engagement of the same lecturer"
                                        : (sameElectiveGroup
                                                ? "a partial overlap of an elective-group window"
                                                : "another schedule for the same section"));
                        messages.add(describeSwapConflict(moved, other, day, reason));
                    }
                    if (!special && candidateCourse != null
                            && candidateCourse.equals(courseCodeOf(other))
                            && !Collections.disjoint(candidateSections, otherSections)) {
                        messages.add(describeSwapConflict(moved, other, day,
                                "course " + candidateCourse
                                        + " is already scheduled for one of these sections on this day"));
                    }
                }
            }
        } else {
            for (ClassSchedule other : daySchedules) {
                if (overlaps(start, end, List.of(other))
                        && other.getScheduleType() == ScheduleType.COURSE) {
                    messages.add(describeSwapConflict(moved, other, day,
                            "a COURSE session already occupies this slot"));
                }
            }
        }
        return messages;
    }

    private String describeSwapConflict(ClassSchedule moved, ClassSchedule other, int day, String reason) {
        String movedLabel = courseCodeOf(moved) != null
                ? courseCodeOf(moved) + " (" + moved.getScheduleType() + ")"
                : String.valueOf(moved.getScheduleType());
        String otherLabel = courseCodeOf(other) != null
                ? courseCodeOf(other) + " (" + other.getScheduleType() + ", "
                        + DAY_NAME[other.getDayOfWeek()] + " P" + other.getStartSlot().getPeriodNo()
                        + (other.getEndSlot().getPeriodNo() != other.getStartSlot().getPeriodNo()
                                ? "-P" + other.getEndSlot().getPeriodNo() : "")
                        + ")"
                : String.valueOf(other.getScheduleType());
        return movedLabel + " would conflict with " + otherLabel + " on day " + day + ": " + reason;
    }

    private static final String[] DAY_NAME = {"", "Mon", "Tue", "Wed", "Thu", "Fri"};

    @Transactional
    public void delete(UUID scheduleId) {
        accessPort.requireHod();
        ClassSchedule schedule = findSchedule(scheduleId);
        GenerationSession generation = schedule.getGeneration();
        requireEditable(generation);
        accessPort.requireEditLockOwnership(generation.getGenerationId());
        scheduleRepository.deleteById(scheduleId);
        eventPort.publishForGeneration(generation.getGenerationId(),
                TimetableEventPort.SCHEDULE_DELETED,
                Map.of("generationId", generation.getGenerationId(),
                        "scheduleId", scheduleId));
    }

    private void requireEditable(GenerationSession generation) {
        if (generation.getStatus() == GenerationStatus.PUBLISHED) {
            throw new BusinessRuleException("Cannot modify schedules of a published timetable");
        }
    }

    private void apply(ClassSchedule schedule, ScheduleRequest request) {
        GenerationSession generation = generationRepository.findById(request.generationId())
                .orElseThrow(() -> new ResourceNotFoundException("Generation session not found"));
        if (generation.getStatus() == GenerationStatus.PUBLISHED) {
            throw new BusinessRuleException("Cannot modify schedules of a published timetable");
        }

        TeachingAssignment assignment = null;
        TeachingAssignmentGroup teachingGroup = null;
        if (request.scheduleType() == ScheduleType.COURSE) {
            boolean hasAssignment = request.teachingAssignmentId() != null;
            boolean hasGroup = request.teachingGroupId() != null;
            if (hasAssignment == hasGroup) {
                throw new ValidationException(
                        "COURSE schedules require exactly one of teachingAssignmentId or teachingGroupId");
            }
            if (hasGroup) {
                teachingGroup = teachingGroupRepository.findById(request.teachingGroupId())
                        .orElseThrow(() -> new ResourceNotFoundException("Teaching group not found"));
                if (!teachingGroup.getTerm().getTermId().equals(generation.getTerm().getTermId())) {
                    throw new ValidationException("Teaching group does not belong to this generation's term");
                }
                if (teachingGroup.getMembers().isEmpty()) {
                    throw new ValidationException("Teaching group has no member assignments");
                }
            } else {
                assignment = assignmentRepository.findById(request.teachingAssignmentId())
                        .orElseThrow(() -> new ResourceNotFoundException("Teaching assignment not found"));
                if (!assignment.getTerm().getTermId().equals(generation.getTerm().getTermId())) {
                    throw new ValidationException("Teaching assignment does not belong to this generation's term");
                }
            }
        } else {
            if (request.teachingAssignmentId() != null || request.teachingGroupId() != null) {
                throw new ValidationException("teachingAssignmentId/teachingGroupId must be null for "
                        + request.scheduleType() + " schedules");
            }
        }

        TimeSlot startSlot = timeSlotRepository.findById(request.startSlotId())
                .orElseThrow(() -> new ResourceNotFoundException("Start time slot not found"));
        TimeSlot endSlot = timeSlotRepository.findById(request.endSlotId())
                .orElseThrow(() -> new ResourceNotFoundException("End time slot not found"));
        if (startSlot.getDisplayOrder() > endSlot.getDisplayOrder()) {
            throw new ValidationException("startSlot must not be after endSlot");
        }
        if (request.dayOfWeek() < 1 || request.dayOfWeek() > 5) {
            throw new ValidationException("Schedules may only be placed Monday-Friday (day 1-5)");
        }

        validateNoConflicts(generation, schedule, request, startSlot, endSlot);

        schedule.setGeneration(generation);
        schedule.setTeachingAssignment(assignment);
        schedule.setTeachingGroup(teachingGroup);
        schedule.setDayOfWeek(request.dayOfWeek());
        schedule.setStartSlot(startSlot);
        schedule.setEndSlot(endSlot);
        schedule.setScheduleType(request.scheduleType());
        if (request.scheduleStatus() != null) {
            schedule.setScheduleStatus(request.scheduleStatus());
        }
    }

    /**
     * Conflict validation shared by create/update. For combined teaching groups
     * the conflict surface is every member section and every member lecturer,
     * and a course may never occur twice on the same day for the same section.
     */
    private void validateNoConflicts(GenerationSession generation, ClassSchedule self,
                                     ScheduleRequest request, TimeSlot startSlot, TimeSlot endSlot) {
        List<ClassSchedule> daySchedules = scheduleRepository.findByGeneration_GenerationId(
                request.generationId()).stream()
                .filter(s -> s.getDayOfWeek().equals(request.dayOfWeek()))
                .filter(s -> !s.getScheduleId().equals(self != null ? self.getScheduleId() : null))
                .filter(s -> s.getScheduleStatus() != ScheduleStatus.CANCELLED)
                .toList();

        if (request.scheduleType() == ScheduleType.COURSE) {
            Set<UUID> candidateStaff = new HashSet<>();
            Set<UUID> candidateSections = new HashSet<>();
            String candidateCourse;
            Course candidateCourseEntity = null;
            UUID candidateGroupId = request.teachingGroupId();
            if (candidateGroupId != null) {
                TeachingAssignmentGroup group = teachingGroupRepository.findById(candidateGroupId)
                        .orElseThrow(() -> new ResourceNotFoundException("Teaching group not found"));
                for (TeachingAssignmentGroupMember m : group.getMembers()) {
                    candidateStaff.add(m.getAssignment().getStaff().getStaffId());
                    candidateSections.add(m.getAssignment().getSection().getSectionId());
                }
                candidateCourse = group.getCourse().getCourseCode();
                candidateCourseEntity = group.getCourse();
            } else {
                TeachingAssignment assignment = assignmentRepository.findById(request.teachingAssignmentId())
                        .orElseThrow();
                candidateStaff.add(assignment.getStaff().getStaffId());
                candidateSections.add(assignment.getSection().getSectionId());
                candidateCourse = assignment.getCourse().getCourseCode();
                candidateCourseEntity = assignment.getCourse();
            }

            for (ClassSchedule other : daySchedules) {
                boolean special = other.getScheduleType() != ScheduleType.COURSE;

                // RULE 13: one session per teaching unit per day.
                if (!special) {
                    if (candidateGroupId != null) {
                        if (other.getTeachingGroup() != null
                                && other.getTeachingGroup().getGroupId().equals(candidateGroupId)) {
                            throw new BusinessRuleException(
                                    "Combined course already has a session on day " + request.dayOfWeek());
                        }
                    } else if (other.getTeachingAssignment() != null
                            && other.getTeachingAssignment().getAssignmentId()
                                    .equals(request.teachingAssignmentId())) {
                        throw new BusinessRuleException(
                                "Course already has a session for this teaching assignment on day "
                                        + request.dayOfWeek());
                    }
                }

                if (overlaps(startSlot, endSlot, List.of(other))) {
                    Set<UUID> otherStaff = coveredStaff(other);
                    Set<UUID> otherSections = coveredSections(other);
                    // Elective co-location: same elective group (is_required=false,
                    // same semester) may share a window ONLY when the window is
                    // IDENTICAL (same day/start/end) and the lecturers differ;
                    // the lecturer conflict rule always wins.
                    boolean sameElectiveGroup = !special && other.getTeachingAssignment() != null
                            && sameElectiveGroup(candidateCourseEntity, other.getTeachingAssignment().getCourse());
                    boolean identicalWindow = startSlot.getDisplayOrder() == other.getStartSlot().getDisplayOrder()
                            && endSlot.getDisplayOrder() == other.getEndSlot().getDisplayOrder();
                    boolean sectionOverlap = !Collections.disjoint(candidateSections, otherSections);
                    // Sections are shared rows across semesters: different-semester
                    // cohorts may legitimately co-exist in one slot (the solver is
                    // semester-scoped); only same-semester co-existence conflicts.
                    boolean sameSemester = sameSemesterCourse(candidateCourseEntity, other);
                    if (special || !Collections.disjoint(candidateStaff, otherStaff)
                            || (sameSemester && !sameElectiveGroup && sectionOverlap)
                            || (sameSemester && sameElectiveGroup && !identicalWindow && sectionOverlap)) {
                        String reason = special ? "a " + other.getScheduleType() + " period"
                                : (!Collections.disjoint(candidateStaff, otherStaff)
                                        ? "another engagement of the same lecturer"
                                        : (sameElectiveGroup
                                                ? "a partial overlap of an elective-group window"
                                                : "another schedule for the same section"));
                        throw new BusinessRuleException("Schedule conflicts with " + reason
                                + " on day " + request.dayOfWeek());
                    }
                    // Same course must not appear twice in one day for the same section.
                    if (!special && candidateCourse != null
                            && candidateCourse.equals(courseCodeOf(other))
                            && !Collections.disjoint(candidateSections, otherSections)) {
                        throw new BusinessRuleException(
                                "Course " + candidateCourse + " is already scheduled for one of these sections on day "
                                        + request.dayOfWeek());
                    }
                }
            }
        } else {
            for (ClassSchedule other : daySchedules) {
                if (overlaps(startSlot, endSlot, List.of(other))
                        && other.getScheduleType() == ScheduleType.COURSE) {
                    throw new BusinessRuleException("COURSE schedule already occupies this slot on day "
                            + request.dayOfWeek());
                }
            }
        }
    }

    // ---------- Shared-teaching helpers ----------

    /** Staff covered by a schedule: its assignment lecturer or every group member lecturer. */
    static Set<UUID> coveredStaff(ClassSchedule s) {
        if (s.getTeachingAssignment() != null) {
            return Set.of(s.getTeachingAssignment().getStaff().getStaffId());
        }
        if (s.getTeachingGroup() != null) {
            Set<UUID> ids = new HashSet<>();
            for (TeachingAssignmentGroupMember m : s.getTeachingGroup().getMembers()) {
                ids.add(m.getAssignment().getStaff().getStaffId());
            }
            return ids;
        }
        return Set.of();
    }

    /** Sections covered by a schedule: its assignment section or every group member section. */
    static Set<UUID> coveredSections(ClassSchedule s) {
        if (s.getTeachingAssignment() != null) {
            return Set.of(s.getTeachingAssignment().getSection().getSectionId());
        }
        if (s.getTeachingGroup() != null) {
            Set<UUID> ids = new HashSet<>();
            for (TeachingAssignmentGroupMember m : s.getTeachingGroup().getMembers()) {
                ids.add(m.getAssignment().getSection().getSectionId());
            }
            return ids;
        }
        return Set.of();
    }

    static String courseCodeOf(ClassSchedule s) {
        if (s.getTeachingAssignment() != null) {
            return s.getTeachingAssignment().getCourse().getCourseCode();
        }
        if (s.getTeachingGroup() != null) {
            return s.getTeachingGroup().getCourse().getCourseCode();
        }
        return null;
    }

    /**
     * True when the candidate course and the other schedule's course belong to the
     * same elective group: both is_required=false and assigned to the same semester.
     * Groups of electives may co-locate on identical windows when lecturers differ.
     */
    private static boolean sameElectiveGroup(Course candidate, Course other) {
        if (candidate == null || other == null
                || candidate.isRequired() || other.isRequired()) {
            return false;
        }
        Semester sa = candidate.getSemester();
        Semester sb = other.getSemester();
        return sa != null && sb != null && sa.getSemesterId().equals(sb.getSemesterId());
    }

    /** Semester of the course behind a schedule (assignment or group). */
    private static Semester semesterOf(ClassSchedule s) {
        if (s.getTeachingAssignment() != null) {
            return s.getTeachingAssignment().getCourse().getSemester();
        }
        if (s.getTeachingGroup() != null) {
            return s.getTeachingGroup().getCourse().getSemester();
        }
        return null;
    }

    /** True when the candidate course and the other schedule are same-semester (or unknown). */
    private static boolean sameSemesterCourse(Course candidate, ClassSchedule other) {
        Semester os = semesterOf(other);
        if (candidate == null || candidate.getSemester() == null || os == null) return true;
        return candidate.getSemester().getSemesterId().equals(os.getSemesterId());
    }

    static boolean overlaps(TimeSlot start, TimeSlot end, List<ClassSchedule> others) {
        for (ClassSchedule other : others) {
            if (start.getDisplayOrder() <= other.getEndSlot().getDisplayOrder()
                    && end.getDisplayOrder() >= other.getStartSlot().getDisplayOrder()) {
                return true;
            }
        }
        return false;
    }

    public ClassSchedule findSchedule(UUID scheduleId) {
        return scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Class schedule not found"));
    }

    static ScheduleResponse toResponse(ClassSchedule schedule) {
        UUID teachingAssignmentId = null;
        UUID teachingGroupId = null;
        String courseCode = null;
        String courseName = null;
        String staffName = null;
        String sectionName = null;
        Integer semesterNo = null;
        List<String> sections = new ArrayList<>();
        List<String> staffNames = new ArrayList<>();

        if (schedule.getTeachingAssignment() != null) {
            teachingAssignmentId = schedule.getTeachingAssignment().getAssignmentId();
            courseCode = schedule.getTeachingAssignment().getCourse().getCourseCode();
            courseName = schedule.getTeachingAssignment().getCourse().getCourseName();
            staffName = schedule.getTeachingAssignment().getStaff().getStaffName();
            sectionName = schedule.getTeachingAssignment().getSection().getSectionName();
            sections.add(sectionName);
            staffNames.add(staffName);
            semesterNo = schedule.getTeachingAssignment().getCourse().getSemester() != null
                    ? schedule.getTeachingAssignment().getCourse().getSemester().getSemesterNo() : null;
        } else if (schedule.getTeachingGroup() != null) {
            TeachingAssignmentGroup group = schedule.getTeachingGroup();
            teachingGroupId = group.getGroupId();
            courseCode = group.getCourse().getCourseCode();
            courseName = group.getCourse().getCourseName();
            List<String> memberSections = new ArrayList<>();
            for (TeachingAssignmentGroupMember m : group.getMembers()) {
                memberSections.add(m.getAssignment().getSection().getSectionName());
                staffNames.add(m.getAssignment().getStaff().getStaffName());
            }
            memberSections.sort(Comparator.naturalOrder());
            sections.addAll(memberSections);
            staffNames = staffNames.stream().distinct().sorted().collect(Collectors.toList());
            sectionName = String.join(" + ", memberSections);
            staffName = String.join(", ", staffNames);
            semesterNo = group.getCourse().getSemester() != null
                    ? group.getCourse().getSemester().getSemesterNo() : null;
        }

        return new ScheduleResponse(
                schedule.getScheduleId(),
                schedule.getGeneration().getGenerationId(),
                teachingAssignmentId,
                teachingGroupId,
                courseCode,
                courseName,
                staffName,
                sectionName,
                semesterNo,
                schedule.getDayOfWeek(),
                schedule.getStartSlot().getSlotId(),
                schedule.getStartSlot().getPeriodNo(),
                schedule.getStartSlot().getStartTime().toString(),
                schedule.getEndSlot().getSlotId(),
                schedule.getEndSlot().getPeriodNo(),
                schedule.getEndSlot().getEndTime().toString(),
                schedule.getScheduleStatus(),
                schedule.getScheduleType(),
                sections,
                staffNames,
                schedule.getCreatedAt());
    }
}
