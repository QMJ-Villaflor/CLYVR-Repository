package clyvr.backend.identity;

@FunctionalInterface
public interface FirebaseTokenVerifier {
    VerifiedIdentity verify(String idToken);
}
