const fs=require('fs');const p='unicconnect-api/src/test/java/com/unicconnect/service/RollCallAttendanceIntegrationTest.java';
let c=fs.readFileSync(p,'utf8');
// every markAttendance(r.sessionId(), new MarkAttendanceRequest(...)) gains the lecturer caller id as 3rd arg.
// The request argument spans lines; safest: replace "attendanceService.markAttendance(" with a helper invocation.
c=c.split('attendanceService.markAttendance(').join('mark( ');
// append helper method before final closing brace of class
const idx=c.lastIndexOf('}');
c=c.slice(0,idx)+`
    /** Test shim: original two-arg call plus explicit caller (the covering lecturer). */
    private java.util.List<com.unicconnect.dto.response.AttendanceResponse> mark(
            UUID sessionId, MarkAttendanceRequest request) {
        UUID callerUserId = lecturer.getUser() != null ? lecturer.getUser().getUserId() : null;
        if (callerUserId == null) {
            // fall back: resolve via the staff row directly when user linkage is absent in tests
            callerUserId = staffRepository.findById(lecturer.getStaffId())
                    .map(s -> s.getUser() != null ? s.getUser().getUserId() : null).orElse(null);
        }
        return attendanceService.markAttendance(sessionId, request, callerUserId);
    }
}
`+c.slice(idx+1);
fs.writeFileSync(p,c);
console.log('test shim added');
