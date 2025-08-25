package com.c2c.c2c.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.net.URI;

/**
 * Redis 설정
 * 
 * 설계 근거:
 * - Docker Compose 환경에서 서비스명 기반 연결
 * - application.yml에서 호스트/포트 설정 분리
 * - C2CProperties를 통한 타입 안전한 설정 바인딩
 * - Lettuce 커넥션 팩토리 사용 (비동기 지원)
 */
@Configuration
@EnableConfigurationProperties(C2CProperties.class)
public class RedisConfig {
    
    private final C2CProperties properties;
    
    public RedisConfig(C2CProperties properties) {
        this.properties = properties;
    }
    
    /**
     * Redis 연결 팩토리 설정
     * Docker Compose 환경에서 서비스명 'redis' 사용
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        try {
            String redisUrl = properties.getRedis().getUrl();
            URI redisUri = URI.create(redisUrl);
            
            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
            
            // Docker Compose에서는 서비스명으로 연결
            String host = redisUri.getHost();
            if (host == null || host.isEmpty()) {
                host = "redis"; // 기본값: Docker Compose 서비스명
            }
            config.setHostName(host);
            
            int port = redisUri.getPort();
            if (port <= 0) {
                port = 6379; // 기본 Redis 포트
            }
            config.setPort(port);
            
            // 인증 정보가 있다면 설정
            String userInfo = redisUri.getUserInfo();
            if (userInfo != null && userInfo.contains(":")) {
                String[] credentials = userInfo.split(":");
                if (credentials.length >= 2) {
                    config.setUsername(credentials[0]);
                    config.setPassword(credentials[1]);
                }
            } else if (userInfo != null && !userInfo.isEmpty()) {
                config.setPassword(userInfo); // 비밀번호만 있는 경우
            }
            
            // Lettuce 커넥션 팩토리 (비동기 지원, 스레드 안전)
            LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
            factory.setValidateConnection(true);
            
            return factory;
            
        } catch (Exception e) {
            throw new RuntimeException("Redis 연결 설정 실패: " + properties.getRedis().getUrl(), e);
        }
    }
    
    /**
     * RedisTemplate 설정
     * 문자열 키, JSON 값 직렬화
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // 키는 문자열 직렬화
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        
        // 값은 JSON 직렬화 (ObjectMapper 공통 사용)
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        
        // 기본 직렬화 설정
        template.setDefaultSerializer(stringSerializer);
        
        template.afterPropertiesSet();
        return template;
    }
    
    /**
     * Redis Message Listener Container 설정
     * Pub/Sub 메시지 수신용
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        
        // 스레드 풀 설정 (Docker 환경에서 리소스 고려)
        container.setTaskExecutor(null); // 기본 SimpleAsyncTaskExecutor 사용
        
        // 에러 핸들러 설정
        container.setErrorHandler(throwable -> {
            // 로깅은 RedisMessageBroker에서 처리
            // 여기서는 컨테이너 재시작만 수행
            if (!container.isRunning()) {
                container.start();
            }
        });
        
        return container;
    }
    
    /**
     * ObjectMapper Bean 설정
     * JSON 직렬화/역직렬화용 (공통 사용)
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}