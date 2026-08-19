package com.unicconnect.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unicconnect.dto.request.CreateGenerationRequest;
import com.unicconnect.dto.request.DragStatusRequest;
import com.unicconnect.dto.request.GenerateTimetableRequest;
import com.unicconnect.dto.response.GenerationManageResponse;
import com.unicconnect.dto.response.GenerationScopeSemester;
import com.unicconnect.dto.response.GenerationSessionResponse;
import com.unicconnect.dto.response.ScheduleResponse;
import com.unicconnect.entity.*;
import com.unicconnect.exception.BusinessRuleException;
import com.unicconnect.exception.ResourceNotFoundException;
import com.unicconnect.exception.TimetableConflictException;
import com.unicconnect.repository.*;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Constraint-aware timetable generator with backtracking, driven by
 * {@code course_meeting_requirements}.
 *
 * <p>Placement rules:
 * <ul>
 *   <li>Scope: teaching assignments are filtered by the generation term and by the
 *       selected Mid/Final exam type (Mid Term = semesters 1/3/5/7, Final Term =
 *       2/4/6/8, resolved from {@code semesters.semester_no}) and by the explicit
 *       semester/section selections made by the lobby creator.</li>
 *   <li>Course sessions are placed via backtracking search (Monday-Friday, 6 real
 *       time slots). Sessions for the same unit never share a day; consecutive-period
 *       sessions are allocated as Period X + Period X+1.</li>
 *   <li>No lecturer overlap, section overlap, or slot overlap.</li>
 *   <li>LECTURE sessions are placed before LAB sessions.</li>
 *   <li>Only LMS and ASSIGNMENT special periods are placed on free slots.
 *       No BREAK schedule rows are ever created (lunch is a slot gap).</li>
 *   <li>Generation is all-or-nothing: if any required session cannot be placed the
 *       transaction rolls back and a precise report is thrown.</li>
 *   <li>Combined-section courses (A+B+C etc.) are taught as ONE unit: an HOD groups
 *       the section assignments of a course (teaching_assignment_groups) and the
 *       generator places a single {@code class_schedules} row for the whole group.</li>
 * </ul>
 */
@Service
@Transactional
public class TimetableGenerationService {

    private static final Logger log = LoggerFactory.getLogger(TimetableGenerationService.class);
    private static final int WORKING_DAY_START = 1;
    private static final int WORKING_DAY_END = 5;
    private static final String[] DAY_NAMES = {
            "", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
    private static final LocalTime LUNCH_END = LocalTime.of(12, 0);
    private static final LocalTime LUNCH_START = LocalTime.of(13, 0);
    private static final int MAX_BACKTRACK_ITERATIONS_PER_SEMESTER = 10_000_000;
    private static final int MAX_SOLVE_ATTEMPTS = 4;
    private static final int MAX_ITERATIONS_PER_ATTEMPT = 1_500_000;
    private static final ThreadLocal<Random> SOLVE_RANDOM =
            ThreadLocal.withInitial(() -> new Random(0x5EED));

    private final GenerationSessionRepository generationRepository;
    private final TeachingAssignmentRepository assignmentRepository;
    private final TeachingAssignmentGroupMemberRepository groupMemberRepository;
    private final CourseMeetingRequirementRepository requirementRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final ClassScheduleRepository scheduleRepository;
    private final AcademicTermRepository termRepository;
    private final SemesterRepository semesterRepository;
    private final SectionRepository sectionRepository;
    private final ExamTypeRepository examTypeRepository;
    private final AttendanceRepository attendanceRepository;
    private final HodAccessService hodAccessService;
    private final TimetableLobbyRepository lobbyRepository;
    private final TimetableLobbyAccessService lobbyAccessService;
    private final TimetableRealtimeEventService realtimeEventService;
    private final ObjectMapper objectMapper;
    private final TimetableGenerationService self;
    private final ExecutorService generationExecutor = Executors.newFixedThreadPool(2);

    public TimetableGenerationService(GenerationSessionRepository generationRepository,
                                      TeachingAssignmentRepository assignmentRepository,
                                      TeachingAssignmentGroupMemberRepository groupMemberRepository,
                                      CourseMeetingRequirementRepository requirementRepository,
                                      TimeSlotRepository timeSlotRepository,
                                      ClassScheduleRepository scheduleRepository,
                                      AcademicTermRepository termRepository,
                                      SemesterRepository semesterRepository,
                                      SectionRepository sectionRepository,
                                      ExamTypeRepository examTypeRepository,
                                      AttendanceRepository attendanceRepository,
                                      HodAccessService hodAccessService,
                                      TimetableLobbyRepository lobbyRepository,
                                      TimetableLobbyAccessService lobbyAccessService,
                                      TimetableRealtimeEventService realtimeEventService,
                                      ObjectMapper objectMapper,
                                      @Lazy TimetableGenerationService self) {
        this.generationRepository = generationRepository;
        this.assignmentRepository = assignmentRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.requirementRepository = requirementRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.scheduleRepository = scheduleRepository;
        this.termRepository = termRepository;
        this.semesterRepository = semesterRepository;
        this.sectionRepository = sectionRepository;
        this.examTypeRepository = examTypeRepository;
        this.attendanceRepository = attendanceRepository;
        this.hodAccessService = hodAccessService;
        this.lobbyRepository = lobbyRepository;
        this.lobbyAccessService = lobbyAccessService;
        this.realtimeEventService = realtimeEventService;
        this.objectMapper = objectMapper;
        this.self = self;
    }

    @PreDestroy
    void shutdownGenerationExecutor() {
        generationExecutor.shutdownNow();
    }

    // ========== PUBLIC READ METHODS ==========

    public List<GenerationSessionResponse> getAll(UUID termId) {
        boolean hod = hodAccessService.currentHod().isPresent();
        List<GenerationSession> sessions = termId != null
                ? generationRepository.findByTerm_TermIdOrderByCreatedAtDesc(termId)
                : generationRepository.findAll();
        return sessions.stream()
                .filter(s -> hod || s.getStatus() == GenerationStatus.PUBLISHED)
                .map(TimetableGenerationService::toResponse).toList();
    }

    public GenerationSessionResponse getById(UUID generationId) {
        return toResponse(findGeneration(generationId));
    }

    public List<GenerationScopeSemester> getGenerationScope(UUID termId, UUID examTypeId) {
        Integer parity = resolveParity(examTypeId);
        List<Section> masterSections = sectionRepository.findAll().stream()
                .sorted(Comparator.comparing(Section::getSectionName))
                .toList();
        List<GenerationScopeSemester> result = new ArrayList<>();
        List<Semester> ordered = semesterRepository.findAll().stream()
                .sorted(Comparator.comparing(Semester::getSemesterNo))
                .toList();
        for (Semester s : ordered) {
            if (parity != null && s.getSemesterNo() % 2 != parity) continue;
            int semNo = s.getSemesterNo();
            List<GenerationScopeSemester.SectionInfo> sectionInfos = masterSections.stream()
                    .filter(sec -> semNo <= 2 ? !"CT".equals(sec.getSectionName()) : true)
                    .map(sec -> new GenerationScopeSemester.SectionInfo(
                            sec.getSectionId(), sec.getSectionName()))
                    .toList();
            result.add(new GenerationScopeSemester(s.getSemesterId(), semNo, sectionInfos));
        }
        return result;
    }

    public GenerationManageResponse getManagementContext(UUID termId) {
        Optional<Staff> hod = hodAccessService.currentHod();
        if (hod.isEmpty()) {
            return new GenerationManageResponse(false, false, null);
        }
        GenerationSession draft = null;
        if (termId != null) {
            draft = generationRepository.findByTerm_TermIdOrderByCreatedAtDesc(termId).stream()
                    .filter(g -> g.getStatus() != GenerationStatus.PUBLISHED)
                    .findFirst()
                    .orElse(null);
        }
        if (draft != null && !lobbyAccessService.canAccessSharedDraft(draft.getGenerationId())) {
            draft = null;
        }
        return new GenerationManageResponse(true, true, draft != null ? toResponse(draft) : null);
    }

    // ========== CREATE / GENERATE ==========

    public GenerationSessionResponse create(CreateGenerationRequest request) {
        AcademicTerm term = termRepository.findById(request.termId())
                .orElseThrow(() -> new ResourceNotFoundException("Academic term not found"));
        Staff generator = hodAccessService.requireHod();
        GenerationSession session = new GenerationSession();
        session.setTerm(term);
        session.setGeneratedByStaff(generator);
        session.setStatus(GenerationStatus.PENDING);
        return toResponse(generationRepository.save(session));
    }

    public GenerationSessionResponse generate(UUID generationId) {
        return doGenerate(generationId, null, null, null);
    }

    public GenerationSessionResponse generate(UUID generationId, UUID semesterId) {
        return doGenerate(generationId, semesterId, null, null);
    }

    public GenerationSessionResponse generate(UUID generationId, GenerateTimetableRequest request) {
        return doGenerate(generationId,
                null,
                request != null ? request.examTypeId() : null,
                request != null ? request.semesters() : null);
    }

    // ========== CORE GENERATION (backtracking) ==========

    private GenerationSessionResponse doGenerate(UUID generationId, UUID semesterId, UUID examTypeId,
                                                 List<GenerateTimetableRequest.SemesterSelection> selections) {
        Staff caller = hodAccessService.requireHod();
        GenerationSession generation = findGeneration(generationId);
        if (generation.getStatus() == GenerationStatus.PUBLISHED) {
            throw new BusinessRuleException("A published timetable cannot be regenerated");
        }

        lobbyRepository.findByGeneration_GenerationId(generationId).ifPresent(lobby -> {
            if (lobby.getStatus() == LobbyStatus.OPEN) {
                throw new BusinessRuleException(
                        "Generation is locked until every invited HOD joins the lobby");
            }
            if (lobby.getStatus() != LobbyStatus.COMPLETED
                    && !lobby.getLeaderStaff().getStaffId().equals(caller.getStaffId())) {
                throw new BusinessRuleException(
                        "Only the lobby leader (creator) can change the shared generation scope");
            }
        });

        Integer parity = resolveParity(examTypeId);
        String examTypeName = parity != null
                ? (parity == 1 ? "Mid Term" : "Final Term") : null;

        // Build semester -> section scope.
        Map<UUID, Set<UUID>> scope = new HashMap<>();
        if (selections != null && !selections.isEmpty()) {
            for (GenerateTimetableRequest.SemesterSelection sel : selections) {
                if (sel.semesterId() == null) continue;
                Semester sem = semesterRepository.findById(sel.semesterId())
                        .orElseThrow(() -> new ResourceNotFoundException("Semester not found"));
                if (parity != null && sem.getSemesterNo() % 2 != parity) {
                    throw new BusinessRuleException("Semester " + sem.getSemesterNo()
                            + " does not belong to the " + examTypeName + " group");
                }
                Set<UUID> sections = (sel.sectionIds() != null && !sel.sectionIds().isEmpty())
                        ? new HashSet<>(sel.sectionIds())
                        : null;
                scope.put(sem.getSemesterId(), sections);
            }
        } else if (parity != null) {
            for (Semester s : semesterRepository.findAll()) {
                if (s.getSemesterNo() % 2 == parity) {
                    scope.put(s.getSemesterId(), null);
                }
            }
        } else if (semesterId != null) {
            semesterRepository.findById(semesterId)
                    .orElseThrow(() -> new ResourceNotFoundException("Semester not found"));
            scope.put(semesterId, null);
        }

        generation.setScopeJson(toScopeJson(examTypeId, scope));

        // Load assignments
        List<TeachingAssignment> assignments = assignmentRepository
                .findWithDetailsByTermId(generation.getTerm().getTermId()).stream()
                .filter(a -> a.getAssignmentStatus() != AssignmentStatus.CANCELLED)
                .filter(a -> inScope(a, scope))
                .toList();

        // Load groups
        List<TeachingAssignmentGroupMember> groupMembers = groupMemberRepository
                .findWithDetailsByTermId(generation.getTerm().getTermId()).stream()
                .filter(m -> m.getAssignment().getAssignmentStatus() != AssignmentStatus.CANCELLED)
                .toList();
        Map<UUID, List<TeachingAssignmentGroupMember>> membersByGroup = new LinkedHashMap<>();
        Set<UUID> groupedAssignmentIds = new HashSet<>();
        for (TeachingAssignmentGroupMember m : groupMembers) {
            groupedAssignmentIds.add(m.getAssignment().getAssignmentId());
            membersByGroup.computeIfAbsent(m.getGroup().getGroupId(), k -> new ArrayList<>()).add(m);
        }

        if (assignments.isEmpty() && membersByGroup.isEmpty()) {
            throw new BusinessRuleException(
                    "No courses or teaching assignments found for the selected term, semesters and sections");
        }

        // Load CMRs
        Map<UUID, List<CourseMeetingRequirement>> requirementsByCourse = new HashMap<>();
        {
            Set<UUID> courseIds = new HashSet<>();
            for (TeachingAssignment a : assignments) courseIds.add(a.getCourse().getCourseId());
            for (List<TeachingAssignmentGroupMember> members : membersByGroup.values()) {
                courseIds.add(members.get(0).getGroup().getCourse().getCourseId());
            }
            for (CourseMeetingRequirement r : requirementRepository
                    .findAllByCourse_CourseIdIn(courseIds)) {
                requirementsByCourse.computeIfAbsent(r.getCourse().getCourseId(),
                        k -> new ArrayList<>()).add(r);
            }
            requirementsByCourse.values()
                    .forEach(list -> list.sort(Comparator.comparing(r -> r.getMeetingType())));
        }

        // ========== VALIDATION ==========
        List<String> errors = new ArrayList<>();

        // Missing CMRs
        for (TeachingAssignment a : assignments) {
            if (requirementsByCourse.getOrDefault(a.getCourse().getCourseId(), List.of()).isEmpty()) {
                errors.add(describe(a) + ": course has no course meeting requirement");
            }
        }

        // Split singletons from grouped
        List<TeachingAssignment> singletons = new ArrayList<>();
        for (TeachingAssignment a : assignments) {
            if (!groupedAssignmentIds.contains(a.getAssignmentId())) {
                singletons.add(a);
            }
        }

        // Group scope validation
        for (List<TeachingAssignmentGroupMember> members : membersByGroup.values()) {
            TeachingAssignmentGroup group = members.get(0).getGroup();
            Course course = group.getCourse();
            Semester sem = course.getSemester();
            boolean semesterInScope = scope.isEmpty()
                    || (sem != null && scope.containsKey(sem.getSemesterId()));
            if (!semesterInScope) continue;
            Set<UUID> scopeSections = sem != null ? scope.get(sem.getSemesterId()) : null;
            if (scopeSections != null) {
                for (TeachingAssignmentGroupMember m : members) {
                    if (!scopeSections.contains(m.getAssignment().getSection().getSectionId())) {
                        throw new BusinessRuleException("Cannot generate timetable: combined class "
                                + describeGroup(members) + " is only partially selected. All sections of a "
                                + "combined class must be selected together.");
                    }
                }
            }
            if (requirementsByCourse.getOrDefault(course.getCourseId(), List.of()).isEmpty()) {
                errors.add(describeGroup(members) + ": course has no course meeting requirement");
            }
        }

        // Rule 1: Course-semester code validation
        for (TeachingAssignment a : singletons) {
            String err = validateCourseSemesterCode(a.getCourse());
            if (err != null) errors.add(describe(a) + ": " + err);
        }
        for (List<TeachingAssignmentGroupMember> members : membersByGroup.values()) {
            String err = validateCourseSemesterCode(members.get(0).getGroup().getCourse());
            if (err != null) errors.add(describeGroup(members) + ": " + err);
        }

        // Rule 2: Course-semester must match generation scope
        for (TeachingAssignment a : singletons) {
            String err = validateCourseGenerationScope(a.getCourse(), scope);
            if (err != null) errors.add(describe(a) + ": " + err);
        }
        for (List<TeachingAssignmentGroupMember> members : membersByGroup.values()) {
            String err = validateCourseGenerationScope(members.get(0).getGroup().getCourse(), scope);
            if (err != null) errors.add(describeGroup(members) + ": " + err);
        }

        // Rule 3: CS/CT separation
        for (TeachingAssignment a : singletons) {
            String err = validateCsCtSeparation(a, scope);
            if (err != null) errors.add(describe(a) + ": " + err);
        }
        for (List<TeachingAssignmentGroupMember> members : membersByGroup.values()) {
            for (TeachingAssignmentGroupMember m : members) {
                String err = validateCsCtSeparation(m.getAssignment(), scope);
                if (err != null) errors.add(describe(m.getAssignment()) + ": " + err);
            }
        }

        // Rule 4: Lecturer ownership
        for (TeachingAssignment a : singletons) {
            String err = validateLecturerOwnership(a);
            if (err != null) errors.add(describe(a) + ": " + err);
        }
        for (List<TeachingAssignmentGroupMember> members : membersByGroup.values()) {
            for (TeachingAssignmentGroupMember m : members) {
                String err = validateLecturerOwnership(m.getAssignment());
                if (err != null) errors.add(describe(m.getAssignment()) + ": " + err);
            }
        }

        if (!errors.isEmpty()) {
            throw new BusinessRuleException("Cannot generate timetable:\n" + String.join("\n", errors));
        }

        // ========== ASYNC GENERATION ==========
        if (generation.getStatus() == GenerationStatus.GENERATING) {
            throw new BusinessRuleException("Timetable generation is already in progress");
        }
        generation.setStatus(GenerationStatus.GENERATING);
        generation.setStartedAt(Instant.now());
        generation.setFinishedAt(null);
        generation = generationRepository.save(generation);

        // Live "generating" state: every connected HOD mirrors the loading UI.
        realtimeEventService.publishForGeneration(generationId,
                TimetableRealtimeEventService.GENERATION_STARTED,
                Map.of("generationId", generationId));

        // Heavy solving runs on a background worker so the HTTP request returns
        // immediately (no proxy/gateway timeouts). afterCommit guarantees the
        // worker only starts once the GENERATING status is committed; the
        // worker's own transaction rolls back on failure, preserving the
        // previously stored schedules.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                generationExecutor.submit(() -> {
                    try {
                        self.runGenerationBackground(generationId);
                    } catch (Exception e) {
                        log.error("Background timetable generation failed for generation {}",
                                generationId, e);
                        self.markGenerationFailed(generationId);
                    }
                });
            }
        });
        return toResponse(generation);
    }

    /**
     * Heavy generation worker. Runs in its own transaction on a background
     * thread (invoked through the Spring proxy so {@code @Transactional}
     * applies). Any exception rolls back the worker transaction â€” previously
     * stored schedules are preserved â€” and the caller then flips the session
     * to FAILED via {@link #markGenerationFailed}.
     */
    public GenerationSessionResponse runGenerationBackground(UUID generationId) {
        GenerationSession generation = findGeneration(generationId);
        if (generation.getStatus() != GenerationStatus.GENERATING) {
            return toResponse(generation);
        }

        // Re-derive scope and exam type from the persisted scope JSON: the
        // worker transaction must not reuse any request-scoped state.
        PersistedScope persisted = parseScope(generation.getScopeJson());
        Map<UUID, Set<UUID>> scope = toScopeMap(persisted);
        UUID examTypeId = persisted != null ? persisted.examTypeId() : null;
        Integer parity = resolveParity(examTypeId);
        String examTypeName = parity != null ? (parity == 1 ? "Mid Term" : "Final Term") : null;

        // Load assignments
        List<TeachingAssignment> assignments = assignmentRepository
                .findWithDetailsByTermId(generation.getTerm().getTermId()).stream()
                .filter(a -> a.getAssignmentStatus() != AssignmentStatus.CANCELLED)
                .filter(a -> inScope(a, scope))
                .toList();

        // Load groups
        List<TeachingAssignmentGroupMember> groupMembers = groupMemberRepository
                .findWithDetailsByTermId(generation.getTerm().getTermId()).stream()
                .filter(m -> m.getAssignment().getAssignmentStatus() != AssignmentStatus.CANCELLED)
                .toList();
        Map<UUID, List<TeachingAssignmentGroupMember>> membersByGroup = new LinkedHashMap<>();
        Set<UUID> groupedAssignmentIds = new HashSet<>();
        for (TeachingAssignmentGroupMember m : groupMembers) {
            groupedAssignmentIds.add(m.getAssignment().getAssignmentId());
            membersByGroup.computeIfAbsent(m.getGroup().getGroupId(), k -> new ArrayList<>()).add(m);
        }

        // Load CMRs
        Map<UUID, List<CourseMeetingRequirement>> requirementsByCourse = new HashMap<>();
        {
            Set<UUID> courseIds = new HashSet<>();
            for (TeachingAssignment a : assignments) courseIds.add(a.getCourse().getCourseId());
            for (List<TeachingAssignmentGroupMember> members : membersByGroup.values()) {
                courseIds.add(members.get(0).getGroup().getCourse().getCourseId());
            }
            for (CourseMeetingRequirement r : requirementRepository
                    .findAllByCourse_CourseIdIn(courseIds)) {
                requirementsByCourse.computeIfAbsent(r.getCourse().getCourseId(),
                        k -> new ArrayList<>()).add(r);
            }
            requirementsByCourse.values()
                    .forEach(list -> list.sort(Comparator.comparing(r -> r.getMeetingType())));
        }

        List<TeachingAssignment> singletons = new ArrayList<>();
        for (TeachingAssignment a : assignments) {
            if (!groupedAssignmentIds.contains(a.getAssignmentId())) {
                singletons.add(a);
            }
        }

        // Scope validation already ran synchronously; the worker re-derives the
        // exact same unit set and runs the solver.
        List<ClassSchedule> existing = scheduleRepository.findByGeneration_GenerationId(generationId);
        scheduleRepository.deleteAll(existing);
        scheduleRepository.flush();

        List<TimeSlot> slots = timeSlotRepository.findAllByOrderByDisplayOrderAscPeriodNoAsc();
        if (slots.isEmpty()) {
            generation.setStatus(GenerationStatus.FAILED);
            generation.setFinishedAt(Instant.now());
            return toResponse(generationRepository.save(generation));
        }

        // Build scheduling units from assignments + groups + CMRs
        List<SchedulingUnit> units = buildSchedulingUnits(
                singletons, membersByGroup, requirementsByCourse, scope);

        // Backtracking solver â€” solve per-semester for performance
        List<ClassSchedule> created = new ArrayList<>();
        List<String> failureReport = new ArrayList<>();
        ConflictGrid grid = new ConflictGrid();
        boolean solved = solveBacktrackingPerSemester(units, slots, generation, created, failureReport, grid);

        if (!solved) {
            generation.setStatus(GenerationStatus.FAILED);
            generation.setFinishedAt(Instant.now());
            generationRepository.save(generation);
            throw new BusinessRuleException("Timetable generation failed:\n"
                    + String.join("\n", failureReport));
        }

        // LMS / ASSIGNMENT special periods
        for (int day = WORKING_DAY_START; day <= WORKING_DAY_END; day++) {
            int freeSlot = firstFreeSlot(day, slots, created);
            if (freeSlot >= 0) {
                placeSpecial(generation, ScheduleType.LMS, freeSlot, day, slots, created);
            }
        }
        for (int day = WORKING_DAY_START; day <= WORKING_DAY_END; day++) {
            int freeSlot = firstFreeSlot(day, slots, created);
            if (freeSlot >= 0) {
                placeSpecial(generation, ScheduleType.ASSIGNMENT, freeSlot, day, slots, created);
            }
        }

        scheduleRepository.saveAll(created);

        generation.setStatus(GenerationStatus.COMPLETED);
        generation.setFinishedAt(Instant.now());
        generation = generationRepository.save(generation);
        log.info("Timetable generated: {} schedules for generation {} (examType={})",
                created.size(), generationId, examTypeName);
        realtimeEventService.publishForGeneration(generationId,
                TimetableRealtimeEventService.GENERATION_COMPLETED,
                Map.of("generationId", generationId));
        return toResponse(generation);
    }

    /**
     * Flips a generation to FAILED after a background worker crashed. Runs in
     * its own transaction; safe to call from the worker's catch block.
     */
    public void markGenerationFailed(UUID generationId) {
        try {
            GenerationSession generation = findGeneration(generationId);
            generation.setStatus(GenerationStatus.FAILED);
            generation.setFinishedAt(Instant.now());
            generationRepository.save(generation);
            realtimeEventService.publishForGeneration(generationId,
                    TimetableRealtimeEventService.GENERATION_FAILED,
                    Map.of("generationId", generationId));
            log.warn("Generation {} marked FAILED after a background worker failure", generationId);
        } catch (ResourceNotFoundException e) {
            log.warn("Generation {} no longer exists; ignoring background worker failure", generationId);
        }
    }

    // ========== SCHEDULING UNIT ==========

    private static class SchedulingUnit {
        final String label;
        final TeachingAssignment assignment;
        final TeachingAssignmentGroup group;
        final Set<UUID> staffIds;
        final Set<UUID> sectionIds;
        final int sessionsPerWeek;
        final int periodsPerSession;
        final int priority;
        final UUID semesterId;
        /**
         * Elective group key (non-null only for elective singleton units).
         * Identity: course.is_required=false + semester_id. Group members may
         * co-locate on identical windows (same day/start/end, same section)
         * only while their lecturers are distinct; the lecturer conflict rule
         * always wins. Null for required courses and combined-group units.
         */
        final String electiveGroup;

        SchedulingUnit(String label, TeachingAssignment assignment, TeachingAssignmentGroup group,
                        Set<UUID> staffIds, Set<UUID> sectionIds,
                        int sessionsPerWeek, int periodsPerSession, int priority, UUID semesterId,
                        String electiveGroup) {
            this.label = label;
            this.assignment = assignment;
            this.group = group;
            this.staffIds = staffIds;
            this.sectionIds = sectionIds;
            this.sessionsPerWeek = sessionsPerWeek;
            this.periodsPerSession = periodsPerSession;
            this.priority = priority;
            this.semesterId = semesterId;
            this.electiveGroup = electiveGroup;
        }

        boolean isElective() {
            return electiveGroup != null;
        }
    }

    /**
     * O(1) conflict grid that tracks which staff/section IDs occupy which day+period.
     * Internal representation: per-day 6-bit occupancy masks (P1=bit0 .. P6=bit5),
     * keyed by staffId, and by semesterId+sectionId. Semester-scoped section keys
     * guarantee Section A in Sem 1 never conflicts with Section A in Sem 3.
     * <p>Section occupancy is recorded per occupant (owner key) with the exact
     * window and optional elective-group key, so elective group members may
     * co-locate on an IDENTICAL window (same day/start/end, same section) while
     * every partial overlap and every staff overlap stays rejected.
     * This is an in-memory solver optimization only; nothing is persisted.
     */
    private static class ConflictGrid {
        /** staffId -> day -> 6-bit period occupancy mask. */
        private final Map<UUID, Map<Integer, Integer>> staff = new HashMap<>();
        /** semesterId -> sectionId -> day -> component/unit key -> occupancy record. */
        private final Map<UUID, Map<UUID, Map<Integer, Map<Object, SectionOcc>>>> sections = new HashMap<>();
        /**
         * Established elective-group windows per section: semesterId -> sectionId ->
         * groupKey -> weekly occurrence (1-based) -> window chosen by the first
         * member to place that occurrence. Later members of the same group in the
         * same section are forced onto the same window, which is what makes
         * co-location a hard constraint (physical load = 2 windows per group).
         */
        private final Map<UUID, Map<UUID, Map<String, Map<Integer, GroupWindow>>>> groupWindows = new HashMap<>();

        private static final class SectionOcc {
            final int mask;
            final int start;
            final int end;
            final String groupKey;
            SectionOcc(int mask, int start, int end, String groupKey) {
                this.mask = mask;
                this.start = start;
                this.end = end;
                this.groupKey = groupKey;
            }
        }

        private static final class GroupWindow {
            final UUID ownerKey;
            final int day;
            final int start;
            final int end;
            GroupWindow(UUID ownerKey, int day, int start, int end) {
                this.ownerKey = ownerKey;
                this.day = day;
                this.start = start;
                this.end = end;
            }
        }

        private static int periodMask(int startOrder, int endOrder) {
            int mask = 0;
            for (int p = startOrder; p <= endOrder; p++) {
                mask |= (1 << (p - 1));
            }
            return mask;
        }

        private static int dayMask(Map<UUID, Map<Integer, Integer>> outer, UUID key, int day) {
            Map<Integer, Integer> days = outer.get(key);
            return days != null ? days.getOrDefault(day, 0) : 0;
        }

        private static void orDay(Map<UUID, Map<Integer, Integer>> outer, UUID key, int day, int mask) {
            outer.computeIfAbsent(key, k -> new HashMap<>()).merge(day, mask, (a, b) -> a | b);
        }

        private static void andNotDay(Map<UUID, Map<Integer, Integer>> outer, UUID key, int day, int mask) {
            Map<Integer, Integer> days = outer.get(key);
            if (days == null) return;
            int next = days.getOrDefault(day, 0) & ~mask;
            if (next == 0) {
                days.remove(day);
                if (days.isEmpty()) outer.remove(key);
            } else {
                days.put(day, next);
            }
        }

        boolean canPlace(Set<UUID> staffIds, Set<UUID> sectionIds, UUID semesterId,
                         int day, int startOrder, int endOrder, String electiveGroup) {
            int mask = periodMask(startOrder, endOrder);
            for (UUID id : staffIds) {
                if ((dayMask(staff, id, day) & mask) != 0) return false;
            }
            Map<UUID, Map<Integer, Map<Object, SectionOcc>>> secDays = sections.get(semesterId);
            if (secDays != null) {
                for (UUID id : sectionIds) {
                    Map<Integer, Map<Object, SectionOcc>> dayOcc = secDays.get(id);
                    if (dayOcc == null) continue;
                    Map<Object, SectionOcc> occ = dayOcc.get(day);
                    if (occ == null) continue;
                    for (SectionOcc o : occ.values()) {
                        if ((o.mask & mask) == 0) continue;
                        // Only an IDENTICAL window of a same-group elective may overlap.
                        if (electiveGroup == null
                                || !electiveGroup.equals(o.groupKey)
                                || o.start != startOrder || o.end != endOrder) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        /**
         * The window already established by the group for the given weekly
         * occurrence in this section, or null when no member placed it yet.
         */
        int[] forcedWindow(String electiveGroup, UUID sectionId, UUID semesterId, int occurrence) {
            Map<UUID, Map<String, Map<Integer, GroupWindow>>> bySec = groupWindows.get(semesterId);
            if (bySec == null) return null;
            Map<String, Map<Integer, GroupWindow>> byGroup = bySec.get(sectionId);
            if (byGroup == null) return null;
            Map<Integer, GroupWindow> occ = byGroup.get(electiveGroup);
            if (occ == null) return null;
            GroupWindow w = occ.get(occurrence);
            return w != null ? new int[] {w.day, w.start, w.end} : null;
        }

        void place(Set<UUID> staffIds, Set<UUID> sectionIds, UUID semesterId,
                   int day, int startOrder, int endOrder, String electiveGroup, SchedulingUnit unitKey,
                   int occurrence) {
            int mask = periodMask(startOrder, endOrder);
            for (UUID id : staffIds) {
                orDay(staff, id, day, mask);
            }
            Map<UUID, Map<Integer, Map<Object, SectionOcc>>> secDays =
                    sections.computeIfAbsent(semesterId, k -> new HashMap<>());
            for (UUID id : sectionIds) {
                Map<Integer, Map<Object, SectionOcc>> dayOcc =
                        secDays.computeIfAbsent(id, k -> new HashMap<>());
                dayOcc.computeIfAbsent(day, k -> new HashMap<>())
                        .put(unitKey, new SectionOcc(mask, startOrder, endOrder, electiveGroup));
                if (electiveGroup != null) {
                    Map<String, Map<Integer, GroupWindow>> byGroup =
                            groupWindows.computeIfAbsent(semesterId, k -> new HashMap<>())
                                    .computeIfAbsent(id, k -> new HashMap<>());
                    byGroup.computeIfAbsent(electiveGroup, k -> new HashMap<>())
                            .putIfAbsent(occurrence,
                                    new GroupWindow(unitOwnerKey(unitKey), day, startOrder, endOrder));
                }
            }
        }

        void remove(Set<UUID> staffIds, Set<UUID> sectionIds, UUID semesterId,
                    int day, int startOrder, int endOrder, String electiveGroup, SchedulingUnit unitKey,
                    int occurrence) {
            int mask = periodMask(startOrder, endOrder);
            for (UUID id : staffIds) {
                andNotDay(staff, id, day, mask);
            }
            Map<UUID, Map<Integer, Map<Object, SectionOcc>>> secDays = sections.get(semesterId);
            if (secDays == null) return;
            for (UUID id : sectionIds) {
                Map<Integer, Map<Object, SectionOcc>> dayOcc = secDays.get(id);
                if (dayOcc == null) continue;
                Map<Object, SectionOcc> occ = dayOcc.get(day);
                if (occ == null) continue;
                occ.remove(unitKey);
                if (electiveGroup != null) {
                    // The established window for this occurrence survives as long as
                    // any same-group member still occupies this exact window: otherwise
                    // backtracking the establishing member could strand another member
                    // on a window the group has abandoned (co-location would break).
                    boolean survivor = false;
                    for (SectionOcc o : occ.values()) {
                        if (electiveGroup.equals(o.groupKey)
                                && o.start == startOrder && o.end == endOrder) {
                            survivor = true;
                            break;
                        }
                    }
                    if (!survivor) {
                        Map<UUID, Map<String, Map<Integer, GroupWindow>>> bySec = groupWindows.get(semesterId);
                        if (bySec != null) {
                            Map<String, Map<Integer, GroupWindow>> byGroup = bySec.get(id);
                            if (byGroup != null) {
                                Map<Integer, GroupWindow> gw = byGroup.get(electiveGroup);
                                if (gw != null) {
                                    gw.remove(occurrence);
                                }
                            }
                        }
                    }
                }
                if (occ.isEmpty()) {
                    dayOcc.remove(day);
                    if (dayOcc.isEmpty()) {
                        secDays.remove(id);
                        if (secDays.isEmpty()) sections.remove(semesterId);
                    }
                }
            }
        }
    }

    private List<SchedulingUnit> buildSchedulingUnits(
            List<TeachingAssignment> singletons,
            Map<UUID, List<TeachingAssignmentGroupMember>> membersByGroup,
            Map<UUID, List<CourseMeetingRequirement>> requirementsByCourse,
            Map<UUID, Set<UUID>> scope) {

        List<SchedulingUnit> units = new ArrayList<>();

        // Groups first (priority 0 = highest)
        for (List<TeachingAssignmentGroupMember> members : membersByGroup.values()) {
            TeachingAssignmentGroup group = members.get(0).getGroup();
            Course course = group.getCourse();
            Semester sem = course.getSemester();
            if (!scope.isEmpty() && (sem == null || !scope.containsKey(sem.getSemesterId()))) continue;

            Set<UUID> staffIds = new HashSet<>();
            Set<UUID> sectionIds = new HashSet<>();
            for (TeachingAssignmentGroupMember m : members) {
                if (!inScope(m.getAssignment(), scope)) continue;
                staffIds.add(m.getAssignment().getStaff().getStaffId());
                sectionIds.add(m.getAssignment().getSection().getSectionId());
            }
            if (sectionIds.isEmpty()) continue;

            List<CourseMeetingRequirement> reqs =
                    requirementsByCourse.getOrDefault(course.getCourseId(), List.of());
            for (CourseMeetingRequirement req : reqs) {
                units.add(new SchedulingUnit(
                        describeGroup(members) + " / " + req.getMeetingType(),
                        null, group, staffIds, sectionIds,
                        req.getSessionsPerWeek(), req.getPeriodsPerSession(), 0,
                        sem != null ? sem.getSemesterId() : null, null));
            }
        }

        // Singletons (priority 1)
        for (TeachingAssignment a : singletons) {
            Set<UUID> staffIds = Set.of(a.getStaff().getStaffId());
            Set<UUID> sectionIds = Set.of(a.getSection().getSectionId());
            UUID semId = a.getCourse().getSemester() != null
                    ? a.getCourse().getSemester().getSemesterId() : null;
            // Elective group identity: is_required=false + semester_id.
            String electiveGroup = (!a.getCourse().isRequired() && semId != null)
                    ? semId.toString() : null;

            List<CourseMeetingRequirement> reqs =
                    requirementsByCourse.getOrDefault(a.getCourse().getCourseId(), List.of());
            for (CourseMeetingRequirement req : reqs) {
                units.add(new SchedulingUnit(
                        describe(a) + " / " + req.getMeetingType(),
                        a, null, staffIds, sectionIds,
                        req.getSessionsPerWeek(), req.getPeriodsPerSession(), 1, semId,
                        electiveGroup));
            }
        }

        // Sort: groups first, then 2-period before 1-period, then more sessions first
        units.sort(Comparator.comparingInt((SchedulingUnit u) -> u.priority)
                .thenComparingInt((SchedulingUnit u) -> -u.periodsPerSession)
                .thenComparingInt((SchedulingUnit u) -> -u.sessionsPerWeek));

        return units;
    }

    // ========== BACKTRACKING SOLVER (per-semester) ==========

    private boolean solveBacktrackingPerSemester(List<SchedulingUnit> units, List<TimeSlot> slots,
                                                  GenerationSession generation,
                                                  List<ClassSchedule> created, List<String> failureReport,
                                                  ConflictGrid grid) {
        // Group units by semester, preserving priority order within each semester
        LinkedHashMap<UUID, List<SchedulingUnit>> bySemester = new LinkedHashMap<>();
        for (SchedulingUnit u : units) {
            bySemester.computeIfAbsent(u.semesterId, k -> new ArrayList<>()).add(u);
        }

        // Weekday COURSE-load accounting per section (days 1-5), keyed per
        // (semester, section) so balance is computed on the section's own
        // semester load, not mixed across semesters.
        Map<String, int[]> sectionDayLoads = new HashMap<>();

        for (Map.Entry<UUID, List<SchedulingUnit>> entry : bySemester.entrySet()) {
            List<SchedulingUnit> semUnits = entry.getValue();

            // Fast capacity pre-check: count total required periods per section
            // Available: 6 periods x 5 days = 30 period-slots per section
            if (!checkCapacityPreconditions(semUnits, failureReport)) {
                return false;
            }

            int[] counter = {0};
            Map<Object, Set<Integer>> usedDaysByCourse = new HashMap<>();
            Map<SchedulingUnit, Integer> placedCounts = new HashMap<>();
            boolean ok = solveWithRestarts(semUnits, slots, generation, created, failureReport, grid, sectionDayLoads);
            if (!ok) {
                if (failureReport.isEmpty()) {
                    failureReport.add("No valid timetable exists for the given constraints.");
                }
                return false;
            }
        }
        return true;
    }

    private boolean checkCapacityPreconditions(List<SchedulingUnit> units, List<String> failureReport) {
        // Sum the required periods of every component of a course first, then apply
        // elective co-location: a group shares windows, so it contributes the load of
        // its largest member course only. Component-level max() would undercount a
        // course split across multiple rows (e.g. 2+2+2+2).
        Map<Object, Integer> periodsByCourse = new HashMap<>();
        for (SchedulingUnit u : units) {
            periodsByCourse.merge(courseKey(u), u.sessionsPerWeek * u.periodsPerSession, Integer::sum);
        }
        Map<UUID, Map<String, Integer>> electiveMaxBySection = new HashMap<>();
        Map<UUID, Integer> periodsBySection = new HashMap<>();
        Map<Object, Set<UUID>> counted = new HashMap<>();
        for (SchedulingUnit u : units) {
            int total = periodsByCourse.get(courseKey(u));
            for (UUID sectionId : u.sectionIds) {
                if (!counted.computeIfAbsent(courseKey(u), k -> new HashSet<>()).add(sectionId)) continue;
                if (u.isElective()) {
                    electiveMaxBySection.computeIfAbsent(sectionId, k -> new HashMap<>())
                            .merge(u.electiveGroup, total, Math::max);
                } else {
                    periodsBySection.merge(sectionId, total, Integer::sum);
                }
            }
        }
        for (Map.Entry<UUID, Map<String, Integer>> e : electiveMaxBySection.entrySet()) {
            for (int max : e.getValue().values()) {
                periodsBySection.merge(e.getKey(), max, Integer::sum);
            }
        }
        // Available per section: 6 periods x 5 days = 30 period-slots
        int maxSlots = 6 * 5;
        for (Map.Entry<UUID, Integer> e : periodsBySection.entrySet()) {
            if (e.getValue() > maxSlots) {
                failureReport.add("Section requires " + e.getValue()
                        + " period-slots/week but only " + maxSlots + " are available.");
                return false;
            }
        }
        return true;
    }

/**
     * Represents a valid placement option for a unit session.
     */
    private static class PlacementOption {
        final int day;
        final int startIdx;
        final int endIdx;
        final int startOrder;
        final int endOrder;

        PlacementOption(int day, int startIdx, int periodsPerSession) {
            this.day = day;
            this.startIdx = startIdx;
            this.endIdx = startIdx + periodsPerSession - 1;
            this.startOrder = startIdx + 1;
            this.endOrder = startIdx + periodsPerSession;
        }
    }

    private static UUID unitOwnerKey(SchedulingUnit u) {
        return u.assignment != null ? u.assignment.getAssignmentId() : u.group.getGroupId();
    }

    /**
     * Course identity shared by all components of a course (one unit per CMR row):
     * the assignment object for singletons, the group object for co-taught groups.
     * Used to key course-level state (used days, occurrence, capacity total).
     */
    private static Object courseKey(SchedulingUnit u) {
        return u.assignment != null ? u.assignment : u.group;
    }

    /**
     * Randomized-restart wrapper around the backtracking search. Symmetric
     * elective/combined configurations can make a single deterministic pass
     * thrash; restarting with a different unit order and option perturbation
     * (bounded total work) escapes those plateaus.
     */
    private boolean solveWithRestarts(List<SchedulingUnit> semUnits, List<TimeSlot> slots,
                                      GenerationSession generation, List<ClassSchedule> created,
                                      List<String> failureReport, ConflictGrid grid,
                                      Map<String, int[]> sectionDayLoads) {
        for (int attempt = 0; attempt < MAX_SOLVE_ATTEMPTS; attempt++) {
            SOLVE_RANDOM.set(new Random(0x5EED + attempt * 101L));
            List<SchedulingUnit> attemptUnits = new ArrayList<>(semUnits);
            if (attempt > 0) {
                Collections.shuffle(attemptUnits, SOLVE_RANDOM.get());
            }
            int[] counter = {0};
            boolean[] iterationLimitReached = {false};
            Map<Object, Set<Integer>> usedDaysByCourse = new HashMap<>();
            Map<SchedulingUnit, Integer> placedCounts = new HashMap<>();
            if (solveRecursive(attemptUnits, slots, generation, created, failureReport,
                    counter, iterationLimitReached, grid, usedDaysByCourse, placedCounts,
                    sectionDayLoads)) {
                return true;
            }
            if (!iterationLimitReached[0]) {
                return false;
            }
            failureReport.clear();
        }
        if (failureReport.isEmpty()) {
            failureReport.add("No valid timetable exists for the given constraints.");
        }
        return false;
    }

    private boolean solveRecursive(List<SchedulingUnit> units, List<TimeSlot> slots,
                                    GenerationSession generation, List<ClassSchedule> created,
                                    List<String> failureReport, int[] counter,
                                    boolean[] iterationLimitReached, ConflictGrid grid,
                                    Map<Object, Set<Integer>> usedDaysByCourse,
                                    Map<SchedulingUnit, Integer> placedCounts,
                                    Map<String, int[]> sectionDayLoads) {
        if (unitIdx(units, placedCounts) >= units.size()) return true;

        if (++counter[0] > MAX_ITERATIONS_PER_ATTEMPT) {
            iterationLimitReached[0] = true;
            return false;
        }
        // Most-constrained-first: find unscheduled unit with fewest valid placements
        SchedulingUnit bestUnit = selectMostConstrainedUnit(units, slots, grid, usedDaysByCourse, placedCounts);
        if (bestUnit == null) {
            // All units fully scheduled
            return true;
        }

        Set<Integer> usedDays = usedDaysByCourse.getOrDefault(courseKey(bestUnit), new HashSet<>());
        List<PlacementOption> options = generateValidPlacements(bestUnit, usedDays, slots, grid);

        if (options.isEmpty()) {
            if (failureReport.isEmpty()) {
                diagnoseUnit(bestUnit, slots, usedDays, created, failureReport);
            }
            return false;
        }

        // Rank options by heuristic: prefer placements that leave more flexibility
        // and spread COURSE periods evenly across the week (soft balance preference;
        // hard constraints are unaffected - all options here are already valid).
        // Score each option ONCE instead of per-comparison (~230 evaluations -> 25 per state)
        Map<PlacementOption, Integer> optionScores = new HashMap<>();
        for (PlacementOption opt : options) {
            optionScores.put(opt, evaluatePlacement(bestUnit, opt, units, slots, grid, usedDaysByCourse, placedCounts, sectionDayLoads));
        }
        options.sort((a, b) -> Integer.compare(optionScores.get(b), optionScores.get(a)));
        // Perturb the ranked order slightly: keeps the heuristic preference
        // while breaking the systematic exploration of symmetric placements.
        Random rand = SOLVE_RANDOM.get();
        for (int i = 1; i < options.size(); i++) {
            if (rand.nextDouble() < 0.25) {
                PlacementOption tmp = options.get(i);
                options.set(i, options.get(i - 1));
                options.set(i - 1, tmp);
            }
        }

        for (PlacementOption opt : options) {
            int occurrence = usedDays.size() + 1;
            int periodCount = opt.endOrder - opt.startOrder + 1;
            ClassSchedule sched = buildSchedule(generation, bestUnit, opt.day, opt.startIdx, slots);
            created.add(sched);
            placedCounts.merge(bestUnit, 1, Integer::sum);
            grid.place(bestUnit.staffIds, bestUnit.sectionIds, bestUnit.semesterId,
                    opt.day, opt.startOrder, opt.endOrder, bestUnit.electiveGroup, bestUnit,
                    occurrence);
            for (UUID sectionId : bestUnit.sectionIds) {
                int[] loads = sectionDayLoads.computeIfAbsent(bestUnit.semesterId + "|" + sectionId, k -> new int[6]);
                loads[opt.day] += periodCount;
            }
            usedDays.add(opt.day);
            usedDaysByCourse.put(courseKey(bestUnit), usedDays);

            // Forward checking: verify remaining units still have at least one valid placement
            if (forwardCheck(units, slots, grid, usedDaysByCourse, placedCounts)) {
                if (solveRecursive(units, slots, generation, created, failureReport, counter, iterationLimitReached, grid, usedDaysByCourse, placedCounts, sectionDayLoads)) {
                    return true;
                }
            }

            // Backtrack
            created.remove(created.size() - 1);
            placedCounts.merge(bestUnit, -1, Integer::sum);
            if (placedCounts.get(bestUnit) <= 0) {
                placedCounts.remove(bestUnit);
            }
            grid.remove(bestUnit.staffIds, bestUnit.sectionIds, bestUnit.semesterId,
                    opt.day, opt.startOrder, opt.endOrder, bestUnit.electiveGroup, bestUnit,
                    occurrence);
            for (UUID sectionId : bestUnit.sectionIds) {
                int[] loads = sectionDayLoads.get(bestUnit.semesterId + "|" + sectionId);
                if (loads != null) {
                    loads[opt.day] -= periodCount;
                }
            }
            usedDays.remove(opt.day);
            if (usedDays.isEmpty()) {
                usedDaysByCourse.remove(courseKey(bestUnit));
            }
        }

        return false;
    }

    private int unitIdx(List<SchedulingUnit> units, Map<SchedulingUnit, Integer> placedCounts) {
        // Count how many units have all their sessions placed (component-accurate:
        // each unit counts only its own placed schedules, not its course's total)
        int placed = 0;
        for (SchedulingUnit u : units) {
            if (placedCounts.getOrDefault(u, 0) >= u.sessionsPerWeek) placed++;
            else break;
        }
        return placed;
    }

private SchedulingUnit selectMostConstrainedUnit(List<SchedulingUnit> units,
                                                       List<TimeSlot> slots, ConflictGrid grid,
                                                       Map<Object, Set<Integer>> usedDaysByCourse,
                                                       Map<SchedulingUnit, Integer> placedCounts) {
        SchedulingUnit best = null;
        int minOptions = Integer.MAX_VALUE;

        for (SchedulingUnit u : units) {
            if (placedCounts.getOrDefault(u, 0) >= u.sessionsPerWeek) continue; // Fully scheduled

            Set<Integer> usedDays = usedDaysByCourse.getOrDefault(courseKey(u), new HashSet<>());
            List<PlacementOption> options = generateValidPlacements(u, usedDays, slots, grid);
            int optionsCount = options.size();

            if (optionsCount == 0) {
                return u; // Immediate failure - this unit has no valid placements
            }
            if (optionsCount < minOptions) {
                minOptions = optionsCount;
                best = u;
            }
        }
        return best;
    }

private List<PlacementOption> generateValidPlacements(SchedulingUnit unit, Set<Integer> usedDays,
                                                          List<TimeSlot> slots, ConflictGrid grid) {
        // Elective co-location is a hard constraint: once any group member has
        // established the window for weekly occurrence (usedDays.size()+1) in this
        // section, every other member must use that exact window. A component may
        // only co-locate when its shape matches the established window: a 1-period
        // component must never be forced into a 2-period window (and vice versa).
        int occurrence = usedDays.size() + 1;
        int[] forced = (unit.isElective() && !unit.sectionIds.isEmpty())
                ? grid.forcedWindow(unit.electiveGroup, unit.sectionIds.iterator().next(),
                        unit.semesterId, occurrence)
                : null;
        if (forced != null && (forced[2] - forced[1] + 1) != unit.periodsPerSession) {
            forced = null;
        }

        List<PlacementOption> options = new ArrayList<>();
        for (int day = WORKING_DAY_START; day <= WORKING_DAY_END; day++) {
            if (usedDays.contains(day)) continue;

            for (int startIdx = 0; startIdx + unit.periodsPerSession <= slots.size(); startIdx++) {
                if (!consecutiveSlots(slots, startIdx, unit.periodsPerSession)) continue;
                int startOrder = startIdx + 1;
                int endOrder = startIdx + unit.periodsPerSession;
                if (forced != null && (day != forced[0] || startOrder != forced[1] || endOrder != forced[2])) {
                    continue;
                }
                if (!grid.canPlace(unit.staffIds, unit.sectionIds, unit.semesterId,
                        day, startOrder, endOrder, unit.electiveGroup)) continue;

                options.add(new PlacementOption(day, startIdx, unit.periodsPerSession));
            }
        }
        return options;
    }

    private int evaluatePlacement(SchedulingUnit unit, PlacementOption opt,
                                   List<SchedulingUnit> units,
                                   List<TimeSlot> slots, ConflictGrid grid,
                                   Map<Object, Set<Integer>> usedDaysByCourse,
                                   Map<SchedulingUnit, Integer> placedCounts,
                                   Map<String, int[]> sectionDayLoads) {
        // Temporarily place and count remaining options for other units
        int occurrence = usedDaysByCourse.getOrDefault(courseKey(unit), new HashSet<>()).size() + 1;
        grid.place(unit.staffIds, unit.sectionIds, unit.semesterId, opt.day, opt.startOrder, opt.endOrder,
                unit.electiveGroup, unit, occurrence);
        Set<Integer> newUsedDays = new HashSet<>(usedDaysByCourse.getOrDefault(courseKey(unit), new HashSet<>()));
        newUsedDays.add(opt.day);
        Map<Object, Set<Integer>> tempUsedDays = new HashMap<>(usedDaysByCourse);
        tempUsedDays.put(courseKey(unit), newUsedDays);

        int totalOptions = 0;
        // Cheap local heuristic: how many valid windows does THIS unit still
        // have after this placement? (Same spirit as the previous all-units
        // count, but ~1 unit instead of ~60 -> the search runs far faster.)
        List<PlacementOption> after = generateValidPlacements(unit, newUsedDays, slots, grid);
        totalOptions = after.size();

        grid.remove(unit.staffIds, unit.sectionIds, unit.semesterId, opt.day, opt.startOrder, opt.endOrder,
                unit.electiveGroup, unit, occurrence);

        // Soft weekday-balance preference: subtract the imbalance the placement
        // would create in the affected sections' COURSE day loads. Pure score -
        // never overrides any hard constraint (every option here is already valid).
        int balancePenalty = dayBalancePenalty(unit, opt, sectionDayLoads);

        return totalOptions - balancePenalty;
    }

    /**
     * Sum of squared deviations of the affected sections' weekday COURSE loads
     * after this placement would be applied. Lower is better; 0 = perfectly
     * balanced. Only COURSE periods count - LMS/ASSIGNMENT/BREAK never appear
     * in the load arrays.
     */
    private int dayBalancePenalty(SchedulingUnit unit, PlacementOption opt,
                                  Map<String, int[]> sectionDayLoads) {
        int worst = 0;
        int periodCount = opt.endOrder - opt.startOrder + 1;
        for (UUID sectionId : unit.sectionIds) {
            int[] base = sectionDayLoads.get(unit.semesterId + "|" + sectionId);
            int[] loads = base == null ? new int[6] : base.clone();
            loads[opt.day] += periodCount;
            int penalty = dayImbalancePenalty(loads);
            if (penalty > worst) worst = penalty;
        }
        return worst;
    }

    /** Sum of squared deviations of COURSE loads over days 1..5; 0 = perfectly balanced. */
    static int dayImbalancePenalty(int[] loads) {
        double total = 0;
        for (int d = WORKING_DAY_START; d <= WORKING_DAY_END; d++) total += loads[d];
        double avg = total / (WORKING_DAY_END - WORKING_DAY_START + 1);
        double ssd = 0;
        for (int d = WORKING_DAY_START; d <= WORKING_DAY_END; d++) {
            double dev = loads[d] - avg;
            ssd += dev * dev;
        }
        return (int) Math.round(ssd);
    }

    private boolean forwardCheck(List<SchedulingUnit> units,
                                  List<TimeSlot> slots, ConflictGrid grid,
                                  Map<Object, Set<Integer>> usedDaysByCourse,
                                  Map<SchedulingUnit, Integer> placedCounts) {
        for (SchedulingUnit u : units) {
            if (placedCounts.getOrDefault(u, 0) >= u.sessionsPerWeek) continue;

            int remainingSessions = u.sessionsPerWeek - placedCounts.getOrDefault(u, 0);
            Set<Integer> used = usedDaysByCourse.getOrDefault(courseKey(u), new HashSet<>());

            // Check 1: enough unused days for remaining sessions
            int availableDays = 0;
            for (int d = WORKING_DAY_START; d <= WORKING_DAY_END; d++) {
                if (!used.contains(d)) availableDays++;
            }
            if (availableDays < remainingSessions) {
                return false;
            }

            // Check 2: enough total UNIQUE free periods across available days
            // Use a bitmask to count unique free period indices (avoid overcounting overlapping blocks)
            int freePeriodMask = 0;
            for (int d = WORKING_DAY_START; d <= WORKING_DAY_END; d++) {
                if (used.contains(d)) continue;
                for (int startIdx = 0; startIdx + u.periodsPerSession <= slots.size(); startIdx++) {
                    if (!consecutiveSlots(slots, startIdx, u.periodsPerSession)) continue;
                    int startOrder = startIdx + 1;
                    int endOrder = startIdx + u.periodsPerSession;
                    if (grid.canPlace(u.staffIds, u.sectionIds, u.semesterId,
                            d, startOrder, endOrder, u.electiveGroup)) {
                        // Mark each period index in this block as free
                        for (int p = startOrder; p <= endOrder; p++) {
                            freePeriodMask |= (1 << (d * 10 + p)); // day*10 + period
                        }
                    }
                }
            }
            int uniqueFreePeriods = Integer.bitCount(freePeriodMask);
            int requiredPeriods = remainingSessions * u.periodsPerSession;
            if (uniqueFreePeriods < requiredPeriods) {
                return false;
            }

            // Check 3: at least one valid placement for next session (existing check)
            List<PlacementOption> opts = generateValidPlacements(u, used, slots, grid);
            if (opts.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private ClassSchedule buildSchedule(GenerationSession generation, SchedulingUnit unit,
                                         int day, int startIdx, List<TimeSlot> slots) {
        ClassSchedule schedule = new ClassSchedule();
        schedule.setGeneration(generation);
        schedule.setTeachingAssignment(unit.assignment);
        schedule.setTeachingGroup(unit.group);
        schedule.setDayOfWeek(day);
        schedule.setStartSlot(slots.get(startIdx));
        schedule.setEndSlot(slots.get(startIdx + unit.periodsPerSession - 1));
        schedule.setScheduleType(ScheduleType.COURSE);
        schedule.setScheduleStatus(ScheduleStatus.PENDING);
        return schedule;
}

    private void diagnoseUnit(SchedulingUnit unit, List<TimeSlot> slots, Set<Integer> usedDays,
                              List<ClassSchedule> created, List<String> failureReport) {
        StringBuilder sb = new StringBuilder();
        sb.append(unit.label).append("\n");
        sb.append("  Required: ").append(unit.sessionsPerWeek)
                .append(" session(s) x ").append(unit.periodsPerSession).append(" period(s)\n");

        boolean anyValid = false;
        for (int day = WORKING_DAY_START; day <= WORKING_DAY_END; day++) {
            boolean dayUsed = usedDays.contains(day);
            for (int startIdx = 0; startIdx + unit.periodsPerSession <= slots.size(); startIdx++) {
                int startOrder = startIdx + 1;
                int endOrder = startIdx + unit.periodsPerSession;
                String window = DAY_NAMES[day] + " P" + startOrder + "-P" + endOrder;

                if (dayUsed) {
                    sb.append("  ").append(window)
                            .append(" -> USED_DAY (this unit already has a session on this day)\n");
                    continue;
                }

                if (!consecutiveSlots(slots, startIdx, unit.periodsPerSession)) {
                    sb.append("  ").append(window)
                            .append(" -> INVALID_CONSECUTIVE_SLOT (periods are not consecutive)\n");
                    continue;
                }

                List<String> reasons = new ArrayList<>();
                for (ClassSchedule s : created) {
                    if (s.getDayOfWeek() != day || s.getScheduleType() != ScheduleType.COURSE) continue;
                    int otherStart = s.getStartSlot().getDisplayOrder();
                    int otherEnd = s.getEndSlot().getDisplayOrder();
                    if (startOrder > otherEnd || endOrder < otherStart) continue;

                    boolean sameGroup = sameElectiveGroupUnit(unit, s);
                    if (!Collections.disjoint(unit.staffIds, ClassScheduleService.coveredStaff(s))) {
                        String cc = ClassScheduleService.courseCodeOf(s);
                        reasons.add(sameGroup
                                ? "ELECTIVE_SHARE_NOT_ALLOWED " + (cc != null ? cc : "unknown")
                                + " (P" + otherStart + "-P" + otherEnd
                                + ") â€” same lecturer; lecturer conflicts always win"
                                : "STAFF_CONFLICT " + (cc != null ? cc : "unknown")
                                + " (P" + otherStart + "-P" + otherEnd + ")");
                    }
                    // Section conflicts are scoped by semester â€” different-semester students
                    // sharing the same section label (e.g. Section A) don't actually conflict.
                    UUID sSemId = scheduleSemesterId(s);
                    if (unit.semesterId != null && unit.semesterId.equals(sSemId)
                            && !Collections.disjoint(unit.sectionIds, ClassScheduleService.coveredSections(s))) {
                        String cc = ClassScheduleService.courseCodeOf(s);
                        reasons.add(sameGroup
                                ? "ELECTIVE_SHARE_NOT_ALLOWED " + (cc != null ? cc : "unknown")
                                + " (P" + otherStart + "-P" + otherEnd
                                + ") â€” only IDENTICAL windows may be shared by one elective group"
                                : "SECTION_CONFLICT " + (cc != null ? cc : "unknown")
                                + " (P" + otherStart + "-P" + otherEnd + ")");
                    }
                }

                if (reasons.isEmpty()) {
                    anyValid = true;
                    sb.append("  ").append(window).append(" -> VALID\n");
                } else {
                    sb.append("  ").append(window).append(" -> ")
                            .append(String.join("; ", reasons)).append("\n");
                }
            }
        }

        if (!anyValid) {
            sb.append("  No valid consecutive ").append(unit.periodsPerSession)
                    .append("-period slot available on any day\n");
        }

        failureReport.add(sb.toString());
    }

    /** Extract semester UUID from a class schedule (for semester-scoped section checks). */
    private static UUID scheduleSemesterId(ClassSchedule s) {
        if (s.getTeachingAssignment() != null && s.getTeachingAssignment().getCourse() != null
                && s.getTeachingAssignment().getCourse().getSemester() != null) {
            return s.getTeachingAssignment().getCourse().getSemester().getSemesterId();
        }
        if (s.getTeachingGroup() != null && s.getTeachingGroup().getCourse() != null
                && s.getTeachingGroup().getCourse().getSemester() != null) {
            return s.getTeachingGroup().getCourse().getSemester().getSemesterId();
        }
        return null;
    }

    /** True when a placed schedule is a member of the same elective group as the unit. */
    private static boolean sameElectiveGroupUnit(SchedulingUnit unit, ClassSchedule s) {
        if (!unit.isElective() || s.getTeachingAssignment() == null) return false;
        Course c = s.getTeachingAssignment().getCourse();
        if (c == null || c.isRequired()) return false;
        Semester sem = c.getSemester();
        return sem != null && unit.semesterId != null && unit.semesterId.equals(sem.getSemesterId());
    }

    /** True when two schedules belong to the same elective group (is_required=false, same semester). */
    private static boolean sameElectiveGroup(ClassSchedule a, ClassSchedule b) {
        Course ca = courseOfSchedule(a);
        Course cb = courseOfSchedule(b);
        if (ca == null || cb == null || ca.isRequired() || cb.isRequired()) return false;
        Semester sa = ca.getSemester();
        Semester sb = cb.getSemester();
        return sa != null && sb != null && sa.getSemesterId().equals(sb.getSemesterId());
    }

    private static Course courseOfSchedule(ClassSchedule s) {
        if (s.getTeachingAssignment() != null) return s.getTeachingAssignment().getCourse();
        if (s.getTeachingGroup() != null) return s.getTeachingGroup().getCourse();
        return null;
    }

    // ========== CONSECUTIVE SLOT CHECK ==========

    private boolean consecutiveSlots(List<TimeSlot> slots, int startIdx, int perSession) {
        for (int i = startIdx; i < startIdx + perSession - 1; i++) {
            var end = slots.get(i).getEndTime();
            var nextStart = slots.get(i + 1).getStartTime();
            // The lunch break (12:00-13:00) falls between period 3 and 4.
            // A 2-period session may bridge it as a fallback when no fully
            // consecutive window exists (original delivered behaviour).
            if (!end.equals(nextStart)) {
                int curPeriod = slots.get(i).getPeriodNo();
                int nextPeriod = slots.get(i + 1).getPeriodNo();
                if (curPeriod == 3 && nextPeriod == 4) {
                    return true;
                }
                return false;
            }
        }
        return true;
    }

    // ========== SPECIAL PERIOD PLACEMENT ==========

    private void placeSpecial(GenerationSession generation, ScheduleType type, int preferredSlot,
                              int day, List<TimeSlot> slots, List<ClassSchedule> created) {
        if (preferredSlot < 0 || preferredSlot >= slots.size()) return;
        ClassSchedule schedule = new ClassSchedule();
        schedule.setGeneration(generation);
        schedule.setDayOfWeek(day);
        schedule.setStartSlot(slots.get(preferredSlot));
        schedule.setEndSlot(slots.get(preferredSlot));
        schedule.setScheduleType(type);
        schedule.setScheduleStatus(ScheduleStatus.PENDING);
        created.add(schedule);
    }

    private int firstFreeSlot(int day, List<TimeSlot> slots, List<ClassSchedule> created) {
        for (int i = 0; i < slots.size(); i++) {
            final int idx = i;
            final int dayOfWeek = day;
            boolean occupied = created.stream().anyMatch(s ->
                    s.getDayOfWeek() == dayOfWeek
                            && s.getStartSlot().getDisplayOrder() <= idx + 1
                            && s.getEndSlot().getDisplayOrder() >= idx + 1);
            if (!occupied) return i;
        }
        return -1;
    }

    // ========== DATA VALIDATION ==========

    /**
     * Extract the curriculum semester from course_code.
     * Course codes follow pattern: PREFIX-NUMBER (e.g., CST-1102, CS-3215, CT-2234, E-1101, M-1201, P-1101).
     * The first two digits of the numeric portion determine the semester:
     * 11xx -> 1, 12xx -> 2, 21xx -> 3, 22xx -> 4, 31xx -> 5, 32xx -> 6, 41xx -> 7, 42xx -> 8.
     */
    private Integer extractCurriculumSemester(String courseCode) {
        if (courseCode == null) return null;
        int dashIdx = courseCode.lastIndexOf('-');
        if (dashIdx < 0 || dashIdx + 3 >= courseCode.length()) return null;
        String numeric = courseCode.substring(dashIdx + 1);
        if (numeric.length() < 2) return null;
        char c0 = numeric.charAt(0);
        char c1 = numeric.charAt(1);
        if (!Character.isDigit(c0) || !Character.isDigit(c1)) return null;
        int d0 = c0 - '0';
        int d1 = c1 - '0';
        if (d0 < 1 || d0 > 4 || d1 < 1 || d1 > 2) return null;
        return (d0 - 1) * 2 + d1;
    }

    /**
     * Validate that a course's course_code matches its assigned semester_id in the database.
     */
    private String validateCourseSemesterCode(Course course) {
        Integer expectedSem = extractCurriculumSemester(course.getCourseCode());
        if (expectedSem == null) return null;
        Semester sem = course.getSemester();
        if (sem != null && sem.getSemesterNo() != expectedSem) {
            return "course code " + course.getCourseCode() + " implies Semester " + expectedSem
                    + " but course is assigned to Semester " + sem.getSemesterNo();
        }
        return null;
    }

    /**
     * Validate that a course's curriculum semester matches the generation scope.
     */
    private String validateCourseGenerationScope(Course course, Map<UUID, Set<UUID>> scope) {
        Integer expectedSem = extractCurriculumSemester(course.getCourseCode());
        if (expectedSem == null) return null;
        Semester sem = course.getSemester();
        if (sem == null) return null;
        if (!scope.containsKey(sem.getSemesterId())) {
            return "course code " + course.getCourseCode() + " belongs to Semester " + expectedSem
                    + " but generation scope does not include Semester " + sem.getSemesterNo();
        }
        return null;
    }

    private String validateCsCtSeparation(TeachingAssignment a, Map<UUID, Set<UUID>> scope) {
        String code = a.getCourse().getCourseCode();
        if (code == null) return null;
        Semester sem = a.getCourse().getSemester();
        if (sem == null || sem.getSemesterNo() < 3) return null;
        String sectionName = a.getSection().getSectionName();
        boolean isCT = code.startsWith("CT");
        boolean isCS = code.startsWith("CS") && !code.startsWith("CST");
        if (isCT && !"CT".equals(sectionName)) {
            return "CT course " + code + " must be assigned to CT section, not " + sectionName;
        }
        if (isCS && "CT".equals(sectionName)) {
            return "CS course " + code + " must not be assigned to CT section";
        }
        return null;
    }

    private String validateLecturerOwnership(TeachingAssignment a) {
        var staffUnit = a.getStaff().getUnit();
        var courseUnit = a.getCourse().getUnit();
        if (staffUnit != null && courseUnit != null
                && !staffUnit.getUnitId().equals(courseUnit.getUnitId())) {
            return "lecturer " + a.getStaff().getStaffName()
                    + " belongs to " + staffUnit.getUnitName()
                    + " but course " + a.getCourse().getCourseCode()
                    + " belongs to " + courseUnit.getUnitName();
        }
        return null;
    }

    // ========== PUBLISH ==========

    public GenerationSessionResponse publish(UUID generationId) {
        hodAccessService.requireHod();
        lobbyAccessService.requireSharedDraftAccess(generationId);
        GenerationSession generation = findGeneration(generationId);
        if (generation.getStatus() != GenerationStatus.COMPLETED) {
            throw new BusinessRuleException("Only a completed generation can be published");
        }
        List<ClassSchedule> schedules = scheduleRepository.findByGeneration_GenerationIdWithDetails(generationId);
        if (schedules.isEmpty()) {
            throw new BusinessRuleException("Cannot publish an empty timetable");
        }
        validateConflictsForPublish(schedules);
        validateCompletenessForPublish(generation, schedules);
        // Publishing a newer timetable replaces the term's current published one:
        // demote it back to a completed draft so it remains viewable in history.
        generationRepository.findFirstByTerm_TermIdAndStatusOrderByCreatedAtDesc(
                        generation.getTerm().getTermId(), GenerationStatus.PUBLISHED)
                .ifPresent(published -> {
                    if (!published.getGenerationId().equals(generationId)) {
                        published.setStatus(GenerationStatus.COMPLETED);
                        published.setPublishedAt(null);
                        generationRepository.save(published);
                        List<ClassSchedule> oldSchedules =
                                scheduleRepository.findByGeneration_GenerationId(published.getGenerationId());
                        for (ClassSchedule schedule : oldSchedules) {
                            schedule.setScheduleStatus(ScheduleStatus.PENDING);
                        }
                        scheduleRepository.saveAll(oldSchedules);
                    }
                });
        generation.setStatus(GenerationStatus.PUBLISHED);
        generation.setPublishedAt(Instant.now());
        for (ClassSchedule schedule : schedules) {
            schedule.setScheduleStatus(ScheduleStatus.CONFIRMED);
        }
        scheduleRepository.saveAll(schedules);
        GenerationSessionResponse response = toResponse(generationRepository.save(generation));

        lobbyRepository.findByGeneration_GenerationId(generationId).ifPresent(lobby -> {
            lobby.setStatus(LobbyStatus.COMPLETED);
            lobbyRepository.save(lobby);
            realtimeEventService.publish(lobby.getLobbyId(),
                    TimetableRealtimeEventService.TIMETABLE_PUBLISHED,
                    Map.of("generationId", generationId, "lobbyId", lobby.getLobbyId()));
        });
        return response;
    }

    /**
     * Broadcasts a live drag/drop gesture to every connected lobby member.
     *
     * <p>Only the HOD who currently holds the editing lock can drag, so this is
     * always a single-sender stream; every other member's browser renders the
     * remote dragging state until the {@code end} event arrives.
     */
    public void publishDragStatus(UUID generationId, DragStatusRequest request) {
        Staff staff = hodAccessService.requireHod();
        lobbyAccessService.requireSharedDraftAccess(generationId);
        String type;
        if (request == null || request.action() == null) {
            throw new BusinessRuleException("Missing drag action");
        }
        switch (request.action()) {
            case "start" -> type = TimetableRealtimeEventService.DRAG_STARTED;
            case "move" -> type = TimetableRealtimeEventService.DRAG_MOVED;
            case "end" -> type = TimetableRealtimeEventService.DRAG_ENDED;
            default -> throw new BusinessRuleException("Unknown drag action: " + request.action());
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("generationId", generationId);
        payload.put("scheduleId", request.scheduleId());
        payload.put("staffId", staff.getStaffId());
        payload.put("staffName", staff.getStaffName());
        payload.put("day", request.day());
        payload.put("period", request.period());
        realtimeEventService.publishForGeneration(generationId, type, payload);
    }

    public GenerationSessionResponse cancel(UUID generationId) {
        hodAccessService.requireHod();
        lobbyAccessService.requireSharedDraftAccess(generationId);
        GenerationSession generation = findGeneration(generationId);
        if (generation.getStatus() == GenerationStatus.PUBLISHED) {
            throw new BusinessRuleException("A published timetable cannot be cancelled");
        }
        List<ClassSchedule> schedules = scheduleRepository.findByGeneration_GenerationId(generationId);
        scheduleRepository.deleteAll(schedules);
        generation.setStatus(GenerationStatus.FAILED);
        generation.setFinishedAt(Instant.now());
        GenerationSessionResponse response = toResponse(generationRepository.save(generation));
        realtimeEventService.publishForGeneration(generationId,
                TimetableRealtimeEventService.TIMETABLE_DELETED,
                Map.of("generationId", generationId));
        return response;
    }

    public void delete(UUID generationId) {
        hodAccessService.requireHod();
        lobbyAccessService.requireSharedDraftAccess(generationId);
        GenerationSession generation = findGeneration(generationId);
        if (attendanceRepository.countBySession_Schedule_Generation_GenerationId(generationId) > 0) {
            throw new BusinessRuleException(
                    "This timetable already has attendance records; deleting it would erase historical "
                            + "attendance. Keep it or cancel it instead.");
        }
        UUID lobbyId = lobbyRepository.findByGeneration_GenerationId(generationId)
                .map(lobby -> lobby.getLobbyId()).orElse(null);
        scheduleRepository.deleteAll(scheduleRepository.findByGeneration_GenerationId(generationId));
        scheduleRepository.flush();
        generationRepository.delete(generation);
        if (lobbyId != null) {
            realtimeEventService.publish(lobbyId, TimetableRealtimeEventService.TIMETABLE_DELETED,
                    Map.of("generationId", generationId, "lobbyId", lobbyId));
        }
    }

    public List<ScheduleResponse> getSchedules(UUID generationId) {
        GenerationSession session = findGeneration(generationId);
        if (session.getStatus() != GenerationStatus.PUBLISHED) {
            if (hodAccessService.currentHod().isEmpty()) {
                throw new BusinessRuleException("You do not have access to this timetable");
            }
            // Any HOD may review a COMPLETED draft (the "View Timetable" flow);
            // only in-progress drafts stay restricted to the lobby.
            if (session.getStatus() != GenerationStatus.COMPLETED
                    && !lobbyAccessService.canAccessSharedDraft(generationId)) {
                throw new BusinessRuleException(
                        "Only the lobby leader or joined lobby members can access this shared draft");
            }
        }
        return scheduleRepository.findByGeneration_GenerationIdWithDetails(generationId).stream()
                .sorted(Comparator.comparing(ClassSchedule::getDayOfWeek)
                        .thenComparing(s -> s.getStartSlot().getDisplayOrder()))
                .map(ClassScheduleService::toResponse).toList();
    }

    // ========== PUBLISH CONFLICT REVALIDATION ==========

    private String describeConflictSlot(ClassSchedule s) {
        TimeSlot start = s.getStartSlot();
        String time = start != null && s.getEndSlot() != null
                ? start.getStartTime() + " - " + s.getEndSlot().getEndTime() : "?";
        int period = start != null ? start.getPeriodNo() : 0;
        return DAY_NAMES[s.getDayOfWeek()] + " P" + period + " (" + time + ")";
    }

    private void validateConflictsForPublish(List<ClassSchedule> schedules) {
        List<String> conflicts = new ArrayList<>();
        for (int day = WORKING_DAY_START; day <= WORKING_DAY_END; day++) {
            final int dayOfWeek = day;
            List<ClassSchedule> daySchedules = schedules.stream()
                    .filter(s -> s.getDayOfWeek() != null && s.getDayOfWeek() == dayOfWeek)
                    .filter(s -> s.getScheduleStatus() != ScheduleStatus.CANCELLED)
                    .toList();
            for (int i = 0; i < daySchedules.size(); i++) {
                ClassSchedule a = daySchedules.get(i);
                if (a.getScheduleType() != ScheduleType.COURSE) continue;
                for (int j = i + 1; j < daySchedules.size(); j++) {
                    ClassSchedule b = daySchedules.get(j);
                    if (b.getScheduleType() == ScheduleType.COURSE
                            && a.getTeachingGroup() != null && b.getTeachingGroup() != null
                            && a.getTeachingGroup().getGroupId().equals(b.getTeachingGroup().getGroupId())) {
                        conflicts.add(describeConflictSlot(a) + " â€” " + ClassScheduleService.courseCodeOf(a)
                                + " is scheduled more than once on " + DAY_NAMES[day] + " for the same section");
                        continue;
                    }
                    if (b.getScheduleType() == ScheduleType.COURSE
                            && ClassScheduleService.courseCodeOf(a) != null
                            && ClassScheduleService.courseCodeOf(a).equals(ClassScheduleService.courseCodeOf(b))
                            && !Collections.disjoint(ClassScheduleService.coveredSections(a),
                                    ClassScheduleService.coveredSections(b))) {
                        conflicts.add(describeConflictSlot(a) + " â€” " + ClassScheduleService.courseCodeOf(a)
                                + " is scheduled more than once on " + DAY_NAMES[day] + " for the same section");
                        continue;
                    }
                    if (!overlapsSlots(a, b)) continue;
                    boolean conflict;
                    String reason;
                    if (b.getScheduleType() != ScheduleType.COURSE) {
                        // A special period (LMS/ASSIGNMENT) never shares with a course.
                        conflict = true;
                        reason = "a special period (LMS/ASSIGNMENT) cannot share this slot with a course";
                    } else if (!Collections.disjoint(ClassScheduleService.coveredStaff(a),
                            ClassScheduleService.coveredStaff(b))) {
                        // The lecturer conflict rule always wins â€” even within an elective group.
                        conflict = true;
                        reason = "the same lecturer is double-booked";
                    } else if (Collections.disjoint(ClassScheduleService.coveredSections(a),
                            ClassScheduleService.coveredSections(b))) {
                        conflict = false;
                        reason = null;
                    } else if (!sameSemester(a, b)) {
                        // Sections are shared rows across semesters: different-semester
                        // cohorts may legitimately co-exist in the same slot (the solver
                        // is semester-scoped); only same-semester co-existence conflicts.
                        conflict = false;
                        reason = null;
                    } else {
                        // Overlapping sections are fine only for identical-window
                        // co-location of the same elective group.
                        conflict = !sameElectiveGroup(a, b);
                        reason = conflict ? "the same section is double-booked" : null;
                    }
                    if (conflict) {
                        conflicts.add(describeConflictSlot(a) + " â€” " + scheduleLabel(a)
                                + " conflicts with " + scheduleLabel(b) + " (" + reason + ")");
                    }
                }
            }
        }
        if (!conflicts.isEmpty()) {
            throw new TimetableConflictException(conflicts);
        }
    }

    private boolean overlapsSlots(ClassSchedule a, ClassSchedule b) {
        return a.getStartSlot().getDisplayOrder() <= b.getEndSlot().getDisplayOrder()
                && b.getStartSlot().getDisplayOrder() <= a.getEndSlot().getDisplayOrder();
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

    /** True when both schedules belong to the same semester (or semester is unknown). */
    private static boolean sameSemester(ClassSchedule a, ClassSchedule b) {
        Semester sa = semesterOf(a);
        Semester sb = semesterOf(b);
        if (sa == null || sb == null) return true;
        return sa.getSemesterId().equals(sb.getSemesterId());
    }

    private String scheduleLabel(ClassSchedule s) {
        if (s.getScheduleType() == ScheduleType.COURSE) {
            if (s.getTeachingAssignment() != null) {
                return s.getTeachingAssignment().getCourse().getCourseCode() + " ("
                        + s.getTeachingAssignment().getStaff().getStaffName() + ", "
                        + s.getTeachingAssignment().getSection().getSectionName() + ")";
            }
            if (s.getTeachingGroup() != null) {
                List<String> names = new ArrayList<>();
                for (TeachingAssignmentGroupMember m : s.getTeachingGroup().getMembers()) {
                    names.add(m.getAssignment().getSection().getSectionName());
                }
                names.sort(Comparator.naturalOrder());
                return s.getTeachingGroup().getCourse().getCourseCode()
                        + " (" + String.join(" + ", names) + ")";
            }
        }
        return s.getScheduleType() + " block";
    }

    // ========== PUBLISH COMPLETENESS ==========

    private void validateCompletenessForPublish(GenerationSession generation,
                                                List<ClassSchedule> allSchedules) {
        List<String> failures = new ArrayList<>();

        for (ClassSchedule s : allSchedules) {
            if (s.getScheduleType() != ScheduleType.COURSE
                    && s.getScheduleType() != ScheduleType.LMS
                    && s.getScheduleType() != ScheduleType.ASSIGNMENT) {
                failures.add("Invalid schedule type " + s.getScheduleType()
                        + " (" + scheduleLabel(s) + ")");
            }
        }

        for (ClassSchedule s : allSchedules) {
            if (s.getDayOfWeek() == null || s.getDayOfWeek() < WORKING_DAY_START
                    || s.getDayOfWeek() > WORKING_DAY_END) {
                failures.add(scheduleLabel(s) + " is scheduled outside Monday-Friday");
            }
            TimeSlot start = s.getStartSlot();
            TimeSlot end = s.getEndSlot();
            if (start == null || end == null) {
                failures.add(scheduleLabel(s) + " has no valid time slots");
                continue;
            }
            int startOrder = start.getDisplayOrder();
            int endOrder = end.getDisplayOrder();
            if (startOrder < 1 || startOrder > 6 || endOrder < 1 || endOrder > 6) {
                failures.add(scheduleLabel(s) + " uses a slot outside the 6 daily periods");
            }
            if (startOrder > endOrder) {
                failures.add(scheduleLabel(s) + " has a start slot after its end slot");
            }
        }

        List<ClassSchedule> courseSchedules = allSchedules.stream()
                .filter(s -> s.getScheduleType() == ScheduleType.COURSE
                        && s.getScheduleStatus() != ScheduleStatus.CANCELLED)
                .toList();

        Map<UUID, List<TeachingAssignmentGroupMember>> membersByGroup = new LinkedHashMap<>();
        for (TeachingAssignmentGroupMember m : groupMemberRepository
                .findWithDetailsByTermId(generation.getTerm().getTermId())) {
            if (m.getAssignment().getAssignmentStatus() == AssignmentStatus.CANCELLED) continue;
            membersByGroup.computeIfAbsent(m.getGroup().getGroupId(), k -> new ArrayList<>()).add(m);
        }
        Set<UUID> groupedAssignmentIds = new HashSet<>();
        membersByGroup.values().forEach(list -> list.forEach(
                m -> groupedAssignmentIds.add(m.getAssignment().getAssignmentId())));

        PersistedScope persisted = parseScope(generation.getScopeJson());
        Map<UUID, Set<UUID>> scopeMap = toScopeMap(persisted);
        boolean useScope = persisted != null;

        if (useScope) {
            for (ClassSchedule s : courseSchedules) {
                if (s.getTeachingGroup() != null) {
                    List<TeachingAssignmentGroupMember> members =
                            membersByGroup.get(s.getTeachingGroup().getGroupId());
                    if (members == null) {
                        failures.add(scheduleLabel(s) + " references an unknown combined group");
                        continue;
                    }
                    for (TeachingAssignmentGroupMember m : members) {
                        if (!inScope(m.getAssignment(), scopeMap)) {
                            failures.add("Cross-semester course in " + scheduleLabel(s)
                                    + ": " + m.getAssignment().getCourse().getCourseCode()
                                    + " is not part of the selected scope");
                        }
                    }
                } else if (s.getTeachingAssignment() != null
                        && !inScope(s.getTeachingAssignment(), scopeMap)) {
                    failures.add("Cross-semester course in " + scheduleLabel(s)
                            + ": " + s.getTeachingAssignment().getCourse().getCourseCode()
                            + " is not part of the selected scope");
                }
            }
        }

        Map<UUID, List<ClassSchedule>> byAssignment = new HashMap<>();
        Map<UUID, List<ClassSchedule>> byGroup = new HashMap<>();
        for (ClassSchedule s : courseSchedules) {
            if (s.getTeachingAssignment() != null) {
                byAssignment.computeIfAbsent(s.getTeachingAssignment().getAssignmentId(),
                        k -> new ArrayList<>()).add(s);
            } else if (s.getTeachingGroup() != null) {
                byGroup.computeIfAbsent(s.getTeachingGroup().getGroupId(),
                        k -> new ArrayList<>()).add(s);
            }
        }

        for (ClassSchedule s : courseSchedules) {
            if (s.getTeachingAssignment() != null
                    && groupedAssignmentIds.contains(s.getTeachingAssignment().getAssignmentId())) {
                failures.add(scheduleLabel(s)
                        + ": a combined-class member assignment is scheduled individually");
            }
        }

        List<TeachingAssignment> expectedAssignments;
        if (useScope) {
            expectedAssignments = assignmentRepository
                    .findWithDetailsByTermId(generation.getTerm().getTermId()).stream()
                    .filter(a -> a.getAssignmentStatus() != AssignmentStatus.CANCELLED)
                    .filter(a -> inScope(a, scopeMap))
                    .filter(a -> !groupedAssignmentIds.contains(a.getAssignmentId()))
                    .toList();
        } else {
            expectedAssignments = assignmentRepository
                    .findWithDetailsByTermId(generation.getTerm().getTermId()).stream()
                    .filter(a -> byAssignment.containsKey(a.getAssignmentId()))
                    .filter(a -> !groupedAssignmentIds.contains(a.getAssignmentId()))
                    .toList();
        }
        Map<UUID, List<CourseMeetingRequirement>> cmrsByCourse = batchLoadCmrs(allSchedules);

        for (TeachingAssignment assignment : expectedAssignments) {
            validateUnitPeriods(failures, assignment.getCourse(), describe(assignment),
                    byAssignment.get(assignment.getAssignmentId()), cmrsByCourse);
        }

        for (Map.Entry<UUID, List<TeachingAssignmentGroupMember>> entry : membersByGroup.entrySet()) {
            List<TeachingAssignmentGroupMember> members = entry.getValue();
            TeachingAssignmentGroup group = members.get(0).getGroup();
            Course course = group.getCourse();
            Semester sem = course.getSemester();
            List<String> sectionNames = members.stream()
                    .map(m -> m.getAssignment().getSection().getSectionName())
                    .sorted().toList();

            boolean inScopeFlag;
            if (!useScope) {
                inScopeFlag = byGroup.containsKey(group.getGroupId());
            } else if (scopeMap.isEmpty()) {
                inScopeFlag = true;
            } else if (sem == null || !scopeMap.containsKey(sem.getSemesterId())) {
                inScopeFlag = false;
            } else {
                Set<UUID> scopeSections = scopeMap.get(sem.getSemesterId());
                inScopeFlag = scopeSections == null || members.stream().allMatch(m ->
                        scopeSections.contains(m.getAssignment().getSection().getSectionId()));
            }
            if (!inScopeFlag) continue;

            String label = "Semester " + (sem != null ? sem.getSemesterNo() : "?")
                    + " / Section " + String.join(" + ", sectionNames)
                    + " / " + course.getCourseCode();
            validateUnitPeriods(failures, course, label, byGroup.get(group.getGroupId()), cmrsByCourse);
        }

        if (!failures.isEmpty()) {
            throw new BusinessRuleException(
                    "Timetable cannot be published:\n" + String.join("\n", failures));
        }
    }

    private void validateUnitPeriods(List<String> failures, Course course, String unitLabel,
                                     List<ClassSchedule> unitSchedules,
                                     Map<UUID, List<CourseMeetingRequirement>> cmrsByCourse) {
        List<CourseMeetingRequirement> requirements = cmrsByCourse
                .getOrDefault(course.getCourseId(), List.of()).stream()
                .sorted(Comparator.comparing(r -> r.getMeetingType()))
                .toList();

        int expectedPeriods = 0;
        List<Integer> expectedLengths = new ArrayList<>();
        List<String> requiredLines = new ArrayList<>();
        for (CourseMeetingRequirement req : requirements) {
            expectedPeriods += req.getSessionsPerWeek() * req.getPeriodsPerSession();
            for (int i = 0; i < req.getSessionsPerWeek(); i++) {
                expectedLengths.add(req.getPeriodsPerSession());
            }
            requiredLines.add(req.getSessionsPerWeek() + " session(s) x "
                    + req.getPeriodsPerSession() + " period(s) [" + req.getMeetingType() + "]");
        }

        List<ClassSchedule> schedules = unitSchedules != null ? unitSchedules : List.of();
        int actualPeriods = 0;
        List<Integer> actualLengths = new ArrayList<>();
        for (ClassSchedule s : schedules) {
            int length = s.getEndSlot().getDisplayOrder() - s.getStartSlot().getDisplayOrder() + 1;
            actualPeriods += length;
            actualLengths.add(length);
        }

        if (expectedPeriods == 0) {
            if (actualPeriods > 0) {
                failures.add(unitLabel + ": the course has no course meeting requirement "
                        + "but " + actualPeriods + " period(s) are scheduled");
            }
            return;
        }

        Collections.sort(expectedLengths);
        Collections.sort(actualLengths);
        if (actualPeriods != expectedPeriods || !expectedLengths.equals(actualLengths)) {
            failures.add(unitLabel
                    + "\n    Required: " + expectedPeriods + " period(s)/week"
                    + " (" + String.join(", ", requiredLines) + ")"
                    + "\n    Scheduled: " + actualPeriods + " period(s)"
                    + " in " + actualLengths.size() + " session(s)");
        }
    }

    private Map<UUID, List<CourseMeetingRequirement>> batchLoadCmrs(List<ClassSchedule> allSchedules) {
        Set<UUID> courseIds = new HashSet<>();
        for (ClassSchedule s : allSchedules) {
            if (s.getTeachingAssignment() != null) {
                courseIds.add(s.getTeachingAssignment().getCourse().getCourseId());
            } else if (s.getTeachingGroup() != null) {
                courseIds.add(s.getTeachingGroup().getCourse().getCourseId());
            }
        }
        Map<UUID, List<CourseMeetingRequirement>> map = new HashMap<>();
        if (!courseIds.isEmpty()) {
            for (CourseMeetingRequirement r : requirementRepository.findAllByCourse_CourseIdIn(courseIds)) {
                map.computeIfAbsent(r.getCourse().getCourseId(), k -> new ArrayList<>()).add(r);
            }
        }
        return map;
    }

    // ========== SCOPE PERSISTENCE ==========

    public record PersistedScope(UUID examTypeId, List<PersistedSemester> semesters) {}
    public record PersistedSemester(UUID semesterId, List<UUID> sectionIds) {}

    private String toScopeJson(UUID examTypeId, Map<UUID, Set<UUID>> scope) {
        List<PersistedSemester> semesters = scope.entrySet().stream()
                .map(e -> new PersistedSemester(e.getKey(),
                        e.getValue() == null ? null : new ArrayList<>(e.getValue())))
                .toList();
        try {
            return objectMapper.writeValueAsString(new PersistedScope(examTypeId, semesters));
        } catch (JsonProcessingException e) {
            throw new BusinessRuleException("Could not persist generation scope: " + e.getMessage());
        }
    }

    private PersistedScope parseScope(String scopeJson) {
        if (scopeJson == null || scopeJson.isBlank()) return null;
        try {
            return objectMapper.readValue(scopeJson, PersistedScope.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private Map<UUID, Set<UUID>> toScopeMap(PersistedScope persisted) {
        Map<UUID, Set<UUID>> scope = new HashMap<>();
        if (persisted != null) {
            for (PersistedSemester ps : persisted.semesters()) {
                scope.put(ps.semesterId(),
                        ps.sectionIds() == null ? null : new HashSet<>(ps.sectionIds()));
            }
        }
        return scope;
    }

    // ========== HELPERS ==========

    private Integer resolveParity(UUID examTypeId) {
        if (examTypeId == null) return null;
        ExamType examType = examTypeRepository.findById(examTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam type not found"));
        String name = examType.getExamTypeName();
        if (name == null
                || !(name.toLowerCase().contains("mid") || name.toLowerCase().contains("final"))) {
            throw new BusinessRuleException(
                    "Only 'Mid Term' or 'Final Term' exam types can drive timetable generation");
        }
        return name.toLowerCase().contains("mid") ? 1 : 0;
    }

    private boolean inScope(TeachingAssignment a, Map<UUID, Set<UUID>> scope) {
        if (scope.isEmpty()) return true;
        Semester sem = a.getCourse().getSemester();
        if (sem == null || !scope.containsKey(sem.getSemesterId())) return false;
        Set<UUID> sections = scope.get(sem.getSemesterId());
        return sections == null || sections.contains(a.getSection().getSectionId());
    }

    private String describe(TeachingAssignment a) {
        Integer semNo = a.getCourse().getSemester() != null
                ? a.getCourse().getSemester().getSemesterNo() : null;
        return "Semester " + (semNo != null ? semNo : "?")
                + " / Section " + a.getSection().getSectionName()
                + " / " + a.getCourse().getCourseCode();
    }

    private String describeGroup(List<TeachingAssignmentGroupMember> members) {
        TeachingAssignmentGroup group = members.get(0).getGroup();
        Integer semNo = group.getCourse().getSemester() != null
                ? group.getCourse().getSemester().getSemesterNo() : null;
        List<String> names = members.stream()
                .map(m -> m.getAssignment().getSection().getSectionName())
                .sorted()
                .toList();
        return "Semester " + (semNo != null ? semNo : "?")
                + " / Sections " + String.join(" + ", names)
                + " / " + group.getCourse().getCourseCode();
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
