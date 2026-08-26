const fs=require('fs');const p='e2e-sse.cjs';let c=fs.readFileSync(p,'utf8');
c=c.split("'/api/timetable-lobbies/' + l.lobId + '/cancel'").join("'/api/timetable-lobbies/' + l.lobbyId + '/cancel'");
c=c.replace('for (const l of lobbies.filter(l => l.status !==','for (const l of lobbies.filter(l => l && l.status !==');
fs.writeFileSync(p,c);console.log('typo fixed');
