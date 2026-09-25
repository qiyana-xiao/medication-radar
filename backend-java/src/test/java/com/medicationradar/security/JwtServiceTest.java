package com.medicationradar.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JwtServiceTest {
    private static final String SECRET = "12345678901234567890123456789012";

    @Test
    void issuedTokenCanBeReadBack() {
        JwtService service = service();
        AuthUser expected = new AuthUser(42L, "tester", "user");

        assertThat(service.parse(service.issue(expected))).isEqualTo(expected);
    }

    private JwtService service() {
        return new JwtService(SECRET, 3600);
    }
}
