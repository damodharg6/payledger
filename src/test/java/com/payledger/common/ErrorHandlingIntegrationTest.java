package com.payledger.common;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.payledger.AbstractIntegrationTest;
import com.payledger.merchant.ApiKeyAuthenticationFilter;
import com.payledger.payment.PaymentController;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Every kind of error comes back as RFC 7807 problem JSON with the right status, and a 500 never
 * shows internal details.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
class ErrorHandlingIntegrationTest extends AbstractIntegrationTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    @Test
    void unexpectedErrorIs500WithoutInternals() throws Exception {
        TestMerchant m = newMerchant();
        mvc.perform(get("/api/v1/test/boom").header(ApiKeyAuthenticationFilter.HEADER, m.apiKey()))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("Content-Type", startsWith(PROBLEM_JSON)))
                .andExpect(jsonPath("$.title").value("Internal error"))
                .andExpect(jsonPath("$.errorId").exists())
                .andExpect(content().string(not(containsString("hunter2"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void badPathVariableIs400() throws Exception {
        TestMerchant m = newMerchant();
        mvc.perform(get("/api/v1/payments/not-a-uuid").header(ApiKeyAuthenticationFilter.HEADER, m.apiKey()))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", startsWith(PROBLEM_JSON)));
    }

    @Test
    void malformedJsonIs400() throws Exception {
        TestMerchant m = newMerchant();
        mvc.perform(post("/api/v1/payments").header(ApiKeyAuthenticationFilter.HEADER, m.apiKey())
                        .header(PaymentController.IDEMPOTENCY_KEY, "k")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amountPaise\": 10,"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", startsWith(PROBLEM_JSON)));
    }

    @Test
    void unknownUrlIs404() throws Exception {
        TestMerchant m = newMerchant();
        mvc.perform(get("/api/v1/does-not-exist").header(ApiKeyAuthenticationFilter.HEADER, m.apiKey()))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", startsWith(PROBLEM_JSON)));
    }

    @Test
    void wrongMethodIs405() throws Exception {
        TestMerchant m = newMerchant();
        mvc.perform(put("/api/v1/payments").header(ApiKeyAuthenticationFilter.HEADER, m.apiKey()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Content-Type", startsWith(PROBLEM_JSON)));
    }

    @Test
    void wrongContentTypeIs415() throws Exception {
        TestMerchant m = newMerchant();
        mvc.perform(post("/api/v1/payments").header(ApiKeyAuthenticationFilter.HEADER, m.apiKey())
                        .header(PaymentController.IDEMPOTENCY_KEY, "k")
                        .contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(header().string("Content-Type", startsWith(PROBLEM_JSON)));
    }
}
