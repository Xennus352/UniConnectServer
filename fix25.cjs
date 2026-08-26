const fs=require('fs');const p='uniconnect-rmi-server/src/main/java/com/unicconnect/rmi/server/facade/TimetableRemoteFacade.java';
let c=fs.readFileSync(p,'utf8');
// import
if(!c.includes('import com.unicconnect.rmi.server.RmiCurrentUserHolder;'))
 c=c.replace('import org.slf4j.Logger;','import com.unicconnect.rmi.server.RmiCurrentUserHolder;\nimport org.slf4j.Logger;');
// helper
c=c.replace('    private static GenerateTimetableRequest toLocal(GenerationRequestDto d) {',
`    /** Publishes the verified identity for the shared-core access ports. */
    private UUID verified(UUID rawCaller) {
        // CallerContextVerifier already resolved the real userId into rawCaller.
        return rawCaller;
    }

    private static GenerateTimetableRequest toLocal(GenerationRequestDto d) {`);
// wrap each delegation: after each 'UUID caller = verifier.verify(ctx);' insert holder.set(caller)
c=c.split('UUID caller = verifier.verify(ctx);').join('UUID caller = verifier.verify(ctx);\n            RmiCurrentUserHolder.set(caller);');
// ensure clear() in catch of every method: replace '} catch (RuntimeException e) { throw FacadeGuard.translate(e); }'
c=c.split('} catch (RuntimeException e) { throw FacadeGuard.translate(e); }')
  .join('} catch (RuntimeException e) { RmiCurrentUserHolder.clear(); throw FacadeGuard.translate(e); }\n            finally { RmiCurrentUserHolder.clear(); }');
fs.writeFileSync(p,c);
console.log('holder wired');
