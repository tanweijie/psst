package app.psst.chat.service;

import app.psst.chat.persistence.*;
import app.psst.chat.record.ConversationRecord;
import app.psst.chat.record.MessageRecord;
import app.psst.chat.record.UserRecord;
import com.vaadin.flow.component.UI;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Service
public class ChatService {
    public static final int MAX_MESSAGE_LENGTH = 2000;
    public static final int HISTORY_LIMIT = 100;

    private final UserRepository users;
    private final ConversationRepository conversations;
    private final ParticipantRepository participants;
    private final MessageRepository messages;
    private final EntityManager entityManager;
    private final ConcurrentHashMap<Long, Set<Subscription>> listeners = new ConcurrentHashMap<>();

    public ChatService(UserRepository users, ConversationRepository conversations,
                       ParticipantRepository participants, MessageRepository messages,
                       EntityManager entityManager) {
        this.users = users;
        this.conversations = conversations;
        this.participants = participants;
        this.messages = messages;
        this.entityManager = entityManager;
    }

    @Transactional
    public UserRecord findOrCreateUser(String name) {
        Optional<UserEntity> existing = users.findByUsername(name);
        if (existing.isPresent()) {
            return user(existing.get());
        }
        try {
            return user(users.saveAndFlush(new UserEntity(name)));
        } catch (DataIntegrityViolationException racedWithAnotherLogin) {
            return user(users.findByUsername(name).orElseThrow());
        }
    }

    @Transactional(readOnly = true)
    public UserRecord currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new AccessDeniedException("Choose a username first");
        }
        return users.findByUsername(authentication.getName()).map(this::user)
                .orElseThrow(() -> new AccessDeniedException("Unknown user"));
    }

    @Transactional(readOnly = true)
    public List<UserRecord> contacts() {
        return users.findByIdNotOrderByUsername(currentUser().id()).stream().map(this::user).toList();
    }

    @Transactional(readOnly = true)
    public List<ConversationRecord> conversations() {
        long me = currentUser().id();
        return participants.findByUser_Id(me).stream()
                .map(ParticipantEntity::getConversation)
                .map(conversation -> {
                    UserEntity other = participants.findByConversation_IdAndUser_IdNot(
                            conversation.getId(), me).getFirst().getUser();
                    return new ConversationRecord(conversation.getId(), user(other));
                })
                .sorted(Comparator.comparing(c -> c.otherUser().username()))
                .toList();
    }

    @Transactional
    public long createConversation(long otherUserId) {
        long me = currentUser().id();
        if (otherUserId == me) {
            throw new IllegalArgumentException("Choose another person");
        }
        if (!users.existsById(otherUserId)) {
            throw new IllegalArgumentException("Contact not found");
        }
        long lowId = Math.min(me, otherUserId);
        long highId = Math.max(me, otherUserId);
        // Lock one user consistently so simultaneous requests for the same pair serialize.
        UserEntity low = entityManager.find(UserEntity.class, lowId, LockModeType.PESSIMISTIC_WRITE);
        UserEntity high = users.findById(highId).orElseThrow();
        Optional<ConversationEntity> existing = participants.findByUser_Id(lowId).stream()
                .map(ParticipantEntity::getConversation)
                .filter(conversation -> participants.existsByConversation_IdAndUser_Id(
                        conversation.getId(), highId))
                .findFirst();
        if (existing.isPresent()) {
            return existing.get().getId();
        }
        ConversationEntity conversation = conversations.save(new ConversationEntity());
        participants.save(new ParticipantEntity(conversation, low));
        participants.save(new ParticipantEntity(conversation, high));
        return conversation.getId();
    }

    @Transactional(readOnly = true)
    public List<MessageRecord> history(long conversationId) {
        requireUserParticipation(conversationId);
        List<MessageRecord> result = new ArrayList<>(messages.findByConversation_Id(
                conversationId, PageRequest.of(0, HISTORY_LIMIT, Sort.by(Sort.Direction.DESC, "id")))
                .stream().map(this::message).toList());
        Collections.reverse(result);
        return result;
    }

    @Transactional
    public MessageRecord send(long conversationId, String text) {
        UserRecord me = requireUserParticipation(conversationId);
        if (text == null || text.isBlank() || text.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Message must contain 1 to 2000 characters");
        }
        MessageEntity saved = messages.save(new MessageEntity(
                conversations.getReferenceById(conversationId),
                users.getReferenceById(me.id()), text));
        MessageRecord message = message(saved);
        List<Long> members = participants.findByConversation_Id(conversationId).stream()
                .map(member -> member.getUser().getId()).toList();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish(message, members);
            }
        });
        return message;
    }

    public boolean isConnected(long userId) {
        return listeners.containsKey(userId);
    }

    public Set<Long> connectedUserIds() {
        return Set.copyOf(listeners.keySet());
    }

    public AutoCloseable subscribe(long userId, UI ui, Consumer<MessageRecord> onMessage, Runnable onPresence) {
        Subscription subscription = new Subscription(ui, onMessage, onPresence);
        listeners.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(subscription);
        announcePresence();
        return () -> {
            Set<Subscription> set = listeners.get(userId);
            if (set != null) {
                set.remove(subscription);
                if (set.isEmpty()) {
                    listeners.remove(userId, set);
                }
            }
            announcePresence();
        };
    }

    private UserRecord requireUserParticipation(long conversationId) {
        UserRecord me = currentUser();
        if (!participants.existsByConversation_IdAndUser_Id(conversationId, me.id())) {
            throw new AccessDeniedException("This conversation is not yours");
        }
        return me;
    }

    private void publish(MessageRecord message, List<Long> memberIds) {
        for (long userId : memberIds) {
            for (Subscription subscription : listeners.getOrDefault(userId, Set.of())) {
                subscription.access(() -> subscription.onMessage.accept(message));
            }
        }
    }

    private void announcePresence() {
        listeners.values().forEach(set ->
                set.forEach(subscription -> subscription.access(subscription.onPresence)));
    }

    private UserRecord user(UserEntity entity) {
        return new UserRecord(entity.getId(), entity.getUsername());
    }

    private MessageRecord message(MessageEntity entity) {
        return new MessageRecord(entity.getId(), entity.getConversation().getId(),
                entity.getSender().getId(), entity.getSender().getUsername(),
                entity.getText(), entity.getSentAt());
    }

    private record Subscription(UI ui, Consumer<MessageRecord> onMessage, Runnable onPresence) {
        void access(Runnable action) {
            if (ui.isAttached()) {
                ui.access(() -> {
                    if (ui.isAttached()) {
                        action.run();
                    }
                });
            }
        }
    }
}
