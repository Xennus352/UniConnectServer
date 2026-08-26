const fs=require('fs');const p='e2e-sse.cjs';let c=fs.readFileSync(p,'utf8');
c=c.replace(`    .catch(e => console.log('SSE ended:', e.name));
`,`    .catch(e => console.log('SSE ended:', e.name));
  await new Promise(r => setTimeout(r, 2500)); // emitter registration window
`);
c=c.replace(`  console.log('WRITE(recreate) ->', c1.code);`,
`  console.log('WRITE(recreate) ->', c1.code, c1.code>=300 ? JSON.stringify(c1.json).slice(0,220) : '');`);
fs.writeFileSync(p,c);console.log('timing+body patched');
