package interview.guide.modules.knowledgebase.service;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.infrastructure.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseContentCacheService {

  private static final String CONTENT_KEY_PREFIX = "knowledgebase:parsed:";
  private static final String CHUNK_KEY_PREFIX = "knowledgebase:chunks:";

  private final RedisService redisService;
  private final ObjectMapper objectMapper;

  public void cacheParsedContent(Long kbId, String content) {
    if (kbId == null || content == null || content.isBlank()) {
      return;
    }
    redisService.setString(contentKey(kbId), content);
  }

  public String getParsedContent(Long kbId) {
    if (kbId == null) {
      return null;
    }
    String content = redisService.getString(contentKey(kbId));
    if (content == null || content.isBlank() || looksLikeBinaryPayload(content)) {
      if (looksLikeBinaryPayload(content)) {
        redisService.delete(contentKey(kbId));
      }
      return null;
    }
    return content;
  }

  public void cacheChunks(Long kbId, List<String> chunks) {
    if (kbId == null || chunks == null) {
      return;
    }
    try {
      redisService.setString(chunkKey(kbId), objectMapper.writeValueAsString(chunks));
    } catch (JacksonException e) {
      log.error("缓存知识库分片失败: kbId={}", kbId, e);
    }
  }

  public List<String> getChunks(Long kbId) {
    if (kbId == null) {
      return List.of();
    }
    String rawJson = redisService.getString(chunkKey(kbId));
    if (rawJson == null || rawJson.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(rawJson, new TypeReference<>() {
      });
    } catch (JacksonException e) {
      log.warn("解析知识库分片缓存失败，已清理旧缓存: kbId={}", kbId, e);
      redisService.delete(chunkKey(kbId));
      return List.of();
    }
  }

  public void evict(Long kbId) {
    if (kbId == null) {
      return;
    }
    redisService.delete(contentKey(kbId));
    redisService.delete(chunkKey(kbId));
  }

  private boolean looksLikeBinaryPayload(String content) {
    if (content == null || content.isBlank()) {
      return false;
    }
    int inspectLength = Math.min(content.length(), 8);
    for (int i = 0; i < inspectLength; i++) {
      char ch = content.charAt(i);
      if (Character.isISOControl(ch) && ch != '\n' && ch != '\r' && ch != '\t') {
        return true;
      }
    }
    return false;
  }

  private String contentKey(Long kbId) {
    return CONTENT_KEY_PREFIX + kbId;
  }

  private String chunkKey(Long kbId) {
    return CHUNK_KEY_PREFIX + kbId;
  }
}
