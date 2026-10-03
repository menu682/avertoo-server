package pp.ua.avertooserver.privatechat;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import pp.ua.avertooserver.user.User;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "private_chats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrivateChat {

    @Id
    @Generated
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user1_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_private_chats_user1"
            )
    )
    private User user1;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user2_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_private_chats_user2"
            )
    )
    private User user2;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PrivateChatStatus status;

    @Generated
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Builder
    private PrivateChat(
            User user1,
            User user2,
            PrivateChatStatus status
    ) {
        this.user1 = user1;
        this.user2 = user2;
        this.status = status;
    }

    public void activate() {
        this.status = PrivateChatStatus.ACTIVE;
    }
}