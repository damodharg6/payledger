package com.payledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.payledger.ledger.LedgerAccountRepository;
import com.payledger.ledger.LedgerAccounts;
import com.payledger.ledger.OwnerType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the database itself enforces the rules: Flyway applied every migration, the clearing
 * account exists, and ledger entries cannot be changed or deleted by anyone.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
class SchemaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    LedgerAccountRepository accounts;

    @Test
    void flywayAppliedAllMigrations() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class);
        assertThat(versions).containsExactly("1", "2");
    }

    @Test
    void bankClearingAccountIsSeeded() {
        var clearing = accounts.findById(LedgerAccounts.BANK_CLEARING_ID).orElseThrow();
        assertThat(clearing.getOwnerType()).isEqualTo(OwnerType.SYSTEM);
        assertThat(clearing.getOwnerRef()).isEqualTo("BANK_CLEARING");
        assertThat(clearing.getCurrency()).isEqualTo("INR");
    }

    @Test
    void ledgerEntriesAreAppendOnly() {
        // A balanced pair (1 paisa debit + 1 paisa credit) so the whole ledger stays balanced.
        UUID txn = UUID.randomUUID();
        jdbc.update("INSERT INTO ledger_transactions (id, type) VALUES (?, 'PAYMENT')", txn);
        jdbc.update("INSERT INTO ledger_entries (transaction_id, account_id, direction, amount_paise) VALUES (?, ?, 'D', 1)",
                txn, LedgerAccounts.BANK_CLEARING_ID);
        jdbc.update("INSERT INTO ledger_entries (transaction_id, account_id, direction, amount_paise) VALUES (?, ?, 'C', 1)",
                txn, LedgerAccounts.BANK_CLEARING_ID);

        assertThatThrownBy(() -> jdbc.update("UPDATE ledger_entries SET amount_paise = 999 WHERE transaction_id = ?", txn))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM ledger_entries WHERE transaction_id = ?", txn))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");

        Long stillThere = jdbc.queryForObject(
                "SELECT count(*) FROM ledger_entries WHERE transaction_id = ? AND amount_paise = 1", Long.class, txn);
        assertThat(stillThere).isEqualTo(2L);
    }

    @Test
    void moneyColumnsAreBigint() {
        List<String> types = jdbc.queryForList("""
                SELECT data_type FROM information_schema.columns
                WHERE column_name IN ('amount_paise', 'balance_paise')""", String.class);
        assertThat(types).isNotEmpty().allMatch("bigint"::equals);
    }
}
