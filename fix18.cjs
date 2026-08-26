const fs=require('fs');const p='unicconnect-api/src/main/java/com/unicconnect/ops/AttendanceRouting.java';
let c=fs.readFileSync(p,'utf8');
c=c.replace('return serviceCalc().daily(sessionId);','return calculationService.daily(sessionId);');
c=c.replace('return serviceCalc().monthly(studentId, courseId, year, month);','return calculationService.monthly(studentId, courseId, year, month);');
c=c.replace('        private AttendanceService serviceCalc() { return service; }\n','');
fs.writeFileSync(p,c);
const p2='unicconnect-api/src/main/java/com/unicconnect/ops/RmiRoutingConfig.java';
let c2=fs.readFileSync(p2,'utf8');
c2=c2.replace('import com.unicconnect.service.AttendanceService;','import com.unicconnect.service.AttendanceCalculationService;\nimport com.unicconnect.service.AttendanceService;');
c2=c2.replace('public AttendanceOperations localAttendanceOperations(AttendanceService service) {\n        return new AttendanceRouting.Local(service);',
 'public AttendanceOperations localAttendanceOperations(AttendanceService service,\n    AttendanceCalculationService calculationService) {\n        return new AttendanceRouting.Local(service, calculationService);');
fs.writeFileSync(p2,c2);
console.log('calc wiring fixed');
