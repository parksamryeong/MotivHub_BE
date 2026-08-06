package com.motivhub.be.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.motivhub.be.auth.dto.TokenPair;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TempAuthCodeService {

    private static final String KEY_PREFIX = "temp-auth-code:";
    private static final Duration TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public TempAuthCodeService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public String issue(TokenPair tokens) {
        String code = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(KEY_PREFIX + code, writeJson(tokens), TTL);
        return code;
    }

    public Optional<TokenPair> consume(String code) {
        String key = KEY_PREFIX + code;
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return Optional.empty();
        }
        redisTemplate.delete(key);
        return Optional.of(readJson(value));
    }

    private String writeJson(TokenPair tokens) {
        try {
            return objectMapper.writeValueAsString(tokens);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("TokenPair 직렬화에 실패했습니다.", e);
        }
    }

    private TokenPair readJson(String value) {
        try {
            return objectMapper.readValue(value, TokenPair.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("TokenPair 역직렬화에 실패했습니다.", e);
        }
    }
}
