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

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ClassScheduleService {

    private final ClassScheduleRepository scheduleRepository;
    private final GenerationSessionRepository generationRepository;
    private final TeachingAssignmentRepository assignmentRepository;
    private final TimeSlotRepository timeSlotRepository;

    public ClassScheduleService(ClassScheduleRepository scheduleRepository,
                                GenerationSessionRepository generationRepository,
                                TeachingAssignmentRepository assignmentRepository,
                                TimeSlotRepository timeSlotRepository) {
        this.scheduleRepository = scheduleRepository;
        this.generationRepository = generationRepository;
        this.assignmentRepository = assignmentRepository;
        this.timeSlotRepository = timeSlotRepository;
    }

    public List<ScheduleResponse> getAll(UUID termId, UUID sectionId, UUID staffId, Integer dayOfWeek) {
        List<ClassSchedule> schedules;
        if (termId != null) {
            schedules = scheduleRepository.findByTermIdWithDetails(termId);
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

    public ScheduleResponse getById(UUID scheduleId) {
        return toResponse(findSchedule(scheduleId));
    }

    @Transactional
    public ScheduleResponse create(ScheduleRequest request) {
        ClassSchedule schedule = new ClassSchedule();
        apply(schedule, request);
        return toResponse(scheduleRepository.save(schedule));
    }

    @Transactional
    public ScheduleResponse update(UUID scheduleId, ScheduleRequest request) {
        ClassSchedule schedule = findSchedule(scheduleId);
        if (schedule.getGeneration().getStatus() == GenerationStatus.PUBLISHED) {
            throw new BusinessRuleException("Cannot modify schedules of a published timetable");
        }
        apply(schedule, request);
        return toResponse(scheduleRepository.save(schedule));
    }

    @Transactional
    public void delete(UUID scheduleId) {
        ClassSchedule schedule = findSchedule(scheduleId);
        if (schedule.getGeneration().getStatus() == GenerationStatus.PUBLISHED) {
            throw new BusinessRuleException("Cannot delete schedules of a published timetable");
        }
        scheduleRepository.deleteById(scheduleId);
    }

    private void apply(ClassSchedule schedule, ScheduleRequest request) {
        GenerationSession generation = generationRepository.findById(request.generationId())
                .orElseThrow(() -> new ResourceNotFoundException("Generation session not found"));
        if (generation.getStatus() == GenerationStatus.PUBLISHED) {
            throw new BusinessRuleException("Cannot modify schedules of a published timetable");
        }

        TeachingAssignment assignment = null;
        if (request.scheduleType() == ScheduleType.COURSE) {
            if (request.teachingAssignmentId() == null) {
                throw new ValidationException("teachingAssignmentId is required for COURSE schedules");
            }
            assignment = assignmentRepository.findById(request.teachingAssignmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Teaching assignment not found"));
            if (!assignment.getTerm().getTermId().equals(generation.getTerm().getTermId())) {
                throw new ValidationException("Teaching assignment does not belong to this generation's term");
            }
        } else {
            if (request.teachingAssignmentId() != null) {
                throw new ValidationException("teachingAssignmentId must be null for "
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

        validateNoConflicts(generation, schedule, request, startSlot, endSlot);

        schedule.setGeneration(generation);
        schedule.setTeachingAssignment(assignment);
        schedule.setDayOfWeek(request.dayOfWeek());
        schedule.setStartSlot(startSlot);
        schedule.setEndSlot(endSlot);
        schedule.setScheduleType(request.scheduleType());
        if (request.scheduleStatus() != null) {
            schedule.setScheduleStatus(request.scheduleStatus());
        }
    }

    private void validateNoConflicts(GenerationSession generation, ClassSchedule self,
                                     ScheduleRequest request, TimeSlot startSlot, TimeSlot endSlot) {
        List<ClassSchedule> daySchedules = scheduleRepository.findByGeneration_GenerationId(
                request.generationId()).stream()
                .filter(s -> s.getDayOfWeek().equals(request.dayOfWeek()))
                .filter(s -> !s.getScheduleId().equals(self != null ? self.getScheduleId() : null))
                .filter(s -> s.getScheduleStatus() != ScheduleStatus.CANCELLED)
                .toList();

        boolean overlapsSlot = overlaps(startSlot, endSlot, daySchedules);
        if (request.scheduleType() == ScheduleType.COURSE) {
            TeachingAssignment assignment = assignmentRepository.findById(request.teachingAssignmentId()).orElseThrow();
            for (ClassSchedule other : daySchedules) {
                boolean sameStaff = other.getTeachingAssignment() != null
                        && other.getTeachingAssignment().getStaff().getStaffId()
                                .equals(assignment.getStaff().getStaffId());
                boolean sameSection = other.getTeachingAssignment() != null
                        && other.getTeachingAssignment().getSection().getSectionId()
                                .equals(assignment.getSection().getSectionId());
                boolean special = other.getScheduleType() != ScheduleType.COURSE;
                if (overlapsSlot && (special || sameStaff || sameSection)) {
                    String reason = special ? "a " + other.getScheduleType() + " period"
                            : (sameStaff ? "another engagement of the same lecturer"
                                        : "another schedule for the same section");
                    if (overlaps(startSlot, endSlot, List.of(other))) {
                        throw new BusinessRuleException("Schedule conflicts with " + reason
                                + " on day " + request.dayOfWeek());
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
        return new ScheduleResponse(
                schedule.getScheduleId(),
                schedule.getGeneration().getGenerationId(),
                schedule.getTeachingAssignment() != null ? schedule.getTeachingAssignment().getAssignmentId() : null,
                schedule.getTeachingAssignment() != null ? schedule.getTeachingAssignment().getCourse().getCourseCode() : null,
                schedule.getTeachingAssignment() != null ? schedule.getTeachingAssignment().getStaff().getStaffName() : null,
                schedule.getTeachingAssignment() != null ? schedule.getTeachingAssignment().getSection().getSectionName() : null,
                schedule.getDayOfWeek(),
                schedule.getStartSlot().getSlotId(),
                schedule.getStartSlot().getPeriodNo(),
                schedule.getEndSlot().getSlotId(),
                schedule.getEndSlot().getPeriodNo(),
                schedule.getScheduleStatus(),
                schedule.getScheduleType(),
                schedule.getCreatedAt());
    }
}