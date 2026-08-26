const fs = require('fs');
const base = 'http://localhost:8080';
const TERM = 'efc68a91-5818-41aa-b4fb-893e240ea0ed';
const FIN = '6a3c3800-f6e8-4611-adb3-587826cabf84';
const PW = 'ucstgo@2026';
const LEADER = 'dawmya@gmail.com';
const OTHER_HODS = ['dawmoe@gmail.com','dawaye@gmail.com','dawnwe@gmail.com','dawsu@gmail.com',
                    'htetnaing@gmail.com','khinkhin@gmail.com','makhin@gmail.com','minzaw@gmail.com',
                    'myintthein@gmail.com','phyothura@gmail.com'];

async function req(p, opt = {}, tok) {
  const r = await fetch(base + p, { ...opt, headers: { 'Content-Type': 'application/json', ...(tok ? { Authorization: 'Bearer ' + tok } : {}) } });
  const body = await r.text();
  let json; try { json = JSON.parse(body); } catch { json = body; }
  return { code: r.status, json };
}
const login = async (e) => (await req('/api/auth/login', { method: 'POST', body: JSON.stringify({ email: e, password: PW }) })).json;

(async () => {
  const me = await login(LEADER);
  const T = me.accessToken;
  console.log('LOGIN', LEADER);

  // clean slate: cancel every non-cancelled lobby
  const lobbies = (await req('/api/timetable-lobbies', {}, T)).json;
  for (const l of lobbies.filter(l => l && l.status !== 'CANCELLED')) {
    const c = await req('/api/timetable-lobbies/' + l.lobbyId + '/cancel', { method: 'POST' }, T).catch(() => null);
    console.log('cancel', (l.lobbyId || l.lobId || '?').slice?.(0, 8), c ? c.code : 'skip');
  }

  // fresh lobby
  const lob = await req('/api/timetable-lobbies', { method: 'POST', body: JSON.stringify({ termId: TERM }) }, T);
  const LID = lob.json.lobbyId;
  console.log('NEW LOBBY', LID);

  // every invited HOD joins (leader included)
  const joiners = [LEADER, ...OTHER_HODS];
  for (const email of joiners) {
    const u = await login(email);
    if (!u.accessToken) { console.log('  join skip (no login):', email); continue; }
    const r = await req('/api/timetable-lobbies/' + LID + '/join', { method: 'POST' }, u.accessToken);
    console.log('  join', email, '->', r.code);
  }

  // ---- self-heal: every HOD cancels lobbies THEY lead, then joins ours ----
  async function unblockAll() {
    for (let round = 0; round < 3; round++) {
      let progress = false;
      for (const email of OTHER_HODS) {
        const u = await login(email);
        if (!u.accessToken) continue;
        const mine = (await req('/api/timetable-lobbies', {}, u.accessToken)).json
          .filter(l => l.status !== 'CANCELLED');
        for (const l of mine) {
          const c = await req('/api/timetable-lobbies/' + l.lobbyId + '/cancel', { method: 'POST' }, u.accessToken);
          if (c.code === 200) { console.log('  cleaned', l.lobbyId.slice(0,8), 'by', email); progress = true; }
        }
        // retry joining our lobby
        const r = await req('/api/timetable-lobbies/' + LID + '/join', { method: 'POST' }, u.accessToken);
        if (r.code === 200) { console.log('  joined', email); progress = true; }
      }
      // did our lobby become fully joined?
      const genProbe = await req('/api/timetable-lobbies/' + LID + '/generate', { method: 'POST' }, T);
      if (genProbe.code === 200) return genProbe;
      if (!progress) await new Promise(r => setTimeout(r, 1500));
    }
    return await req('/api/timetable-lobbies/' + LID + '/generate', { method: 'POST' }, T);
  }

  // ---- Browser B listener BEFORE generation starts ----
  const events = [];
  const ctrl = new AbortController();
  fetch(base + '/api/realtime/lobbies/' + LID + '/stream',
    { headers: { Authorization: 'Bearer ' + T, Accept: 'text/event-stream' }, signal: ctrl.signal })
    .then(async res => {
      console.log('SSE STATUS', res.status, res.headers.get('content-type'));
      const reader = res.body.getReader(), dec = new TextDecoder();
      let buf = '';
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buf += dec.decode(value, { stream: true });
        let i;
        while ((i = buf.indexOf('\n\n')) >= 0) {
          const chunk = buf.slice(0, i); buf = buf.slice(i + 2);
          const dl = chunk.split('\n').find(l => l.startsWith('data:'));
          if (!dl) continue;
          try { events.push(JSON.parse(dl.slice(5).trim())); } catch {}
        }
      }
    })
    .catch(e => console.log('SSE ended:', e.name));
  await new Promise(r => setTimeout(r, 2500));

  // ---- lobby-generate creates+links the generation ----
  const lg2 = await unblockAll();
  console.log('LOBBY-GENERATE ->', lg2.code, lg2.code >= 300 ? JSON.stringify(lg2.json).slice(0, 160) : '');
  const gid = lg2.json && lg2.json.generationId;
  if (!gid) process.exit(1);
  console.log('linked gid=', gid);

  // ---- scoped solve via RMI-routed REST ----
  const scope = await req('/api/generations/scope?termId=' + TERM + '&examTypeId=' + FIN, {}, T);
  const s2 = scope.json.find(x => x.semesterNo === 2);
  const run = await req('/api/generations/' + gid + '/generate', { method: 'POST', body: JSON.stringify({
    examTypeId: FIN,
    semesters: [{ semesterId: s2.semesterId, sectionIds: s2.sections.filter(y => y.sectionName !== 'C').map(y => y.sectionId) }],
    autoBindCurriculum: true }) }, T);
  console.log('SCOPED RUN ->', run.code, 'snapshot=', run.j && run.j.status);

  let st = '';
  for (let i = 0; i < 30 && !['COMPLETED', 'FAILED', 'PUBLISHED'].includes(st); i++) {
    await new Promise(r => setTimeout(r, 3000));
    const g = await req('/api/generations/' + gid, {}, T);
    st = g.json && g.json.status;
  }
  console.log('GEN FINAL=', st);

  // ---- lock + REAL WRITE: delete/recreate an LMS filler ----
  await req('/api/generations/' + gid + '/lock', { method: 'POST' }, T);
  const scheds = (await req('/api/generations/' + gid + '/schedules', {}, T)).json;
  const slots = (await req('/api/time-slots', {}, T)).json;
  const target = scheds.find(s => s.scheduleType !== 'COURSE') || scheds[0];
  console.log('TARGET:', target.scheduleType, 'D' + target.dayOfWeek, '-P' + target.startPeriodNo);

  const d1 = await req('/api/schedules/' + target.scheduleId, { method: 'DELETE' }, T);
  console.log('WRITE(delete) ->', d1.code);
  await new Promise(r => setTimeout(r, 3500));

  const slot0 = slots.find(s => s.periodNo === target.startPeriodNo).slotId;
  const c1 = await req('/api/schedules', { method: 'POST', body: JSON.stringify({
    generationId: gid, teachingAssignmentId: null, teachingGroupId: null,
    dayOfWeek: target.dayOfWeek, startSlotId: slot0, endSlotId: slot0,
    scheduleType: target.scheduleType, scheduleStatus: 'PENDING' }) }, T);
  console.log('WRITE(recreate) ->', c1.code);
  await new Promise(r => setTimeout(r, 4000));
  ctrl.abort();

  console.log('\n===== CAPTURED SSE EVENTS =====');
  for (const e of events)
    console.log(new Date().toISOString().slice(11, 19), JSON.stringify(e).slice(0, 190));
  const has = t => events.filter(e => e.type === t).length;
  console.log('\nVERDICT: GENERATION_STARTED=' + !!has('GENERATION_STARTED') +
    ' GENERATION_COMPLETED=' + !!has('GENERATION_COMPLETED') +
    ' SCHEDULE_DELETED=' + !!has('SCHEDULE_DELETED') +
    ' SCHEDULE_CREATED=' + !!has('SCHEDULE_CREATED') +
    ' | total=' + events.length);
  fs.writeFileSync('sse-evidence.txt', events.map(e => JSON.stringify(e)).join('\n'));
})().catch(e => { console.error('FATAL', e); process.exit(1); });
