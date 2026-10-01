package app.psst.chat.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ParticipantRepository extends JpaRepository<ParticipantEntity, Long> {
    boolean existsByConversation_IdAndUser_Id(Long conversationId, Long userId);
    List<ParticipantEntity> findByConversation_Id(Long conversationId);
    List<ParticipantEntity> findByConversation_IdAndUser_IdNot(Long conversationId, Long userId);
    List<ParticipantEntity> findByUser_Id(Long userId);
}
