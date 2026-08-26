const fs=require('fs');const p='e2e-sse.cjs';let c=fs.readFileSync(p,'utf8');
c=c.replace("const OTHER_HODS = ['dawmoe@gmail.com','dawaye@gmail.com','dawnwe@gmail.com','dawsu@gmail.com',\n                    'htetnaing@gmail.com','khinkhin@gmail.com','makhin@gmail.com'];",
"const OTHER_HODS = ['dawmoe@gmail.com','dawaye@gmail.com','dawnwe@gmail.com','dawsu@gmail.com',\n                    'htetnaing@gmail.com','khinkhin@gmail.com','makhin@gmail.com','minzaw@gmail.com',\n                    'myintthein@gmail.com','phyothura@gmail.com'];");
fs.writeFileSync(p,c);console.log('roster extended');
