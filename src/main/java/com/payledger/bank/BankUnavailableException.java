package com.payledger.bank;

/**
 * The bank could not be reached at all (connection refused, 5xx). The charge did not reach it.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public class BankUnavailableException extends RuntimeException {

    public BankUnavailableException(String message) {
        super(message);
    }
}
