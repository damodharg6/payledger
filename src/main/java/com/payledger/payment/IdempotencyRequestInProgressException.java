package com.payledger.payment;

import com.payledger.common.ConflictException;

/**
 * A request with this Idempotency-Key is still being processed (HTTP 409). The client should
 * wait a moment and retry with the same key; it will then get the stored result.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public class IdempotencyRequestInProgressException extends ConflictException {

    public IdempotencyRequestInProgressException(String key) {
        super("A request with Idempotency-Key '" + key + "' is still in progress; retry shortly");
    }
}
