package com.unicconnect.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unicconnect.dto.request.CreateGenerationRequest;
import com.unicconnect.dto.request.GenerateTimetableRequest;
import com.unicconnect.dto.response.GenerationManageResponse;
import com.unicconnect.dto.response.GenerationScopeSemester;
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
 * Greedy timetable generator driven entirely by {@code course_meeting_requirements}.
 *
 * <p>Placement rules:
 * <ul>
 *   <li>Scope: teaching assignments are filtered by the generation term and by the
 *       selected Mid/Final exam type (Mid Term = semesters 1/3/5/7, Final Term =
 *       2/4/6/8, resolved from {@code semesters.semester_no}) and by the explicit
 *       semester/section selections made by the lobby creator.</li>
 *   <li>Course sessions are placed first (Monday-Friday only, 6 real time slots).
 *       Sessions for the same assignment never share a day; consecutive-period
 *       sessions are allocated as Period X + Period X+1.</li>
 *   <li>No lecturer overlap, section overlap, or slot overlap. A course never
 *       exceeds its weekly requirement.</li>
 *   <li>LECTURE sessions are placed before LAB sessions.</li>
 *   <li>Only LMS and ASSIGNMENT special periods are placed on free slots.
 *       No BREAK schedule rows are ever created (lunch is a slot gap).</li>
 *   <li>Special periods have null teaching_assignment_id and null teaching_group_id
 *       as required by schema.</li>
 *   <li>Generation is all-or-nothing: if any required session cannot be placed the
 *       transaction rolls back (no partial {@code class_schedules}) and a precise
 *       report is thrown.</li>
 *   <li>Combined-section courses (A+B+C etc.) are taught as ONE unit: an HOD groups
 *       the section assignments of a course (teaching_assignment_groups) and the
 *       generator places a single {@code class_schedules} row for the whole group.
 *       The group's course requirement is consumed once, every member section and
 *       every member lecturer participates in conflict checks, and a course never
 *       occurs twice on the same day for the same section.</li>
 * </ul>
 */
@Service
@Transactional
public class TimetableGenerationService {

    private static final Logger log = LoggerFactory.getLogger(TimetableGenerationService.class);
    private static final int WORKING_DAY_START = 1; // Monday
    private static final int WORKING_DAY_END = 5;   // Friday
    private static final String[] DAY_NAMES = {
            "", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};

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
                                      ObjectMapper objectMapper) {
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
    }

    public List<GenerationSessionResponse> getAll(UUID termId) {
        // Normal users must never see drafts — only published generations.
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

    /**
     * Applicable semesters and their existing sections for the generation
     * configuration UI. Every semester of the selected exam type's convention is
     * returned (Mid Term = 1/3/5/7, Final Term = 2/4/6/8, resolved from
     * {@code semesters.semester_no}). Sections are derived from real
     * teaching-assignment data for the term; a parity semester that has no
     * assignments in this term (for example Final Term semester 8 before its
     * courses/assignments are created) is still surfaced with the master section
     * list so the scope stays complete. Generating for such a semester reports
     * "no courses or teaching assignments found" until data exists.
     */
    public List<GenerationScopeSemester> getGenerationScope(UUID termId, UUID examTypeId) {
        Integer parity = resolveParity(examTypeId);
        Map<UUID, Map<UUID, String>> bySem = new HashMap<>();
        for (TeachingAssignment a : assignmentRepository.findWithDetailsByTermId(termId)) {
            if (a.getAssignmentStatus() == AssignmentStatus.CANCELLED) continue;
            Semester sem = a.getCourse().getSemester();
            if (sem == null) continue;
            if (parity != null && sem.getSemesterNo() % 2 != parity) continue;
            bySem.computeIfAbsent(sem.getSemesterId(), k -> new LinkedHashMap<>())
                    .putIfAbsent(a.getSection().getSectionId(), a.getSection().getSectionName());
        }

        List<Section> masterSections = sectionRepository.findAll().stream()
                .sorted(Comparator.comparing(Section::getSectionName))
                .toList();

        List<GenerationScopeSemester> result = new ArrayList<>();
        List<Semester> ordered = semesterRepository.findAll().stream()
                .sorted(Comparator.comparing(Semester::getSemesterNo))
                .toList();
        for (Semester s : ordered) {
            if (parity != null && s.getSemesterNo() % 2 != parity) continue;
            Map<UUID, String> sections = bySem.get(s.getSemesterId());
            List<GenerationScopeSemester.SectionInfo> sectionInfos;
            if (sections == null) {
                sectionInfos = masterSections.stream()
                        .map(section -> new GenerationScopeSemester.SectionInfo(
                                section.getSectionId(), section.getSectionName()))
                        .toList();
            } else {
                sectionInfos = sections.entrySet().stream()
                        .map(e -> new GenerationScopeSemester.SectionInfo(e.getKey(), e.getValue()))
                        .sorted(Comparator.comparing(GenerationScopeSemester.SectionInfo::sectionName))
                        .toList();
            }
            result.add(new GenerationScopeSemester(s.getSemesterId(), s.getSemesterNo(), sectionInfos));
        }
        return result;
    }

    /**
     * Management workspace context: whether the caller is allowed to manage the
     * timetable for a term and the current draft (latest non-published) generation.
     * Authorization is decided server-side from the authenticated staff member's
     * active HOD assignment, never from a frontend flag.
     */
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
            // The draft is owned by a live lobby this staff member is not part of:
            // it must not be exposed through the generic management context.
            draft = null;
        }
        return new GenerationManageResponse(true, true, draft != null ? toResponse(draft) : null);
    }

    public GenerationSessionResponse create(CreateGenerationRequest request) {
        AcademicTerm term = termRepository.findById(request.termId())
                .orElseThrow(() -> new ResourceNotFoundException("Academic term not found"));
        // Generation is an HOD action; derive the generator from the authenticated user
        // instead of trusting a client-supplied staff id.
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

    private GenerationSessionResponse doGenerate(UUID generationId, UUID semesterId, UUID examTypeId,
                                                 List<GenerateTimetableRequest.SemesterSelection> selections) {
        Staff caller = hodAccessService.requireHod();
        GenerationSession generation = findGeneration(generationId);
        if (generation.getStatus() == GenerationStatus.PUBLISHED) {
            throw new BusinessRuleException("A published timetable cannot be regenerated");
        }

        // Creator-scope protection: while a shared lobby owns this generation,
        // only the lobby leader (creator) may run generation. Joined HODs must
        // not silently replace the shared scope with a different one.
        lobbyRepository.findByGeneration_GenerationId(generationId).ifPresent(lobby -> {
            if (lobby.getStatus() != LobbyStatus.COMPLETED
                    && !lobby.getLeaderStaff().getStaffId().equals(caller.getStaffId())) {
                throw new BusinessRuleException(
                        "Only the lobby leader (creator) can change the shared generation scope");
            }
        });

        // Mid/Final selection reuses the existing exam_types table.
        Integer parity = resolveParity(examTypeId);
        String examTypeName = parity != null
                ? (parity == 1 ? "Mid Term" : "Final Term") : null;

        // Build the semester -> section scope. null sections = every section of that semester.
        // An explicit selection IS the complete scope: never add unselected parity
        // semesters, otherwise Semester 2 A+B would silently pull in Semester 4/6/8.
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

        // Persist the selected scope so publish can revalidate completeness against
        // the exact scope that was generated.
        generation.setScopeJson(toScopeJson(examTypeId, scope));

        List<TeachingAssignment> assignments = assignmentRepository
                .findWithDetailsByTermId(generation.getTerm().getTermId()).stream()
                .filter(a -> a.getAssignmentStatus() != AssignmentStatus.CANCELLED)
                .filter(a -> inScope(a, scope))
                .toList();

        // Combined (shared) classes: members grouped by teaching group, grouped
        // assignments excluded from the per-section singleton list.
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

        // Load every course meeting requirement for the in-scope courses in a single
        // query instead of one query per course: against the remote database the
        // previous N+1 pattern made a full-scope generation approach the client's
        // request timeout.
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

        // Validation pass: every in-scope course must have a course meeting requirement.
        List<String> gaps = new ArrayList<>();
        for (TeachingAssignment a : assignments) {
            if (requirementsByCourse.getOrDefault(a.getCourse().getCourseId(), List.of()).isEmpty()) {
                gaps.add(describe(a) + ": course has no course meeting requirement");
            }
        }

        // Combined-group validation: a group is one unit, so every member section
        // must be part of the selected scope, otherwise the shared class cannot be
        // scheduled completely.
        List<TeachingAssignment> singletons = new ArrayList<>();
        for (TeachingAssignment a : assignments) {
            if (!groupedAssignmentIds.contains(a.getAssignmentId())) {
                singletons.add(a);
            }
        }
        for (List<TeachingAssignmentGroupMember> members : membersByGroup.values()) {
            TeachingAssignmentGroup group = members.get(0).getGroup();
            Course course = group.getCourse();
            Semester sem = course.getSemester();
            boolean semesterInScope = scope.isEmpty()
                    || (sem != null && scope.containsKey(sem.getSemesterId()));
            if (!semesterInScope) {
                // Whole group outside the selected semesters -> treated like an
                // out-of-scope singleton: skipped, never scheduled.
                continue;
            }
            Set<UUID> scopeSections = sem != null ? scope.get(sem.getSemesterId()) : null;
            if (scopeSections != null) {
                for (TeachingAssignmentGroupMember m : members) {
                    if (!scopeSections.contains(m.getAssignment().getSection().getSectionId())) {
                        throw new BusinessRuleException("Cannot generate timetable: combined class "
                                + describeGroup(members) + " is only partially selected. All sections of a "
                                + "combined class must be selected together (the whole A + B + C group).");
                    }
                }
            }
            if (requirementsByCourse.getOrDefault(course.getCourseId(), List.of()).isEmpty()) {
                gaps.add(describeGroup(members) + ": course has no course meeting requirement");
            }
        }
        if (!gaps.isEmpty()) {
            throw new BusinessRuleException("Cannot generate timetable:\n" + String.join("\n", gaps));
        }

        generation.setStatus(GenerationStatus.GENERATING);
        generation.setStartedAt(Instant.now());
        generation.setFinishedAt(null);
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

        // Place course sessions from requirements (LECTURE before LAB per course).
        // Combined (shared) classes are placed first so the widest set of sections
        // benefits from a free slot; each group is one scheduling unit.
        for (List<TeachingAssignmentGroupMember> members : membersByGroup.values()) {
            TeachingAssignmentGroup group = members.get(0).getGroup();
            Course course = group.getCourse();
            Semester sem = course.getSemester();
            if (!scope.isEmpty() && (sem == null || !scope.containsKey(sem.getSemesterId()))) {
                continue;
            }
            List<CourseMeetingRequirement> requirements =
                    requirementsByCourse.getOrDefault(course.getCourseId(), List.of());
            for (CourseMeetingRequirement req : requirements) {
                int placed = placeGroupSessions(generation, group, members, req, slots, occupied, created);
                if (placed < req.getSessionsPerWeek()) {
                    gaps.add(describeGroup(members) + " / " + req.getMeetingType()
                            + ": only " + placed + " of " + req.getSessionsPerWeek()
                            + " sessions (" + req.getPeriodsPerSession() + " period(s) each) could be scheduled");
                }
            }
        }
        for (TeachingAssignment assignment : singletons) {
            List<CourseMeetingRequirement> requirements =
                    requirementsByCourse.getOrDefault(assignment.getCourse().getCourseId(), List.of());
            for (CourseMeetingRequirement req : requirements) {
                int placed = placeCourseSessions(generation, assignment, req, slots, occupied, created);
                if (placed < req.getSessionsPerWeek()) {
                    gaps.add(describe(assignment) + " / " + req.getMeetingType()
                            + ": only " + placed + " of " + req.getSessionsPerWeek()
                            + " sessions (" + req.getPeriodsPerSession() + " period(s) each) could be scheduled");
                }
            }
        }

        if (!gaps.isEmpty()) {
            throw new BusinessRuleException("Timetable generation failed:\n" + String.join("\n", gaps));
        }

        // Free-time special periods (no BREAK rows).
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
        log.info("Timetable generated: {} schedules for generation {} (examType={})",
                created.size(), generationId, examTypeName);
        // Notify every lobby participant so their timetable updates in real time.
        realtimeEventService.publishForGeneration(generationId,
                TimetableRealtimeEventService.GENERATION_COMPLETED,
                Map.of("generationId", generationId));
        return toResponse(generation);
    }

    public GenerationSessionResponse publish(UUID generationId) {
        hodAccessService.requireHod();
        lobbyAccessService.requireSharedDraftAccess(generationId);
        GenerationSession generation = findGeneration(generationId);
        if (generation.getStatus() != GenerationStatus.COMPLETED) {
            throw new BusinessRuleException("Only a completed generation can be published");
        }
        List<ClassSchedule> schedules = scheduleRepository.findByGeneration_GenerationId(generationId);
        if (schedules.isEmpty()) {
            throw new BusinessRuleException("Cannot publish an empty timetable");
        }
        validateConflictsForPublish(schedules);
        validateCompletenessForPublish(generation, schedules);
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
        GenerationSessionResponse response = toResponse(generationRepository.save(generation));

        // The lobby lifecycle completes with publish; everyone leaves the draft
        // workspace and sees the published timetable.
        lobbyRepository.findByGeneration_GenerationId(generationId).ifPresent(lobby -> {
            lobby.setStatus(LobbyStatus.COMPLETED);
            lobbyRepository.save(lobby);
            realtimeEventService.publish(lobby.getLobbyId(),
                    TimetableRealtimeEventService.TIMETABLE_PUBLISHED,
                    Map.of("generationId", generationId, "lobbyId", lobby.getLobbyId()));
        });
        return response;
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
            if (!lobbyAccessService.canAccessSharedDraft(generationId)) {
                throw new BusinessRuleException(
                        "Only the lobby leader or joined lobby members can access this shared draft");
            }
        }
        return scheduleRepository.findByGeneration_GenerationId(generationId).stream()
                .sorted(Comparator.comparing(ClassSchedule::getDayOfWeek)
                        .thenComparing(s -> s.getStartSlot().getDisplayOrder()))
                .map(ClassScheduleService::toResponse).toList();
    }

    // ---------- Placement helpers ----------

    /**
     * Resolves the Mid/Final semester parity from the existing {@code exam_types}
     * table: 'Mid Term' = odd semesters (1/3/5/7), 'Final Term' = even (2/4/6/8).
     */
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

    private int placeCourseSessions(GenerationSession generation, TeachingAssignment assignment,
                                    CourseMeetingRequirement req, List<TimeSlot> slots,
                                    boolean[][] occupied, List<ClassSchedule> created) {
        Set<UUID> staffIds = new HashSet<>();
        staffIds.add(assignment.getStaff().getStaffId());
        Set<UUID> sectionIds = new HashSet<>();
        sectionIds.add(assignment.getSection().getSectionId());
        return placeUnitSessions(generation, assignment, null, staffIds, sectionIds,
                req, slots, occupied, created);
    }

    private int placeGroupSessions(GenerationSession generation, TeachingAssignmentGroup group,
                                   List<TeachingAssignmentGroupMember> members,
                                   CourseMeetingRequirement req, List<TimeSlot> slots,
                                   boolean[][] occupied, List<ClassSchedule> created) {
        Set<UUID> staffIds = new HashSet<>();
        Set<UUID> sectionIds = new HashSet<>();
        for (TeachingAssignmentGroupMember m : members) {
            staffIds.add(m.getAssignment().getStaff().getStaffId());
            sectionIds.add(m.getAssignment().getSection().getSectionId());
        }
        return placeUnitSessions(generation, null, group, staffIds, sectionIds,
                req, slots, occupied, created);
    }

    /**
     * Places the weekly sessions of one scheduling unit (a single-section teaching
     * assignment or a combined teaching group). A unit never occupies two slots on
     * the same day, so a course can never appear twice on one day for the same
     * section. The busy checks cover every member lecturer and member section.
     */
    private int placeUnitSessions(GenerationSession generation,
                                  TeachingAssignment assignment,
                                  TeachingAssignmentGroup group,
                                  Set<UUID> staffIds,
                                  Set<UUID> sectionIds,
                                  CourseMeetingRequirement req, List<TimeSlot> slots,
                                  boolean[][] occupied, List<ClassSchedule> created) {
        int sessions = req.getSessionsPerWeek();
        int perSession = req.getPeriodsPerSession();
        int placed = 0;

        Set<Integer> usedDays = new HashSet<>();
        for (int d = WORKING_DAY_START; d <= WORKING_DAY_END && usedDays.size() < sessions; d++) {
            for (int startIdx = 0; startIdx + perSession <= slots.size(); startIdx++) {
                if (isFree(occupied, d, startIdx, perSession)
                        && !coveredStaffBusy(created, staffIds, d, startIdx, perSession)
                        && !coveredSectionBusy(created, sectionIds, d, startIdx, perSession)) {
                    created.add(buildUnitSchedule(generation, assignment, group, d,
                            slots, startIdx, perSession));
                    markOccupied(occupied, d, startIdx, perSession);
                    usedDays.add(d);
                    placed++;
                    break;
                }
            }
        }
        return placed;
    }

    private void placeSpecial(GenerationSession generation, ScheduleType type, int preferredSlot,
                              int day, List<TimeSlot> slots, boolean[][] occupied,
                              List<ClassSchedule> created) {
        int target = (preferredSlot >= 0 && preferredSlot < slots.size()) ? preferredSlot : -1;
        if (target < 0) return;
        ClassSchedule schedule = new ClassSchedule();
        schedule.setGeneration(generation);
        schedule.setDayOfWeek(day);
        schedule.setStartSlot(slots.get(target));
        schedule.setEndSlot(slots.get(target));
        schedule.setScheduleType(type);
        schedule.setScheduleStatus(ScheduleStatus.PENDING);
        created.add(schedule);
        markOccupied(occupied, day, target, 1);
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

    private boolean coveredStaffBusy(List<ClassSchedule> created, Set<UUID> staffIds,
                                     int day, int startIdx, int length) {
        int startOrder = startIdx;
        int endOrder = startIdx + length - 1;
        for (ClassSchedule s : created) {
            if (s.getDayOfWeek() != day || s.getScheduleType() != ScheduleType.COURSE) continue;
            int otherStart = s.getStartSlot().getDisplayOrder();
            int otherEnd = s.getEndSlot().getDisplayOrder();
            if (startOrder > otherEnd || endOrder < otherStart) continue;
            if (!Collections.disjoint(staffIds, ClassScheduleService.coveredStaff(s))) return true;
        }
        return false;
    }

    private boolean coveredSectionBusy(List<ClassSchedule> created, Set<UUID> sectionIds,
                                       int day, int startIdx, int length) {
        int startOrder = startIdx;
        int endOrder = startIdx + length - 1;
        for (ClassSchedule s : created) {
            if (s.getDayOfWeek() != day || s.getScheduleType() != ScheduleType.COURSE) continue;
            int otherStart = s.getStartSlot().getDisplayOrder();
            int otherEnd = s.getEndSlot().getDisplayOrder();
            if (startOrder > otherEnd || endOrder < otherStart) continue;
            if (!Collections.disjoint(sectionIds, ClassScheduleService.coveredSections(s))) return true;
        }
        return false;
    }

    private ClassSchedule buildUnitSchedule(GenerationSession generation,
                                            TeachingAssignment assignment,
                                            TeachingAssignmentGroup group,
                                            int day, List<TimeSlot> slots,
                                            int startIdx, int length) {
        ClassSchedule schedule = new ClassSchedule();
        schedule.setGeneration(generation);
        schedule.setTeachingAssignment(assignment);
        schedule.setTeachingGroup(group);
        schedule.setDayOfWeek(day);
        schedule.setStartSlot(slots.get(startIdx));
        schedule.setEndSlot(slots.get(startIdx + length - 1));
        schedule.setScheduleType(ScheduleType.COURSE);
        schedule.setScheduleStatus(ScheduleStatus.PENDING);
        return schedule;
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

    // ---------- Publish conflict revalidation ----------

    private void validateConflictsForPublish(List<ClassSchedule> schedules) {
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
                    // A course must never occur twice on one day for the same section,
                    // even in non-overlapping periods.
                    if (b.getScheduleType() == ScheduleType.COURSE
                            && a.getTeachingGroup() != null && b.getTeachingGroup() != null
                            && a.getTeachingGroup().getGroupId().equals(b.getTeachingGroup().getGroupId())) {
                        throw new BusinessRuleException("Cannot publish: " + ClassScheduleService.courseCodeOf(a)
                                + " is scheduled more than once on " + DAY_NAMES[day] + " for the same section");
                    }
                    if (b.getScheduleType() == ScheduleType.COURSE
                            && ClassScheduleService.courseCodeOf(a) != null
                            && ClassScheduleService.courseCodeOf(a).equals(ClassScheduleService.courseCodeOf(b))
                            && !Collections.disjoint(ClassScheduleService.coveredSections(a),
                                    ClassScheduleService.coveredSections(b))) {
                        throw new BusinessRuleException("Cannot publish: " + ClassScheduleService.courseCodeOf(a)
                                + " is scheduled more than once on " + DAY_NAMES[day] + " for the same section");
                    }
                    if (!overlapsSlots(a, b)) continue;
                    boolean conflict = b.getScheduleType() != ScheduleType.COURSE
                            || !Collections.disjoint(ClassScheduleService.coveredStaff(a),
                                    ClassScheduleService.coveredStaff(b))
                            || !Collections.disjoint(ClassScheduleService.coveredSections(a),
                                    ClassScheduleService.coveredSections(b));
                    if (conflict) {
                        throw new BusinessRuleException("Cannot publish: unresolved timetable conflict on "
                                + DAY_NAMES[day] + " between " + scheduleLabel(a) + " and " + scheduleLabel(b));
                    }
                }
            }
        }
    }

    private boolean overlapsSlots(ClassSchedule a, ClassSchedule b) {
        return a.getStartSlot().getDisplayOrder() <= b.getEndSlot().getDisplayOrder()
                && b.getStartSlot().getDisplayOrder() <= a.getEndSlot().getDisplayOrder();
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

    // ---------- Scope persistence + publish completeness ----------

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
        if (scopeJson == null || scopeJson.isBlank()) {
            return null;
        }
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

    /**
     * Publish-time completeness revalidation (req 1-9). The workload is
     * recalculated from course_meeting_requirements for the exact persisted scope
     * and compared with what is actually in class_schedules after HOD drag/drop
     * and delete operations. All failures are reported together; publish is
     * rejected unless every selected teaching unit exactly matches its
     * requirement (period count AND session structure AND section coverage).
     */
    private void validateCompletenessForPublish(GenerationSession generation,
                                                List<ClassSchedule> allSchedules) {
        List<String> failures = new ArrayList<>();

        // Req 8: only COURSE, LMS, ASSIGNMENT schedule types may be published.
        for (ClassSchedule s : allSchedules) {
            if (s.getScheduleType() != ScheduleType.COURSE
                    && s.getScheduleType() != ScheduleType.LMS
                    && s.getScheduleType() != ScheduleType.ASSIGNMENT) {
                failures.add("Invalid schedule type " + s.getScheduleType()
                        + " (" + scheduleLabel(s) + ")");
            }
        }

        // Req 9: Monday-Friday, valid DB slots, start <= end, within 6 periods/day.
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

        // Teaching groups for the term: a group is ONE unit, requirement consumed once.
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

        // Req 7: every COURSE schedule must belong to a semester/section in the scope.
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

        // Index the draft's course schedules by teaching unit.
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

        // A grouped member must only appear through its group schedule.
        for (ClassSchedule s : courseSchedules) {
            if (s.getTeachingAssignment() != null
                    && groupedAssignmentIds.contains(s.getTeachingAssignment().getAssignmentId())) {
                failures.add(scheduleLabel(s)
                        + ": a combined-class member assignment is scheduled individually");
            }
        }

        // Singleton teaching assignments that must be complete.
        List<TeachingAssignment> expectedAssignments;
        if (useScope) {
            expectedAssignments = assignmentRepository
                    .findWithDetailsByTermId(generation.getTerm().getTermId()).stream()
                    .filter(a -> a.getAssignmentStatus() != AssignmentStatus.CANCELLED)
                    .filter(a -> inScope(a, scopeMap))
                    .filter(a -> !groupedAssignmentIds.contains(a.getAssignmentId()))
                    .toList();
        } else {
            // Legacy generation without a persisted scope: validate the units that
            // the draft actually references (best effort for old data).
            expectedAssignments = assignmentRepository
                    .findWithDetailsByTermId(generation.getTerm().getTermId()).stream()
                    .filter(a -> byAssignment.containsKey(a.getAssignmentId()))
                    .filter(a -> !groupedAssignmentIds.contains(a.getAssignmentId()))
                    .toList();
        }
        for (TeachingAssignment assignment : expectedAssignments) {
            validateUnitPeriods(failures, assignment.getCourse(), describe(assignment),
                    byAssignment.get(assignment.getAssignmentId()));
        }

        // Combined groups: same validation, requirement consumed ONCE.
        for (Map.Entry<UUID, List<TeachingAssignmentGroupMember>> entry : membersByGroup.entrySet()) {
            List<TeachingAssignmentGroupMember> members = entry.getValue();
            TeachingAssignmentGroup group = members.get(0).getGroup();
            Course course = group.getCourse();
            Semester sem = course.getSemester();
            List<String> sectionNames = members.stream()
                    .map(m -> m.getAssignment().getSection().getSectionName())
                    .sorted().toList();

            boolean inScope;
            if (!useScope) {
                inScope = byGroup.containsKey(group.getGroupId());
            } else if (scopeMap.isEmpty()) {
                inScope = true;
            } else if (sem == null || !scopeMap.containsKey(sem.getSemesterId())) {
                inScope = false;
            } else {
                Set<UUID> scopeSections = scopeMap.get(sem.getSemesterId());
                inScope = scopeSections == null || members.stream().allMatch(m ->
                        scopeSections.contains(m.getAssignment().getSection().getSectionId()));
            }
            if (!inScope) continue;

            String label = "Semester " + (sem != null ? sem.getSemesterNo() : "?")
                    + " / Section " + String.join(" + ", sectionNames)
                    + " / " + course.getCourseCode();
            validateUnitPeriods(failures, course, label, byGroup.get(group.getGroupId()));
        }

        if (!failures.isEmpty()) {
            throw new BusinessRuleException(
                    "Timetable cannot be published:\n" + String.join("\n", failures));
        }
    }

    private void validateUnitPeriods(List<String> failures, Course course, String unitLabel,
                                     List<ClassSchedule> unitSchedules) {
        List<CourseMeetingRequirement> requirements = requirementRepository
                .findByCourse_CourseId(course.getCourseId()).stream()
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

    /** Serialized form of the generation scope (Mid/Final + semester -> sections). */
    public record PersistedScope(UUID examTypeId, List<PersistedSemester> semesters) {}

    public record PersistedSemester(UUID semesterId, List<UUID> sectionIds) {}

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
