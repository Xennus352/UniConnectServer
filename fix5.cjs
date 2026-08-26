const fs=require('fs');
let p='uniconnect-core/src/main/java/com/unicconnect/rmi/remote/TimetableRemote.java';
let c=fs.readFileSync(p,'utf8');
c=c.replace('    /** Runs the solver for an EXISTING generation session asynchronously.',
`    /** Creates a PENDING generation session owned by the caller
     *  (HOD+LECTURER required). */
    GenerationHandleDto createGeneration(UUID termId, CallerContext ctx) throws RemoteException;

    /** Runs the solver for an EXISTING generation session asynchronously.`);
fs.writeFileSync(p,c);

p='uniconnect-api/src/main/java/com/unicconnect/ops/TimetableGenerationRouting.java';
c=fs.readFileSync(p,'utf8');
c=c.replace(`        @Override public GenerationSessionResponse create(CreateGenerationRequest r) {
            // Session creation is a cheap metadata insert; still executed
            // inside the RMI Server so ownership data stays consistent there.
            return fromHandleToSnapshot(client.write(remote ->
                    remote.createGeneration(r.termId(), ctxFactory.forCurrentUser()), r));
        }

        private GenerationSessionResponse fromHandleToSnapshot(Object unused, CreateGenerationRequest r) {
            throw new UnsupportedOperationException();
        }`,
`        @Override public GenerationSessionResponse create(CreateGenerationRequest r) {
            // Cheap metadata insert executed inside the RMI Server so session
            // ownership stays consistent with where the solver will run.
            var handle = client.write(remote ->
                    remote.createGeneration(r.termId(), ctxFactory.forCurrentUser()));
            return getById(handle.generationId());
        }`);
fs.writeFileSync(p,c);
console.log('create path fixed');
