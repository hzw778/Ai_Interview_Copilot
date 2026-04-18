package interview.guide.modules.knowledgebase.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.ai.rag.vector")
public class KnowledgeBaseVectorProperties {

    /**
     * 单个 chunk 的目标 token 数。
     */
    private int chunkSize = 320;

    /**
     * chunk 最小字符数，避免切出太碎的片段。
     */
    private int minChunkSizeChars = 180;

    /**
     * 小于该长度的 chunk 不参与向量化。
     */
    private int minChunkLengthToEmbed = 40;

    /**
     * 单份知识库允许生成的最大 chunk 数。
     */
    private int maxNumChunks = 512;

    /**
     * 启动时自动修复 chunkCount 缺失的老知识库。
     */
    private boolean repairMissingChunkCountOnStartup = true;
}
