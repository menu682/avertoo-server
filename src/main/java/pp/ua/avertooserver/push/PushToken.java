package pp.ua.avertooserver.push;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/**
 * A device push token registered by a user.
 *
 * <p>A user may own several devices, therefore the token is stored
 * per user with a composite primary key {@code (userId, token)}.</p>
 *
 * <p>The token is an Expo push token obtained on the client via
 * {@code expo-notifications}; the backend relays wake-up commands
 * to it through the Expo Push Service.</p>
 */
@Entity
@Table(name = "device_push_tokens")
@IdClass(PushToken.PushTokenId.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PushToken {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "token", nullable = false)
    private String token;

    @Column(name = "platform", length = 20)
    private String platform;

    /**
     * Composite primary key of a push token.
     */
    public static class PushTokenId
            implements Serializable {

        private UUID userId;
        private String token;

        public PushTokenId() {
        }

        public PushTokenId(UUID userId, String token) {
            this.userId = userId;
            this.token = token;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }

            if (!(other instanceof PushTokenId that)) {
                return false;
            }

            return java.util.Objects.equals(userId, that.userId)
                    && java.util.Objects.equals(token, that.token);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(userId, token);
        }
    }
}
