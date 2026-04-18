package interview.guide.modules.resume.repository;

import interview.guide.modules.resume.model.ResumeAnalysisEntity;
import interview.guide.modules.resume.model.ResumeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ResumeAnalysisRepository {

    ResumeAnalysisEntity selectByIdInternal(@Param("id") Long id);

    ResumeAnalysisEntity selectLatestByResumeIdInternal(@Param("resumeId") Long resumeId);

    List<ResumeAnalysisEntity> selectByResumeIdInternal(@Param("resumeId") Long resumeId);

    int insertEntity(@Param("entity") ResumeAnalysisEntity entity);

    int updateEntity(@Param("entity") ResumeAnalysisEntity entity);

    int deleteByIdInternal(@Param("id") Long id);

    default ResumeAnalysisEntity save(ResumeAnalysisEntity entity) {
        if (entity.getId() == null) {
            insertEntity(entity);
        } else {
            updateEntity(entity);
        }
        return entity;
    }

    default List<ResumeAnalysisEntity> findByResumeOrderByAnalyzedAtDesc(ResumeEntity resume) {
        return findByResumeIdOrderByAnalyzedAtDesc(resume.getId());
    }

    default ResumeAnalysisEntity findFirstByResumeIdOrderByAnalyzedAtDesc(Long resumeId) {
        return selectLatestByResumeIdInternal(resumeId);
    }

    default List<ResumeAnalysisEntity> findByResumeIdOrderByAnalyzedAtDesc(Long resumeId) {
        return selectByResumeIdInternal(resumeId);
    }

    default void deleteAll(List<ResumeAnalysisEntity> entities) {
        if (entities == null) {
            return;
        }
        for (ResumeAnalysisEntity entity : entities) {
            if (entity != null && entity.getId() != null) {
                deleteByIdInternal(entity.getId());
            }
        }
    }
}
