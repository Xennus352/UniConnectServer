const fs=require('fs');const p='unicconnect-api/src/main/java/com/unicconnect/rmi/client/RmiStubCache.java';
let c=fs.readFileSync(p,'utf8');
c=c.replace('import java.util.function.Function;','import java.util.function.Function;\n\n/** Remote lambda that is allowed to throw the checked RMI exception. */\n@FunctionalInterface\ninterface RemoteCall<T, R> {\n    R apply(T stub) throws RemoteException;\n}');
c=c.replace('    /** Invoke a remote READ; one reconnect/retry is allowed. */\n    public <R> R read(Function<T, R> call) {',
            '    /** Invoke a remote READ; one reconnect/retry is allowed. */\n    public <R> R read(RemoteCall<T, R> call) {');
c=c.replace('    /** Invoke a remote WRITE; never retried. */\n    public <R> R write(Function<T, R> call) {',
            '    /** Invoke a remote WRITE; never retried. */\n    public <R> R write(RemoteCall<T, R> call) {');
fs.writeFileSync(p,c);
// TimetableGenerationRouting.runGeneration typing fix
const p2='unicconnect-api/src/main/java/com/unicconnect/ops/TimetableGenerationRouting.java';
let c2=fs.readFileSync(p2,'utf8');
c2=c2.replace(`            GenerationStatusDto dto = client.write(remote -> remote.runGeneration(id,
                    toRequestDto(r), ctxFactory.forCurrentUser()));
            // Snapshot right after submission: PENDING/RUNNING; UI polls status.
            return new GenerationSessionResponse(dto.generationId(), dto.termId(), dto.academicYear(),
                    dto.generatedByStaffId(), dto.generatedByStaffNo(), dto.status(),
                    dto.startedAt(), dto.publishedAt(), dto.finishedAt(), dto.createdAt(),
                    dto.failureReport());`,
`            client.write(remote -> remote.runGeneration(id,
                    toRequestDto(r), ctxFactory.forCurrentUser()));
            // Snapshot right after submission: PENDING/RUNNING; UI polls status.
            return getById(id);`);
fs.writeFileSync(p2,c2);
console.log('stub cache + routing fixed');
