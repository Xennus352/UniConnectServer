const fs=require('fs');const p='unicconnect-api/src/main/java/com/unicconnect/rmi/client/RmiStubCache.java';
let c=fs.readFileSync(p,'utf8');
c=c.replace('@FunctionalInterface\ninterface RemoteCall<T, R> {','@FunctionalInterface\npublic interface RemoteCall<T, R> {');
fs.writeFileSync(p,c);console.log('RemoteCall public');
