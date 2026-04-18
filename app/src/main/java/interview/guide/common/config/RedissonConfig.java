package interview.guide.common.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Programmatic Redisson configuration that avoids sending AUTH when Redis has no password.
 */
@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(RedisProperties redisProperties) {
        Config config = new Config();
        String host = redisProperties.getHost() != null ? redisProperties.getHost() : "127.0.0.1";
        int port = redisProperties.getPort() != 0 ? redisProperties.getPort() : 6379;

        SingleServerConfig singleServerConfig = config.useSingleServer()
            .setAddress("redis://" + host + ":" + port)
            .setDatabase(redisProperties.getDatabase());

        if (redisProperties.getTimeout() != null) {
            singleServerConfig.setTimeout((int) redisProperties.getTimeout().toMillis());
            singleServerConfig.setConnectTimeout((int) redisProperties.getTimeout().toMillis());
        }

        if (StringUtils.hasText(redisProperties.getPassword())) {
            singleServerConfig.setPassword(redisProperties.getPassword());
        }

        return Redisson.create(config);
    }
}
