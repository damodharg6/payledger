package com.payledger.common;

/**
 * The request is well-formed but cannot be processed as sent; mapped to HTTP 422.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public class UnprocessableEntityException extends RuntimeException {

    public UnprocessableEntityException(String message) {
        super(message);
    }
}
