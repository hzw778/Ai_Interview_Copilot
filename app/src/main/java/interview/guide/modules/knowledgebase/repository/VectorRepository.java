package interview.guide.modules.knowledgebase.repository;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.vectorstore.redis.autoconfigure.RedisVectorStoreProperties;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.json.Path2;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

@Slf4j
@Repository
@RequiredArgsConstructor
public class VectorRepository {

  private static final Path2 ROOT_PATH = Path2.ROOT_PATH;
  private static final Predicate<Object> RESPONSE_OK = response -> "OK".equals(String.valueOf(response));
  private static final Predicate<Object> RESPONSE_DEL_OK = response ->
      Optional.ofNullable(response)
          .map(String::valueOf)
          .map(value -> !"0".equals(value))
          .orElse(false);

  private final JedisPooled jedisPooled;
  private final RedisVectorStoreProperties redisVectorStoreProperties;

  public void saveAll(List<RedisVectorDocument> documents) {
    if (documents == null || documents.isEmpty()) {
      return;
    }

    try (Pipeline pipeline = jedisPooled.pipelined()) {
      for (RedisVectorDocument document : documents) {
        Map<String, Object> payload = new HashMap<>(document.getMetadata());
        payload.put(RedisVectorStore.DEFAULT_CONTENT_FIELD_NAME, document.getContent());
        payload.put(RedisVectorStore.DEFAULT_EMBEDDING_FIELD_NAME, document.getEmbedding());
        pipeline.jsonSetWithEscape(key(document.getDocumentId()), ROOT_PATH, payload);
      }

      List<Object> responses = pipeline.syncAndReturnAll();
      responses.stream()
          .filter(Predicate.not(RESPONSE_OK))
          .findAny()
          .ifPresent(response -> {
            throw new IllegalStateException("Redis 向量文档写入失败: " + response);
          });
      log.info("已写入 Redis 向量文档: count={}", documents.size());
    } catch (Exception e) {
      log.error("写入 Redis 向量文档失败: count={}", documents.size(), e);
      throw new IllegalStateException("写入 Redis 向量文档失败", e);
    }
  }

  public void deleteByDocumentIds(List<String> documentIds) {
    if (documentIds == null || documentIds.isEmpty()) {
      return;
    }

    try (Pipeline pipeline = jedisPooled.pipelined()) {
      for (String documentId : documentIds) {
        if (documentId != null && !documentId.isBlank()) {
          pipeline.jsonDel(key(documentId));
        }
      }

      List<Object> responses = pipeline.syncAndReturnAll();
      responses.stream()
          .filter(Predicate.not(RESPONSE_DEL_OK))
          .findAny()
          .ifPresent(response -> log.warn("Redis 删除向量文档返回非预期结果: {}", response));
      log.info("已删除 Redis 向量文档: count={}", documentIds.size());
    } catch (Exception e) {
      log.error("按 documentId 删除 Redis 向量文档失败: count={}", documentIds.size(), e);
      throw new IllegalStateException("删除 Redis 向量文档失败", e);
    }
  }

  @Transactional(rollbackFor = Exception.class)
  public int deleteByKnowledgeBaseId(Long knowledgeBaseId) {
    log.info("开始兜底删除知识库向量数据: kbId={}", knowledgeBaseId);
    try {
      String keyPattern = redisVectorStoreProperties.getPrefix() + "*";
      Set<String> keys = jedisPooled.keys(keyPattern);
      if (keys == null || keys.isEmpty()) {
        return 0;
      }

      int deleted = 0;
      for (String key : keys) {
        try {
          Map<String, Object> document = jedisPooled.jsonGet(key, Map.class);
          if (document == null) {
            continue;
          }

          Object kbId = document.get("kb_id");
          if (kbId != null && knowledgeBaseId.toString().equals(String.valueOf(kbId))) {
            deleted += jedisPooled.jsonDel(key) > 0 ? 1 : 0;
          }
        } catch (Exception ignored) {
          // 跳过无效或历史残留键
        }
      }

      log.info("兜底删除知识库向量数据完成: kbId={}, deleted={}", knowledgeBaseId, deleted);
      return deleted;
    } catch (Exception e) {
      log.warn("兜底删除知识库向量数据失败: kbId={}", knowledgeBaseId, e);
      return 0;
    }
  }

  private String key(String documentId) {
    return redisVectorStoreProperties.getPrefix() + documentId;
  }

  @Getter
  public static class RedisVectorDocument {

    private final String documentId;
    private final String content;
    private final float[] embedding;
    private final Map<String, Object> metadata;

    public RedisVectorDocument(
        String documentId,
        String content,
        float[] embedding,
        Map<String, Object> metadata
    ) {
      this.documentId = documentId;
      this.content = content;
      this.embedding = embedding;
      this.metadata = metadata;
    }
  }
}
