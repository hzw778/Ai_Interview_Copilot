package interview.guide.modules.knowledgebase.service;

import interview.guide.infrastructure.mapper.KnowledgeBaseMapper;
import interview.guide.infrastructure.mapper.RagChatMapper;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.model.RagChatMessageEntity;
import interview.guide.modules.knowledgebase.model.RagChatSessionEntity;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import interview.guide.modules.knowledgebase.repository.RagChatMessageRepository;
import interview.guide.modules.knowledgebase.repository.RagChatSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RagChatSessionService Test")
class RagChatSessionServiceTest {

  @Mock
  private RagChatSessionRepository sessionRepository;

  @Mock
  private RagChatMessageRepository messageRepository;

  @Mock
  private KnowledgeBaseRepository knowledgeBaseRepository;

  @Mock
  private KnowledgeBaseQueryService queryService;

  @Mock
  private RagChatMapper ragChatMapper;

  @Mock
  private KnowledgeBaseMapper knowledgeBaseMapper;

  @Mock
  private RagChatMemoryCacheService memoryCacheService;

  private RagChatSessionService sessionService;

  @BeforeEach
  void setUp() {
    sessionService = new RagChatSessionService(
        sessionRepository,
        messageRepository,
        knowledgeBaseRepository,
        queryService,
        ragChatMapper,
        knowledgeBaseMapper,
        memoryCacheService
    );
  }

  @Test
  @DisplayName("Prepare stream message should write user turn into redis memory")
  void prepareStreamMessageShouldWriteUserTurnIntoRedisMemory() {
    RagChatSessionEntity session = buildSession(1L, 4L);
    session.setMessageCount(0);
    AtomicLong idGenerator = new AtomicLong(100L);

    when(sessionRepository.findByIdWithKnowledgeBases(1L)).thenReturn(Optional.of(session));
    when(messageRepository.save(any(RagChatMessageEntity.class))).thenAnswer(invocation -> {
      RagChatMessageEntity message = invocation.getArgument(0);
      if (message.getId() == null) {
        message.setId(idGenerator.getAndIncrement());
      }
      return message;
    });
    when(sessionRepository.save(session)).thenReturn(session);

    Long messageId = sessionService.prepareStreamMessage(1L, "你好，我是小黑");

    assertThat(messageId).isEqualTo(101L);
    verify(memoryCacheService).appendUserTurn(1L, "你好，我是小黑");
  }

  @Test
  @DisplayName("Complete stream message should write assistant turn into redis memory")
  void completeStreamMessageShouldWriteAssistantTurnIntoRedisMemory() {
    RagChatSessionEntity session = buildSession(1L, 4L);
    RagChatMessageEntity message = new RagChatMessageEntity();
    message.setId(88L);
    message.setSession(session);
    message.setType(RagChatMessageEntity.MessageType.ASSISTANT);
    message.setContent("");
    message.setCompleted(false);

    when(messageRepository.findById(88L)).thenReturn(Optional.of(message));
    when(messageRepository.save(message)).thenReturn(message);

    sessionService.completeStreamMessage(88L, "你刚才介绍自己叫小黑。");

    verify(memoryCacheService).appendAssistantTurn(1L, "你刚才介绍自己叫小黑。");
  }

  @Test
  @DisplayName("Get stream answer should prefer redis memory")
  void getStreamAnswerShouldPreferRedisMemory() {
    RagChatSessionEntity session = buildSession(1L, 4L);
    List<KnowledgeBaseQueryService.ConversationTurn> history =
        List.of(new KnowledgeBaseQueryService.ConversationTurn("user", "我叫小黑"));

    when(sessionRepository.findByIdWithKnowledgeBases(1L)).thenReturn(Optional.of(session));
    when(memoryCacheService.getRememberedName(1L)).thenReturn(Optional.of("小黑"));
    when(memoryCacheService.getConversationHistory(1L)).thenReturn(history);
    when(queryService.answerQuestionStream(List.of(4L), "我是谁", history)).thenReturn(Flux.just("你刚才介绍自己叫小黑。"));

    List<String> response = sessionService.getStreamAnswer(1L, "我是谁").collectList().block();

    assertThat(response).containsExactly("你刚才介绍自己叫小黑。");
    verify(messageRepository, never()).findBySessionIdOrderByMessageOrderAsc(anyLong());
  }

  @Test
  @DisplayName("Get stream answer should fallback to mysql and rebuild redis memory")
  void getStreamAnswerShouldFallbackToMysqlAndRebuildRedisMemory() {
    RagChatSessionEntity session = buildSession(1L, 4L);
    List<RagChatMessageEntity> dbMessages = List.of(
        buildMessage(session, 1L, RagChatMessageEntity.MessageType.USER, "我叫小黑", true, 0),
        buildMessage(session, 2L, RagChatMessageEntity.MessageType.USER, "我是谁", true, 1)
    );
    List<KnowledgeBaseQueryService.ConversationTurn> expectedHistory =
        List.of(new KnowledgeBaseQueryService.ConversationTurn("user", "我叫小黑"));

    when(sessionRepository.findByIdWithKnowledgeBases(1L)).thenReturn(Optional.of(session));
    when(memoryCacheService.getRememberedName(1L)).thenReturn(Optional.of("小黑"));
    when(memoryCacheService.getConversationHistory(1L)).thenReturn(List.of());
    when(messageRepository.findBySessionIdOrderByMessageOrderAsc(1L)).thenReturn(dbMessages);
    when(queryService.answerQuestionStream(List.of(4L), "我是谁", expectedHistory)).thenReturn(Flux.just("你刚才介绍自己叫小黑。"));

    List<String> response = sessionService.getStreamAnswer(1L, "我是谁").collectList().block();

    assertThat(response).containsExactly("你刚才介绍自己叫小黑。");
    verify(memoryCacheService).rebuildConversationHistory(eq(1L), eq(List.of(
        new KnowledgeBaseQueryService.ConversationTurn("user", "我叫小黑"),
        new KnowledgeBaseQueryService.ConversationTurn("user", "我是谁")
    )));
  }

  private RagChatSessionEntity buildSession(Long sessionId, Long knowledgeBaseId) {
    KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
    knowledgeBase.setId(knowledgeBaseId);

    RagChatSessionEntity session = new RagChatSessionEntity();
    session.setId(sessionId);
    session.setKnowledgeBases(Set.of(knowledgeBase));
    session.setMessageCount(0);
    return session;
  }

  private RagChatMessageEntity buildMessage(
      RagChatSessionEntity session,
      Long id,
      RagChatMessageEntity.MessageType type,
      String content,
      boolean completed,
      int order
  ) {
    RagChatMessageEntity message = new RagChatMessageEntity();
    message.setId(id);
    message.setSession(session);
    message.setType(type);
    message.setContent(content);
    message.setCompleted(completed);
    message.setMessageOrder(order);
    return message;
  }
}
