const fs=require('fs');const p='e2e-sse.cjs';let c=fs.readFileSync(p,'utf8');
c=c.replace(`  console.log('SCOPED RUN ->', run.code, 'snapshot=' + run.j.status);

  let st = '';
  for (let i = 0; i < 30 && !['COMPLETED', 'FAILED', 'PUBLISHED'].includes(st); i++) {
    await new Promise(r => setTimeout(r, 3000));
    st = (await req('/api/generations/' + GID, {}, T)).json.status;
  }`,
`  console.log('SCOPED RUN ->', run.code, 'snapshot=' + (run.j && run.j.status));

  let st = '';
  for (let i = 0; i < 30 && !['COMPLETED', 'FAILED', 'PUBLISHED'].includes(st); i++) {
    await new Promise(r => setTimeout(r, 3000));
    const g = await req('/api/generations/' + gid, {}, T);
    st = g.json && g.json.status;
  }`);
c=c.split("'/api/generations/' + GID + '/lock'").join("'/api/generations/' + gid + '/lock'");
c=c.split("'/api/generations/' + GID + '/schedules'").join("'/api/generations/' + gid + '/schedules'");
c=c.replace('    generationId: GID, teachingAssignmentId: null','    generationId: gid, teachingAssignmentId: null');
fs.writeFileSync(p,c);console.log('gid refs fixed');
