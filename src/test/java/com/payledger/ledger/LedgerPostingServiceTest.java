package com.payledger.ledger;

import static com.payledger.ledger.LedgerLine.credit;
import static com.payledger.ledger.LedgerLine.debit;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Checks that each payment event debits and credits the right accounts.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@ExtendWith(MockitoExtension.class)
class LedgerPostingServiceTest {

    private static final UUID PAYMENT = UUID.randomUUID();
    private static final UUID MERCHANT_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ACC = UUID.randomUUID();
    private static final UUID MERCHANT_ACC = UUID.randomUUID();
    private static final UUID CLEARING = LedgerAccounts.BANK_CLEARING_ID;

    @Mock
    LedgerService ledger;

    @Mock
    LedgerAccountService accounts;

    @InjectMocks
    LedgerPostingService postings;

    @BeforeEach
    void setUp() {
        when(accounts.customerAccountId(MERCHANT_ID, "cust-1")).thenReturn(CUSTOMER_ACC);
    }

    @Test
    void succeededPaymentMovesMoneyFromCustomerToMerchant() {
        when(accounts.merchantAccountId(MERCHANT_ID)).thenReturn(MERCHANT_ACC);
        postings.recordPaymentSucceeded(PAYMENT, MERCHANT_ID, "cust-1", 500);
        verify(ledger).post(eq(LedgerTransactionType.PAYMENT), eq(PAYMENT),
                eq(List.of(debit(CUSTOMER_ACC, 500), credit(MERCHANT_ACC, 500))));
    }

    @Test
    void debitOnFailedPaymentIsHeldInClearing() {
        postings.recordDebitHeldForFailedPayment(PAYMENT, MERCHANT_ID, "cust-1", 500);
        verify(ledger).post(eq(LedgerTransactionType.PAYMENT), eq(PAYMENT),
                eq(List.of(debit(CUSTOMER_ACC, 500), credit(CLEARING, 500))));
    }

    @Test
    void reversalReturnsMoneyFromClearingToCustomer() {
        postings.recordReversal(PAYMENT, MERCHANT_ID, "cust-1", 500);
        verify(ledger).post(eq(LedgerTransactionType.REVERSAL), eq(PAYMENT),
                eq(List.of(debit(CLEARING, 500), credit(CUSTOMER_ACC, 500))));
    }

    @Test
    void refundMovesMoneyFromMerchantBackToCustomer() {
        when(accounts.merchantAccountId(MERCHANT_ID)).thenReturn(MERCHANT_ACC);
        postings.recordRefund(PAYMENT, MERCHANT_ID, "cust-1", 200);
        verify(ledger).post(eq(LedgerTransactionType.REFUND), eq(PAYMENT),
                eq(List.of(debit(MERCHANT_ACC, 200), credit(CUSTOMER_ACC, 200))));
    }
}
