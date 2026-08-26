package com.unicconnect.rmi.server.facade;

import com.unicconnect.exception.BusinessRuleException;
import com.unicconnect.exception.DuplicateResourceException;
import com.unicconnect.exception.ResourceNotFoundException;
import com.unicconnect.exception.ValidationException;
import com.unicconnect.rmi.contract.RemoteBusinessException;

/** Domain-exception → serializable RemoteBusinessException translation. */
final class FacadeGuard {

    private FacadeGuard() {}

    static java.rmi.RemoteException translate(RuntimeException e) {
        if (e instanceof ResourceNotFoundException) {
            return remote(RemoteBusinessException.Code.NOT_FOUND, e.getMessage());
        }
        if (e instanceof ValidationException) {
            return remote(RemoteBusinessException.Code.VALIDATION, e.getMessage());
        }
        if (e instanceof DuplicateResourceException) {
            return remote(RemoteBusinessException.Code.DUPLICATE, e.getMessage());
        }
        if (e instanceof BusinessRuleException) {
            return remote(RemoteBusinessException.Code.BUSINESS_RULE, e.getMessage());
        }
        // Never leak internal stack traces across the wire.
        return remote(RemoteBusinessException.Code.BUSINESS_RULE,
                "Remote operation failed: " + e.getClass().getSimpleName());
    }

    private static RemoteBusinessException remote(RemoteBusinessException.Code code, String msg) {
        try {
            return new RemoteBusinessException(code, msg == null ? "error" : msg);
        } catch (java.rmi.RemoteException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
