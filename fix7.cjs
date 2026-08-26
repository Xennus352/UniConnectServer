const fs=require('fs');const p='uniconnect-rmi-server/src/main/java/com/unicconnect/rmi/server/ServerTimetableAccessPort.java';
let c=fs.readFileSync(p,'utf8');
c=c.replace('import com.unicconnect.entity.GenerationStatus;\n','');
c=c.replace('\n    // referenced indirectly through GenerationStatus import to keep parity\n    @SuppressWarnings("unused")\n    private static GenerationStatus[] statusValues() { return GenerationStatus.values(); }\n','');
c=c.replace('.flatMap(l -> lobbyMemberRepository.findByLobby_LobbyIdAndStaff_StaffId(\n                                l.getLobbyId(), staffIdOf(generationId)).isPresent())',
            '.flatMap(l -> lobbyMemberRepository.findByLobby_LobbyIdAndStaff_StaffId(\n                                l.getLobbyId(), callerStaffId()).isPresent())');
c=c.replace('    private UUID staffIdOf(UUID ignoredGenerationId) {\n        // member lookup needs a staff id; resolve from the caller identity.\n        return staffRepository.findByUser_UserId(RmiCurrentUserHolder.requireUserId())\n                .map(Staff::getStaffId)\n                .orElseThrow(() -> new BusinessRuleException("Only staff can perform this action"));\n    }',
'    private UUID callerStaffId() {\n        return staffRepository.findByUser_UserId(RmiCurrentUserHolder.requireUserId())\n                .map(Staff::getStaffId)\n                .orElseThrow(() -> new BusinessRuleException("Only staff can perform this action"));\n    }');
fs.writeFileSync(p,c);console.log('access port cleaned');
