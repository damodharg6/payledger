package com.payledger;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * Base class for integration tests: the full Spring app, MockMvc and a Testcontainers PostgreSQL.
 * Ledger rows can never be deleted (append-only trigger), so tests do not clean up; they use
 * unique data (random emails, ids) instead.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {
}
