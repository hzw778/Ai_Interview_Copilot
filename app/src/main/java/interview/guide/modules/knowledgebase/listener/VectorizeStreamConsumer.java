package interview.guide.modules.knowledgebase.listener;

import interview.guide.common.async.AbstractStreamConsumer;
import interview.guide.common.constant.AsyncTaskStreamConstants;
import interview.guide.infrastructure.redis.RedisService;
import interview.guide.modules.knowledgebase.model.VectorStatus;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import interview.guide.modules.knowledgebase.service.KnowledgeBaseVectorService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.StreamMessageId;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class VectorizeStreamConsumer extends AbstractStreamConsumer<Long> {

  private final KnowledgeBaseVectorService vectorService;
  private final KnowledgeBaseRepository knowledgeBaseRepository;

  public VectorizeStreamConsumer(
      RedisService redisService,
      KnowledgeBaseVectorService vectorService,
      KnowledgeBaseRepository knowledgeBaseRepository
  ) {
    super(redisService);
    this.vectorService = vectorService;
    this.knowledgeBaseRepository = knowledgeBaseRepository;
  }

  @Override
  protected String taskDisplayName() {
    return "知识库向量化";
  }

  @Override
  protected String streamKey() {
    return AsyncTaskStreamConstants.KB_VECTORIZE_STREAM_KEY;
  }

  @Override
  protected String groupName() {
    return AsyncTaskStreamConstants.KB_VECTORIZE_GROUP_NAME;
  }

  @Override
  protected String consumerPrefix() {
    return AsyncTaskStreamConstants.KB_VECTORIZE_CONSUMER_PREFIX;
  }

  @Override
  protected String threadName() {
    return "vectorize-consumer";
  }

  @Override
  protected Long parsePayload(StreamMessageId messageId, Map<String, String> data) {
    String kbId = data.get(AsyncTaskStreamConstants.FIELD_KB_ID);
    if (kbId == null || kbId.isBlank()) {
      log.warn("知识库向量化消息缺少 kbId: messageId={}", messageId);
      return null;
    }
    return Long.parseLong(kbId);
  }

  @Override
  protected String payloadIdentifier(Long kbId) {
    return "kbId=" + kbId;
  }

  @Override
  protected void markProcessing(Long kbId) {
    updateVectorStatus(kbId, VectorStatus.PROCESSING, null);
  }

  @Override
  protected void processBusiness(Long kbId) {
    vectorService.vectorizeKnowledgeBase(kbId);
  }

  @Override
  protected void markCompleted(Long kbId) {
    updateVectorStatus(kbId, VectorStatus.COMPLETED, null);
  }

  @Override
  protected void markFailed(Long kbId, String error) {
    updateVectorStatus(kbId, VectorStatus.FAILED, error);
  }

  @Override
  protected void retryMessage(Long kbId, int retryCount) {
    try {
      redisService().streamAdd(
          AsyncTaskStreamConstants.KB_VECTORIZE_STREAM_KEY,
          Map.of(
              AsyncTaskStreamConstants.FIELD_KB_ID, kbId.toString(),
              AsyncTaskStreamConstants.FIELD_RETRY_COUNT, String.valueOf(retryCount)
          ),
          AsyncTaskStreamConstants.STREAM_MAX_LEN
      );
      log.info("知识库向量化任务重新入队: kbId={}, retryCount={}", kbId, retryCount);
    } catch (Exception e) {
      log.error("知识库向量化任务重试入队失败: kbId={}", kbId, e);
      updateVectorStatus(kbId, VectorStatus.FAILED, truncateError("重试入队失败: " + e.getMessage()));
    }
  }

  private void updateVectorStatus(Long kbId, VectorStatus status, String error) {
    try {
      knowledgeBaseRepository.findById(kbId).ifPresent(knowledgeBase -> {
        knowledgeBase.setVectorStatus(status);
        knowledgeBase.setVectorError(error);
        knowledgeBaseRepository.save(knowledgeBase);
      });
    } catch (Exception e) {
      log.error("更新知识库向量化状态失败: kbId={}, status={}", kbId, status, e);
    }
  }
}
