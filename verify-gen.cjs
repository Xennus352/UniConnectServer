const fs=require('fs');
const base='http://localhost:8080';
const post=async(p,body,tok)=>{const r=await fetch(base+p,{method:'POST',headers:{'Content-Type':'application/json',...(tok?{Authorization:'Bearer '+tok}:{})},body:JSON.stringify(body)});return {code:r.status,j:await r.json().catch(()=>({}))};};
const get=async(p,tok)=>{const r=await fetch(base+p,{headers:tok?{Authorization:'Bearer '+tok}:{}});return {code:r.status,j:await r.json().catch(()=>({}))};};
(async()=>{
  const login=await post('/api/auth/login',{email:'dawmya@gmail.com',password:'ucstgo@2026'});
  console.log('LOGIN ->',login.code);
  if(login.code!==200){console.log(JSON.stringify(login.j));process.exit(1);}
  const tok=login.j.accessToken,h=tok;
  const sc=(await get('/api/generations/scope?termId=efc68a91-5818-41aa-b4fb-893e240ea0ed&examTypeId=6a3c3800-f6e8-4611-adb3-587826cabf84',h)).j;
  const s2=sc.find(x=>x.semesterNo===2);
  const cre=await post('/api/generations',{termId:'efc68a91-5818-41aa-b4fb-893e240ea0ed'},h);
  console.log('CREATE ->',cre.code,'status='+cre.j.status);
  const gid=cre.j.generationId;
  const run=await post('/api/generations/'+gid+'/generate',
    {examTypeId:'6a3c3800-f6e8-4611-adb3-587826cabf84',
     semesters:[{semesterId:s2.semesterId,sectionIds:s2.sections.filter(y=>y.sectionName!=='C').map(y=>y.sectionId)}],
     autoBindCurriculum:true},h);
  console.log('SUBMIT ->',run.code,'snapshot='+(run.j.status||'?'));
  let st='';
  for(let i=0;i<40;i++){
    await new Promise(r=>setTimeout(r,8000));
    const s=await get('/api/generations/'+gid,h); st=s.j.status;
    console.log(new Date().toISOString().slice(11,19),'status='+st);
    if(['COMPLETED','FAILED','PUBLISHED'].includes(st)){
      if(s.j.failureReport) console.log(String(s.j.failureReport).split('\n').slice(0,4).join('\n'));
      break;
    }
  }
  if(st==='COMPLETED'){
    const d=await get('/api/generations/'+gid+'/schedules',h);
    const arr=d.j;
    console.log('draft schedules='+arr.length);
    const dist={};for(const x of arr){const k='Sem'+(x.semesterNo??'null')+'['+(x.sections||[]).join('+')+']';dist[k]=(dist[k]||0)+1;}
    Object.keys(dist).sort().forEach(k=>console.log('  '+k+' x'+dist[k]));
    const p34=arr.filter(x=>x.startPeriodNo===3&&x.endPeriodNo===4).length;
    console.log('P3-P4(lunch-cross) rows='+p34);
    fs.writeFileSync('gen-result.txt','gid='+gid+' status='+st+' schedules='+arr.length);
  } else { fs.writeFileSync('gen-result.txt','gid='+gid+' status='+st); }
})().catch(e=>{console.error('FATAL',e.message);process.exit(1);});
