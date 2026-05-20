package com.vista.pdg.exception;

public class UnsupportedStructureException extends RuntimeException {
    public UnsupportedStructureException(String type) {
        super("Unsupported structure type: " + type);
    }
}
