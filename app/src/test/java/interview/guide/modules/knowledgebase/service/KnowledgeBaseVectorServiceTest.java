package interview.guide.modules.knowledgebase.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.infrastructure.file.TextCleaningService;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseChunkEntity;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseChunkRepository;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import interview.guide.modules.knowledgebase.repository.VectorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("KnowledgeBaseVectorService Test")
class KnowledgeBaseVectorServiceTest {

  @Mock
  private EmbeddingModel embeddingModel;

  @Mock
  private VectorStore vectorStore;

  @Mock
  private VectorRepository vectorRepository;

  @Mock
  private KnowledgeBaseRepository knowledgeBaseRepository;

  @Mock
  private KnowledgeBaseChunkRepository chunkRepository;

  @Mock
  private KnowledgeBaseChunkPersistenceService chunkPersistenceService;

  @Mock
  private KnowledgeBaseContentCacheService contentCacheService;

  @Mock
  private KnowledgeBaseParseService parseService;

  private KnowledgeBaseVectorService vectorService;

  @BeforeEach
  void setUp() {
    vectorService = new KnowledgeBaseVectorService(
        embeddingModel,
        vectorStore,
        vectorRepository,
        knowledgeBaseRepository,
        chunkRepository,
        chunkPersistenceService,
        contentCacheService,
        parseService,
        new TextCleaningService(),
        new ObjectMapper(),
        new KnowledgeBaseVectorProperties()
    );
  }

  @Test
  @DisplayName("Vectorize should persist chunks into mysql and redis once")
  void vectorizeShouldPersistChunksIntoMysqlAndRedis() {
    when(knowledgeBaseRepository.findById(anyLong())).thenReturn(Optional.of(knowledgeBase()));
    when(chunkRepository.findDocumentIdsByKnowledgeBaseId(1L)).thenReturn(List.of("kb-1-chunk-0000"));
    when(embeddingModel.embed(anyList())).thenReturn(List.of(new float[] {0.1f, 0.2f, 0.3f}));

    String content = """
        Redis 是一个高性能的键值数据库。

        它常用于缓存、会话管理和排行榜等场景。
        """;

    vectorService.vectorizeAndStore(1L, content);

    verify(vectorRepository).deleteByDocumentIds(List.of("kb-1-chunk-0000"));
    verify(chunkPersistenceService).replaceChunks(eq(1L), anyList());
    verify(vectorRepository).saveAll(anyList());
    verify(contentCacheService).cacheParsedContent(1L, content.trim());
  }

  @Test
  @DisplayName("Similarity search should filter by knowledge base id")
  void similaritySearchShouldFilterByKnowledgeBaseId() {
    List<Document> documents = List.of(
        new Document("doc-1", "Redis 内容", java.util.Map.of("kb_id", "1")),
        new Document("doc-2", "MySQL 内容", java.util.Map.of("kb_id", "2"))
    );
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(documents);

    List<Document> result = vectorService.similaritySearch("Redis", List.of(1L), 5, 0.0);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getId()).isEqualTo("doc-1");
  }

  @Test
  @DisplayName("Delete should prefer exact document ids from mysql")
  void deleteShouldPreferExactDocumentIdsFromMysql() {
    when(chunkRepository.findDocumentIdsByKnowledgeBaseId(1L)).thenReturn(List.of("doc-1", "doc-2"));

    vectorService.deleteByKnowledgeBaseId(1L);

    verify(vectorRepository).deleteByDocumentIds(List.of("doc-1", "doc-2"));
    verify(vectorRepository, never()).deleteByKnowledgeBaseId(1L);
  }

  @Test
  @DisplayName("Vectorize should write chunk metadata with stable source fields")
  void vectorizeShouldWriteChunkMetadata() {
    when(knowledgeBaseRepository.findById(anyLong())).thenReturn(Optional.of(knowledgeBase()));
    when(chunkRepository.findDocumentIdsByKnowledgeBaseId(1L)).thenReturn(List.of());
    when(embeddingModel.embed(anyList())).thenReturn(List.of(new float[] {0.1f, 0.2f, 0.3f}));

    ArgumentCaptor<List<KnowledgeBaseChunkEntity>> captor = ArgumentCaptor.forClass(List.class);

    vectorService.vectorizeAndStore(1L, "Redis 是一个高性能数据库，用于缓存和高并发场景。");

    verify(chunkPersistenceService).replaceChunks(eq(1L), captor.capture());
    assertThat(captor.getValue()).hasSize(1);
    assertThat(captor.getValue().get(0).getDocumentId()).isEqualTo("kb-1-chunk-0000");
    assertThat(captor.getValue().get(0).getSourceFilename()).isEqualTo("redis.md");
  }

  @Test
  @DisplayName("Vectorize should normalize compatibility ideographs before persisting")
  void vectorizeShouldNormalizeCompatibilityIdeographsBeforePersisting() {
    when(knowledgeBaseRepository.findById(anyLong())).thenReturn(Optional.of(knowledgeBase()));
    when(chunkRepository.findDocumentIdsByKnowledgeBaseId(1L)).thenReturn(List.of());
    when(embeddingModel.embed(anyList())).thenReturn(List.of(new float[] {0.1f, 0.2f, 0.3f}));

    ArgumentCaptor<List<KnowledgeBaseChunkEntity>> captor = ArgumentCaptor.forClass(List.class);

    vectorService.vectorizeAndStore(1L, "Redis ⾯试⾼频题整理");

    verify(chunkPersistenceService).replaceChunks(eq(1L), captor.capture());
    assertThat(captor.getValue()).hasSize(1);
    assertThat(captor.getValue().get(0).getContent()).contains("Redis 面试高频题整理");
  }

  private KnowledgeBaseEntity knowledgeBase() {
    KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
    entity.setId(1L);
    entity.setName("Redis 基础");
    entity.setOriginalFilename("redis.md");
    entity.setStorageKey("knowledge/redis.md");
    return entity;
  }
}
