const fs=require('fs');const p='unicconnect-api/src/main/java/com/unicconnect/rmi/client/RmiStubCache.java';
let c=fs.readFileSync(p,'utf8');
c=c.replace('/** Remote lambda that is allowed to throw the checked RMI exception. */\n@FunctionalInterface\npublic interface RemoteCall<T, R> {\n    R apply(T stub) throws RemoteException;\n}\n','');
fs.writeFileSync(p,c);
fs.writeFileSync('unicconnect-api/src/main/java/com/unicconnect/rmi/client/RemoteCall.java',
`package com.unicconnect.rmi.client;

import java.rmi.RemoteException;

/** Remote lambda that is allowed to throw the checked RMI exception. */
@FunctionalInterface
public interface RemoteCall<T, R> {
    R apply(T stub) throws RemoteException;
}
`);
console.log('RemoteCall split');
