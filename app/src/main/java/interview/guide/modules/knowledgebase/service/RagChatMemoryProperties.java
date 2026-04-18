package interview.guide.modules.knowledgebase.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "app.ai.rag.memory")
public class RagChatMemoryProperties {

  private Duration ttl = Duration.ofHours(24);
  private int maxMessages = 12;
}
