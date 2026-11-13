package com.payledger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base class for integration tests: the full Spring app, MockMvc and a Testcontainers PostgreSQL.
 * Ledger rows can never be deleted (append-only trigger), so tests do not clean up; each test
 * registers its own merchant and uses unique data instead.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mvc;

    /** A merchant created through the real API, with its one-time API key. */
    protected record TestMerchant(UUID id, String apiKey) {
    }

    protected TestMerchant newMerchant() throws Exception {
        String body = mvc.perform(post("/api/v1/merchants").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Test Shop","email":"m-%s@example.com"}""".formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new TestMerchant(UUID.fromString(JsonPath.read(body, "$.merchantId")), JsonPath.read(body, "$.apiKey"));
    }
}
