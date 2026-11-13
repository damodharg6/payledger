package com.payledger.ledger;

import com.payledger.common.PageResponse;
import com.payledger.merchant.AuthenticatedMerchant;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only ledger endpoints. Nothing here can move money; postings only happen inside the
 * payment and refund flows.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@RestController
@RequestMapping("/api/v1/ledger/accounts")
public class LedgerController {

    private final LedgerQueryService queries;

    public LedgerController(LedgerQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/{id}/balance")
    public AccountBalance balance(@AuthenticationPrincipal AuthenticatedMerchant merchant, @PathVariable UUID id) {
        return queries.balance(merchant.merchantId(), id);
    }

    @GetMapping("/{id}/entries")
    public PageResponse<LedgerEntryView> entries(@AuthenticationPrincipal AuthenticatedMerchant merchant,
                                                 @PathVariable UUID id,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return queries.entries(merchant.merchantId(), id, page, size);
    }
}
