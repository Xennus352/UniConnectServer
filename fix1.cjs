const fs=require('fs');
const dir='uniconnect-core/src/main/java/com/unicconnect/service/';
function fix(file,re,to){const p=dir+file;let c=fs.readFileSync(p,'utf8');const before=c;c=c.replace(re,to);if(c===before){console.log('NOCHANGE '+file);}fs.writeFileSync(p,c);}
// AttendanceService ctor: remove SecurityUtil param (regex-tolerant of indentation)
fix('AttendanceService.java', /\n\s*SecurityUtil securityUtil,\n(\s*RollCallService rollCallService\) \{)/, '\n$1');
// ClassScheduleService ctor: replace three concrete params with ports
fix('ClassScheduleService.java', /\n\s*HodAccessService hodAccessService,\n\s*TimetableEditLockService editLockService,\n\s*TimetableRealtimeEventService realtimeEventService\) \{/,
   '\n                                 TimetableAccessPort accessPort,\n                                 TimetableEventPort eventPort) {');
console.log('fixups done');
