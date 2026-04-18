package interview.guide.modules.knowledgebase.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseChunkEntity;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseChunkRepository;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseChunkPersistenceService {

  private final KnowledgeBaseChunkRepository chunkRepository;
  private final KnowledgeBaseRepository knowledgeBaseRepository;

  @Transactional(rollbackFor = Exception.class)
  public void replaceChunks(Long knowledgeBaseId, List<KnowledgeBaseChunkEntity> chunks) {
    chunkRepository.deleteByKnowledgeBaseId(knowledgeBaseId);
    if (chunks != null && !chunks.isEmpty()) {
      chunkRepository.batchInsert(chunks);
    }

    var knowledgeBase = knowledgeBaseRepository.findById(knowledgeBaseId)
        .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库不存在"));
    knowledgeBase.setChunkCount(chunks == null ? 0 : chunks.size());
    knowledgeBaseRepository.save(knowledgeBase);
    log.info("已持久化知识库分片: kbId={}, chunkCount={}", knowledgeBaseId, knowledgeBase.getChunkCount());
  }
}
