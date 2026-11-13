package com.payledger.bank;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * A pretend bank/UPI switch with realistic bad behaviour: random latency, declines, "debited but
 * failed", answers that arrive after the caller gave up, and inquiries that say "still pending".
 * <p>
 * <b>Random mode</b> uses the rates in {@link BankProperties}.
 * <p>
 * <b>Scenario mode</b> (for tests and demos, like a card network's test cards): if the
 * customerRef starts with one of these names, followed by the end or by {@code - : . @}, the
 * outcome is fixed:
 * <ul>
 *   <li>{@code sim_success}, {@code sim_decline}, {@code sim_debit_fail}</li>
 *   <li>{@code sim_timeout_success}, {@code sim_timeout_debit_fail}: decided, but answered too late</li>
 *   <li>{@code sim_timeout_pending}: too late, the first inquiry says PENDING, later ones SUCCESS</li>
 *   <li>{@code sim_bank_down}: the bank is unreachable, nothing is recorded</li>
 * </ul>
 * Like a real bank, it remembers each charge by payment id: charging again returns the first
 * answer instead of debiting twice.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@Component
public class MockBankClient implements BankClient {

    private static final Logger log = LoggerFactory.getLogger(MockBankClient.class);

    /** How one charge will behave. */
    record Plan(BankOutcome outcome, boolean slow, boolean pendingOnFirstInquiry, boolean randomPending, boolean bankDown) {
    }

    private record Charge(BankResponse response, Plan plan, AtomicInteger inquiries) {
    }

    static final Map<String, Plan> SCENARIOS = Map.of(
            "sim_success", new Plan(BankOutcome.SUCCESS, false, false, false, false),
            "sim_decline", new Plan(BankOutcome.DECLINED, false, false, false, false),
            "sim_debit_fail", new Plan(BankOutcome.DEBITED_NOT_CREDITED, false, false, false, false),
            "sim_timeout_success", new Plan(BankOutcome.SUCCESS, true, false, false, false),
            "sim_timeout_debit_fail", new Plan(BankOutcome.DEBITED_NOT_CREDITED, true, false, false, false),
            "sim_timeout_pending", new Plan(BankOutcome.SUCCESS, true, true, false, false),
            "sim_bank_down", new Plan(null, false, false, false, true));

    private final BankProperties props;
    private final Random random;
    private final Map<UUID, Charge> charges = new ConcurrentHashMap<>();

    public MockBankClient(BankProperties props) {
        this.props = props;
        this.random = props.seed() == null ? new Random() : new Random(props.seed());
    }

    @Override
    public BankResponse charge(BankChargeRequest request) {
        Charge previous = charges.get(request.paymentId());
        if (previous != null) {
            return previous.response();
        }
        Plan plan = scenarioFor(request.customerRef()).orElseGet(this::randomPlan);
        if (plan.bankDown()) {
            throw new BankUnavailableException("Mock bank is down");
        }
        sleep(randomLatency());

        BankResponse response = new BankResponse(plan.outcome(), "MOCK-" + request.paymentId().toString().substring(0, 8),
                messageFor(plan.outcome()));
        // The outcome is decided and recorded now, even if the answer will reach the caller too late.
        Charge charge = charges.computeIfAbsent(request.paymentId(), id -> new Charge(response, plan, new AtomicInteger()));
        if (plan.slow()) {
            log.debug("Mock bank: payment {} decided {} but answers slowly", request.paymentId(), plan.outcome());
            sleep(props.slowResponseDelay());
        }
        return charge.response();
    }

    @Override
    public BankResponse inquire(UUID paymentId) {
        Charge charge = charges.get(paymentId);
        if (charge == null) {
            return BankResponse.notFound();
        }
        sleep(randomLatency());
        int inquiry = charge.inquiries().incrementAndGet();
        if (charge.plan().pendingOnFirstInquiry() && inquiry == 1) {
            return BankResponse.pending("Bank still processing");
        }
        if (charge.plan().randomPending() && random.nextDouble() < props.inquiryPendingRate()) {
            return BankResponse.pending("Bank still processing");
        }
        return charge.response();
    }

    static Optional<Plan> scenarioFor(String customerRef) {
        if (customerRef == null || !customerRef.startsWith("sim_")) {
            return Optional.empty();
        }
        String name = customerRef.split("[-:.@]", 2)[0];
        return Optional.ofNullable(SCENARIOS.get(name));
    }

    private Plan randomPlan() {
        double r = random.nextDouble();
        if (r < props.successRate()) {
            return new Plan(BankOutcome.SUCCESS, false, false, true, false);
        }
        r -= props.successRate();
        if (r < props.declineRate()) {
            return new Plan(BankOutcome.DECLINED, false, false, true, false);
        }
        r -= props.declineRate();
        if (r < props.debitedFailureRate()) {
            return new Plan(BankOutcome.DEBITED_NOT_CREDITED, false, false, true, false);
        }
        // Timeout bucket: the bank still decides a real outcome, it just answers too late.
        return new Plan(randomDecidedOutcome(), true, false, true, false);
    }

    private BankOutcome randomDecidedOutcome() {
        double total = props.successRate() + props.declineRate() + props.debitedFailureRate();
        if (total == 0) {
            return BankOutcome.SUCCESS;
        }
        double r = random.nextDouble() * total;
        if (r < props.successRate()) {
            return BankOutcome.SUCCESS;
        }
        return r < props.successRate() + props.declineRate() ? BankOutcome.DECLINED : BankOutcome.DEBITED_NOT_CREDITED;
    }

    private Duration randomLatency() {
        long min = props.minLatency().toMillis();
        long max = props.maxLatency().toMillis();
        return Duration.ofMillis(max <= min ? min : min + random.nextLong(max - min + 1));
    }

    private static String messageFor(BankOutcome outcome) {
        return switch (outcome) {
            case SUCCESS -> "Approved";
            case DECLINED -> "Declined by issuing bank";
            case DEBITED_NOT_CREDITED -> "Customer debited but credit to merchant failed";
            default -> outcome.name();
        };
    }

    private static void sleep(Duration d) {
        if (d.isZero() || d.isNegative()) {
            return;
        }
        try {
            Thread.sleep(d);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();  // the caller gave up waiting; the outcome is already recorded
        }
    }
}
