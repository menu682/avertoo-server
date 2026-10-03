package pp.ua.avertooserver.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pp.ua.avertooserver.common.exception.InvalidCredentialsException;
import pp.ua.avertooserver.user.User;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateToken() {

        byte[] randomBytes = new byte[32];

        secureRandom.nextBytes(randomBytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }

    private String hashToken(String token) {

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    token.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {

            throw new IllegalStateException(
                    "SHA-256 algorithm is not available",
                    exception
            );
        }
    }

    public String createRefreshToken(User user) {

        refreshTokenRepository.deleteByUser(user);
        refreshTokenRepository.flush();

        String token = generateToken();

        String tokenHash = hashToken(token);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(OffsetDateTime.now().plusDays(30))
                .build();

        refreshTokenRepository.save(refreshToken);

        return token;
    }

    public RefreshToken validateRefreshToken(String token) {

        String tokenHash = hashToken(token);

        RefreshToken refreshToken =
                refreshTokenRepository.findByTokenHash(tokenHash)
                        .orElseThrow(
                                InvalidCredentialsException::new
                        );

        if (refreshToken.getExpiresAt()
                .isBefore(OffsetDateTime.now())) {

            throw new InvalidCredentialsException();
        }

        return refreshToken;
    }
}