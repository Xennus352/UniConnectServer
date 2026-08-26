const fs=require('fs');const p='uniconnect-core/src/main/java/com/unicconnect/rmi/remote/TimetableRemote.java';
let c=fs.readFileSync(p,'utf8');
c=c.replace('    /** Current lifecycle status / failure report of one generation. */',
 `    /** Runs the solver for an EXISTING generation session asynchronously.
     *  Returns immediately with its handle (HOD+LECTURER required). */
    GenerationHandleDto runGeneration(UUID generationId, GenerationRequestDto request,
                                      CallerContext ctx) throws RemoteException;

    /** Current lifecycle status / failure report of one generation. */`);
fs.writeFileSync(p,c);console.log('remote extended');
