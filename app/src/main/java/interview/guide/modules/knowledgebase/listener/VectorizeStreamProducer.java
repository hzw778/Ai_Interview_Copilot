package interview.guide.modules.knowledgebase.listener;

import interview.guide.common.async.AbstractStreamProducer;
import interview.guide.common.constant.AsyncTaskStreamConstants;
import interview.guide.infrastructure.redis.RedisService;
import interview.guide.modules.knowledgebase.model.VectorStatus;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class VectorizeStreamProducer extends AbstractStreamProducer<Long> {

  private final KnowledgeBaseRepository knowledgeBaseRepository;

  public VectorizeStreamProducer(RedisService redisService, KnowledgeBaseRepository knowledgeBaseRepository) {
    super(redisService);
    this.knowledgeBaseRepository = knowledgeBaseRepository;
  }

  public void sendVectorizeTask(Long kbId) {
    sendTask(kbId);
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
  protected Map<String, String> buildMessage(Long kbId) {
    return Map.of(
        AsyncTaskStreamConstants.FIELD_KB_ID, kbId.toString(),
        AsyncTaskStreamConstants.FIELD_RETRY_COUNT, "0"
    );
  }

  @Override
  protected String payloadIdentifier(Long kbId) {
    return "kbId=" + kbId;
  }

  @Override
  protected void onSendFailed(Long kbId, String error) {
    knowledgeBaseRepository.findById(kbId).ifPresent(knowledgeBase -> {
      knowledgeBase.setVectorStatus(VectorStatus.FAILED);
      knowledgeBase.setVectorError(truncateError(error));
      knowledgeBaseRepository.save(knowledgeBase);
    });
  }
}
