const fs=require('fs');const p='uniconnect-rmi-server/src/main/java/com/unicconnect/rmi/server/facade/TimetableRemoteFacade.java';
let c=fs.readFileSync(p,'utf8');
c=c.replace('import com.uncconnect.PlaceholderNever;\n','');
fs.writeFileSync(p,c);console.log('import cleaned');
