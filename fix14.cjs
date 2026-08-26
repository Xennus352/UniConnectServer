const fs=require('fs');
function edit(p,pairs){let c=fs.readFileSync(p,'utf8');for(const [f,t] of pairs){if(!c.includes(f)){console.log('MISS '+p+' :: '+f.slice(0,50));continue;}c=c.split(f).join(t);}fs.writeFileSync(p,c);}
const A='unicconnect-api/src/main/java/com/unicconnect/';

// CallerContextFactory: SecurityUtil lives in util package
edit(A+'security/CallerContextFactory.java',[
 ['import com.unicconnect.rmi.client.RmiClientProperties;',
  'import com.unicconnect.rmi.client.RmiClientProperties;\nimport com.unicconnect.util.SecurityUtil;'],
]);

// TimetableGenerationRouting: GenerationStatusDto import
edit(A+'ops/TimetableGenerationRouting.java',[
 ['import com.unicconnect.rmi.dto.GenerationRequestDto;',
  'import com.unicconnect.rmi.dto.GenerationRequestDto;\nimport com.unicconnect.rmi.dto.GenerationStatusDto;'],
]);

// AttendanceController -> operations routing
edit(A+'controller/AttendanceController.java',[
 [`import com.unicconnect.service.AttendanceService;`,
  `import com.unicconnect.ops.AttendanceOperations;\nimport com.unicconnect.service.AttendanceService;\nimport com.unicconnect.util.SecurityUtil;`],
 [`    private final AttendanceService service;\n\n    public AttendanceController(AttendanceService service) {\n        this.service = service;\n    }`,
  `    private final AttendanceService service;\n    private final AttendanceOperations attendanceOperations;\n    private final SecurityUtil securityUtil;\n\n    public AttendanceController(AttendanceService service,\n                                AttendanceOperations attendanceOperations,\n                                SecurityUtil securityUtil) {\n        this.service = service;\n        this.attendanceOperations = attendanceOperations;\n        this.securityUtil = securityUtil;\n    }`],
 ['        return ResponseEntity.ok(service.markAttendance(sessionId, request));',
  '        return ResponseEntity.ok(attendanceOperations.mark(sessionId, request, securityUtil.currentUserId()));'],
 ['        return ResponseEntity.ok(service.update(attendanceId, request));',
  '        return ResponseEntity.ok(attendanceOperations.update(attendanceId, request, securityUtil.currentUserId()));'],
 [`        service.delete(attendanceId);\n        return ResponseEntity.noContent().build();`,
  `        attendanceOperations.delete(attendanceId, securityUtil.currentUserId());\n        return ResponseEntity.noContent().build();`],
]);
console.log('fixes applied');
