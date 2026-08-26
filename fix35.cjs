const fs=require('fs');const p='e2e-sse.cjs';let c=fs.readFileSync(p,'utf8');
// insert cross-leader lobby cleanup right before the Browser-B listener block
c=c.replace(`  // ---- Browser B listener BEFORE generation starts ----`,
`  // ---- self-heal: every HOD cancels lobbies THEY lead, then joins ours ----
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

  // ---- Browser B listener BEFORE generation starts ----`);
// replace direct call with unblockAll usage
c=c.replace(`  const lg2 = await req('/api/timetable-lobbies/' + LID + '/generate', { method: 'POST' }, T);
  console.log('LOBBY-GENERATE ->', lg2.code, lg2.code >= 300 ? JSON.stringify(lg2.json).slice(0, 160) : '');
  const gid = lg2.json && lg2.json.generationId;`,
`  const lg2 = await unblockAll();
  console.log('LOBBY-GENERATE ->', lg2.code, lg2.code >= 300 ? JSON.stringify(lg2.json).slice(0, 160) : '');
  const gid = lg2.json && lg2.json.generationId;`);
fs.writeFileSync(p,c);console.log('unblock logic added');
