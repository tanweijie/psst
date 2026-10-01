package app.psst.chat.persistence;

import jakarta.persistence.*;

@Entity
@Table(name = "conversation")
public class ConversationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public ConversationEntity() {}

    public Long getId() { return id; }
}
