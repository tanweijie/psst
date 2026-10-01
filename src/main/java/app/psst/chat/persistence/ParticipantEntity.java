package app.psst.chat.persistence;

import jakarta.persistence.*;

@Entity
@Table(name = "participant",
        uniqueConstraints = @UniqueConstraint(columnNames = {"conversation_id", "user_id"}))
public class ParticipantEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ConversationEntity conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    protected ParticipantEntity() {}

    public ParticipantEntity(ConversationEntity conversation, UserEntity user) {
        this.conversation = conversation;
        this.user = user;
    }

    public Long getId() { return id; }
    public ConversationEntity getConversation() { return conversation; }
    public UserEntity getUser() { return user; }
}
