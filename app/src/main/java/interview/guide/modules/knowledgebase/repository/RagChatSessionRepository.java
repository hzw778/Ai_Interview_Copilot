package interview.guide.modules.knowledgebase.repository;

import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.model.RagChatSessionEntity;
import interview.guide.modules.knowledgebase.model.RagChatSessionEntity.SessionStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Mapper
public interface RagChatSessionRepository {

    RagChatSessionEntity selectByIdInternal(@Param("id") Long id);

    List<RagChatSessionEntity> findByStatusOrderByUpdatedAtDesc(@Param("status") SessionStatus status);

    List<RagChatSessionEntity> findAllByOrderByUpdatedAtDesc();

    List<RagChatSessionEntity> findAllOrderByPinnedAndUpdatedAtDesc();

    List<RagChatSessionEntity> findByKnowledgeBaseIds(@Param("kbIds") List<Long> knowledgeBaseIds);

    List<KnowledgeBaseEntity> selectKnowledgeBasesBySessionId(@Param("sessionId") Long sessionId);

    int insertEntity(@Param("entity") RagChatSessionEntity entity);

    int updateEntity(@Param("entity") RagChatSessionEntity entity);

    int deleteById(@Param("id") Long id);

    int countByIdInternal(@Param("id") Long id);

    int deleteKnowledgeBaseRelations(@Param("sessionId") Long sessionId);

    int insertKnowledgeBaseRelation(
        @Param("sessionId") Long sessionId,
        @Param("knowledgeBaseId") Long knowledgeBaseId
    );

    default Optional<RagChatSessionEntity> findById(Long id) {
        return findByIdWithKnowledgeBases(id);
    }

    default Optional<RagChatSessionEntity> findByIdWithMessagesAndKnowledgeBases(Long id) {
        return findByIdWithKnowledgeBases(id);
    }

    default Optional<RagChatSessionEntity> findByIdWithKnowledgeBases(Long id) {
        RagChatSessionEntity session = selectByIdInternal(id);
        if (session == null) {
            return Optional.empty();
        }
        List<KnowledgeBaseEntity> knowledgeBases = selectKnowledgeBasesBySessionId(id);
        session.setKnowledgeBases(new java.util.LinkedHashSet<>(knowledgeBases));
        return Optional.of(session);
    }

    default RagChatSessionEntity save(RagChatSessionEntity entity) {
        if (entity.getId() == null) {
            insertEntity(entity);
        } else {
            updateEntity(entity);
            deleteKnowledgeBaseRelations(entity.getId());
        }
        if (entity.getKnowledgeBases() != null) {
            for (KnowledgeBaseEntity knowledgeBase : entity.getKnowledgeBases()) {
                if (knowledgeBase != null && knowledgeBase.getId() != null) {
                    insertKnowledgeBaseRelation(entity.getId(), knowledgeBase.getId());
                }
            }
        }
        return entity;
    }

    default boolean existsById(Long id) {
        return countByIdInternal(id) > 0;
    }
}
