package interview.guide.modules.knowledgebase.repository;

import interview.guide.modules.knowledgebase.model.KnowledgeBaseChunkEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KnowledgeBaseChunkRepository {

  List<KnowledgeBaseChunkEntity> findByDocumentIds(@Param("documentIds") List<String> documentIds);

  List<KnowledgeBaseChunkEntity> findLightweightByKnowledgeBaseIds(@Param("knowledgeBaseIds") List<Long> knowledgeBaseIds);

  List<KnowledgeBaseChunkEntity> findTopByKnowledgeBaseId(
      @Param("knowledgeBaseId") Long knowledgeBaseId,
      @Param("limit") int limit
  );

  List<String> findDocumentIdsByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);

  int deleteByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);

  int batchInsert(@Param("chunks") List<KnowledgeBaseChunkEntity> chunks);
}
