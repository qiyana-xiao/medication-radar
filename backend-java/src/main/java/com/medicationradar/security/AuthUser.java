package com.medicationradar.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Set;

public record AuthUser(Long id, String username, String role) {
    private static final Set<String> VALID_ROLES = Set.of("patient", "doctor", "admin");

    @JsonIgnore
    public boolean isAdmin() {
        return "admin".equals(role);
    }

    @JsonIgnore
    public boolean isDoctor() {
        return "doctor".equals(role);
    }

    @JsonIgnore
    public boolean isPatient() {
        return "patient".equals(role);
    }

    @JsonIgnore
    public boolean canAnalyze() {
        return isPatient() || isDoctor();
    }

    @JsonIgnore
    public boolean isValidRole() {
        return VALID_ROLES.contains(role);
    }
}
