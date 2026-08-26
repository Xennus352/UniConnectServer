const fs=require('fs');const p='unicconnect-api/pom.xml';
let c=fs.readFileSync(p,'utf8');
c=c.replace(`                <configuration>\n                    <excludes>`,
`                <configuration>\n                    <mainClass>com.unicconnect.UniConnectApplication</mainClass>\n                    <excludes>`);
fs.writeFileSync(p,c);console.log('api pom mainClass set');
