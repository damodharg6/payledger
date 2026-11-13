package com.payledger.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only endpoint (lives in src/test, never shipped) that fails with an exception whose
 * message contains a fake secret, to prove 500 responses never leak internals.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@RestController
class BoomTestController {

    static final String SECRET = "jdbc:postgresql://db/prod?password=hunter2";

    @GetMapping("/api/v1/test/boom")
    String boom() {
        throw new IllegalStateException("Connection failed: " + SECRET);
    }
}
