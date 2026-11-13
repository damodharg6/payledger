package com.payledger.common;

/**
 * The request clashes with the current state of a resource; mapped to HTTP 409.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
