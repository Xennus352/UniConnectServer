const fs=require('fs');const p='e2e-sse.cjs';let c=fs.readFileSync(p,'utf8');
c=c.replace(`  const cre = await req('/api/generations', { method: 'POST', body: JSON.stringify({ termId: TERM }) }, T);
  const gid = cre.json.generationId;
  await req('/api/generations/' + gid + '/generate', { method: 'POST', body: JSON.stringify({ examTypeId: FIN, semesters: [{ semesterId: s2.semesterId, sectionIds: s2.sections.filter(y => y.sectionName !== 'C').map(y => y.sectionId) }], autoBindCurriculum: true }) }, T);`,
`  // UI flow: lobby endpoint CREATES + LINKS the generation session
  const lobGen = await req('/api/timetable-lobbies/' + LID + '/generate', { method: 'POST' }, T);
  const gid = lobGen.json.generationId;
  console.log('LOBBY-GENERATE ->', lobGen.code, 'linked gid=' + gid);
  // scoped solve runs through the RMI-routed REST path
  await req('/api/generations/' + gid + '/generate', { method: 'POST', body: JSON.stringify({ examTypeId: FIN, semesters: [{ semesterId: s2.semesterId, sectionIds: s2.sections.filter(y => y.sectionName !== 'C').map(y => y.sectionId) }], autoBindCurriculum: true }) }, T);`);
c=c.replace(`  console.log('GENERATE ->', run.code, 'snapshot=' + run.j.status);`,
`  console.log('SCOPED RUN ->', run.code, 'snapshot=' + run.j.status);`);
fs.writeFileSync(p,c);console.log('sequence fixed');
