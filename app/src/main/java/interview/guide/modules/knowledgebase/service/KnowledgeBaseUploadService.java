package interview.guide.modules.knowledgebase.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.infrastructure.file.FileHashService;
import interview.guide.infrastructure.file.FileStorageService;
import interview.guide.infrastructure.file.FileValidationService;
import interview.guide.modules.knowledgebase.listener.VectorizeStreamProducer;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.model.VectorStatus;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseUploadService {

  private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;

  private final KnowledgeBaseParseService parseService;
  private final KnowledgeBasePersistenceService persistenceService;
  private final FileStorageService storageService;
  private final KnowledgeBaseRepository knowledgeBaseRepository;
  private final FileValidationService fileValidationService;
  private final FileHashService fileHashService;
  private final VectorizeStreamProducer vectorizeStreamProducer;
  private final KnowledgeBaseContentCacheService contentCacheService;

  public Map<String, Object> uploadKnowledgeBase(MultipartFile file, String name, String category) {
    fileValidationService.validateFile(file, MAX_FILE_SIZE, "知识库");

    String fileName = file.getOriginalFilename();
    log.info("收到知识库上传请求: fileName={}, size={}, category={}", fileName, file.getSize(), category);

    String contentType = parseService.detectContentType(file);
    validateContentType(contentType, fileName);

    String fileHash = fileHashService.calculateHash(file);
    Optional<KnowledgeBaseEntity> existingKnowledgeBase = knowledgeBaseRepository.findByFileHash(fileHash);
    if (existingKnowledgeBase.isPresent()) {
      log.info("检测到重复知识库上传: hash={}, kbId={}", fileHash, existingKnowledgeBase.get().getId());
      return persistenceService.handleDuplicateKnowledgeBase(existingKnowledgeBase.get(), fileHash);
    }

    String content = parseService.parseContent(file);
    if (content == null || content.trim().isEmpty()) {
      throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_PARSE_FAILED, "无法从文件中提取有效文本内容");
    }

    String fileKey = storageService.uploadKnowledgeBase(file);
    String fileUrl = storageService.getFileUrl(fileKey);
    KnowledgeBaseEntity savedKnowledgeBase = persistenceService.saveKnowledgeBase(
        file,
        name,
        category,
        fileKey,
        fileUrl,
        fileHash
    );

    contentCacheService.cacheParsedContent(savedKnowledgeBase.getId(), content);
    vectorizeStreamProducer.sendVectorizeTask(savedKnowledgeBase.getId());

    log.info("知识库上传完成，已进入异步向量化队列: kbId={}, name={}",
        savedKnowledgeBase.getId(), savedKnowledgeBase.getName());

    return Map.of(
        "knowledgeBase", Map.of(
            "id", savedKnowledgeBase.getId(),
            "name", savedKnowledgeBase.getName(),
            "category", savedKnowledgeBase.getCategory() != null ? savedKnowledgeBase.getCategory() : "",
            "fileSize", savedKnowledgeBase.getFileSize(),
            "contentLength", content.length(),
            "vectorStatus", VectorStatus.PENDING.name()
        ),
        "storage", Map.of(
            "fileKey", fileKey,
            "fileUrl", fileUrl
        ),
        "duplicate", false
    );
  }

  public void revectorize(Long kbId) {
    KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepository.findById(kbId)
        .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库不存在"));

    String content = parseService.downloadAndParseContent(
        knowledgeBase.getStorageKey(),
        knowledgeBase.getOriginalFilename()
    );
    if (content == null || content.trim().isEmpty()) {
      throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_PARSE_FAILED, "无法从知识库文件中提取文本内容");
    }

    contentCacheService.cacheParsedContent(kbId, content);
    persistenceService.updateVectorStatusToPending(kbId);
    vectorizeStreamProducer.sendVectorizeTask(kbId);
    log.info("知识库已重新入队向量化: kbId={}", kbId);
  }

  private void validateContentType(String contentType, String fileName) {
    fileValidationService.validateContentType(
        contentType,
        fileName,
        fileValidationService::isKnowledgeBaseMimeType,
        fileValidationService::isMarkdownExtension,
        "不支持的知识库文件类型: " + contentType
    );
  }
}
