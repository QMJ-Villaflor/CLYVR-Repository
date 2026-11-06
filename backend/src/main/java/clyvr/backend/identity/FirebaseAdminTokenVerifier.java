package clyvr.backend.identity;

import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import java.util.Objects;

public final class FirebaseAdminTokenVerifier implements FirebaseTokenVerifier {

    private final FirebaseAuth firebaseAuth;

    public FirebaseAdminTokenVerifier(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = Objects.requireNonNull(firebaseAuth, "firebaseAuth");
    }

    @Override
    public VerifiedIdentity verify(String idToken) {
        try {
            FirebaseToken token = firebaseAuth.verifyIdToken(idToken, true);
            return new VerifiedIdentity(token.getUid());
        } catch (FirebaseAuthException exception) {
            AuthErrorCode errorCode = exception.getAuthErrorCode();
            if (errorCode == AuthErrorCode.INVALID_ID_TOKEN
                    || errorCode == AuthErrorCode.REVOKED_ID_TOKEN
                    || errorCode == AuthErrorCode.USER_DISABLED) {
                throw new InvalidFirebaseTokenException();
            }
            throw new TokenVerifierUnavailableException();
        } catch (IllegalArgumentException exception) {
            throw new InvalidFirebaseTokenException();
        }
    }
}
