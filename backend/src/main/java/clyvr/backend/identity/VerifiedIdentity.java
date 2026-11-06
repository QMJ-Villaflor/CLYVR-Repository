package clyvr.backend.identity;

import java.util.Objects;
import java.util.regex.Pattern;

public record VerifiedIdentity(String firebaseUid) {

    private static final Pattern UID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,128}");

    public VerifiedIdentity {
        Objects.requireNonNull(firebaseUid, "firebaseUid");
        if (!UID_PATTERN.matcher(firebaseUid).matches()) {
            throw new IllegalArgumentException("Firebase returned an invalid user identifier.");
        }
    }
}
