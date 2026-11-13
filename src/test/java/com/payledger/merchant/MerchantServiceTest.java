package com.payledger.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.payledger.common.NotFoundException;
import com.payledger.ledger.LedgerAccount;
import com.payledger.ledger.LedgerAccountService;
import com.payledger.ledger.OwnerType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit tests for {@link MerchantService} with mocked repositories.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@ExtendWith(MockitoExtension.class)
class MerchantServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");

    @Mock
    MerchantRepository merchants;

    @Mock
    LedgerAccountService ledgerAccounts;

    @Mock
    ApiKeys apiKeys;

    @Mock
    DashboardUserRepository dashboardUsers;

    @Mock
    PasswordEncoder passwords;

    MerchantService service;

    @BeforeEach
    void setUp() {
        service = new MerchantService(merchants, dashboardUsers, ledgerAccounts, apiKeys, passwords, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void registerStoresHashNotKeyAndOpensLedgerAccount() {
        when(apiKeys.generate()).thenReturn("plk_secret");
        when(ledgerAccounts.openMerchantAccount(any(), anyString())).thenAnswer(inv ->
                new LedgerAccount(UUID.randomUUID(), OwnerType.MERCHANT, inv.getArgument(0).toString(), "x", "INR", NOW));

        RegisterMerchantResponse response = service.register(
                new RegisterMerchantRequest("  Chai Point ", " Owner@Chai.IN ", ""));

        ArgumentCaptor<Merchant> saved = ArgumentCaptor.forClass(Merchant.class);
        verify(merchants).saveAndFlush(saved.capture());
        Merchant m = saved.getValue();
        assertThat(m.getApiKeyHash()).isEqualTo(ApiKeys.sha256("plk_secret"));
        assertThat(m.getName()).isEqualTo("Chai Point");
        assertThat(m.getEmail()).isEqualTo("owner@chai.in");
        assertThat(m.getWebhookUrl()).isNull();
        assertThat(m.getCreatedAt()).isEqualTo(NOW);

        verify(ledgerAccounts).openMerchantAccount(eq(m.getId()), eq("Chai Point"));
        assertThat(response.apiKey()).isEqualTo("plk_secret");
        assertThat(response.merchantId()).isEqualTo(m.getId());
        assertThat(response.ledgerAccountId()).isNotNull();
        verify(dashboardUsers, never()).save(any());
    }

    @Test
    void dashboardPasswordCreatesAdminLoginWithBcryptHashOnly() {
        when(apiKeys.generate()).thenReturn("plk_secret");
        when(passwords.encode("correct horse battery")).thenReturn("$2a$10$hash");
        when(ledgerAccounts.openMerchantAccount(any(), anyString())).thenAnswer(inv ->
                new LedgerAccount(UUID.randomUUID(), OwnerType.MERCHANT, inv.getArgument(0).toString(), "x", "INR", NOW));

        service.register(new RegisterMerchantRequest("Shop", "Owner@Shop.in", null, "correct horse battery"));

        ArgumentCaptor<DashboardUser> user = ArgumentCaptor.forClass(DashboardUser.class);
        verify(dashboardUsers).save(user.capture());
        assertThat(user.getValue().getEmail()).isEqualTo("owner@shop.in");
        assertThat(user.getValue().getPasswordHash()).isEqualTo("$2a$10$hash");
        assertThat(user.getValue().getRole()).isEqualTo(DashboardUser.Role.ADMIN);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(merchants.existsByEmail("taken@shop.in")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterMerchantRequest("Shop", "TAKEN@shop.in", null)))
                .isInstanceOf(DuplicateMerchantException.class);
        verify(merchants, never()).saveAndFlush(any());
        verify(ledgerAccounts, never()).openMerchantAccount(any(), anyString());
    }

    @Test
    void getReturnsProfileWithLedgerAccount() {
        UUID id = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(merchants.findById(id)).thenReturn(Optional.of(new Merchant(id, "Shop", "a@b.in", "hash", null, NOW)));
        when(ledgerAccounts.findMerchantAccount(id)).thenReturn(Optional.of(
                new LedgerAccount(accountId, OwnerType.MERCHANT, id.toString(), "x", "INR", NOW)));

        MerchantResponse r = service.get(id);

        assertThat(r.merchantId()).isEqualTo(id);
        assertThat(r.ledgerAccountId()).isEqualTo(accountId);
    }

    @Test
    void getUnknownMerchantThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(merchants.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(id)).isInstanceOf(NotFoundException.class);
    }
}
