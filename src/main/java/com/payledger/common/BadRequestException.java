package com.payledger.common;

/**
 * Invalid input that Bean Validation cannot catch on its own (for example from &gt; to); HTTP 400.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
