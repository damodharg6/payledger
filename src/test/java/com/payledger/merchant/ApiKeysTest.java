package com.payledger.merchant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for API key generation and hashing.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
class ApiKeysTest {

    private final ApiKeys apiKeys = new ApiKeys();

    @Test
    void generatedKeysHavePrefixAnd256BitsOfRandomness() {
        String key = apiKeys.generate();
        assertThat(key).startsWith("plk_");
        // 32 bytes in Base64URL without padding = 43 characters
        assertThat(key.substring(4)).hasSize(43).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void generatedKeysAreUnique() {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            keys.add(apiKeys.generate());
        }
        assertThat(keys).hasSize(1_000);
    }

    @Test
    void sha256IsDeterministicLowerHex() {
        String hash = ApiKeys.sha256("plk_example");
        assertThat(hash).hasSize(64).matches("[0-9a-f]+").isEqualTo(ApiKeys.sha256("plk_example"));
        assertThat(ApiKeys.sha256("plk_other")).isNotEqualTo(hash);
    }

    @Test
    void sha256MatchesKnownVector() {
        assertThat(ApiKeys.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
