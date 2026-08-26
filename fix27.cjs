const fs=require('fs');const p='uniconnect-rmi-server/src/main/java/com/unicconnect/rmi/server/facade/TimetableRemoteFacade.java';
let c=fs.readFileSync(p,'utf8');
c=c.replace(`            GenerateTimetableRequest gtr = toLocal(request);
            generationExecutor.submit(() -> {
                try {
                    generationService.generate(generationId, gtr);
                    log.info("[RMI] generation {} finished", generationId);
                } catch (Exception e) {
                    log.error("[RMI] generation {} FAILED: {}", generationId, e.toString());
                }
            });`,
`            final UUID taskCaller = caller;
            final GenerateTimetableRequest gtr = toLocal(request);
            generationExecutor.submit(() -> {
                // The solver resolves HOD/lobby rules through the access port,
                // which needs the caller identity on THIS worker thread too.
                com.unicconnect.rmi.server.RmiCurrentUserHolder.set(taskCaller);
                try {
                    generationService.generate(generationId, gtr);
                    log.info("[RMI] generation {} finished", generationId);
                } catch (Exception e) {
                    log.error("[RMI] generation {} FAILED: {}", generationId, e.toString());
                } finally {
                    com.unicconnect.rmi.server.RmiCurrentUserHolder.clear();
                }
            });`);
fs.writeFileSync(p,c);console.log('worker identity wired');
