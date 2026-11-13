package com.payledger.payment;

import com.payledger.common.PageResponse;
import com.payledger.merchant.AuthenticatedMerchant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payment endpoints. Every call is scoped to the authenticated merchant.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String REPLAYED = "Idempotent-Replayed";

    private final PaymentService paymentService;
    private final PaymentQueryService queries;
    private final RefundService refundService;

    public PaymentController(PaymentService paymentService, PaymentQueryService queries, RefundService refundService) {
        this.paymentService = paymentService;
        this.queries = queries;
        this.refundService = refundService;
    }

    /**
     * Creates a payment. A missing header is a 400. A replay returns the stored status code with
     * {@code Idempotent-Replayed: true}.
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> create(
            @AuthenticationPrincipal AuthenticatedMerchant merchant,
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {
        PaymentResult result = paymentService.create(merchant.merchantId(), idempotencyKey.trim(), request);
        var response = ResponseEntity.status(result.httpStatus())
                .location(URI.create("/api/v1/payments/" + result.payment().id()));
        if (result.replayed()) {
            response.header(REPLAYED, "true");
        }
        return response.body(result.payment());
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@AuthenticationPrincipal AuthenticatedMerchant merchant, @PathVariable UUID id) {
        return queries.get(merchant.merchantId(), id);
    }

    /** Example: {@code ?status=FAILED&from=2026-11-01T00:00:00Z&to=2026-12-01T00:00:00Z&page=0&size=20}. */
    @GetMapping
    public PageResponse<PaymentSummary> list(
            @AuthenticationPrincipal AuthenticatedMerchant merchant,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queries.list(merchant.merchantId(), status, from, to, page, size);
    }

    /** Full or partial refund of a SUCCEEDED payment. Also needs an Idempotency-Key. */
    @PostMapping("/{id}/refunds")
    public ResponseEntity<RefundResponse> refund(
            @AuthenticationPrincipal AuthenticatedMerchant merchant,
            @PathVariable UUID id,
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody CreateRefundRequest request) {
        RefundResult result = refundService.refund(merchant.merchantId(), id, idempotencyKey.trim(), request);
        var response = ResponseEntity.status(201);
        if (result.replayed()) {
            response.header(REPLAYED, "true");
        }
        return response.body(result.refund());
    }
}
