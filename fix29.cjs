const fs=require('fs');let c=fs.readFileSync('e2e-sse.cjs','utf8');
const start=c.indexOf("  // free period on same day");
const end=c.indexOf("  console.log('MOVE TARGET:");
c=c.slice(0,start)+`  // LMS/ASSIGNMENT rows conflict only when a COURSE schedule
  // overlaps the candidate period on that day (validateNoConflicts else-branch).
  const courseBusy=(day,p)=>scheds.some(o=>o.scheduleId!==target.scheduleId&&o.scheduleType==='COURSE'&&o.dayOfWeek===day&&p>=o.startPeriodNo&&p<=o.endPeriodNo);
  let newDay=null,newP=null;
  outer:
  for(let d=1;d<=5;d++)for(let p=1;p<=6;p++){
    if(d===target.dayOfWeek&&p===target.startPeriodNum)continue;
    if(d===target.dayOfWeek&&p>=Math.min(target.startPeriodNo,target.endPeriodNo)&&p<=Math.max(target.startPeriodNo,target.endPeriodNo))continue;
    if(!courseBusy(d,p)){newDay=d;newP=p;break outer;}
  }
  const freeP=newP;
  `+c.slice(end);
c=c.replace(`  console.log('MOVE TARGET: day='+target.dayOfWeek+' P'+freeP+' (from P'+target.startPeriodNo+') type='+target.scheduleType+' sections=['+target.sections.join(',')+']');`,
`  console.log('MOVE TARGET: D'+newDay+'-P'+freeP+' (from D'+target.dayOfWeek+'-P'+target.startPeriodNo+') type='+target.scheduleType);
  if(freeP==null){console.log('NO FREE CELL FOUND ON ANY DAY');process.exit(2);}`);
// use newDay in the write + revert bodies
c=c.replace('    dayOfWeek: target.dayOfWeek,\n    startSlotId: byPeriod.get(freeP).slotId','    dayOfWeek: newDay,\n    startSlotId: byPeriod.get(freeP).slotId');
c=c.replace('    dayOfWeek: target.dayOfWeek, startSlotId: os, endSlotId: os,','    dayOfWeek: target.dayOfWeek, startSlotId: os, endSlotId: os,');
// log failure body on non-2xx
c=c.replace(`  console.log('WRITE(move) ->', upd.code);`,
`  console.log('WRITE(move) ->', upd.code, upd.code>=300?JSON.stringify(upd.json).slice(0,200):'');`);
c=c.replace(`  console.log('WRITE(revert) ->', rev.code);`,
`  console.log('WRITE(revert) ->', rev.code, rev.code>=300?JSON.stringify(rev.json).slice(0,200):'');`);
fs.writeFileSync('e2e-sse.cjs',c);console.log('planner v3');
