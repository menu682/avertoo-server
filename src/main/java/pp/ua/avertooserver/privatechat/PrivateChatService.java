package pp.ua.avertooserver.privatechat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pp.ua.avertooserver.common.exception.PrivateChatAccessDeniedException;
import pp.ua.avertooserver.common.exception.PrivateChatNotFoundException;
import pp.ua.avertooserver.common.exception.UserNotFoundException;
import pp.ua.avertooserver.privatechat.dto.PrivateChatCreateResponse;
import pp.ua.avertooserver.privatechat.dto.PrivateChatCreateResult;
import pp.ua.avertooserver.privatechat.dto.PrivateChatListItemResponse;
import pp.ua.avertooserver.privatechat.dto.PrivateChatResponse;
import pp.ua.avertooserver.user.User;
import pp.ua.avertooserver.user.UserRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PrivateChatService {

    private final PrivateChatRepository privateChatRepository;
    private final UserRepository userRepository;

    @Transactional
    public PrivateChatCreateResponse createChat(
            Long user1UserId,
            Long user2UserId
    ) {
        if (isSelfChat(user1UserId, user2UserId)) {
            return new PrivateChatCreateResponse(
                    null,
                    PrivateChatCreateResult.SELF_CHAT
            );
        }

        User user1 = getUser(user1UserId);
        User user2 = getUser(user2UserId);

        PrivateChat privateChat = findChat(
                user1UserId,
                user2UserId
        );

        if (privateChat == null) {
            privateChat = createPendingChat(user1, user2);

            return new PrivateChatCreateResponse(
                    privateChat.getId(),
                    PrivateChatCreateResult.REQUEST_CREATED
            );
        }

        return handleExistingChat(
                privateChat,
                user1UserId
        );
    }

    @Transactional(readOnly = true)
    public List<PrivateChatResponse> getActiveChats(Long userId) {
        return privateChatRepository
                .findActiveChatsByUserId(userId)
                .stream()
                .map(privateChat -> new PrivateChatResponse(
                        privateChat.getId(),
                        privateChat.getUser1().getUserId(),
                        privateChat.getUser2().getUserId()
                ))
                .toList();
    }

    /**
     * Returns all chats of the user regardless of their status.
     *
     * <p>The client uses this list to synchronize its local chat
     * storage when the chat list screen is opened. The returned
     * list contains both PENDING and ACTIVE chats ordered by
     * creation time (newest first).</p>
     */
    @Transactional(readOnly = true)
    public List<PrivateChatListItemResponse> getChatsByUserId(
            Long userId
    ) {
        return privateChatRepository
                .findChatsByUserId(userId)
                .stream()
                .map(privateChat -> {

                    Long otherUserId =
                            privateChat.getUser1().getUserId().equals(userId)
                                    ? privateChat.getUser2().getUserId()
                                    : privateChat.getUser1().getUserId();

                    return new PrivateChatListItemResponse(
                            privateChat.getId(),
                            otherUserId,
                            privateChat.getStatus(),
                            privateChat.getCreatedAt()
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public Long getOtherUserId(
            UUID chatId,
            Long userId
    ) {
        PrivateChat privateChat =
                privateChatRepository.findById(chatId)
                        .orElseThrow(
                                () -> new PrivateChatNotFoundException(chatId)
                        );

        Long user1UserId =
                privateChat.getUser1().getUserId();

        Long user2UserId =
                privateChat.getUser2().getUserId();

        if (!user1UserId.equals(userId)
                && !user2UserId.equals(userId)) {

            throw new PrivateChatAccessDeniedException(
                    chatId
            );
        }

        return user1UserId.equals(userId)
                ? user2UserId
                : user1UserId;
    }

    @Transactional
    public Long deleteChat(
            UUID chatId,
            Long userId
    ) {
        PrivateChat privateChat = privateChatRepository.findById(chatId)
                .orElseThrow(() -> new PrivateChatNotFoundException(chatId));

        Long user1UserId = privateChat.getUser1().getUserId();
        Long user2UserId = privateChat.getUser2().getUserId();

        if (!user1UserId.equals(userId)
                && !user2UserId.equals(userId)) {
            throw new PrivateChatAccessDeniedException(chatId);
        }

        Long otherUserId = user1UserId.equals(userId)
                ? user2UserId
                : user1UserId;

        privateChatRepository.delete(privateChat);

        return otherUserId;
    }

    private boolean isSelfChat(
            Long user1UserId,
            Long user2UserId
    ) {
        return user1UserId.equals(user2UserId);
    }

    private User getUser(Long userId) {
        return userRepository.findByUserId(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    private PrivateChat findChat(
            Long user1UserId,
            Long user2UserId
    ) {
        return privateChatRepository
                .findByUserIds(user1UserId, user2UserId)
                .orElse(null);
    }

    private PrivateChat createPendingChat(
            User user1,
            User user2
    ) {
        PrivateChat privateChat = PrivateChat.builder()
                .user1(user1)
                .user2(user2)
                .status(PrivateChatStatus.PENDING)
                .build();

        return privateChatRepository.save(privateChat);
    }

    private PrivateChatCreateResponse handleExistingChat(
            PrivateChat privateChat,
            Long user1UserId
    ) {
        if (isChatActive(privateChat)) {
            return new PrivateChatCreateResponse(
                    privateChat.getId(),
                    PrivateChatCreateResult.CHAT_ALREADY_ACTIVE
            );
        }

        if (isOriginalRequester(privateChat, user1UserId)) {
            return new PrivateChatCreateResponse(
                    privateChat.getId(),
                    PrivateChatCreateResult.REQUEST_ALREADY_PENDING
            );
        }

        return activateChat(privateChat);
    }

    private boolean isChatActive(PrivateChat privateChat) {
        return privateChat.getStatus() == PrivateChatStatus.ACTIVE;
    }

    private boolean isOriginalRequester(
            PrivateChat privateChat,
            Long user1UserId
    ) {
        return privateChat.getUser1().getUserId().equals(user1UserId);
    }

    private PrivateChatCreateResponse activateChat(
            PrivateChat privateChat
    ) {
        privateChat.activate();

        return new PrivateChatCreateResponse(
                privateChat.getId(),
                PrivateChatCreateResult.CHAT_ACTIVATED
        );
    }
}