package pp.ua.avertooserver.security;

import org.springframework.data.jpa.repository.JpaRepository;
import pp.ua.avertooserver.user.User;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    Optional<RefreshToken> findByUser(User user);

    void deleteByUser(User user);
}