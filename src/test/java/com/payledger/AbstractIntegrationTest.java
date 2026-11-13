package com.payledger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.payledger.ledger.LedgerAccountService;
import com.payledger.ledger.LedgerPostingService;
import com.payledger.merchant.ApiKeyAuthenticationFilter;
import com.payledger.payment.Payment;
import com.payledger.payment.PaymentController;
import com.payledger.payment.PaymentRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

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

    @Autowired
    protected TransactionTemplate tx;

    @Autowired
    protected PaymentRepository paymentRepository;

    @Autowired
    protected LedgerPostingService ledgerPostings;

    @Autowired
    protected LedgerAccountService ledgerAccounts;

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

    /** Creates a payment through the API and returns its id. */
    protected UUID createPayment(TestMerchant m, long amountPaise, String customerRef) throws Exception {
        String body = mvc.perform(post("/api/v1/payments")
                        .header(ApiKeyAuthenticationFilter.HEADER, m.apiKey())
                        .header(PaymentController.IDEMPOTENCY_KEY, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountPaise":%d,"customerRef":"%s"}""".formatted(amountPaise, customerRef)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    /**
     * A payment the bank has confirmed: CREATED → PROCESSING → SUCCEEDED plus its PAYMENT posting
     * (D customer, C merchant), in one transaction, as the Phase 4 flow will do.
     */
    protected UUID succeededPayment(TestMerchant m, long amountPaise, String customerRef) throws Exception {
        UUID id = createPayment(m, amountPaise, customerRef);
        tx.executeWithoutResult(s -> {
            Payment p = paymentRepository.findById(id).orElseThrow();
            p.startProcessing(Instant.now());
            p.succeed("TEST-BANK-" + id, Instant.now());
            ledgerPostings.recordPaymentSucceeded(id, m.id(), customerRef, amountPaise);
        });
        return id;
    }
}
