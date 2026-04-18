package interview.guide.modules.interview.repository;

import interview.guide.modules.interview.model.InterviewAnswerEntity;
import interview.guide.modules.interview.model.InterviewSessionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface InterviewAnswerRepository {

    List<InterviewAnswerEntity> findBySessionIdOrderByQuestionIndex(@Param("sessionId") Long sessionId);

    List<InterviewAnswerEntity> findBySessionStringIdOrderByQuestionIndex(@Param("sessionId") String sessionId);

    InterviewAnswerEntity findBySessionStringIdAndQuestionIndexInternal(
        @Param("sessionId") String sessionId,
        @Param("questionIndex") Integer questionIndex
    );

    int insertEntity(@Param("entity") InterviewAnswerEntity entity);

    int updateEntity(@Param("entity") InterviewAnswerEntity entity);

    default List<InterviewAnswerEntity> findBySessionOrderByQuestionIndex(InterviewSessionEntity session) {
        return findBySessionIdOrderByQuestionIndex(session.getId());
    }

    default List<InterviewAnswerEntity> findBySession_SessionIdOrderByQuestionIndex(String sessionId) {
        return findBySessionStringIdOrderByQuestionIndex(sessionId);
    }

    default Optional<InterviewAnswerEntity> findBySession_SessionIdAndQuestionIndex(
        String sessionId,
        Integer questionIndex
    ) {
        return Optional.ofNullable(findBySessionStringIdAndQuestionIndexInternal(sessionId, questionIndex));
    }

    default InterviewAnswerEntity save(InterviewAnswerEntity entity) {
        if (entity.getId() == null) {
            insertEntity(entity);
        } else {
            updateEntity(entity);
        }
        return entity;
    }

    default List<InterviewAnswerEntity> saveAll(List<InterviewAnswerEntity> entities) {
        if (entities == null) {
            return List.of();
        }
        for (InterviewAnswerEntity entity : entities) {
            save(entity);
        }
        return entities;
    }
}
