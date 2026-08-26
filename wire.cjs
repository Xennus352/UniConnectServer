const fs=require('fs');
const dir='uniconnect-api/src/main/java/com/unicconnect/';
function edit(rel,pairs){const p=dir+rel;let c=fs.readFileSync(p,'utf8').replace(/\r\n/g,'\n');
 for(const [f,t] of pairs){ if(!c.includes(f)){console.log('MISS '+rel+' :: '+JSON.stringify(f.slice(0,60)));process.exitCode=1;} c=c.split(f).join(t);}
 fs.writeFileSync(p,c);}

edit('controller/UserController.java',[
 [`import com.unicconnect.service.UserService;\nimport com.unicconnect.util.SecurityUtil;`,
  `import com.unicconnect.ops.UserOperations;\nimport com.unicconnect.service.UserService;\nimport com.unicconnect.util.SecurityUtil;`],
 [`    private final UserService userService;\n    private final SecurityUtil securityUtil;\n\n    public UserController(UserService userService, SecurityUtil securityUtil) {\n        this.userService = userService;\n        this.securityUtil = securityUtil;\n    }`,
  `    private final UserService userService;\n    private final UserOperations userOperations;\n    private final SecurityUtil securityUtil;\n\n    public UserController(UserService userService,\n                          UserOperations userOperations,\n                          SecurityUtil securityUtil) {\n        this.userService = userService;\n        this.userOperations = userOperations;\n        this.securityUtil = securityUtil;\n    }`],
 ['        return ResponseEntity.ok(userService.getAllUsers());','        return ResponseEntity.ok(userOperations.getAll());'],
 ['        return ResponseEntity.status(201).body(userService.createUser(request));','        return ResponseEntity.status(201).body(userOperations.create(request));'],
 ['        return ResponseEntity.ok(userService.getUsersByRole(roleName));','        return ResponseEntity.ok(userOperations.byRole(roleName));'],
 ['        return ResponseEntity.ok(userService.getUserById(userId));','        return ResponseEntity.ok(userOperations.getById(userId));'],
 ['        return ResponseEntity.ok(userService.updateStatus(userId, request));','        return ResponseEntity.ok(userOperations.updateStatus(userId, request));'],
 ['        return ResponseEntity.ok(userService.updateUser(userId, request));','        return ResponseEntity.ok(userOperations.update(userId, request));'],
 [`        userService.deleteUser(userId);\n        return ResponseEntity.noContent().build();`,
  `        userOperations.delete(securityUtil.currentUserId(), userId);\n        return ResponseEntity.noContent().build();`],
 [`        userService.deleteUsers(request.userIds());\n        return ResponseEntity.noContent().build();`,
  `        userOperations.deleteBulk(securityUtil.currentUserId(), request.userIds());\n        return ResponseEntity.noContent().build();`],
 ['        return ResponseEntity.ok(userService.updateRole(userId, request));','        return ResponseEntity.ok(userOperations.updateRole(userId, request));'],
]);

// ---------- RollCallController ----------
let rc=`import com.unicconnect.dto.response.RollCallStudentsResponse;\nimport com.unicconnect.entity.Staff;\nimport com.unicconnect.service.AttendanceCalculationService;\nimport com.unicconnect.service.AttendanceService;\nimport com.unicconnect.service.RollCallService;`;
let rcrep=`import com.unicconnect.dto.response.RollCallStudentsResponse;\nimport com.unicconnect.entity.Staff;\nimport com.unicconnect.ops.AttendanceOperations;\nimport com.unicconnect.service.RollCallService;`;
edit('controller/RollCallController.java',[
 [rc,rcrep],
 [`    private final RollCallService rollCallService;\n    private final AttendanceService attendanceService;\n    private final AttendanceCalculationService calculationService;\n\n    public RollCallController(RollCallService rollCallService,\n                              AttendanceService attendanceService,\n                              AttendanceCalculationService calculationService) {\n        this.rollCallService = rollCallService;\n        this.attendanceService = attendanceService;\n        this.calculationService = calculationService;\n    }`,
  `    private final RollCallService rollCallService;\n    private final AttendanceOperations attendanceOperations;\n    private final com.unicconnect.util.SecurityUtil securityUtil;\n\n    public RollCallController(RollCallService rollCallService,\n                              AttendanceOperations attendanceOperations,\n                              com.unicconnect.util.SecurityUtil securityUtil) {\n        this.rollCallService = rollCallService;\n        this.attendanceOperations = attendanceOperations;\n        this.securityUtil = securityUtil;\n    }\n\n    /** Authenticated staff id, resolved once per request. */\n    private UUID caller() { return securityUtil.currentUserId(); }`],
]);
c=fs.readFileSync(dir+'controller/RollCallController.java','utf8');
c=c.replace(/rollCallService\.requireLecturer\(\)/g,'rollCallService.requireLecturer(caller())');
c=c.replace(`        return ResponseEntity.ok(attendanceService.markAttendance(sessionId, request));`,
            `        return ResponseEntity.ok(attendanceOperations.mark(sessionId, request, caller()));`);
c=c.replace(`        rollCallService.requireLecturer(caller());\n        return ResponseEntity.ok(calculationService.daily(sessionId));`,
            `        rollCallService.requireLecturer(caller());\n        return ResponseEntity.ok(attendanceOperations.daily(sessionId, caller()));`);
c=c.replace(`        rollCallService.requireLecturer(caller());\n        return ResponseEntity.ok(\n                calculationService.monthly(studentId, courseId, year, month));`,
            `        rollCallService.requireLecturer(caller());\n        return ResponseEntity.ok(\n                attendanceOperations.monthly(studentId, courseId, year, month, caller()));`);
c=c.replace(`        return ResponseEntity.ok(rollCallService.historyByCohort(\n                courseCode, semesterNo, sectionName, fromDate, toDate));`,
            `        return ResponseEntity.ok(rollCallService.historyByCohort(\n                courseCode, semesterNo, sectionName, fromDate, toDate, caller()));`);
fs.writeFileSync(dir+'controller/RollCallController.java',c);

// ---------- ClassScheduleController ----------
edit('controller/ClassScheduleController.java',[
 [`import com.unicconnect.service.ClassScheduleService;\nimport com.unicconnect.service.ClassSessionService;`,
  `import com.unicconnect.ops.TimetableQueryOperations;\nimport com.unicconnect.service.ClassScheduleService;\nimport com.unicconnect.service.ClassSessionService;`],
 [`    private final ClassScheduleService scheduleService;\n    private final ClassSessionService sessionService;\n\n    public ClassScheduleController(ClassScheduleService scheduleService,\n                                   ClassSessionService sessionService) {\n        this.scheduleService = scheduleService;\n        this.sessionService = sessionService;\n    }`,
  `    private final ClassScheduleService scheduleService;\n    private final ClassSessionService sessionService;\n    private final TimetableQueryOperations queryOperations;\n\n    public ClassScheduleController(ClassScheduleService scheduleService,\n                                   ClassSessionService sessionService,\n                                   TimetableQueryOperations queryOperations) {\n        this.scheduleService = scheduleService;\n        this.sessionService = sessionService;\n        this.queryOperations = queryOperations;\n    }`],
 ['        return ResponseEntity.ok(scheduleService.getAll(termId, sectionId, staffId, dayOfWeek));',
  '        return ResponseEntity.ok(queryOperations.getAll(termId, sectionId, staffId, dayOfWeek));'],
 ['        return ResponseEntity.ok(scheduleService.getPublished(termId));',
  '        return ResponseEntity.ok(queryOperations.getPublished(termId));'],
 [`    @GetMapping(\"/{scheduleId}\")\n    public ResponseEntity<ScheduleResponse> getById(@PathVariable UUID scheduleId) {\n        return ResponseEntity.ok(scheduleService.getById(scheduleId));\n    }`,
  `    @GetMapping(\"/{scheduleId}\")\n    public ResponseEntity<ScheduleResponse> getById(@PathVariable UUID scheduleId) {\n        return ResponseEntity.ok(queryOperations.getById(scheduleId));\n    }`],
]);

// ---------- TimetableGenerationController ----------
edit('controller/TimetableGenerationController.java',[
 [`import com.unicconnect.service.ClassScheduleService;\nimport com.unicconnect.service.TimetableEditLockService;\nimport com.unicconnect.service.TimetableGenerationService;`,
  `import com.unicconnect.ops.TimetableGenerationOperations;\nimport com.unicconnect.service.ClassScheduleService;\nimport com.unicconnect.service.TimetableEditLockService;\nimport com.unicconnect.service.TimetableGenerationService;`],
 [`    private final TimetableGenerationService service;\n    private final TimetableEditLockService lockService;\n    private final ClassScheduleService scheduleService;\n\n    public TimetableGenerationController(TimetableGenerationService service,\n                                         TimetableEditLockService lockService,\n                                         ClassScheduleService scheduleService) {\n        this.service = service;\n        this.lockService = lockService;\n        this.scheduleService = scheduleService;\n    }`,
  `    private final TimetableGenerationService service;\n    private final TimetableEditLockService lockService;\n    private final ClassScheduleService scheduleService;\n    private final TimetableGenerationOperations generationOperations;\n\n    public TimetableGenerationController(TimetableGenerationService service,\n                                         TimetableEditLockService lockService,\n                                         ClassScheduleService scheduleService,\n                                         TimetableGenerationOperations generationOperations) {\n        this.service = service;\n        this.lockService = lockService;\n        this.scheduleService = scheduleService;\n        this.generationOperations = generationOperations;\n    }`],
 ['        return ResponseEntity.ok(service.getAll(termId));','        return ResponseEntity.ok(generationOperations.getAll(termId));'],
 ['        return ResponseEntity.ok(service.getById(generationId));','        return ResponseEntity.ok(generationOperations.getById(generationId));'],
 [`        return ResponseEntity.ok(service.create(request));`,
  `        return ResponseEntity.ok(generationOperations.create(request));`],
 [`        return ResponseEntity.ok(service.generate(generationId, request));`,
  `        return ResponseEntity.ok(generationOperations.generate(generationId, request));`],
]);

console.log('controllers wired');
