package com.unicconnect.rmi.contract;

import java.rmi.RemoteException;

/**
 * Serializable carrier for domain errors crossing the RMI boundary. The
 * client translates {@link #code} back into the equivalent local exception so
 * existing REST error handling is preserved.
 */
public class RemoteBusinessException extends RemoteException {

    private static final long serialVersionUID = 1L;

    public enum Code { NOT_FOUND, VALIDATION, BUSINESS_RULE, DUPLICATE, ACCESS_DENIED, UNSUPPORTED }

    public final Code code;
    public final String detail;

    public RemoteBusinessException(Code code, String detail) throws RemoteException {
        super(detail);
        this.code = code;
        this.detail = detail;
    }
}
