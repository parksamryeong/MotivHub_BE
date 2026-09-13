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
        removeFromActiveKeys(taskId, field);
    }

    /**
     * 활성 키 목록에서만 제거한다(버퍼/메타 데이터는 건드리지 않음). 버퍼가 TTL로 소멸했는데 활성
     * 키 엔트리만 남은 "유령 키"를 폴러가 발견했을 때 정리하는 용도 - 활성 키 집합에는 TTL을 걸 수
     * 없으므로(집합 전체가 한 키라서 살아있는 다른 버퍼까지 같이 죽는다) 이렇게 발견 시점에 지운다.
     */
    public void removeFromActiveKeys(Long taskId, TaskEditableField field) {
        redisTemplate.opsForSet().remove(ACTIVE_KEYS, activeKeyId(taskId, field));
    }

    public Set<String> activeBufferKeys() {
        Set<String> members = redisTemplate.opsForSet().members(ACTIVE_KEYS);
        return members == null ? Set.of() : members;
    }

    public void markRequested(Long taskId, TaskEditableField field) {
        String metaKey = metaKey(taskId, field);
        hashOperations.put(metaKey, FIELD_LAST_REQUESTED_AT, String.valueOf(Instant.now().toEpochMilli()));
        // TTL은 여기서 갱신하지 않는다 - appendUpdate가 bufferKey/metaKey 둘 다 같은 시점에 TTL을
        // 걸어두므로 메타 키만 따로 만료될 일은 없다. 여기서 metaKey의 TTL만 갱신하면 폴러가
        // retry-seconds마다 이 메서드를 호출할 때마다 metaKey가 계속 연장되면서, bufferKey는 원래
        // TTL대로 만료되는데 metadata()는 계속 값을 반환해 저장 요청이 무한히 재발송되는 회귀가
        // 생긴다(최종 리뷰 재검토에서 발견, 되돌림).
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
