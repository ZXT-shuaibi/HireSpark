package com.nageoffer.ai.ragent.user.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserPasswordServiceTest {

    private final UserPasswordService passwordService = new UserPasswordService();

    @Test
    void hashesPasswordInsteadOfKeepingPlaintext() {
        String encoded = passwordService.encode("S3cret-pass");

        assertThat(encoded).isNotEqualTo("S3cret-pass");
        assertThat(encoded).startsWith("pbkdf2$");
        assertThat(passwordService.matches("S3cret-pass", encoded)).isTrue();
        assertThat(passwordService.matches("wrong-pass", encoded)).isFalse();
    }

    @Test
    void supportsLegacyPlaintextForLoginMigrationOnly() {
        assertThat(passwordService.matches("legacy", "legacy")).isTrue();
        assertThat(passwordService.matches("wrong", "legacy")).isFalse();
        assertThat(passwordService.needsRehash("legacy")).isTrue();
    }
}
