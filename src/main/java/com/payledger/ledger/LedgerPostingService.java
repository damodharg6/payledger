package com.payledger.ledger;

import static com.payledger.ledger.LedgerLine.credit;
import static com.payledger.ledger.LedgerLine.debit;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The four ways a payment moves money, written as double-entry postings:
 * <pre>
 *   Payment succeeded          D customer   C merchant
 *   Debited, payment failed    D customer   C bank clearing   (money is "stuck")
 *   Reversal (T+1)             D clearing   C customer        (money goes back)
 *   Refund                     D merchant   C customer
 * </pre>
 * After every failed payment has been reversed, the clearing account is back to zero.
 * <p>
 * Every method joins the caller's transaction, so the payment's status change and its money
 * movement are committed together or not at all.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@Service
public class LedgerPostingService {

    private final LedgerService ledger;
    private final LedgerAccountService accounts;

    public LedgerPostingService(LedgerService ledger, LedgerAccountService accounts) {
        this.ledger = ledger;
        this.accounts = accounts;
    }

    @Transactional
    public LedgerTransaction recordPaymentSucceeded(UUID paymentId, UUID merchantId, String customerRef, long amountPaise) {
        UUID customer = accounts.customerAccountId(merchantId, customerRef);
        UUID merchant = accounts.merchantAccountId(merchantId);
        return ledger.post(LedgerTransactionType.PAYMENT, paymentId,
                List.of(debit(customer, amountPaise), credit(merchant, amountPaise)));
    }

    @Transactional
    public LedgerTransaction recordDebitHeldForFailedPayment(UUID paymentId, UUID merchantId, String customerRef,
                                                             long amountPaise) {
        UUID customer = accounts.customerAccountId(merchantId, customerRef);
        return ledger.post(LedgerTransactionType.PAYMENT, paymentId,
                List.of(debit(customer, amountPaise), credit(LedgerAccounts.BANK_CLEARING_ID, amountPaise)));
    }

    @Transactional
    public LedgerTransaction recordReversal(UUID paymentId, UUID merchantId, String customerRef, long amountPaise) {
        UUID customer = accounts.customerAccountId(merchantId, customerRef);
        return ledger.post(LedgerTransactionType.REVERSAL, paymentId,
                List.of(debit(LedgerAccounts.BANK_CLEARING_ID, amountPaise), credit(customer, amountPaise)));
    }

    @Transactional
    public LedgerTransaction recordRefund(UUID paymentId, UUID merchantId, String customerRef, long amountPaise) {
        UUID customer = accounts.customerAccountId(merchantId, customerRef);
        UUID merchant = accounts.merchantAccountId(merchantId);
        return ledger.post(LedgerTransactionType.REFUND, paymentId,
                List.of(debit(merchant, amountPaise), credit(customer, amountPaise)));
    }
}
