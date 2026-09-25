package com.medicationradar.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PasswordServiceTest {
    private final PasswordService service = new PasswordService(new BCryptPasswordEncoder(4));

    @Test
    void createsAndVerifiesBcryptHashForNewAccounts() {
        String hash = service.encode("new-password");

        assertThat(hash).startsWith("$2");
        assertThat(service.matches("new-password", hash)).isTrue();
        assertThat(service.matches("wrong-password", hash)).isFalse();
    }
}
