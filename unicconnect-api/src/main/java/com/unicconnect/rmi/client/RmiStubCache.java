package com.unicconnect.rmi.client;

import com.unicconnect.exception.BusinessRuleException;
import com.unicconnect.rmi.contract.RemoteBusinessException;

import java.net.ConnectException;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.util.function.Function;


/**
 * Cached-stub RMI invoker shared by all three domain clients.
 *
 * <ul>
 *   <li>Stub is looked up once and cached; a lost server triggers exactly one
 *       transparent relookup.</li>
 *   <li>READ operations may retry once after relookup; WRITE operations never
 *       retry (the remote side may already have committed).</li>
 *   <li>{@link RemoteBusinessException} is translated back into the matching
 *       local exception so GlobalExceptionHandler output stays identical.</li>
 * </ul>
 */
public abstract class RmiStubCache<T> {

    private final String host;
    private final int port;
    private final String binding;
    private final Class<T> type;
    private volatile T stub;

    protected RmiStubCache(String host, int port, String binding, Class<T> type) {
        this.host = host;
        this.port = port;
        this.binding = binding;
        this.type = type;
    }

    @SuppressWarnings("unchecked")
    public synchronized void relookup() {
        try {
            stub = (T) java.rmi.Naming.lookup("rmi://" + host + ":" + port + "/" + binding);
        } catch (Exception e) {
            throw new BusinessRuleException("RMI backend unavailable (" + binding + ")");
        }
    }

    public T stub() {
        T s = stub;
        if (s == null) { synchronized (this) { if (stub == null) relookup(); s = stub; } }
        return s;
    }

    /** Invoke a remote READ; one reconnect/retry is allowed. */
    public <R> R read(RemoteCall<T, R> call) {
        try {
            return call.apply(stub());
        } catch (RemoteException first) {
            if (!isConnectivity(first)) throw translate(first);
            synchronized (this) { relookup(); }
            try {
                return call.apply(stub());
            } catch (RemoteException second) {
                throw translate(second);
            }
        }
    }

    /**
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
    }

    private static boolean isConnectivity(RemoteException e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof ConnectException || cause instanceof NoSuchObjectException) return true;
            cause = cause.getCause();
        }
        return e instanceof NoSuchObjectException;
    }

    private static RuntimeException translate(RemoteException e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof RemoteBusinessException rbe) {
                return switch (rbe.code) {
                    case NOT_FOUND -> new com.unicconnect.exception.ResourceNotFoundException(rbe.detail);
                    case VALIDATION -> new com.unicconnect.exception.ValidationException(rbe.detail);
                    case DUPLICATE -> new com.unicconnect.exception.DuplicateResourceException(rbe.detail);
                    case BUSINESS_RULE -> new com.unicconnect.exception.BusinessRuleException(rbe.detail);
                    case ACCESS_DENIED -> new com.unicconnect.exception.BusinessRuleException(rbe.detail);
                    case UNSUPPORTED -> new BusinessRuleException(rbe.detail);
                };
            }
            cause = cause.getCause();
        }
        return new BusinessRuleException("RMI backend unavailable");
    }
}
