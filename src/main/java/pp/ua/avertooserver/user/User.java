package pp.ua.avertooserver.user;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import org.hibernate.annotations.Generated;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @Generated
    @Column(name = "id")
    private UUID id;

    @Generated
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Generated
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Builder
    private User(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

}