package com.payledger.merchant;

import com.payledger.common.ConflictException;

/**
 * A merchant with this email already exists (HTTP 409).
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public class DuplicateMerchantException extends ConflictException {

    public DuplicateMerchantException(String email) {
        super("A merchant with email " + email + " already exists");
    }
}
