package com.cisco.dhruva.sip.proxy.errors;


import com.cisco.dsb.exception.DhruvaException;

public class DestinationUnreachableException extends DhruvaException {

    public DestinationUnreachableException(String msg) {
        super(msg);
    }

    public DestinationUnreachableException(String message, Exception exception) {
        super(message, exception);
    }
}
