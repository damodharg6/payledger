package com.payledger.common;

/**
 * A requested resource does not exist (or the caller may not see it); mapped to HTTP 404.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
