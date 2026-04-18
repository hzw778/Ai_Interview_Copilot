package interview.guide.common.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.vectorstore.redis.autoconfigure.RedisVectorStoreProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import redis.clients.jedis.JedisPooled;

/**
 * Manual RedisVectorStore configuration for Spring AI 1.0.
 * The starter auto-configuration requires JedisConnectionFactory, while this project uses Lettuce by default.
 */
@Configuration
@EnableConfigurationProperties(RedisVectorStoreProperties.class)
public class RedisVectorStoreConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public JedisPooled jedisPooled(RedisProperties redisProperties) {
        String host = StringUtils.hasText(redisProperties.getHost()) ? redisProperties.getHost() : "127.0.0.1";
        int port = redisProperties.getPort() > 0 ? redisProperties.getPort() : 6379;
        int database = redisProperties.getDatabase();

        String uri;
        if (StringUtils.hasText(redisProperties.getPassword())) {
            uri = String.format("redis://:%s@%s:%d/%d", redisProperties.getPassword(), host, port, database);
        } else {
            uri = String.format("redis://%s:%d/%d", host, port, database);
        }
        return new JedisPooled(uri);
    }

    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    public VectorStore vectorStore(
        EmbeddingModel embeddingModel,
        JedisPooled jedisPooled,
        RedisVectorStoreProperties redisVectorStoreProperties
    ) {
        return RedisVectorStore.builder(jedisPooled, embeddingModel)
            .indexName(redisVectorStoreProperties.getIndexName())
            .prefix(redisVectorStoreProperties.getPrefix())
            .initializeSchema(redisVectorStoreProperties.isInitializeSchema())
            .metadataFields(
                RedisVectorStore.MetadataField.tag("kb_id"),
                RedisVectorStore.MetadataField.text("source_name"),
                RedisVectorStore.MetadataField.text("source_file_name"),
                RedisVectorStore.MetadataField.numeric("chunk_index")
            )
            .build();
    }
}
