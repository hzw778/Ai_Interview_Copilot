package interview.guide.modules.knowledgebase.service;

import interview.guide.infrastructure.file.TextCleaningService;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.model.VectorStatus;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseChunkRepository;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeBaseVectorRepairRunner implements ApplicationRunner {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeBaseChunkRepository chunkRepository;
    private final KnowledgeBaseParseService parseService;
    private final KnowledgeBaseVectorService vectorService;
    private final KnowledgeBaseVectorProperties vectorProperties;
    private final TextCleaningService textCleaningService;

    @Override
    public void run(ApplicationArguments args) {
        if (!vectorProperties.isRepairMissingChunkCountOnStartup()) {
            return;
        }

        List<KnowledgeBaseEntity> candidates = knowledgeBaseRepository
            .findByVectorStatusOrderByUploadedAtDesc(VectorStatus.COMPLETED)
            .stream()
            .filter(kb -> kb.getChunkCount() == null
                || kb.getChunkCount() <= 0
                || chunkRepository.findDocumentIdsByKnowledgeBaseId(kb.getId()).isEmpty()
                || needsTextNormalizationRepair(kb.getId()))
            .toList();

        if (candidates.isEmpty()) {
            return;
        }

        log.warn("检测到 {} 个知识库需要修复 Redis/MySQL 分片或文本规范化，开始自动重建索引", candidates.size());

        for (KnowledgeBaseEntity knowledgeBase : candidates) {
            try {
                String content = parseService.downloadAndParseContent(
                    knowledgeBase.getStorageKey(),
                    knowledgeBase.getOriginalFilename()
                );
                vectorService.vectorizeAndStore(knowledgeBase.getId(), content);
                log.info("知识库分片元数据修复完成: kbId={}, name={}",
                    knowledgeBase.getId(), knowledgeBase.getName());
            } catch (Exception e) {
                log.error("知识库分片元数据修复失败: kbId={}, name={}",
                    knowledgeBase.getId(), knowledgeBase.getName(), e);
            }
        }
    }

    private boolean needsTextNormalizationRepair(Long knowledgeBaseId) {
        return chunkRepository.findTopByKnowledgeBaseId(knowledgeBaseId, 3).stream()
            .map(chunk -> chunk.getContent() == null ? "" : chunk.getContent())
            .anyMatch(content -> !textCleaningService.normalizeUnicode(content).equals(content));
    }
}
