const fs=require('fs');
const dir='uniconnect-core/src/main/java/com/unicconnect/service/';
// 1) drop leftover lobbyAccessService in generation service
let p=dir+'TimetableGenerationService.java';let c=fs.readFileSync(p,'utf8');
c=c.replace('    private final TimetableLobbyAccessService lobbyAccessService;\n','');
c=c.replace('        this.lobbyAccessService = lobbyAccessService;\n','');
fs.writeFileSync(p,c);
// 2) TeachingAssignmentGroupService -> ports
p=dir+'TeachingAssignmentGroupService.java';c=fs.readFileSync(p,'utf8');
c=c.replace('import org.springframework.stereotype.Service;',
 'import com.unicconnect.service.port.TimetableAccessPort;\nimport com.unicconnect.service.port.TimetableEventPort;\nimport org.springframework.stereotype.Service;');
c=c.replace('    private final HodAccessService hodAccessService;\n    private final TimetableRealtimeEventService realtimeEventService;',
 '    private final TimetableAccessPort accessPort;\n    private final TimetableEventPort eventPort;');
c=c.replace('                                          HodAccessService hodAccessService,\n                                          TimetableRealtimeEventService realtimeEventService) {',
 '                                          TimetableAccessPort accessPort,\n                                          TimetableEventPort eventPort) {');
c=c.replace('        this.hodAccessService = hodAccessService;\n        this.realtimeEventService = realtimeEventService;',
 '        this.accessPort = accessPort;\n        this.eventPort = eventPort;');
c=c.split('hodAccessService.requireHod();').join('accessPort.requireHod();');
c=c.split('realtimeEventService.publishForTerm(').join('eventPort.publishForTerm(');
c=c.split('TimetableRealtimeEventService.TEACHING_GROUP_').join('TimetableEventPort.TEACHING_GROUP_');
fs.writeFileSync(p,c);
console.log('leftovers fixed');
