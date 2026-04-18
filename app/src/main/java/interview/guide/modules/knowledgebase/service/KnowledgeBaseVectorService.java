package interview.guide.modules.knowledgebase.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.infrastructure.file.TextCleaningService;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseChunkEntity;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseChunkRepository;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import interview.guide.modules.knowledgebase.repository.VectorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KnowledgeBaseVectorService {

  private static final int MAX_BATCH_SIZE = 10;
  private static final Pattern MARKDOWN_HEADING_PATTERN = Pattern.compile("(?m)^#{1,6}\\s+.+$");
  private static final int MAX_SECTION_LENGTH = 2400;

  private final EmbeddingModel embeddingModel;
  private final VectorStore vectorStore;
  private final VectorRepository vectorRepository;
  private final KnowledgeBaseRepository knowledgeBaseRepository;
  private final KnowledgeBaseChunkRepository chunkRepository;
  private final KnowledgeBaseChunkPersistenceService chunkPersistenceService;
  private final KnowledgeBaseContentCacheService contentCacheService;
  private final KnowledgeBaseParseService parseService;
  private final TextCleaningService textCleaningService;
  private final ObjectMapper objectMapper;
  private final TextSplitter textSplitter;

  public KnowledgeBaseVectorService(
      EmbeddingModel embeddingModel,
      VectorStore vectorStore,
      VectorRepository vectorRepository,
      KnowledgeBaseRepository knowledgeBaseRepository,
      KnowledgeBaseChunkRepository chunkRepository,
      KnowledgeBaseChunkPersistenceService chunkPersistenceService,
      KnowledgeBaseContentCacheService contentCacheService,
      KnowledgeBaseParseService parseService,
      TextCleaningService textCleaningService,
      ObjectMapper objectMapper,
      KnowledgeBaseVectorProperties vectorProperties
  ) {
    this.embeddingModel = embeddingModel;
    this.vectorStore = vectorStore;
    this.vectorRepository = vectorRepository;
    this.knowledgeBaseRepository = knowledgeBaseRepository;
    this.chunkRepository = chunkRepository;
    this.chunkPersistenceService = chunkPersistenceService;
    this.contentCacheService = contentCacheService;
    this.parseService = parseService;
    this.textCleaningService = textCleaningService;
    this.objectMapper = objectMapper;
    this.textSplitter = TokenTextSplitter.builder()
        .withChunkSize(vectorProperties.getChunkSize())
        .withMinChunkSizeChars(vectorProperties.getMinChunkSizeChars())
        .withMinChunkLengthToEmbed(vectorProperties.getMinChunkLengthToEmbed())
        .withMaxNumChunks(vectorProperties.getMaxNumChunks())
        .withKeepSeparator(true)
        .build();
  }

  public void vectorizeKnowledgeBase(Long knowledgeBaseId) {
    KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepository.findById(knowledgeBaseId)
        .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库不存在"));

    String content = contentCacheService.getParsedContent(knowledgeBaseId);
    if (content == null || content.isBlank()) {
      content = parseService.downloadAndParseContent(
          knowledgeBase.getStorageKey(),
          knowledgeBase.getOriginalFilename()
      );
      contentCacheService.cacheParsedContent(knowledgeBaseId, content);
    }

    vectorizeAndStore(knowledgeBaseId, content);
  }

  public void vectorizeAndStore(Long knowledgeBaseId, String content) {
    Objects.requireNonNull(knowledgeBaseId, "knowledgeBaseId must not be null");
    Objects.requireNonNull(content, "content must not be null");

    KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepository.findById(knowledgeBaseId)
        .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库不存在"));
    String normalizedContent = textCleaningService.cleanText(content);
    contentCacheService.cacheParsedContent(knowledgeBaseId, normalizedContent);

    try {
      List<PreparedChunk> preparedChunks = buildChunks(
          knowledgeBaseId,
          knowledgeBase.getName(),
          knowledgeBase.getOriginalFilename(),
          normalizedContent
      );
      contentCacheService.cacheChunks(
          knowledgeBaseId,
          preparedChunks.stream().map(PreparedChunk::content).toList()
      );

      List<String> previousDocumentIds = chunkRepository.findDocumentIdsByKnowledgeBaseId(knowledgeBaseId);
      deleteExistingVectorData(knowledgeBaseId, previousDocumentIds);

      if (preparedChunks.isEmpty()) {
        chunkPersistenceService.replaceChunks(knowledgeBaseId, List.of());
        log.info("知识库内容为空，已清空分片和索引: kbId={}", knowledgeBaseId);
        return;
      }

      List<KnowledgeBaseChunkEntity> chunkEntities = new ArrayList<>();
      List<VectorRepository.RedisVectorDocument> redisDocuments = new ArrayList<>();
      LocalDateTime now = LocalDateTime.now();

      for (int start = 0; start < preparedChunks.size(); start += MAX_BATCH_SIZE) {
        int end = Math.min(start + MAX_BATCH_SIZE, preparedChunks.size());
        List<PreparedChunk> batch = preparedChunks.subList(start, end);
        List<float[]> embeddings = embeddingModel.embed(batch.stream().map(PreparedChunk::content).toList());
        if (embeddings.size() != batch.size()) {
          throw new BusinessException(
              ErrorCode.KNOWLEDGE_BASE_VECTORIZATION_FAILED,
              "Embedding 结果数量与分片数量不一致"
          );
        }

        for (int i = 0; i < batch.size(); i++) {
          PreparedChunk preparedChunk = batch.get(i);
          float[] embedding = embeddings.get(i);
          chunkEntities.add(toChunkEntity(preparedChunk, embedding, now));
          redisDocuments.add(new VectorRepository.RedisVectorDocument(
              preparedChunk.documentId(),
              preparedChunk.content(),
              embedding,
              Map.of(
                  "kb_id", String.valueOf(preparedChunk.knowledgeBaseId()),
                  "source_name", preparedChunk.sourceName(),
                  "source_file_name", preparedChunk.sourceFilename(),
                  "chunk_index", preparedChunk.chunkIndex()
              )
          ));
        }
      }

      chunkPersistenceService.replaceChunks(knowledgeBaseId, chunkEntities);
      vectorRepository.saveAll(redisDocuments);
      log.info("知识库向量化完成: kbId={}, chunks={}", knowledgeBaseId, chunkEntities.size());
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      log.error("知识库向量化失败: kbId={}", knowledgeBaseId, e);
      throw new BusinessException(
          ErrorCode.KNOWLEDGE_BASE_VECTORIZATION_FAILED,
          "知识库向量化失败: " + e.getMessage(),
          e
      );
    }
  }

  public List<Document> similaritySearch(String query, List<Long> knowledgeBaseIds, int topK, double minScore) {
    if (topK <= 0 || query == null || query.isBlank()) {
      return List.of();
    }

    try {
      SearchRequest.Builder builder = SearchRequest.builder()
          .query(query)
          .topK(resolveCandidateTopK(topK, knowledgeBaseIds));

      if (minScore > 0) {
        builder.similarityThreshold(minScore);
      }
      if (knowledgeBaseIds != null && !knowledgeBaseIds.isEmpty()) {
        builder.filterExpression(buildKbFilterExpression(knowledgeBaseIds));
      }

      List<Document> rawResults = vectorStore.similaritySearch(builder.build());
      List<Document> filteredResults = postFilterResults(rawResults, knowledgeBaseIds, topK);
      if (!filteredResults.isEmpty() || knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
        return filteredResults;
      }

      return similaritySearchFallback(query, knowledgeBaseIds, topK, minScore);
    } catch (Exception e) {
      log.warn("Redis 向量检索前置过滤失败，回退到本地过滤: query={}", query, e);
      return similaritySearchFallback(query, knowledgeBaseIds, topK, minScore);
    }
  }

  public void deleteByKnowledgeBaseId(Long knowledgeBaseId) {
    List<String> documentIds = chunkRepository.findDocumentIdsByKnowledgeBaseId(knowledgeBaseId);
    if (!documentIds.isEmpty()) {
      vectorRepository.deleteByDocumentIds(documentIds);
      return;
    }
    vectorRepository.deleteByKnowledgeBaseId(knowledgeBaseId);
  }

  private List<Document> similaritySearchFallback(
      String query,
      List<Long> knowledgeBaseIds,
      int topK,
      double minScore
  ) {
    try {
      SearchRequest.Builder builder = SearchRequest.builder()
          .query(query)
          .topK(Math.max(topK * 8, topK + 16));
      if (minScore > 0) {
        builder.similarityThreshold(Math.max(minScore * 0.5, 0.12));
      }

      List<Document> allResults = vectorStore.similaritySearch(builder.build());
      if (allResults == null || allResults.isEmpty()) {
        return List.of();
      }
      return postFilterResults(allResults, knowledgeBaseIds, topK);
    } catch (Exception e) {
      log.error("Redis 向量检索失败: query={}", query, e);
      throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_QUERY_FAILED, "知识库检索失败: " + e.getMessage(), e);
    }
  }

  private boolean isDocInKnowledgeBases(Document document, List<Long> knowledgeBaseIds) {
    Object kbId = document.getMetadata().get("kb_id");
    if (kbId == null) {
      return false;
    }
    try {
      return knowledgeBaseIds.contains(Long.parseLong(String.valueOf(kbId)));
    } catch (NumberFormatException e) {
      return false;
    }
  }

  private String buildKbFilterExpression(List<Long> knowledgeBaseIds) {
    String values = knowledgeBaseIds.stream()
        .filter(Objects::nonNull)
        .map(String::valueOf)
        .map(id -> "'" + id + "'")
        .collect(Collectors.joining(", "));
    return "kb_id in [" + values + "]";
  }

  private int resolveCandidateTopK(int topK, List<Long> knowledgeBaseIds) {
    if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
      return topK;
    }
    return Math.max(topK * 6, topK + 12);
  }

  private List<Document> postFilterResults(List<Document> results, List<Long> knowledgeBaseIds, int topK) {
    if (results == null || results.isEmpty()) {
      return List.of();
    }

    return results.stream()
        .filter(document -> document.getText() != null && !document.getText().isBlank())
        .filter(document -> knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()
            || isDocInKnowledgeBases(document, knowledgeBaseIds))
        .collect(Collectors.toMap(
            Document::getId,
            document -> document,
            (left, right) -> left,
            java.util.LinkedHashMap::new
        ))
        .values()
        .stream()
        .limit(topK)
        .toList();
  }

  private List<PreparedChunk> buildChunks(
      Long knowledgeBaseId,
      String sourceName,
      String sourceFilename,
      String content
  ) {
    List<PreparedChunk> chunks = new ArrayList<>();
    Set<String> seenTexts = new LinkedHashSet<>();
    int chunkIndex = 0;
    String normalizedSourceName = textCleaningService.normalizeUnicode(sourceName);
    String normalizedSourceFilename = textCleaningService.normalizeUnicode(sourceFilename);

    for (String section : splitIntoSections(content)) {
      List<Document> sectionChunks = textSplitter.apply(List.of(new Document(section)));
      if (sectionChunks.isEmpty()) {
        sectionChunks = List.of(new Document(section));
      }

      for (Document chunk : sectionChunks) {
        String chunkText = chunk.getText() == null ? "" : textCleaningService.cleanText(chunk.getText());
        if (chunkText.isBlank() || !seenTexts.add(chunkText)) {
          continue;
        }

        int currentChunkIndex = chunkIndex++;
        chunks.add(new PreparedChunk(
            knowledgeBaseId,
            documentId(knowledgeBaseId, currentChunkIndex),
            currentChunkIndex,
            chunkText,
            normalizedSourceName,
            normalizedSourceFilename
        ));
      }
    }

    return chunks;
  }

  private List<String> splitIntoSections(String content) {
    String normalized = content == null ? "" : content.replace("\r\n", "\n").trim();
    if (normalized.isBlank()) {
      return List.of();
    }

    if (MARKDOWN_HEADING_PATTERN.matcher(normalized).find()) {
      List<String> sections = new ArrayList<>();
      String[] lines = normalized.split("\\R");
      StringBuilder current = new StringBuilder();
      for (String line : lines) {
        if (line.matches("^#{1,6}\\s+.+$") && current.length() > 0) {
          sections.add(current.toString().trim());
          current = new StringBuilder();
        }
        current.append(line).append('\n');
      }
      if (current.length() > 0) {
        sections.add(current.toString().trim());
      }
      return sections;
    }

    List<String> sections = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    for (String block : normalized.split("\\R\\R+")) {
      if (block.isBlank()) {
        continue;
      }
      if (current.length() + block.length() > MAX_SECTION_LENGTH && current.length() > 0) {
        sections.add(current.toString().trim());
        current = new StringBuilder();
      }
      if (current.length() > 0) {
        current.append("\n\n");
      }
      current.append(block.trim());
    }
    if (current.length() > 0) {
      sections.add(current.toString().trim());
    }
    return sections;
  }

  private KnowledgeBaseChunkEntity toChunkEntity(
      PreparedChunk preparedChunk,
      float[] embedding,
      LocalDateTime now
  ) throws JsonProcessingException {
    KnowledgeBaseChunkEntity entity = new KnowledgeBaseChunkEntity();
    entity.setKnowledgeBaseId(preparedChunk.knowledgeBaseId());
    entity.setDocumentId(preparedChunk.documentId());
    entity.setChunkIndex(preparedChunk.chunkIndex());
    entity.setContent(preparedChunk.content());
    entity.setContentLength(preparedChunk.content().length());
    entity.setEmbeddingJson(objectMapper.writeValueAsString(embedding));
    entity.setSourceName(preparedChunk.sourceName());
    entity.setSourceFilename(preparedChunk.sourceFilename());
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    return entity;
  }

  private void deleteExistingVectorData(Long knowledgeBaseId, List<String> previousDocumentIds) {
    if (previousDocumentIds != null && !previousDocumentIds.isEmpty()) {
      vectorRepository.deleteByDocumentIds(previousDocumentIds);
      return;
    }
    vectorRepository.deleteByKnowledgeBaseId(knowledgeBaseId);
  }

  private String documentId(Long knowledgeBaseId, int chunkIndex) {
    return String.format(Locale.ROOT, "kb-%d-chunk-%04d", knowledgeBaseId, chunkIndex);
  }

  private record PreparedChunk(
      Long knowledgeBaseId,
      String documentId,
      int chunkIndex,
      String content,
      String sourceName,
      String sourceFilename
  ) {
  }
}
