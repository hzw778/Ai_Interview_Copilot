package interview.guide.modules.knowledgebase.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.infrastructure.redis.RedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("RagChatMemoryCacheService Test")
class RagChatMemoryCacheServiceTest {

  private final Map<String, Object> store = new HashMap<>();
  private RedisService redisService;
  private RagChatMemoryCacheService memoryCacheService;

  @BeforeEach
  void setUp() {
    redisService = mock(RedisService.class);

    doAnswer(invocation -> {
      store.put(invocation.getArgument(0), invocation.getArgument(1));
      return null;
    }).when(redisService).setString(anyString(), anyString(), any(Duration.class));

    doAnswer(invocation -> store.remove(invocation.getArgument(0)) != null)
        .when(redisService).delete(anyString());

    when(redisService.exists(anyString())).thenAnswer(invocation -> store.containsKey(invocation.getArgument(0)));
    when(redisService.getString(anyString())).thenAnswer(invocation -> store.get(invocation.getArgument(0)));
    when(redisService.expire(anyString(), any(Duration.class))).thenReturn(true);

    RagChatMemoryProperties properties = new RagChatMemoryProperties();
    properties.setTtl(Duration.ofHours(24));
    properties.setMaxMessages(3);
    memoryCacheService = new RagChatMemoryCacheService(redisService, properties, new ObjectMapper());
  }

  @Test
  @DisplayName("Should append and trim turns in redis memory")
  void shouldAppendAndTrimTurnsInRedisMemory() {
    memoryCacheService.initializeSession(5L);
    memoryCacheService.appendUserTurn(5L, "你好，我叫小黑");
    memoryCacheService.appendAssistantTurn(5L, "第二句");
    memoryCacheService.appendUserTurn(5L, "第三句");
    memoryCacheService.appendAssistantTurn(5L, "第四句");

    List<KnowledgeBaseQueryService.ConversationTurn> history = memoryCacheService.getConversationHistory(5L);

    assertThat(memoryCacheService.buildMemoryKey(5L)).isEqualTo("rag:chat:memory:5");
    assertThat(store.get("rag:chat:memory:5")).isInstanceOf(String.class);
    assertThat(history).extracting(KnowledgeBaseQueryService.ConversationTurn::content)
        .containsExactly("第二句", "第三句", "第四句");
    assertThat(memoryCacheService.getRememberedName(5L)).contains("小黑");
  }

  @Test
  @DisplayName("Should rebuild and clear session memory")
  void shouldRebuildAndClearSessionMemory() {
    memoryCacheService.rebuildConversationHistory(
        9L,
        List.of(
            new KnowledgeBaseQueryService.ConversationTurn("user", "我叫小黑"),
            new KnowledgeBaseQueryService.ConversationTurn("assistant", "收到")
        )
    );

    assertThat(memoryCacheService.exists(9L)).isTrue();
    assertThat(memoryCacheService.getConversationHistory(9L)).hasSize(2);

    memoryCacheService.clearSession(9L);

    assertThat(memoryCacheService.exists(9L)).isFalse();
    assertThat(memoryCacheService.getConversationHistory(9L)).isEmpty();
  }
}
