package com.payledger.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.payledger.bank.BankChargeRequest;
import com.payledger.bank.BankGateway;
import com.payledger.bank.BankOutcome;
import com.payledger.bank.BankResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The processor calls the bank, and only inquires when the answer is unknown.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@ExtendWith(MockitoExtension.class)
class PaymentProcessorTest {

    private static final UUID ID = UUID.randomUUID();
    private static final BankChargeRequest REQUEST = new BankChargeRequest(ID, "c", 100);

    @Mock
    PaymentTransitions transitions;

    @Mock
    BankGateway bank;

    @InjectMocks
    PaymentProcessor processor;

    @Test
    void knownAnswerIsAppliedWithoutInquiry() {
        BankResponse ok = new BankResponse(BankOutcome.SUCCESS, "B", "Approved");
        when(transitions.startProcessing(ID)).thenReturn(REQUEST);
        when(bank.charge(REQUEST)).thenReturn(ok);
        when(transitions.applyBankResult(ID, ok)).thenReturn(PaymentStatus.SUCCEEDED);

        assertThat(processor.process(ID)).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(bank, never()).inquire(any());
    }

    @Test
    void timeoutLeadsToInquiryAndTheInquiryAnswerIsApplied() {
        BankResponse truth = new BankResponse(BankOutcome.DEBITED_NOT_CREDITED, "B", "debited");
        when(transitions.startProcessing(ID)).thenReturn(REQUEST);
        when(bank.charge(REQUEST)).thenReturn(BankResponse.timeout());
        when(bank.inquire(ID)).thenReturn(truth);
        when(transitions.applyBankResult(ID, truth)).thenReturn(PaymentStatus.FAILED);

        assertThat(processor.process(ID)).isEqualTo(PaymentStatus.FAILED);
        verify(transitions, never()).applyBankResult(ID, BankResponse.timeout());
    }

    @Test
    void resolveMarksProcessingThenInquires() {
        BankResponse notFound = BankResponse.notFound();
        when(bank.inquire(ID)).thenReturn(notFound);
        when(transitions.applyBankResult(ID, notFound)).thenReturn(PaymentStatus.FAILED);

        assertThat(processor.resolve(ID)).isEqualTo(PaymentStatus.FAILED);
        InOrder order = inOrder(transitions, bank);
        order.verify(transitions).startProcessing(ID);
        order.verify(bank).inquire(ID);
        order.verify(transitions).applyBankResult(ID, notFound);
    }
}
