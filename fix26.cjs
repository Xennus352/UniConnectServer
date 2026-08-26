const fs=require('fs');const p='unicconnect-api/src/main/java/com/unicconnect/rmi/client/RmiStubCache.java';
let c=fs.readFileSync(p,'utf8');
c=c.replace(`    /** Invoke a remote WRITE; never retried. */
    public <R> R write(RemoteCall<T, R> call) {
        try {
            return call.apply(stub());
        } catch (RemoteException e) {
            throw translate(e);
        }
    }`,
`    /**
     * Invoke a remote WRITE; never blindly retried. A stale cached stub
     * (RMI Server restarted) is detected via NoSuchObject/Connect failures,
     * which prove the request never reached the CURRENT server, so exactly
     * one relookup + single fresh attempt is safe.
     */
    public <R> R write(RemoteCall<T, R> call) {
        try {
            return call.apply(stub());
        } catch (java.rmi.NoSuchObjectException | java.rmi.ConnectException
                 | java.rmi.ConnectIOException stale) {
            synchronized (this) { relookup(); }
            try {
                return call.apply(stub());
            } catch (RemoteException e) {
                throw translate(e);
            }
        } catch (RemoteException e) {
            throw translate(e);
        }
    }`);
c=c.replace(`        } catch (RemoteException first) {
            if (!isConnectivity(first)) throw translate(first);`,
`        } catch (RemoteException first) {
            if (!isConnectivity(first)) throw translate(first);`);
fs.writeFileSync(p,c);console.log('write() hardened');
