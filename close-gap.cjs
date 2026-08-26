const fs=require('fs');const base='http://localhost:8080';
const GID='2b9bc1d3-4d7f-457f-93be-beb0c99dd3ef';
const LID='dab377b7-521b-4b46-9c96-d33024e96d30';
(async()=>{
 const lg=await fetch(base+'/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:'dawmya@gmail.com',password:'ucstgo@2026'})});
 const T=(await lg.json()).accessToken;
 const g=async(p,o={})=>{const r=await fetch(base+p,{...o,headers:{'Content-Type':'application/json',Authorization:'Bearer '+T,...(o.headers||{})}});const t=await r.text();let j;try{j=JSON.parse(t)}catch{j=t}return{code:r.status,j};};
 const scheds=(await g('/api/generations/'+GID+'/schedules')).j;
 console.log('day1 rows:');
 for(const o of scheds.filter(x=>x.dayOfWeek===1))
   console.log(' ',o.scheduleType,(o.courseCode||'(filler)'),'P'+o.startPeriodNo+'-P'+o.endPeriodNo,'sections=[',(o.sections||[]).join(','),']',o.scheduleId.slice(0,8));
 // find truly free period on ANY day
 const occ=new Set();
 for(const o of scheds)if(o.scheduleType==='COURSE')for(let p=o.startPeriodNo;p<=o.endPeriodNo;p++)occ.add(o.dayOfWeek+':'+p);
 let cell=null;outer:for(let d=1;d<=5;d++)for(let p=1;p<=6;p++){if(!occ.has(d+':'+p)){cell={d,p};break outer}}
 console.log('free cell:',cell);
 if(!cell){console.log('grid full - use a different section pair');process.exit(0)}
 // reopen SSE briefly
 const events=[];const ctrl=new AbortController();
 fetch(base+'/api/realtime/lobbies/'+LID+'/stream',{headers:{Authorization:'Bearer '+T,Accept:'text/event-stream'},signal:ctrl.signal})
  .then(async r=>{const rd=r.body.getReader(),dec=new TextDecoder();let b='';
   while(true){const{done,value}=await rd.read();if(done)break;b+=dec.decode(value,{stream:true});let i;
    while((i=b.indexOf('\n\n'))>=0){const ch=b.slice(0,i);b=b.slice(i+2);const dl=ch.split('\n').find(l=>l.startsWith('data:'));if(dl)try{events.push(JSON.parse(dl.slice(5).trim()))}catch{}}}})
  .catch(()=>{});
 await new Promise(r=>setTimeout(r,2500));
 const slot=JSON.parse(fs.readFileSync('slots-cache.json','utf8'))[cell.p-1]?.slotId;
 const cre=await g('/api/schedules',{method:'POST',body:JSON.stringify({generationId:GID,teachingAssignmentId:null,teachingGroupId:null,dayOfWeek:cell.d,startSlotId:slot,endSlotId:slot,scheduleType:'LMS',scheduleStatus:'PENDING'})});
 console.log('CREATE@D'+cell.d+'-P'+cell.p,'->',cre.code,cre.code>=300?JSON.stringify(cre.j).slice(0,160):'');
 await new Promise(r=>setTimeout(r,3500));ctrl.abort();
 const cr=events.filter(e=>e.type==='SCHEDULE_CREATED');
 console.log('SSE events this round:',events.map(e=>e.type).join(', '));
 console.log('SCHEDULE_CREATED captured=',cr.length>0);
 fs.appendFileSync('sse-evidence.txt','\n---round2---\n'+events.map(e=>JSON.stringify(e)).join('\n'));
})().catch(e=>{console.error('FATAL',e.message);process.exit(1)});
