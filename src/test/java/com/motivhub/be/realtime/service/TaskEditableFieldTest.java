package com.motivhub.be.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TaskEditableFieldTest {

    @Test
    void fromPathSegmentResolvesDescription() {
        assertThat(TaskEditableField.fromPathSegment("description")).isEqualTo(TaskEditableField.DESCRIPTION);
    }

    @Test
    void fromPathSegmentResolvesNote() {
        assertThat(TaskEditableField.fromPathSegment("note")).isEqualTo(TaskEditableField.NOTE);
    }

    @Test
    void fromPathSegmentRejectsUnknownValue() {
        assertThatThrownBy(() -> TaskEditableField.fromPathSegment("status"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pathSegmentRoundTripsForEveryValue() {
        for (TaskEditableField field : TaskEditableField.values()) {
            assertThat(TaskEditableField.fromPathSegment(field.pathSegment())).isEqualTo(field);
        }
    }
}
