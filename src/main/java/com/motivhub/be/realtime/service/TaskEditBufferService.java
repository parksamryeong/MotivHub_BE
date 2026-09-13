package com.motivhub.be.realtime.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class TaskEditBufferService {

    private static final String BUFFER_KEY_PREFIX = "task:edit-buffer:";
    private static final String META_KEY_PREFIX = "task:edit-buffer-meta:";
    private static final String ACTIVE_KEYS = "task:edit-buffer:active";
    // 정상 동작 중엔 저장 성공 시마다 삭제되므로 거의 발동하지 않는 안전망 성격의 TTL.
    private static final Duration BUFFER_TTL = Duration.ofHours(1);

    private static final String FIELD_FIRST_UPDATE_AT = "firstUpdateAt";
    private static final String FIELD_LAST_UPDATE_AT = "lastUpdateAt";
    private static final String FIELD_LAST_REQUESTED_AT = "lastRequestedAt";

    private final StringRedisTemplate redisTemplate;
    private final HashOperations<String, String, String> hashOperations;

    public TaskEditBufferService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.hashOperations = redisTemplate.opsForHash();
    }

    public void appendUpdate(Long taskId, TaskEditableField field, String base64Update) {
        String bufferKey = bufferKey(taskId, field);
        String metaKey = metaKey(taskId, field);
        String now = String.valueOf(Instant.now().toEpochMilli());

        redisTemplate.opsForList().rightPush(bufferKey, base64Update);
        hashOperations.putIfAbsent(metaKey, FIELD_FIRST_UPDATE_AT, now);
        hashOperations.put(metaKey, FIELD_LAST_UPDATE_AT, now);
        redisTemplate.expire(bufferKey, BUFFER_TTL);
        redisTemplate.expire(metaKey, BUFFER_TTL);
        redisTemplate.opsForSet().add(ACTIVE_KEYS, activeKeyId(taskId, field));
    }

    public List<String> listUpdates(Long taskId, TaskEditableField field) {
        List<String> values = redisTemplate.opsForList().range(bufferKey(taskId, field), 0, -1);
        return values == null ? List.of() : values;
    }

    public boolean isEmpty(Long taskId, TaskEditableField field) {
        Long size = redisTemplate.opsForList().size(bufferKey(taskId, field));
        return size == null || size == 0;
    }

    public void clear(Long taskId, TaskEditableField field) {
        redisTemplate.delete(bufferKey(taskId, field));
        redisTemplate.delete(metaKey(taskId, field));
        redisTemplate.opsForSet().remove(ACTIVE_KEYS, activeKeyId(taskId, field));
    }

    public Set<String> activeBufferKeys() {
        Set<String> members = redisTemplate.opsForSet().members(ACTIVE_KEYS);
        return members == null ? Set.of() : members;
    }

    public void markRequested(Long taskId, TaskEditableField field) {
        hashOperations.put(metaKey(taskId, field), FIELD_LAST_REQUESTED_AT,
                String.valueOf(Instant.now().toEpochMilli()));
    }

    public Optional<BufferMetadata> metadata(Long taskId, TaskEditableField field) {
        String metaKey = metaKey(taskId, field);
        String first = hashOperations.get(metaKey, FIELD_FIRST_UPDATE_AT);
        String last = hashOperations.get(metaKey, FIELD_LAST_UPDATE_AT);
        if (first == null || last == null) {
            return Optional.empty();
        }
        String requested = hashOperations.get(metaKey, FIELD_LAST_REQUESTED_AT);
        return Optional.of(new BufferMetadata(
                Instant.ofEpochMilli(Long.parseLong(first)),
                Instant.ofEpochMilli(Long.parseLong(last)),
                requested == null ? null : Instant.ofEpochMilli(Long.parseLong(requested))));
    }

    private String bufferKey(Long taskId, TaskEditableField field) {
        return BUFFER_KEY_PREFIX + taskId + ":" + field.pathSegment();
    }

    private String metaKey(Long taskId, TaskEditableField field) {
        return META_KEY_PREFIX + taskId + ":" + field.pathSegment();
    }

    private String activeKeyId(Long taskId, TaskEditableField field) {
        return taskId + ":" + field.pathSegment();
    }

    public record BufferMetadata(Instant firstUpdateAt, Instant lastUpdateAt, Instant lastRequestedAt) {
    }
}
