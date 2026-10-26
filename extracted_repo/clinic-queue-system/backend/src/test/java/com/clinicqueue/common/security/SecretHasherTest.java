package com.clinicqueue.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecretHasherTest {

    @Test
    void secretsAreHighEntropyAndUnique() {
        String a = SecretHasher.generateSecret();
        String b = SecretHasher.generateSecret();

        assertThat(a).hasSize(64); // 32 bytes hex
        assertThat(a).isNotEqualTo(b);
        assertThat(a).matches("[0-9a-f]{64}");
    }

    @Test
    void hashIsStableAndHidesInput() {
        String secret = "a".repeat(64);

        String hash = SecretHasher.hash(secret);

        assertThat(hash).hasSize(64);
        assertThat(hash).isNotEqualTo(secret);
        assertThat(SecretHasher.hash(secret)).isEqualTo(hash);
        assertThat(SecretHasher.hash(secret + "x")).isNotEqualTo(hash);
    }
}
