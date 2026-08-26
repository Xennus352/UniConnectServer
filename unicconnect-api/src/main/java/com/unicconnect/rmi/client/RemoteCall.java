package com.unicconnect.rmi.client;

import java.rmi.RemoteException;

/** Remote lambda that is allowed to throw the checked RMI exception. */
@FunctionalInterface
public interface RemoteCall<T, R> {
    R apply(T stub) throws RemoteException;
}
