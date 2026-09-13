package com.motivhub.be.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.motivhub.be.support.AbstractIntegrationTest;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

class TaskEditBufferServiceTest extends AbstractIntegrationTest {

    @Autowired private TaskEditBufferService bufferService;
    @Autowired private StringRedisTemplate redisTemplate;

    @Test
    void newBufferIsEmpty() {
        assertThat(bufferService.isEmpty(9001L, TaskEditableField.DESCRIPTION)).isTrue();
        assertThat(bufferService.listUpdates(9001L, TaskEditableField.DESCRIPTION)).isEmpty();
    }

    @Test
    void appendingUpdateMakesBufferNonEmptyAndPreservesOrder() {
        bufferService.appendUpdate(9002L, TaskEditableField.DESCRIPTION, "update-1");
        bufferService.appendUpdate(9002L, TaskEditableField.DESCRIPTION, "update-2");

        assertThat(bufferService.isEmpty(9002L, TaskEditableField.DESCRIPTION)).isFalse();
        assertThat(bufferService.listUpdates(9002L, TaskEditableField.DESCRIPTION))
                .containsExactly("update-1", "update-2");
    }

    @Test
    void descriptionAndNoteBuffersForSameTaskAreIndependent() {
        bufferService.appendUpdate(9003L, TaskEditableField.DESCRIPTION, "desc-update");
        bufferService.appendUpdate(9003L, TaskEditableField.NOTE, "note-update");

        assertThat(bufferService.listUpdates(9003L, TaskEditableField.DESCRIPTION)).containsExactly("desc-update");
        assertThat(bufferService.listUpdates(9003L, TaskEditableField.NOTE)).containsExactly("note-update");
    }

    @Test
    void appendingRecordsFirstAndLastUpdateMetadata() {
        bufferService.appendUpdate(9004L, TaskEditableField.DESCRIPTION, "update-1");

        TaskEditBufferService.BufferMetadata metadata =
                bufferService.metadata(9004L, TaskEditableField.DESCRIPTION).orElseThrow();

        assertThat(metadata.firstUpdateAt()).isNotNull();
        assertThat(metadata.lastUpdateAt()).isNotNull();
        assertThat(metadata.lastRequestedAt()).isNull();
    }

    @Test
    void firstUpdateAtDoesNotChangeOnSubsequentAppends() {
        bufferService.appendUpdate(9005L, TaskEditableField.DESCRIPTION, "update-1");
        var first = bufferService.metadata(9005L, TaskEditableField.DESCRIPTION).orElseThrow().firstUpdateAt();

        bufferService.appendUpdate(9005L, TaskEditableField.DESCRIPTION, "update-2");
        var second = bufferService.metadata(9005L, TaskEditableField.DESCRIPTION).orElseThrow().firstUpdateAt();

        assertThat(second).isEqualTo(first);
    }

    @Test
    void metadataIsEmptyWhenNoUpdatesAppended() {
        assertThat(bufferService.metadata(9006L, TaskEditableField.DESCRIPTION)).isEmpty();
    }

    @Test
    void markRequestedSetsLastRequestedAt() {
        bufferService.appendUpdate(9007L, TaskEditableField.DESCRIPTION, "update-1");

        bufferService.markRequested(9007L, TaskEditableField.DESCRIPTION);

        TaskEditBufferService.BufferMetadata metadata =
                bufferService.metadata(9007L, TaskEditableField.DESCRIPTION).orElseThrow();
        assertThat(metadata.lastRequestedAt()).isNotNull();
    }

    @Test
    void appendingAddsKeyToActiveBufferKeys() {
        bufferService.appendUpdate(9008L, TaskEditableField.NOTE, "update-1");

        assertThat(bufferService.activeBufferKeys()).contains("9008:note");
    }

    @Test
    void clearingRemovesBufferMetadataAndActiveKey() {
        bufferService.appendUpdate(9009L, TaskEditableField.DESCRIPTION, "update-1");

        bufferService.clear(9009L, TaskEditableField.DESCRIPTION);

        assertThat(bufferService.isEmpty(9009L, TaskEditableField.DESCRIPTION)).isTrue();
        assertThat(bufferService.metadata(9009L, TaskEditableField.DESCRIPTION)).isEmpty();
        assertThat(bufferService.activeBufferKeys()).doesNotContain("9009:description");
    }

    @Test
    void appendingSetsExpirationOnBufferKey() {
        bufferService.appendUpdate(9010L, TaskEditableField.DESCRIPTION, "update-1");

        Long ttlSeconds = redisTemplate.getExpire("task:edit-buffer:9010:description", TimeUnit.SECONDS);

        assertThat(ttlSeconds).isPositive();
    }
}
