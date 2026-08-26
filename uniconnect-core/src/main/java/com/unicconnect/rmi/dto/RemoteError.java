package com.unicconnect.rmi.dto;

import java.io.Serializable;

/** Generic error payload; client re-maps to local exceptions. */
public record RemoteError(String code, String message) implements Serializable {
    private static final long serialVersionUID = 1L;
}
