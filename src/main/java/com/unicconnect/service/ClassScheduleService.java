package com.unicconnect.service;

import com.unicconnect.dto.request.ScheduleRequest;
import com.unicconnect.dto.response.ScheduleResponse;
import com.unicconnect.entity.*;
import com.unicconnect.exception.BusinessRuleException;
import com.unicconnect.exception.ResourceNotFoundException;
import com.unicconnect.exception.ValidationException;
import com.unicconnect.repository.*;
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
    private final HodAccessService hodAccessService;
    private final TimetableEditLockService editLockService;
    private final TimetableRealtimeEventService realtimeEventService;

    public ClassScheduleService(ClassScheduleRepository scheduleRepository,
                                GenerationSessionRepository generationRepository,
                                TeachingAssignmentRepository assignmentRepository,
                                TeachingAssignmentGroupRepository teachingGroupRepository,
                                TimeSlotRepository timeSlotRepository,
                                HodAccessService hodAccessService,
                                TimetableEditLockService editLockService,
                                TimetableRealtimeEventService realtimeEventService) {
        this.scheduleRepository = scheduleRepository;
        this.generationRepository = generationRepository;
        this.assignmentRepository = assignmentRepository;
        this.teachingGroupRepository = teachingGroupRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.hodAccessService = hodAccessService;
        this.editLockService = editLockService;
        this.realtimeEventService = realtimeEventService;
    }

    public List<ScheduleResponse> getAll(UUID termId, UUID sectionId, UUID staffId, Integer dayOfWeek) {
        List<ClassSchedule> schedules;
        if (termId != null) {
            schedules = scheduleRepository.findByTermIdWithDetails(termId);
            // Normal users must never see draft schedules: only the published
            // generation for the term (if any) is visible to non-HOD callers.
            if (hodAccessService.currentHod().isEmpty()) {
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
        hodAccessService.requireHod();
        GenerationSession generation = generationRepository.findById(request.generationId())
                .orElseThrow(() -> new ResourceNotFoundException("Generation session not found"));
        requireEditable(generation);
        editLockService.requireLockOwned(generation.getGenerationId());
        ClassSchedule schedule = new ClassSchedule();
        apply(schedule, request);
        ScheduleResponse response = toResponse(scheduleRepository.save(schedule));
        realtimeEventService.publishForGeneration(generation.getGenerationId(),
                TimetableRealtimeEventService.SCHEDULE_CREATED,
                Map.of("generationId", generation.getGenerationId(),
                        "scheduleId", response.scheduleId()));
        return response;
    }

    @Transactional
    public ScheduleResponse update(UUID scheduleId, ScheduleRequest request) {
        hodAccessService.requireHod();
        ClassSchedule schedule = findSchedule(scheduleId);
        GenerationSession generation = schedule.getGeneration();
        requireEditable(generation);
        editLockService.requireLockOwned(generation.getGenerationId());
        apply(schedule, request);
        ScheduleResponse response = toResponse(scheduleRepository.save(schedule));
        realtimeEventService.publishForGeneration(generation.getGenerationId(),
                TimetableRealtimeEventService.SCHEDULE_UPDATED,
                Map.of("generationId", generation.getGenerationId(),
                        "scheduleId", scheduleId));
        return response;
    }

    @Transactional
    public void delete(UUID scheduleId) {
        hodAccessService.requireHod();
        ClassSchedule schedule = findSchedule(scheduleId);
        GenerationSession generation = schedule.getGeneration();
        requireEditable(generation);
        editLockService.requireLockOwned(generation.getGenerationId());
        scheduleRepository.deleteById(scheduleId);
        realtimeEventService.publishForGeneration(generation.getGenerationId(),
                TimetableRealtimeEventService.SCHEDULE_DELETED,
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
            UUID candidateGroupId = request.teachingGroupId();
            if (candidateGroupId != null) {
                TeachingAssignmentGroup group = teachingGroupRepository.findById(candidateGroupId)
                        .orElseThrow(() -> new ResourceNotFoundException("Teaching group not found"));
                for (TeachingAssignmentGroupMember m : group.getMembers()) {
                    candidateStaff.add(m.getAssignment().getStaff().getStaffId());
                    candidateSections.add(m.getAssignment().getSection().getSectionId());
                }
                candidateCourse = group.getCourse().getCourseCode();
            } else {
                TeachingAssignment assignment = assignmentRepository.findById(request.teachingAssignmentId())
                        .orElseThrow();
                candidateStaff.add(assignment.getStaff().getStaffId());
                candidateSections.add(assignment.getSection().getSectionId());
                candidateCourse = assignment.getCourse().getCourseCode();
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
                    if (special || !Collections.disjoint(candidateStaff, otherStaff)
                            || !Collections.disjoint(candidateSections, otherSections)) {
                        String reason = special ? "a " + other.getScheduleType() + " period"
                                : (!Collections.disjoint(candidateStaff, otherStaff)
                                        ? "another engagement of the same lecturer"
                                        : "another schedule for the same section");
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
                schedule.getEndSlot().getSlotId(),
                schedule.getEndSlot().getPeriodNo(),
                schedule.getScheduleStatus(),
                schedule.getScheduleType(),
                sections,
                staffNames,
                schedule.getCreatedAt());
    }
}
