const fs=require('fs');const path=require('path');
let fixed=0;
function walk(d){if(!fs.existsSync(d))return;for(const f of fs.readdirSync(d)){const p=path.join(d,f);const s=fs.statSync(p);
 if(s.isDirectory())walk(p);
 else if(/\.(java|yml)$/.test(f)){const b=fs.readFileSync(p);
  if(b[0]===0xEF&&b[1]===0xBB&&b[2]===0xBF){fs.writeFileSync(p,b.slice(3));fixed++;}}}}
walk('uniconnect-core/src/main/java');
walk('unicconnect-api/src/main/java');
walk('unicconnect-api/src/main/resources');
walk('uniconnect-rmi-server/src/main/java');
walk('uniconnect-rmi-server/src/main/resources');
console.log('BOM stripped:',fixed);
