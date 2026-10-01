package app.psst.chat.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "chat_message", indexes = @Index(columnList = "conversation_id, id"))
public class MessageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ConversationEntity conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private UserEntity sender;

    @Column(name = "body", nullable = false, length = 2000)
    private String text;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    protected MessageEntity() {}

    public MessageEntity(ConversationEntity conversation, UserEntity sender, String text) {
        this.conversation = conversation;
        this.sender = sender;
        this.text = text;
        this.sentAt = Instant.now();
    }

    public Long getId() { return id; }
    public ConversationEntity getConversation() { return conversation; }
    public UserEntity getSender() { return sender; }
    public String getText() { return text; }
    public Instant getSentAt() { return sentAt; }
}
