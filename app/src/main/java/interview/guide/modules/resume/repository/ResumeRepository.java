package interview.guide.modules.resume.repository;

import interview.guide.modules.resume.model.ResumeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ResumeRepository {

    ResumeEntity selectByIdInternal(@Param("id") Long id);

    ResumeEntity selectByFileHashInternal(@Param("fileHash") String fileHash);

    int countByIdInternal(@Param("id") Long id);

    int countByFileHashInternal(@Param("fileHash") String fileHash);

    List<ResumeEntity> findAll();

    int insertEntity(@Param("entity") ResumeEntity entity);

    int updateEntity(@Param("entity") ResumeEntity entity);

    int deleteByIdInternal(@Param("id") Long id);

    default Optional<ResumeEntity> findById(Long id) {
        return Optional.ofNullable(selectByIdInternal(id));
    }

    default Optional<ResumeEntity> findByFileHash(String fileHash) {
        return Optional.ofNullable(selectByFileHashInternal(fileHash));
    }

    default boolean existsById(Long id) {
        return countByIdInternal(id) > 0;
    }

    default boolean existsByFileHash(String fileHash) {
        return countByFileHashInternal(fileHash) > 0;
    }

    default ResumeEntity save(ResumeEntity entity) {
        if (entity.getId() == null) {
            insertEntity(entity);
        } else {
            updateEntity(entity);
        }
        return entity;
    }

    default void delete(ResumeEntity entity) {
        if (entity != null && entity.getId() != null) {
            deleteByIdInternal(entity.getId());
        }
    }
}
