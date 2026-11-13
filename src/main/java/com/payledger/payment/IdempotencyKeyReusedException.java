package com.payledger.payment;

import com.payledger.common.UnprocessableEntityException;

/**
 * The same Idempotency-Key was sent with a different request body (HTTP 422). This is a client
 * bug: one key must always mean one specific payment.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public class IdempotencyKeyReusedException extends UnprocessableEntityException {

    public IdempotencyKeyReusedException(String key) {
        super("Idempotency-Key '" + key + "' was already used with a different request body");
    }
}
