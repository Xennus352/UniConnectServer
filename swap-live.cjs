const fs=require('fs');const base='http://localhost:8080';
const GID='2b9bc1d3-4d7f-457f-93be-beb0c99dd3ef';
const LID='dab377b7-521b-4b46-9c96-d33024e96d30';
(async()=>{
 const lg=await fetch(base+'/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:'dawmya@gmail.com',password:'ucstgo@2026'})});
 const T=(await lg.json()).accessToken;
 const g=async(p,o={})=>{const r=await fetch(base+p,{...o,headers:{'Content-Type':'application/json',Authorization:'Bearer '+T,...(o.headers||{})}});const t=await r.text();let j;try{j=JSON.parse(t)}catch{j=t}return{code:r.status,j}};
 await g('/api/generations/'+GID+'/lock',{method:'POST'});
 const scheds=(await g('/api/generations/'+GID+'/schedules')).j.filter(x=>x.scheduleType==='COURSE');
 const slots=(await g('/api/time-slots')).j;
 // find candidate pair: same span, different days
 let pair=null;
 outer:
 for(let i=0;i<scheds.length;i++)for(let j2=i+1;j2<scheds.length;j2++){
   const X=scheds[i],Y=scheds[j2];
   if(X.dayOfWeek===Y.dayOfWeek)continue;
   const sx=X.endPeriodNo-X.startPeriodNo, sy=Y.endPeriodNo-Y.startPeriodNo;
   if(sx!==sy)continue;
   pair={X,Y};break outer;
 }
 if(!pair){console.log('no pair');process.exit(0)}
 const {X,Y}=pair;
 console.log('SWAP PAIR:',X.courseCode,'D'+X.dayOfWeek+'-P'+X.startPeriodNo,'sections=['+X.sections+'] <->',Y.courseCode,'D'+Y.dayOfWeek+'-P'+Y.startPeriodNo+' sections=['+Y.sections+']');
 // SSE listener
 const events=[];const ctrl=new AbortController();
 fetch(base+'/api/realtime/lobbies/'+LID+'/stream',{headers:{Authorization:'Bearer '+T,Accept:'text/event-stream'},signal:ctrl.signal})
  .then(async r=>{const rd=r.body.getReader(),dec=new TextDecoder();let b='';
    while(true){const{done,value}=await rd.read();if(done)break;b+=dec.decode(value,{stream:true});let i;
     while((i=b.indexOf('\n\n'))>=0){const ch=b.slice(0,i);b=b.slice(i+2);const dl=ch.split('\n').find(l=>l.startsWith('data:'));if(dl)try{events.push(JSON.parse(dl.slice(5).trim()))}catch{}}}})
  .catch(()=>{});
 await new Promise(r=>setTimeout(r,2500));
 const sw=await g('/api/generations/'+GID+'/swap',{method:'POST',body:JSON.stringify({scheduleId:X.scheduleId,targetDay:Y.dayOfWeek,targetPeriod:Y.startPeriodNo,force:false})});
 console.log('SWAP -> ',sw.code,'swapped='+sw.j.swapped,'conflicts='+(sw.j.conflicts||[]).length);
 await new Promise(r=>setTimeout(r,4000));
 // revert
 const rev=await g('/api/generations/'+GID+'/swap',{method:'POST',body:JSON.stringify({scheduleId:X.scheduleId,targetDay:X.dayOfWeek,targetPeriod:X.startPeriodNo,force:true})});
 console.log('REVERT -> ',rev.code,'swapped='+rev.j.swapped);
 await new Promise(r=>setTimeout(r,4000));ctrl.abort();
 const ups=events.filter(e=>e.type==='SCHEDULE_UPDATED');
 console.log('\n===== EVENTS =====');for(const e of events)console.log(new Date().toISOString().slice(11,19),JSON.stringify(e).slice(0,190));
 console.log('\nVERDICT: SCHEDULE_UPDATED count='+ups.length+' ids='+[...new Set(ups.map(e=>(e.scheduleId||'').slice(0,8)))].join(','));
 fs.appendFileSync('sse-evidence.txt','\n---swap round---\n'+events.map(e=>JSON.stringify(e)).join('\n'));
})().catch(e=>{console.error('FATAL',e.message);process.exit(1)});
