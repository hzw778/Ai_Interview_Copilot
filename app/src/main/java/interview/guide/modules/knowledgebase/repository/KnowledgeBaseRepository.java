package interview.guide.modules.knowledgebase.repository;

import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.model.VectorStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface KnowledgeBaseRepository {

    KnowledgeBaseEntity selectByIdInternal(@Param("id") Long id);

    List<KnowledgeBaseEntity> findAllById(@Param("ids") List<Long> ids);

    KnowledgeBaseEntity selectByFileHashInternal(@Param("fileHash") String fileHash);

    int countByIdInternal(@Param("id") Long id);

    int countByFileHashInternal(@Param("fileHash") String fileHash);

    List<KnowledgeBaseEntity> findAllByOrderByUploadedAtDesc();

    List<String> findAllCategories();

    List<KnowledgeBaseEntity> findByCategoryOrderByUploadedAtDesc(@Param("category") String category);

    List<KnowledgeBaseEntity> findByCategoryIsNullOrderByUploadedAtDesc();

    List<KnowledgeBaseEntity> searchByKeyword(@Param("keyword") String keyword);

    List<KnowledgeBaseEntity> findAllByOrderByFileSizeDesc();

    List<KnowledgeBaseEntity> findAllByOrderByAccessCountDesc();

    List<KnowledgeBaseEntity> findAllByOrderByQuestionCountDesc();

    int incrementQuestionCountBatch(@Param("ids") List<Long> ids);

    long sumQuestionCount();

    long sumAccessCount();

    long count();

    long countByVectorStatus(@Param("vectorStatus") VectorStatus vectorStatus);

    List<KnowledgeBaseEntity> findByVectorStatusOrderByUploadedAtDesc(@Param("vectorStatus") VectorStatus vectorStatus);

    int insertEntity(@Param("entity") KnowledgeBaseEntity entity);

    int updateEntity(@Param("entity") KnowledgeBaseEntity entity);

    int deleteById(@Param("id") Long id);

    default Optional<KnowledgeBaseEntity> findById(Long id) {
        return Optional.ofNullable(selectByIdInternal(id));
    }

    default Optional<KnowledgeBaseEntity> findByFileHash(String fileHash) {
        return Optional.ofNullable(selectByFileHashInternal(fileHash));
    }

    default boolean existsById(Long id) {
        return countByIdInternal(id) > 0;
    }

    default boolean existsByFileHash(String fileHash) {
        return countByFileHashInternal(fileHash) > 0;
    }

    default KnowledgeBaseEntity save(KnowledgeBaseEntity entity) {
        if (entity.getId() == null) {
            insertEntity(entity);
        } else {
            updateEntity(entity);
        }
        return entity;
    }
}
