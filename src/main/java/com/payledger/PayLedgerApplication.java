package com.payledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * PayLedger: a payment and ledger platform.
 * No customer is charged twice, no rupee is lost, failed payments are reversed within T+1.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@SpringBootApplication
public class PayLedgerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PayLedgerApplication.class, args);
    }
}
