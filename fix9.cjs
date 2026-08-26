const fs=require('fs');const p='uniconnect-core/src/main/java/com/unicconnect/service/UserService.java';
let c=fs.readFileSync(p,'utf8');
c=c.replace('studentRepository.findByUser_UserId(userId)','studentRepository.findByUser_UserId(targetUserId)');
c=c.replace('staffRepository.findByUser_UserId(userId).ifPresent(staff ->','staffRepository.findByUser_UserId(targetUserId).ifPresent(staff ->');
fs.writeFileSync(p,c);console.log('fixed');
