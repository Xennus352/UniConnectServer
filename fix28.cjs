const fs=require('fs');let c=fs.readFileSync('e2e-sse.cjs','utf8');
c=c.replace(`  const slots = (await j('/api/time-slots', null, T)).json;
  slots.sort((a,b)=>a.display_order??a.displayOrder??0 - 0);
  const byPeriod = new Map(); for (const s of slots) byPeriod.set(s.periodNo ?? s.period_no, s);
  const filler = scheds.find(s => s.scheduleType !== 'COURSE');
  if (!filler) { console.log('NO FILLER ROW FOUND — will use COURSE-to-free-cell move'); }
  const target = filler || scheds.find(s => s.scheduleType === 'COURSE');

  // find a free period on the same day for the move
  const busy = new Set();
  for (const o of scheds) {
    if (o.scheduleId === target.scheduleId || o.dayOfWeek !== target.dayOfWeek) continue;
    if (o.scheduleType === 'COURSE') for (let p = x0(o) ; p <= x1(o); p++) busy.add(p);
  }
  function x0(x){return x.startPeriodNo} function x1(x){return x.endPeriodNo}
  let freeP = null;
  for (let p = 1; p <= 6; p++) if (!busy.has(p)) { freeP = p; break; }
  console.log('MOVE TARGET: day='+target.dayOfWeek+' P'+freeP+' (from P'+target.startPeriodNo+') type='+target.scheduleType);`,
`  const slots = (await j('/api/time-slots', null, T)).json;
  slots.sort((a,b)=>a.displayOrder-b.displayOrder);
  const byPeriod = new Map(); for (const s of slots) byPeriod.set(s.periodNo, s);
  const filler = scheds.find(s => s.scheduleType !== 'COURSE');
  const target = filler || scheds.find(s => s.scheduleType === 'COURSE');
  const tSections = new Set(target.sections||[]);

  // free period on same day: no OTHER course schedule whose sections
  // intersect the target's coverage overlaps that period
  const isBusy = (p) => scheds.some(o =>
    o.scheduleId !== target.scheduleId &&
    o.dayOfWeek === target.dayOfWeek &&
    o.scheduleType === 'COURSE' &&
    p >= o.startPeriodNo && p <= o.endPeriodNo &&
    (o.sections||[]).some(sn => tSections.has(sn)));
  let freeP = null;
  for (let p = 1; p <= 6; p++) {
    if (p === target.startPeriodNo) continue;
    if (!isBusy(p)) { freeP = p; break; }
  }
  console.log('MOVE TARGET: day='+target.dayOfWeek+' P'+freeP+' (from P'+target.startPeriodNo+') type='+target.scheduleType+' sections=['+target.sections.join(',')+']');`);
// clean the slot id fallbacks
c=c.split("byPeriod.get(freeP)?.slotId ?? byPeriod.get(freeP)?.slot_id").join("byPeriod.get(freeP).slotId");
c=c.replace(`  const origStart = slots.find(s => s.slotId === target.startSlotId) ||
                    slots.find(s => (s.slot_id ?? s.slotId) === target.startSlotId);
  const os = origStart?.slotId ?? origStart?.slot_id ?? target.startSlotId;`,
`  const os = target.startSlotId;`);
fs.writeFileSync('e2e-sse.cjs',c);console.log('planner fixed');
