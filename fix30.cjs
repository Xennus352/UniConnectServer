const fs=require('fs');let c=fs.readFileSync('e2e-sse.cjs','utf8');
const start=c.indexOf("  // ---- REAL WRITE: move filler to the free cell ----");
const end=c.indexOf("  console.log('\n===== CAPTURED SSE EVENTS =====');".replace('\\n','\n'));
c=c.slice(0,start)+`  // ---- REAL WRITE: delete filler -> observe SCHEDULE_DELETED,
  // then re-create it -> observe SCHEDULE_CREATED (DB restored) ----
  const slot0 = byPeriod.get(target.startPeriodNo).slotId;
  const d1 = await req('/api/schedules/' + target.scheduleId, { method: 'DELETE' }, T);
  console.log('WRITE(delete) ->', d1.code);
  await new Promise(r => setTimeout(r, 3500));

  nodeDragRelay();

  const c1 = await req('/api/schedules', { method: 'POST', body: JSON.stringify({
    generationId: gid, teachingAssignmentId: null, teachingGroupId: null,
    dayOfWeek: target.dayOfWeek, startSlotId: slot0, endSlotId: slot0,
    scheduleType: target.scheduleType, scheduleStatus: 'PENDING' }) }, T);
  console.log('WRITE(recreate) ->', c1.code);
  await new Promise(r => setTimeout(r, 4000));
  ctrl.abort();
`+c.slice(end);
// helper that exercises the pure drag relay too (no DB change)
c=c.replace(`  // ---- SSE collector (connection #2 = "the other browser") ----`,
`  function nodeDragRelay(){
    // exercised from Node below after delete
  }
  async function dragRelay(tok,gid,sid){
    await fetch(base+'/api/generations/'+gid+'/drag',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+tok},body:JSON.stringify({action:'start',scheduleId:sid})});
    await fetch(base+'/api/generations/'+gid+'/drag',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+tok},body:JSON.stringify({action:'move',scheduleId:sid,day:3,period:5})});
    await fetch(base+'/api/generations/'+gid+'/drag',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+tok},body:JSON.stringify({action:'end',scheduleId:sid})});
  }
  globalThis.dragRelay=dragRelay;

  // ---- SSE collector (connection #2 = "the other browser") ----`);
c=c.replace(`  console.log('WRITE(delete) ->', d1.code);`,
`  console.log('WRITE(delete) ->', d1.code);
  await globalThis.dragRelay(T, gid, target.scheduleId);
  await new Promise(r => setTimeout(r, 1500));`);
fs.writeFileSync('e2e-sse.cjs',c);console.log('write-probe switched to delete/recreate + drag relay');
