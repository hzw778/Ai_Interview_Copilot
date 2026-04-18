package interview.guide.modules.knowledgebase.service;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.infrastructure.file.TextCleaningService;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseChunkEntity;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("KnowledgeBaseQueryService Test")
class KnowledgeBaseQueryServiceTest {

  @Mock
  private LlmProviderRegistry llmProviderRegistry;

  @Mock
  private KnowledgeBaseVectorService vectorService;

  @Mock
  private KnowledgeBaseListService listService;

  @Mock
  private KnowledgeBaseCountService countService;

  @Mock
  private KnowledgeBaseChunkRepository chunkRepository;

  private ChatClient chatClient;
  private KnowledgeBaseQueryService queryService;

  @BeforeEach
  void setUp() throws Exception {
    chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    KnowledgeBaseQueryProperties queryProperties = new KnowledgeBaseQueryProperties();
    queryProperties.getRewrite().setEnabled(false);
    queryService = new KnowledgeBaseQueryService(
        llmProviderRegistry,
        vectorService,
        listService,
        countService,
        chunkRepository,
        new TextCleaningService(),
        queryProperties,
        new DefaultResourceLoader()
    );
  }

  @Test
  @DisplayName("Greeting should bypass retrieval")
  void greetingShouldBypassRetrieval() {
    List<String> chunks = queryService.answerQuestionStream(List.of(1L), "你好").collectList().block();

    assertThat(chunks).containsExactly("已连接当前选中的知识库，你可以直接提问，我会先检索再回答。");
    verify(countService, never()).updateQuestionCounts(anyList());
    verify(vectorService, never()).similaritySearch(anyString(), anyList(), anyInt(), anyDouble());
  }

  @Test
  @DisplayName("Knowledge question should return fixed fallback when no hit exists")
  void knowledgeQuestionShouldReturnNoResultWhenNothingRetrieved() {
    when(vectorService.similaritySearch(anyString(), eq(List.of(1L)), anyInt(), anyDouble())).thenReturn(List.of());
    when(chunkRepository.findLightweightByKnowledgeBaseIds(List.of(1L))).thenReturn(List.of());

    String answer = queryService.answerQuestion(List.of(1L), "Redis 持久化是什么");

    assertThat(answer).contains("没有检索到足够相关的内容");
    verify(countService).updateQuestionCounts(List.of(1L));
  }

  @Test
  @DisplayName("Retrieved answer should append source section")
  void retrievedAnswerShouldAppendSources() {
    when(llmProviderRegistry.getDefaultChatClient()).thenReturn(chatClient);
    Document document = new Document(
        "doc-1",
        "Redis 是一个以内存为主的键值数据库。",
        java.util.Map.of(
            "kb_id", "1",
            "source_name", "Redis 基础",
            "source_file_name", "redis.md",
            "chunk_index", 0
        )
    );
    when(vectorService.similaritySearch(anyString(), eq(List.of(1L)), anyInt(), anyDouble()))
        .thenReturn(List.of(document));
    when(chunkRepository.findByDocumentIds(List.of("doc-1"))).thenReturn(List.of(chunk("doc-1")));
    when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
        .thenReturn("## 结论\n\nRedis 是一个以内存为主的键值数据库。[1]");

    String answer = queryService.answerQuestion(List.of(1L), "什么是 Redis");

    assertThat(answer).contains("## 结论");
    assertThat(answer).contains("## 参考来源");
    assertThat(answer).contains("redis.md");
  }

  @Test
  @DisplayName("Catalog query should be found by keyword fallback")
  void catalogQueryShouldBeFoundByKeywordFallback() {
    when(llmProviderRegistry.getDefaultChatClient()).thenReturn(chatClient);
    when(vectorService.similaritySearch(anyString(), eq(List.of(1L)), anyInt(), anyDouble()))
        .thenReturn(List.of());
    when(chunkRepository.findLightweightByKnowledgeBaseIds(List.of(1L))).thenReturn(List.of(
        keywordChunk("doc-10", 0, "详解 57 道 Redis 面试高频题，让天下没有难背的八股。"),
        keywordChunk("doc-11", 1, "Java 面试指南收录的 Redis 原题整理。")
    ));
    when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
        .thenReturn("## 结论\n\n这份资料里整理了 Redis 的高频面试题、原题和专题。");

    String answer = queryService.answerQuestion(List.of(1L), "redis的常见面试题有哪些");

    assertThat(answer).contains("高频面试题");
    assertThat(answer).contains("## 参考来源");
  }

  @Test
  @DisplayName("Compatibility ideographs should still be matched by keyword fallback")
  void compatibilityIdeographsShouldStillBeMatchedByKeywordFallback() {
    when(llmProviderRegistry.getDefaultChatClient()).thenReturn(chatClient);
    when(vectorService.similaritySearch(anyString(), eq(List.of(1L)), anyInt(), anyDouble()))
        .thenReturn(List.of());
    when(chunkRepository.findLightweightByKnowledgeBaseIds(List.of(1L))).thenReturn(List.of(
        keywordChunk("doc-30", 0, "详解 57 道 Redis ⾯试⾼频题，让天下没有难背的八股。"),
        keywordChunk("doc-31", 1, "Java ⾯试指南收录的 Redis 原题整理。")
    ));
    when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
        .thenReturn("## 结论\n\n这份资料中整理了 Redis 的高频面试题与原题。[1][2]");

    String answer = queryService.answerQuestion(List.of(1L), "redis的常见面试题有哪些");

    assertThat(answer).contains("高频面试题");
    assertThat(answer).contains("## 参考来源");
    assertThat(answer).contains("[1]");
  }

  @Test
  @DisplayName("No-result answer should not append noisy sources")
  void noResultAnswerShouldNotAppendNoisySources() {
    when(vectorService.similaritySearch(anyString(), eq(List.of(1L)), anyInt(), anyDouble()))
        .thenReturn(List.of());
    when(chunkRepository.findLightweightByKnowledgeBaseIds(List.of(1L))).thenReturn(List.of(
        keywordChunk("doc-20", 0, "Redis 是一个基于内存的键值数据库。")
    ));

    String answer = queryService.answerQuestion(List.of(1L), "Redis如何实现异步消息队列");

    assertThat(answer).isEqualTo("抱歉，当前选中的知识库中没有检索到足够相关的内容。请换一个更具体的问题，或补充关键词后再试。");
    assertThat(answer).doesNotContain("参考来源");
  }

  @Test
  @DisplayName("Answer with citations and insufficiency note should still append sources")
  void citedPartialAnswerShouldStillAppendSources() {
    when(llmProviderRegistry.getDefaultChatClient()).thenReturn(chatClient);
    Document document = new Document(
        "doc-40",
        "Redis 使用 List 实现异步消息队列，使用 ZSet 实现延时消息队列。",
        java.util.Map.of(
            "kb_id", "1",
            "source_name", "redis",
            "source_file_name", "redis.pdf",
            "chunk_index", 12
        )
    );
    when(vectorService.similaritySearch(anyString(), eq(List.of(1L)), anyInt(), anyDouble()))
        .thenReturn(List.of(document));
    when(chunkRepository.findByDocumentIds(List.of("doc-40"))).thenReturn(List.of(chunk("doc-40")));
    when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
        .thenReturn("## 回答\n\nRedis 可以用 List 实现异步消息队列。[1]\n\n当前知识库未提供 ACK 机制的更多细节。");

    String answer = queryService.answerQuestion(List.of(1L), "Redis如何实现异步消息队列");

    assertThat(answer).contains("## 回答");
    assertThat(answer).contains("当前知识库未提供 ACK 机制的更多细节");
    assertThat(answer).contains("## 参考来源");
  }

  private KnowledgeBaseChunkEntity chunk(String documentId) {
    KnowledgeBaseChunkEntity entity = new KnowledgeBaseChunkEntity();
    entity.setKnowledgeBaseId(1L);
    entity.setDocumentId(documentId);
    entity.setChunkIndex(0);
    entity.setContent("Redis 是一个以内存为主的键值数据库。");
    entity.setSourceName("Redis 基础");
    entity.setSourceFilename("redis.md");
    return entity;
  }

  private KnowledgeBaseChunkEntity keywordChunk(String documentId, int chunkIndex, String content) {
    KnowledgeBaseChunkEntity entity = new KnowledgeBaseChunkEntity();
    entity.setKnowledgeBaseId(1L);
    entity.setDocumentId(documentId);
    entity.setChunkIndex(chunkIndex);
    entity.setContent(content);
    entity.setSourceName("redis");
    entity.setSourceFilename("面渣逆袭Redis篇V2.0_lite.pdf");
    return entity;
  }
}
