package interview.guide.modules.knowledgebase.repository;

import interview.guide.modules.knowledgebase.model.RagChatMessageEntity;
import interview.guide.modules.knowledgebase.model.RagChatMessageEntity.MessageType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface RagChatMessageRepository {

    RagChatMessageEntity selectByIdInternal(@Param("id") Long id);

    List<RagChatMessageEntity> findBySessionIdOrderByMessageOrderAsc(@Param("sessionId") Long sessionId);

    RagChatMessageEntity findTopBySessionIdOrderByMessageOrderDescInternal(@Param("sessionId") Long sessionId);

    Long findSessionIdByMessageId(@Param("id") Long id);

    long countBySessionId(@Param("sessionId") Long sessionId);

    List<RagChatMessageEntity> findBySessionIdAndCompletedFalse(@Param("sessionId") Long sessionId);

    int deleteBySessionId(@Param("sessionId") Long sessionId);

    long countByType(@Param("type") MessageType type);

    int insertEntity(@Param("entity") RagChatMessageEntity entity);

    int updateEntity(@Param("entity") RagChatMessageEntity entity);

    default Optional<RagChatMessageEntity> findById(Long id) {
        return Optional.ofNullable(selectByIdInternal(id));
    }

    default Optional<RagChatMessageEntity> findTopBySessionIdOrderByMessageOrderDesc(Long sessionId) {
        return Optional.ofNullable(findTopBySessionIdOrderByMessageOrderDescInternal(sessionId));
    }

    default RagChatMessageEntity save(RagChatMessageEntity entity) {
        if (entity.getId() == null) {
            insertEntity(entity);
        } else {
            updateEntity(entity);
        }
        return entity;
    }
}
