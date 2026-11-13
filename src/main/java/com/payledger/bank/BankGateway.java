package com.payledger.bank;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Calls the {@link BankClient} with a hard timeout and turns every kind of trouble into a
 * {@link BankResponse} instead of an exception:
 * <ul>
 *   <li>no answer in time → {@link BankOutcome#TIMEOUT} (the charge may still have happened)</li>
 *   <li>bank unreachable or error → {@link BankOutcome#PENDING} (we do not know; inquire)</li>
 * </ul>
 * Neither is ever treated as "failed": that would be guessing, and a wrong guess means either a
 * double charge (customer retries) or lost money (we never reverse a real debit).
 * <p>
 * Each call runs on a Java 21 virtual thread, so thousands of slow bank calls cost almost
 * nothing while we wait.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@Component
public class BankGateway {

    private static final Logger log = LoggerFactory.getLogger(BankGateway.class);

    private final BankClient client;
    private final Duration timeout;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public BankGateway(BankClient client, BankProperties props) {
        this.client = client;
        this.timeout = props.timeout();
    }

    public BankResponse charge(BankChargeRequest request) {
        return call(() -> client.charge(request), BankResponse.timeout(), "charge " + request.paymentId());
    }

    public BankResponse inquire(UUID paymentId) {
        return call(() -> client.inquire(paymentId), BankResponse.pending("Inquiry timed out"), "inquiry " + paymentId);
    }

    private BankResponse call(Callable<BankResponse> work, BankResponse onTimeout, String what) {
        Future<BankResponse> future = executor.submit(work);
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("Bank {} timed out after {}", what, timeout);
            return onTimeout;
        } catch (ExecutionException e) {
            log.warn("Bank {} failed: {}", what, e.getCause().toString());
            return BankResponse.pending("Bank error: " + e.getCause().getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return BankResponse.pending("Interrupted while waiting for bank");
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
