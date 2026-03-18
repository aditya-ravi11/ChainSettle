package com.chainsettle.exception;

public class SettlementFailedException extends RuntimeException {
    public SettlementFailedException(final String message) {
        super(message);
    }
}

