package clyvr.backend.security;

import clyvr.backend.identity.BackendRole;

public record UserCredential(
        long userId,
        String passwordHash,
        BackendRole role,
        boolean active,
        Long doctorId,
        String firstName,
        String lastName,
        String email,
        String phone) {
}
