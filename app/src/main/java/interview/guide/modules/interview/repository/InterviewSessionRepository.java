package interview.guide.modules.interview.repository;

import interview.guide.modules.interview.model.InterviewSessionEntity;
import interview.guide.modules.interview.model.InterviewSessionEntity.SessionStatus;
import interview.guide.modules.resume.model.ResumeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface InterviewSessionRepository {

    InterviewSessionEntity selectBySessionIdInternal(@Param("sessionId") String sessionId);

    List<InterviewSessionEntity> selectByResumeIdOrderByCreatedAtDescInternal(@Param("resumeId") Long resumeId);

    List<InterviewSessionEntity> selectTop10ByResumeIdOrderByCreatedAtDescInternal(@Param("resumeId") Long resumeId);

    InterviewSessionEntity selectFirstByResumeIdAndStatusInOrderByCreatedAtDescInternal(
        @Param("resumeId") Long resumeId,
        @Param("statuses") List<SessionStatus> statuses
    );

    InterviewSessionEntity selectByResumeIdAndStatusInInternal(
        @Param("resumeId") Long resumeId,
        @Param("statuses") List<SessionStatus> statuses
    );

    List<InterviewSessionEntity> findAllByOrderByCreatedAtDesc();

    List<InterviewSessionEntity> findTop10BySkillIdOrderByCreatedAtDesc(@Param("skillId") String skillId);

    List<InterviewSessionEntity> findTop10ByResumeIdAndSkillIdOrderByCreatedAtDesc(
        @Param("resumeId") Long resumeId,
        @Param("skillId") String skillId
    );

    int insertEntity(@Param("entity") InterviewSessionEntity entity);

    int updateEntity(@Param("entity") InterviewSessionEntity entity);

    int deleteByIdInternal(@Param("id") Long id);

    default Optional<InterviewSessionEntity> findBySessionId(String sessionId) {
        return Optional.ofNullable(selectBySessionIdInternal(sessionId));
    }

    default Optional<InterviewSessionEntity> findBySessionIdWithResume(String sessionId) {
        return findBySessionId(sessionId);
    }

    default List<InterviewSessionEntity> findByResumeOrderByCreatedAtDesc(ResumeEntity resume) {
        return findByResumeIdOrderByCreatedAtDesc(resume.getId());
    }

    default List<InterviewSessionEntity> findByResumeIdOrderByCreatedAtDesc(Long resumeId) {
        return selectByResumeIdOrderByCreatedAtDescInternal(resumeId);
    }

    default List<InterviewSessionEntity> findTop10ByResumeIdOrderByCreatedAtDesc(Long resumeId) {
        return selectTop10ByResumeIdOrderByCreatedAtDescInternal(resumeId);
    }

    default Optional<InterviewSessionEntity> findFirstByResumeIdAndStatusInOrderByCreatedAtDesc(
        Long resumeId,
        List<SessionStatus> statuses
    ) {
        return Optional.ofNullable(selectFirstByResumeIdAndStatusInOrderByCreatedAtDescInternal(resumeId, statuses));
    }

    default Optional<InterviewSessionEntity> findByResumeIdAndStatusIn(
        Long resumeId,
        List<SessionStatus> statuses
    ) {
        return Optional.ofNullable(selectByResumeIdAndStatusInInternal(resumeId, statuses));
    }

    default InterviewSessionEntity save(InterviewSessionEntity entity) {
        if (entity.getId() == null) {
            insertEntity(entity);
        } else {
            updateEntity(entity);
        }
        return entity;
    }

    default void delete(InterviewSessionEntity entity) {
        if (entity != null && entity.getId() != null) {
            deleteByIdInternal(entity.getId());
        }
    }

    default void deleteAll(List<InterviewSessionEntity> entities) {
        if (entities == null) {
            return;
        }
        for (InterviewSessionEntity entity : entities) {
            delete(entity);
        }
    }
}
