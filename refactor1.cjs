const fs = require('fs');
const dir = 'uniconnect-core/src/main/java/com/unicconnect/service/';
function edit(file, pairs) {
  const p = dir + file;
  let c = fs.readFileSync(p, 'utf8').replace(/\r\n/g, '\n');
  for (const [from, to] of pairs) {
    if (!c.includes(from)) { console.log(`MISS ${file} :: ${JSON.stringify(from.slice(0,70))}`); process.exitCode = 1; continue; }
    c = c.split(from).join(to);
  }
  fs.writeFileSync(p, c);
}

edit('UserService.java', [
  ['import com.unicconnect.util.SecurityUtil;\n', ''],
  ['    private final SecurityUtil securityUtil;\n', ''],
  ['                       RefreshTokenRepository refreshTokenRepository,\n                       SecurityUtil securityUtil) {',
   '                       RefreshTokenRepository refreshTokenRepository) {'],
  ['        this.refreshTokenRepository = refreshTokenRepository;\n        this.securityUtil = securityUtil;\n',
   '        this.refreshTokenRepository = refreshTokenRepository;\n'],
  ['    public void deleteUser(UUID userId) {\n        User user = findUser(userId);\n        if (userId.equals(securityUtil.currentUserId())) {',
   '    public void deleteUser(UUID actingUserId, UUID targetUserId) {\n        User user = findUser(targetUserId);\n        if (targetUserId.equals(actingUserId)) {'],
  ['    public void deleteUsers(List<UUID> userIds) {\n        for (UUID userId : userIds) {\n            deleteUser(userId);',
   '    public void deleteUsers(UUID actingUserId, List<UUID> userIds) {\n        for (UUID targetId : userIds) {\n            deleteUser(actingUserId, targetId);'],
]);

edit('AttendanceService.java', [
  ['import com.unicconnect.util.SecurityUtil;\n', ''],
  ['    private final SecurityUtil securityUtil;\n', ''],
  ['                               TimeSlotRepository timeSlotRepository,\n                               SecurityUtil securityUtil,\n                               RollCallService rollCallService) {',
   '                               TimeSlotRepository timeSlotRepository,\n                               RollCallService rollCallService) {'],
  ['        this.timeSlotRepository = timeSlotRepository;\n        this.securityUtil = securityUtil;\n',
   '        this.timeSlotRepository = timeSlotRepository;\n'],
  ['    public List<AttendanceResponse> markAttendance(UUID sessionId, MarkAttendanceRequest request) {',
   '    public List<AttendanceResponse> markAttendance(UUID sessionId, MarkAttendanceRequest request,\n                                                   UUID callerUserId) {'],
  ['        Staff staff = staffRepository.findByUser_UserId(securityUtil.currentUserId())\n                .orElseThrow(() -> new BusinessRuleException("Only staff can perform roll call"));\n        rollCallService.authorizeLecturerForSchedule(staff, session.getSchedule());',
   '        Staff staff = staffRepository.findByUser_UserId(callerUserId)\n                .orElseThrow(() -> new BusinessRuleException("Only staff can perform roll call"));\n        rollCallService.authorizeLecturerForSchedule(staff, session.getSchedule());'],
  ['    public AttendanceResponse update(UUID attendanceId, UpdateAttendanceRequest request) {\n        Attendance attendance = findAttendance(attendanceId);\n        Staff staff = staffRepository.findByUser_UserId(securityUtil.currentUserId())',
   '    public AttendanceResponse update(UUID attendanceId, UpdateAttendanceRequest request,\n                                     UUID callerUserId) {\n        Attendance attendance = findAttendance(attendanceId);\n        Staff staff = staffRepository.findByUser_UserId(callerUserId)'],
]);

edit('RollCallService.java', [
  ['    private final com.unicconnect.util.SecurityUtil securityUtil;\n', ''],
  ['                           com.unicconnect.repository.StaffPositionAssignmentRepository positionAssignmentRepository,\n                           com.unicconnect.util.SecurityUtil securityUtil) {',
   '                           com.unicconnect.repository.StaffPositionAssignmentRepository positionAssignmentRepository) {'],
  ['        this.positionAssignmentRepository = positionAssignmentRepository;\n        this.securityUtil = securityUtil;\n',
   '        this.positionAssignmentRepository = positionAssignmentRepository;\n'],
  ['    /** Current staff holding an active LECTURER position today. */\n    public Staff requireLecturer() {\n        UUID userId = securityUtil.currentUserId();',
   '    /** Staff holding an active LECTURER position today (explicit authenticated user id). */\n    public Staff requireLecturer(UUID userId) {'],
  ['            LocalDate from, LocalDate to) {\n        Staff lecturer = requireLecturer();',
   '            LocalDate from, LocalDate to, UUID callerUserId) {\n        Staff lecturer = requireLecturer(callerUserId);'],
]);

edit('ClassScheduleService.java', [
  ['import org.springframework.stereotype.Service;',
   'import com.unicconnect.service.port.TimetableAccessPort;\nimport com.unicconnect.service.port.TimetableEventPort;\nimport org.springframework.stereotype.Service;'],
  ['    private final HodAccessService hodAccessService;\n    private final TimetableEditLockService editLockService;\n    private final TimetableRealtimeEventService realtimeEventService;',
   '    private final TimetableAccessPort accessPort;\n    private final TimetableEventPort eventPort;'],
  ['                                 HodAccessService hodAccessService,\n                                 TimetableEditLockService editLockService,\n                                 TimetableRealtimeEventService realtimeEventService) {',
   '                                 TimetableAccessPort accessPort,\n                                 TimetableEventPort eventPort) {'],
  ['        this.hodAccessService = hodAccessService;\n        this.editLockService = editLockService;\n        this.realtimeEventService = realtimeEventService;',
   '        this.accessPort = accessPort;\n        this.eventPort = eventPort;'],
  ['hodAccessService.currentHod()', 'accessPort.currentHod()'],
  ['hodAccessService.requireHod();', 'accessPort.requireHod();'],
  ['editLockService.requireLockOwned(', 'accessPort.requireEditLockOwnership('],
  ['realtimeEventService.publishForGeneration(', 'eventPort.publishForGeneration('],
  ['TimetableRealtimeEventService.SCHEDULE_', 'TimetableEventPort.SCHEDULE_'],
]);

edit('TimetableGenerationService.java', [
  ['import org.springframework.context.annotation.Lazy;',
   'import com.unicconnect.service.port.TimetableAccessPort;\nimport com.unicconnect.service.port.TimetableEventPort;\nimport org.springframework.context.annotation.Lazy;'],
  ['    private final HodAccessService hodAccessService;', '    private final TimetableAccessPort accessPort;'],
  ['    private final TimetableRealtimeEventService realtimeEventService;', '    private final TimetableEventPort eventPort;'],
  ['                                      HodAccessService hodAccessService,\n', ''],
  ['                                      TimetableLobbyAccessService lobbyAccessService,\n', ''],
  ['                                      TimetableRealtimeEventService realtimeEventService,\n', ''],
  ['                                      ObjectMapper objectMapper,',
   '                                      TimetableAccessPort accessPort,\n                                      TimetableEventPort eventPort,\n                                      ObjectMapper objectMapper,'],
  ['        this.hodAccessService = hodAccessService;', '        this.accessPort = accessPort;'],
  ['        this.realtimeEventService = realtimeEventService;', '        this.eventPort = eventPort;'],
  ['hodAccessService.requireHod()', 'accessPort.requireHod()'],
  ['hodAccessService.currentHod()', 'accessPort.currentHod()'],
  ['lobbyAccessService.canAccessSharedDraft(', 'accessPort.canAccessSharedDraft('],
  ['lobbyAccessService.requireSharedDraftAccess(', 'accessPort.requireSharedDraftAccess('],
  ['realtimeEventService.publishForGeneration(', 'eventPort.publishForGeneration('],
  ['realtimeEventService.publish(', 'eventPort.publish('],
  ['TimetableRealtimeEventService.', 'TimetableEventPort.'],
]);
console.log('edits applied');
