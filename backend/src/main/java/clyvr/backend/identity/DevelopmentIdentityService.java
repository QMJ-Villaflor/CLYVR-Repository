package clyvr.backend.identity;

import clyvr.backend.database.ClyvrDataRepository;
import java.net.InetAddress;
import java.sql.SQLException;
import java.util.Objects;

public final class DevelopmentIdentityService {

    private final boolean enabled;
    private final DemoIdentityMappings mappings;
    private final ClyvrDataRepository repository;

    public DevelopmentIdentityService(
            boolean enabled,
            DemoIdentityMappings mappings,
            ClyvrDataRepository repository) {
        this.enabled = enabled;
        this.mappings = Objects.requireNonNull(mappings);
        this.repository = Objects.requireNonNull(repository);
    }

    public DevelopmentIdentity resolve(String demoKey, String remoteAddress) throws SQLException {
        if (!enabled || !isLoopback(remoteAddress)) {
            throw new DevelopmentIdentityUnavailableException();
        }
        long userId = mappings.userId(demoKey);
        return repository.findActiveIdentity(userId)
                .map(identity -> new DevelopmentIdentity(
                        demoKey, userId, identity.role(), identity.doctorId()))
                .orElseThrow(InvalidDevelopmentIdentityException::new);
    }

    public long resolveDoctorId(String doctorKey) {
        if (!enabled) {
            throw new DevelopmentIdentityUnavailableException();
        }
        return mappings.doctorId(doctorKey);
    }

    public java.util.Map<String, Long> configuredDoctorIds() {
        if (!enabled) {
            throw new DevelopmentIdentityUnavailableException();
        }
        return mappings.configuredDoctorIds();
    }

    private static boolean isLoopback(String address) {
        if (address == null || address.isBlank()) {
            return false;
        }
        try {
            return InetAddress.getByName(address).isLoopbackAddress();
        } catch (Exception exception) {
            return false;
        }
    }

    public static final class DevelopmentIdentityUnavailableException extends RuntimeException {
    }

    public static final class InvalidDevelopmentIdentityException extends RuntimeException {
    }

    public static final class ForbiddenRoleException extends RuntimeException {
    }
}
