const fs=require('fs');
function edit(p,pairs){let c=fs.readFileSync(p,'utf8');for(const [f,t] of pairs){if(!c.includes(f)){console.log('MISS '+p+' :: '+f.slice(0,50));continue;}c=c.split(f).join(t);}fs.writeFileSync(p,c);}
const F='uniconnect-rmi-server/src/main/java/com/unicconnect/rmi/server/';

// 1) verifier import location
for (const n of ['facade/UserRemoteFacade.java','facade/AttendanceRemoteFacade.java','facade/TimetableRemoteFacade.java'])
  edit(F+n,[['import com.unicconnect.rmi.contract.CallerContextVerifier;','import com.unicconnect.rmi.server.CallerContextVerifier;']]);

// 2) updateRole signature parity
edit(F+'facade/UserRemoteFacade.java',[
 ['    public UserDto updateRole(UUID userId, RoleChangeDto request, CallerContext ctx) throws RemoteException {\n        try {\n            UUID caller = verifier.verify(ctx);\n            log.info("[RMI] UserRemote.updateRole caller={} target={} roleId={}", caller, userId, request.roleId());\n            return com.unicconnect.rmi.dto.RmiMappers.toUserDto(userService.updateRole(userId,\n                    new UpdateUserRoleRequest(request.roleId())));',
  '    public UserDto updateRole(UUID userId, UUID roleId, CallerContext ctx) throws RemoteException {\n        try {\n            UUID caller = verifier.verify(ctx);\n            log.info("[RMI] UserRemote.updateRole caller={} target={} roleId={}", caller, userId, roleId);\n            return com.unicconnect.rmi.dto.RmiMappers.toUserDto(userService.updateRole(userId,\n                    new UpdateUserRoleRequest(roleId)));'],
 ['import com.unicconnect.rmi.dto.RoleChangeDto;\n',''],
]);

// 3) startGeneration implementation
edit(F+'facade/TimetableRemoteFacade.java',[
 ['    @Override\n    public GenerationHandleDto runGeneration(',
  `    @Override\n    public GenerationHandleDto startGeneration(UUID termId, GenerationRequestDto request,\n                                               CallerContext ctx) throws RemoteException {\n        var handle = createGeneration(termId, ctx);\n        return runGeneration(handle.generationId(), request, ctx);\n    }\n\n    @Override\n    public GenerationHandleDto runGeneration(`],
]);

// 4) FacadeGuard checked typing
edit(F+'facade/FacadeGuard.java',[
 ['    static RuntimeException translate(RuntimeException e) {',
  '    static java.rmi.RemoteException translate(RuntimeException e) {'],
]);

// 5) Optional chain fix
edit(F+'ServerTimetableAccessPort.java',[
 ['        return lobbyRepository.findByGeneration_GenerationId(generationId)\n                .filter(l -> l.getStatus() == LobbyStatus.OPEN)\n                .flatMap(l -> lobbyMemberRepository.findByLobby_LobbyIdAndStaff_StaffId(\n                                l.getLobbyId(), callerStaffId()).isPresent())\n                .orElse(false);',
  '        Boolean memberOfOpenLobby = lobbyRepository.findByGeneration_GenerationId(generationId)\n                .filter(l -> l.getStatus() == LobbyStatus.OPEN)\n                .map(l -> lobbyMemberRepository.findByLobby_LobbyIdAndStaff_StaffId(\n                        l.getLobbyId(), callerStaffId()).isPresent())\n                .orElse(Boolean.FALSE);\n        return memberOfOpenLobby;'],
]);
console.log('rmi-server fixes applied');
