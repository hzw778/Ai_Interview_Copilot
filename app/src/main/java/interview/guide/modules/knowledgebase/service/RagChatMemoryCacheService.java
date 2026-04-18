package interview.guide.modules.knowledgebase.service;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.infrastructure.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagChatMemoryCacheService {

  private static final String MEMORY_KEY_PREFIX = "rag:chat:memory:";
  private static final Pattern SELF_INTRO_PATTERN = Pattern.compile(
      "^(?:你好[，,、\\s]*)?(?:我是|我叫|叫我|你可以叫我|我的名字是)(.+)$");

  private final RedisService redisService;
  private final RagChatMemoryProperties memoryProperties;
  private final ObjectMapper objectMapper;

  public void initializeSession(Long sessionId) {
    if (sessionId == null) {
      return;
    }
    saveMemory(sessionId, new CachedConversationMemory(sessionId, new ArrayList<>(), null));
  }

  public void appendUserTurn(Long sessionId, String content) {
    appendTurn(sessionId, "user", content);
  }

  public void appendAssistantTurn(Long sessionId, String content) {
    appendTurn(sessionId, "assistant", content);
  }

  public List<KnowledgeBaseQueryService.ConversationTurn> getConversationHistory(Long sessionId) {
    if (sessionId == null) {
      return List.of();
    }

    CachedConversationMemory memory = readMemory(sessionId);
    if (memory == null || memory.turns() == null || memory.turns().isEmpty()) {
      return List.of();
    }

    redisService.expire(buildMemoryKey(sessionId), memoryProperties.getTtl());
    return memory.turns().stream()
        .filter(turn -> turn != null && turn.role() != null && turn.content() != null)
        .map(turn -> new KnowledgeBaseQueryService.ConversationTurn(turn.role(), turn.content()))
        .toList();
  }

  public Optional<String> getRememberedName(Long sessionId) {
    if (sessionId == null) {
      return Optional.empty();
    }

    CachedConversationMemory memory = readMemory(sessionId);
    if (memory == null || memory.rememberedName() == null || memory.rememberedName().isBlank()) {
      return Optional.empty();
    }
    redisService.expire(buildMemoryKey(sessionId), memoryProperties.getTtl());
    return Optional.of(memory.rememberedName());
  }

  public void rebuildConversationHistory(
      Long sessionId,
      List<KnowledgeBaseQueryService.ConversationTurn> conversationHistory
  ) {
    if (sessionId == null) {
      return;
    }

    List<CachedConversationTurn> turns = new ArrayList<>();
    if (conversationHistory != null) {
      for (KnowledgeBaseQueryService.ConversationTurn turn : conversationHistory) {
        if (turn == null || turn.role() == null || turn.content() == null) {
          continue;
        }
        String role = turn.role().trim();
        String content = turn.content().trim();
        if (role.isBlank() || content.isBlank()) {
          continue;
        }
        turns.add(new CachedConversationTurn(role, content));
      }
    }

    saveMemory(sessionId, new CachedConversationMemory(
        sessionId,
        trimTurns(turns),
        resolveRememberedName(turns, null)
    ));
  }

  public void clearSession(Long sessionId) {
    if (sessionId == null) {
      return;
    }
    redisService.delete(buildMemoryKey(sessionId));
  }

  public boolean exists(Long sessionId) {
    return sessionId != null && redisService.exists(buildMemoryKey(sessionId));
  }

  public String buildMemoryKey(Long sessionId) {
    return MEMORY_KEY_PREFIX + sessionId;
  }

  private void appendTurn(Long sessionId, String role, String content) {
    if (sessionId == null || role == null || content == null || content.isBlank()) {
      return;
    }

    CachedConversationMemory memory = readMemory(sessionId);
    List<CachedConversationTurn> turns = memory == null || memory.turns() == null
        ? new ArrayList<>()
        : new ArrayList<>(memory.turns());
    String normalizedContent = content.trim();
    turns.add(new CachedConversationTurn(role, normalizedContent));
    saveMemory(sessionId, new CachedConversationMemory(
        sessionId,
        trimTurns(turns),
        resolveRememberedName(turns, memory == null ? null : memory.rememberedName())
    ));
    log.debug("更新 RAG 会话记忆: sessionId={}, role={}, turns={}", sessionId, role, turns.size());
  }

  private CachedConversationMemory readMemory(Long sessionId) {
    String rawJson = redisService.getString(buildMemoryKey(sessionId));
    if (rawJson == null || rawJson.isBlank()) {
      return null;
    }

    try {
      return objectMapper.readValue(rawJson, CachedConversationMemory.class);
    } catch (JacksonException e) {
      log.warn("解析 RAG 会话记忆失败，已清理旧缓存: sessionId={}", sessionId, e);
      clearSession(sessionId);
      return null;
    }
  }

  private void saveMemory(Long sessionId, CachedConversationMemory memory) {
    try {
      String json = objectMapper.writeValueAsString(memory);
      redisService.setString(buildMemoryKey(sessionId), json, memoryProperties.getTtl());
    } catch (JacksonException e) {
      log.error("序列化 RAG 会话记忆失败: sessionId={}", sessionId, e);
    }
  }

  private List<CachedConversationTurn> trimTurns(List<CachedConversationTurn> turns) {
    int maxMessages = Math.max(1, memoryProperties.getMaxMessages());
    if (turns.size() <= maxMessages) {
      return turns;
    }
    return new ArrayList<>(turns.subList(turns.size() - maxMessages, turns.size()));
  }

  private String resolveRememberedName(List<CachedConversationTurn> turns, String fallbackName) {
    for (int i = turns.size() - 1; i >= 0; i--) {
      CachedConversationTurn turn = turns.get(i);
      if (!"user".equalsIgnoreCase(turn.role())) {
        continue;
      }
      Optional<String> extractedName = extractIntroducedName(turn.content());
      if (extractedName.isPresent()) {
        return extractedName.get();
      }
    }
    return fallbackName;
  }

  private Optional<String> extractIntroducedName(String content) {
    if (content == null || content.isBlank()) {
      return Optional.empty();
    }

    Matcher matcher = SELF_INTRO_PATTERN.matcher(content.trim());
    if (!matcher.matches()) {
      return Optional.empty();
    }

    String candidate = matcher.group(1)
        .replaceAll("[，,。.!！?？].*$", "")
        .trim();
    if (candidate.isBlank() || candidate.length() > 16) {
      return Optional.empty();
    }
    return Optional.of(candidate);
  }

  public record CachedConversationMemory(Long sessionId, List<CachedConversationTurn> turns, String rememberedName)
      implements Serializable {
  }

  public record CachedConversationTurn(String role, String content) implements Serializable {
  }
}
