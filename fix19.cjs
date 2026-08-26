const fs=require('fs');const p='unicconnect-api/src/test/java/com/unicconnect/service/RollCallAttendanceIntegrationTest.java';
let c=fs.readFileSync(p,'utf8');
c=c.replace(/attendanceService\.markAttendance\((\s*[^;]*?)\.sessionId\(\),\n(\s*)new MarkAttendanceRequest\(/g,
            (m,s1,s2)=>`attendanceService.markAttendance(${s1}.sessionId(),\n${s2}lecturer.getUserIdForTestPlaceholder() == null ? null : null,`);
console.log('manual approach too fragile - using simpler replace');
fs.writeFileSync(p,c);
