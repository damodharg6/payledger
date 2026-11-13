package com.payledger.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the mock bank: fixed scenarios, random rates, de-duplication and inquiries.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
class MockBankClientTest {

    private static BankProperties props(double success, double decline, double debitFail, double timeout) {
        return new BankProperties(success, decline, debitFail, timeout, Duration.ZERO, Duration.ZERO,
                Duration.ofMillis(200), Duration.ofMillis(60), 0.0, 42L);
    }

    private static BankChargeRequest charge(String customerRef) {
        return new BankChargeRequest(UUID.randomUUID(), customerRef, 1_000);
    }

    private final MockBankClient bank = new MockBankClient(props(1, 0, 0, 0));

    @Test
    void scenarioPrefixesFixTheOutcome() {
        assertThat(bank.charge(charge("sim_success")).outcome()).isEqualTo(BankOutcome.SUCCESS);
        assertThat(bank.charge(charge("sim_decline-42")).outcome()).isEqualTo(BankOutcome.DECLINED);
        assertThat(bank.charge(charge("sim_debit_fail:x")).outcome()).isEqualTo(BankOutcome.DEBITED_NOT_CREDITED);
        assertThat(bank.charge(charge("sim_timeout_debit_fail")).outcome()).isEqualTo(BankOutcome.DEBITED_NOT_CREDITED);
    }

    @Test
    void scenarioNamesMustMatchExactly() {
        assertThat(MockBankClient.scenarioFor("sim_declined")).isEmpty();
        assertThat(MockBankClient.scenarioFor("customer-1")).isEmpty();
        assertThat(MockBankClient.scenarioFor("sim_decline.7")).isPresent();
    }

    @Test
    void bankDownThrowsAndRecordsNothing() {
        BankChargeRequest request = charge("sim_bank_down");
        assertThatThrownBy(() -> bank.charge(request)).isInstanceOf(BankUnavailableException.class);
        assertThat(bank.inquire(request.paymentId()).outcome()).isEqualTo(BankOutcome.NOT_FOUND);
    }

    @Test
    void chargingTheSamePaymentTwiceReturnsTheFirstAnswer() {
        MockBankClient random = new MockBankClient(props(0.5, 0.5, 0, 0));
        BankChargeRequest request = charge("cust-1");
        BankResponse first = random.charge(request);
        for (int i = 0; i < 20; i++) {
            assertThat(random.charge(request)).isEqualTo(first);
        }
    }

    @Test
    void inquiryReturnsWhatReallyHappened() {
        BankChargeRequest request = charge("sim_debit_fail");
        BankResponse charged = bank.charge(request);
        assertThat(bank.inquire(request.paymentId())).isEqualTo(charged);
        assertThat(bank.inquire(UUID.randomUUID()).outcome()).isEqualTo(BankOutcome.NOT_FOUND);
    }

    @Test
    void timeoutScenarioAnswersLateButIsAlreadyDecided() {
        BankChargeRequest request = charge("sim_timeout_success");
        long start = System.nanoTime();
        assertThat(bank.charge(request).outcome()).isEqualTo(BankOutcome.SUCCESS);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(60));
    }

    @Test
    void pendingScenarioIsPendingOnFirstInquiryOnly() {
        BankChargeRequest request = charge("sim_timeout_pending");
        bank.charge(request);
        assertThat(bank.inquire(request.paymentId()).outcome()).isEqualTo(BankOutcome.PENDING);
        assertThat(bank.inquire(request.paymentId()).outcome()).isEqualTo(BankOutcome.SUCCESS);
    }

    @Test
    void randomOutcomesFollowTheConfiguredRates() {
        MockBankClient random = new MockBankClient(props(0.7, 0.2, 0.1, 0));
        Map<BankOutcome, Integer> counts = new EnumMap<>(BankOutcome.class);
        int n = 10_000;
        for (int i = 0; i < n; i++) {
            counts.merge(random.charge(charge("cust")).outcome(), 1, Integer::sum);
        }
        assertThat(counts.get(BankOutcome.SUCCESS) / (double) n).isBetween(0.67, 0.73);
        assertThat(counts.get(BankOutcome.DECLINED) / (double) n).isBetween(0.17, 0.23);
        assertThat(counts.get(BankOutcome.DEBITED_NOT_CREDITED) / (double) n).isBetween(0.08, 0.12);
    }

    @Test
    void ratesMustAddUpToOne() {
        assertThatThrownBy(() -> props(0.5, 0.2, 0.1, 0.1)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("add up to 1");
        assertThatThrownBy(() -> props(1.5, -0.5, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BankProperties(1, 0, 0, 0, Duration.ofMillis(10), Duration.ofMillis(5),
                null, null, 0, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
