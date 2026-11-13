package com.payledger.merchant;

import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Merchant endpoints. Registration is public; {@code /me} needs the merchant's API key.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@RestController
@RequestMapping("/api/v1/merchants")
public class MerchantController {

    private final MerchantService merchantService;

    public MerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    /** Returns 201 with the API key. {@code Cache-Control: no-store} keeps proxies from caching the secret. */
    @PostMapping
    public ResponseEntity<RegisterMerchantResponse> register(@Valid @RequestBody RegisterMerchantRequest request) {
        RegisterMerchantResponse body = merchantService.register(request);
        return ResponseEntity.created(URI.create("/api/v1/merchants/me"))
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    @GetMapping("/me")
    public MerchantResponse me(@AuthenticationPrincipal AuthenticatedMerchant merchant) {
        return merchantService.get(merchant.merchantId());
    }
}
