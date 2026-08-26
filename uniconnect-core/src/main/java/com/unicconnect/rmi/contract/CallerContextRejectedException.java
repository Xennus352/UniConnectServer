package com.unicconnect.rmi.contract;

/** Thrown by the RMI Server when a CallerContext fails verification. */
public class CallerContextRejectedException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public CallerContextRejectedException(String message) { super(message); }
}
